package com.proxy.wireopen.util;

import android.content.Context;
import android.content.SharedPreferences;

import com.proxy.wireopen.model.ConnectionRecord;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

/**
 * TrafficStatsManager manages:
 * 1. Historical all-time traffic statistics with reset capability.
 * 2. Real-time current session statistics.
 * 3. FlClash-style connection request records — populated ONLY from real captured TUN packets.
 */
public class TrafficStatsManager {
    private static final String PREF_NAME = "wireopen_traffic_stats";
    private static final String KEY_ALL_TIME_RX = "all_time_rx_bytes";
    private static final String KEY_ALL_TIME_TX = "all_time_tx_bytes";
    private static final String KEY_CONNECT_COUNT = "connect_count";

    private static TrafficStatsManager instance;
    private final SharedPreferences prefs;

    // In-memory session stats
    private long sessionRxBytes = 0;
    private long sessionTxBytes = 0;
    private long sessionStartTime = 0;
    private boolean sessionActive = false;

    private String outboundIp = "--";
    private String remoteEndpoint = "--";
    private String protocolChannel = "--";
    private String virtualTunIp = "--";
    private double currentDownSpeed = 0.0;
    private double currentUpSpeed = 0.0;

    // Real captured connections only — no fake data
    private final List<ConnectionRecord> connections = Collections.synchronizedList(new ArrayList<>());

    // DNS reverse lookup: destination IP → resolved hostname (populated by packet sniffer)
    private final ConcurrentHashMap<String, String> dnsCache = new ConcurrentHashMap<>();

    // Dedup window: URL → last-seen timestamp, to collapse repeated packets within 2s
    private final ConcurrentHashMap<String, Long> recentKeys = new ConcurrentHashMap<>();

    public static synchronized void init(Context context) {
        if (instance == null) {
            instance = new TrafficStatsManager(context.getApplicationContext());
        }
    }

    public static synchronized TrafficStatsManager getInstance() {
        return instance;
    }

    private TrafficStatsManager(Context context) {
        this.prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        if (!prefs.contains(KEY_ALL_TIME_RX)) {
            prefs.edit()
                    .putLong(KEY_ALL_TIME_RX, 0L)
                    .putLong(KEY_ALL_TIME_TX, 0L)
                    .putInt(KEY_CONNECT_COUNT, 0)
                    .apply();
        }
        // Connections list intentionally empty — populated from real packet capture only
    }

    // ─── DNS Cache ───────────────────────────────────────────────────────────

    /** Store a DNS A/AAAA response mapping. Called by the TUN packet sniffer. */
    public void cacheDns(String ip, String hostname) {
        if (ip != null && !ip.isEmpty() && hostname != null && !hostname.isEmpty()) {
            dnsCache.put(ip, hostname);
        }
    }

    /** Return the cached hostname for an IP, or the raw IP string if unknown. */
    public String getCachedHostname(String ip) {
        String h = dnsCache.get(ip);
        return (h != null && !h.isEmpty()) ? h : ip;
    }

    /**
     * Set to avoid repeated resolution and broadcast for same IP:Port in current session
     */
    private final java.util.Set<String> knownConnections = Collections.synchronizedSet(new java.util.HashSet<>());
    private final java.util.concurrent.ExecutorService dnsPool = java.util.concurrent.Executors.newFixedThreadPool(4);

    /**
     * Async DNS resolver.
     */
    public void resolveHostnameAsync(String ip, String nodeName, String proto, int port, android.content.Context context) {
        String connKey = proto + ":" + ip + ":" + port;
        if (knownConnections.contains(connKey)) {
            return; // Already resolved or queued
        }
        knownConnections.add(connKey);

        String cached = getCachedHostname(ip);
        if (!cached.equals(ip)) {
            emitConnection(proto + "://" + cached + ":" + port, nodeName, context);
            return;
        }

        dnsPool.submit(() -> {
            try {
                String[] parts = ip.split("\\.");
                byte[] addr = new byte[4];
                for (int i = 0; i < 4; i++) addr[i] = (byte) Integer.parseInt(parts[i]);
                java.net.InetAddress inetAddr = java.net.InetAddress.getByAddress(addr);

                String hostName = inetAddr.getHostName();
                if (hostName != null && !hostName.isEmpty() && !hostName.equals(ip)) {
                    if (hostName.endsWith(".")) hostName = hostName.substring(0, hostName.length() - 1);
                    cacheDns(ip, hostName);
                }
            } catch (Exception ignored) {}
            String finalHostname = getCachedHostname(ip);
            emitConnection(proto + "://" + finalHostname + ":" + port, nodeName, context);
        });
    }

    private final android.os.Handler uiDebounceHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private Runnable uiDebounceRunnable;

    private void emitConnection(String targetUrl, String nodeName, android.content.Context context) {
        com.proxy.wireopen.model.ConnectionRecord record = new com.proxy.wireopen.model.ConnectionRecord(
                targetUrl, "刚刚", 0, 0, "🌐", nodeName, "GLOBAL");
        addConnection(record);

        if (context != null) {
            if (uiDebounceRunnable != null) {
                uiDebounceHandler.removeCallbacks(uiDebounceRunnable);
            }
            uiDebounceRunnable = () -> {
                android.content.Intent broadcast = new android.content.Intent("com.proxy.wireopen.CONNECTION_CAPTURED");
                broadcast.setPackage(context.getPackageName());
                context.sendBroadcast(broadcast);
            };
            uiDebounceHandler.postDelayed(uiDebounceRunnable, 200);
        }
    }

    // ─── Session lifecycle ───────────────────────────────────────────────────

    public synchronized void startSession(String endpoint, String protocol, String tunIp) {
        knownConnections.clear();
        this.sessionActive = true;
        this.sessionStartTime = System.currentTimeMillis();
        this.sessionRxBytes = 0;
        this.sessionTxBytes = 0;
        this.remoteEndpoint = endpoint != null ? endpoint : "--";
        this.protocolChannel = protocol != null ? protocol : "--";
        this.virtualTunIp = tunIp != null ? tunIp : "--";

        prefs.edit().putInt(KEY_CONNECT_COUNT, prefs.getInt(KEY_CONNECT_COUNT, 0) + 1).apply();

        // Clear stale records from previous session
        connections.clear();
        recentKeys.clear();
        dnsCache.clear();
    }

    public synchronized void updateSessionTraffic(long rxBytes, long txBytes, double downSpeed, double upSpeed) {
        long deltaRx = Math.max(0, rxBytes - this.sessionRxBytes);
        long deltaTx = Math.max(0, txBytes - this.sessionTxBytes);
        this.sessionRxBytes = rxBytes;
        this.sessionTxBytes = txBytes;
        this.currentDownSpeed = downSpeed;
        this.currentUpSpeed = upSpeed;
        if (deltaRx > 0 || deltaTx > 0) {
            prefs.edit()
                    .putLong(KEY_ALL_TIME_RX, getAllTimeRxBytes() + deltaRx)
                    .putLong(KEY_ALL_TIME_TX, getAllTimeTxBytes() + deltaTx)
                    .apply();
        }
    }

    public synchronized void stopSession() {
        this.sessionActive = false;
        this.currentDownSpeed = 0;
        this.currentUpSpeed = 0;
    }

    public synchronized boolean isSessionActive() { return sessionActive; }

    // ─── All-time statistics ─────────────────────────────────────────────────

    public synchronized long getAllTimeRxBytes() { return prefs.getLong(KEY_ALL_TIME_RX, 0L); }
    public synchronized long getAllTimeTxBytes() { return prefs.getLong(KEY_ALL_TIME_TX, 0L); }
    public synchronized long getAllTimeTotalBytes() { return getAllTimeRxBytes() + getAllTimeTxBytes(); }
    public synchronized int getConnectionCount() { return prefs.getInt(KEY_CONNECT_COUNT, 0); }

    public synchronized void resetAllTimeStats() {
        prefs.edit()
                .putLong(KEY_ALL_TIME_RX, 0L)
                .putLong(KEY_ALL_TIME_TX, 0L)
                .putInt(KEY_CONNECT_COUNT, 0)
                .apply();
        LogManager.log("TrafficStatsManager", "历史所有流量统计已彻底重置为零");
    }

    // ─── Session getters ─────────────────────────────────────────────────────

    public synchronized long getSessionRxBytes() { return sessionRxBytes; }
    public synchronized long getSessionTxBytes() { return sessionTxBytes; }
    public synchronized String getOutboundIp() { return outboundIp; }
    public synchronized void setOutboundIp(String ip) { this.outboundIp = ip; }
    public synchronized String getRemoteEndpoint() { return remoteEndpoint; }
    public synchronized String getProtocolChannel() { return protocolChannel; }
    public synchronized String getVirtualTunIp() { return virtualTunIp; }
    public synchronized double getCurrentDownSpeed() { return currentDownSpeed; }
    public synchronized double getCurrentUpSpeed() { return currentUpSpeed; }

    public synchronized String getSessionDurationFormatted() {
        if (!sessionActive || sessionStartTime <= 0) return "00:00:00";
        long d = (System.currentTimeMillis() - sessionStartTime) / 1000;
        return String.format(Locale.US, "%02d:%02d:%02d", d / 3600, (d % 3600) / 60, d % 60);
    }

    // ─── Connections Queue (real captured packets only) ──────────────────────

    public List<ConnectionRecord> getConnections() {
        synchronized (connections) {
            return new ArrayList<>(connections);
        }
    }

    /**
     * Add a real connection record captured from the TUN device.
     * Deduplicates entries seen within 2 seconds — accumulates bytes instead of inserting duplicate rows.
     */
    public void addConnection(ConnectionRecord record) {
        if (record == null) return;
        String key = record.getTargetUrl();
        long now = System.currentTimeMillis();
        Long last = recentKeys.get(key);
        if (last != null && (now - last) < 2000) {
            synchronized (connections) {
                for (ConnectionRecord c : connections) {
                    if (key.equals(c.getTargetUrl())) {
                        c.addRxBytes(record.getRxBytes());
                        c.addTxBytes(record.getTxBytes());
                        break;
                    }
                }
            }
            return;
        }
        recentKeys.put(key, now);
        connections.add(0, record);
        while (connections.size() > 500) {
            connections.remove(connections.size() - 1);
        }
    }

    public boolean removeConnection(String id) {
        if (id == null) return false;
        synchronized (connections) {
            Iterator<ConnectionRecord> it = connections.iterator();
            while (it.hasNext()) {
                if (id.equals(it.next().getId())) { it.remove(); return true; }
            }
        }
        return false;
    }

    public void clearAllConnections() {
        connections.clear();
        recentKeys.clear();
    }

    public static String formatBytesStatic(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format(Locale.US, "%.2f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format(Locale.US, "%.2f MB", bytes / (1024.0 * 1024.0));
        return String.format(Locale.US, "%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0));
    }
}
