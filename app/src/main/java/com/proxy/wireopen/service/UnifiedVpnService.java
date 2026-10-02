package com.proxy.wireopen.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.net.VpnService;
import android.os.Build;

import androidx.core.app.NotificationCompat;

import com.proxy.wireopen.MainActivity;
import com.proxy.wireopen.engine.IVpnEngine;
import com.proxy.wireopen.engine.OpenVpnEngine;
import com.proxy.wireopen.engine.WireGuardEngine;
import com.proxy.wireopen.model.ConnectionRecord;
import com.proxy.wireopen.model.ConnectionState;
import com.proxy.wireopen.model.ProtocolType;
import com.proxy.wireopen.model.ProxyProfile;
import com.proxy.wireopen.util.LogManager;
import com.proxy.wireopen.util.TrafficStatsManager;

import java.io.BufferedReader;
import java.io.FileReader;
import java.net.InetAddress;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Unified Android VpnService supporting both WireGuard and OpenVPN protocols.
 * Fully compatible with Android 16+ (API 36).
 *
 * Connection capture: polls /proc/net/tcp and /proc/net/udp every 500ms to detect
 * new real connections, resolves IPs to hostnames via async reverse DNS,
 * and reports them to TrafficStatsManager for display in the Requests tab.
 */
public class UnifiedVpnService extends VpnService {
    public static final String ACTION_START_VPN = "com.proxy.wireopen.START_VPN";
    public static final String ACTION_STOP_VPN = "com.proxy.wireopen.STOP_VPN";
    public static final String ACTION_STATE_CHANGED = "com.proxy.wireopen.STATE_CHANGED";
    public static final String ACTION_TRAFFIC_UPDATE = "com.proxy.wireopen.TRAFFIC_UPDATE";
    /** Broadcast sent when a new real connection record is captured from /proc/net */
    public static final String ACTION_CONNECTION_CAPTURED = "com.proxy.wireopen.CONNECTION_CAPTURED";

    public static final String EXTRA_PROFILE = "extra_profile";
    public static final String EXTRA_STATE = "extra_state";
    public static final String EXTRA_RX_BYTES = "extra_rx_bytes";
    public static final String EXTRA_TX_BYTES = "extra_tx_bytes";

    private static final String CHANNEL_ID = "wireopen_vpn_channel";
    private static final int NOTIFICATION_ID = 1001;

    private static ConnectionState currentState = ConnectionState.DISCONNECTED;
    private static ProxyProfile currentProfile = null;

    private IVpnEngine activeEngine;

    // Connection monitor fields
    private final AtomicBoolean monitorRunning = new AtomicBoolean(false);
    private Thread monitorThread = null;

    public static ConnectionState getCurrentState() { return currentState; }
    public static ProxyProfile getCurrentProfile() { return currentProfile; }

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_NOT_STICKY;
        String action = intent.getAction();
        if (ACTION_START_VPN.equals(action)) {
            ProxyProfile profile = (ProxyProfile) intent.getSerializableExtra(EXTRA_PROFILE);
            if (profile != null) startTunnel(profile);
        } else if (ACTION_STOP_VPN.equals(action)) {
            stopTunnel();
        }
        return START_NOT_STICKY;
    }

    private synchronized void startTunnel(ProxyProfile profile) {
        if (activeEngine != null && activeEngine.isRunning()) {
            activeEngine.stop();
        }
        stopMonitor();

        currentProfile = profile;
        updateState(ConnectionState.CONNECTING);
        LogManager.log("UnifiedVpnService", "正在启动代理服务: " + profile.getName() + " [" + profile.getProtocolType() + "]");

        Notification notification = buildNotification("正在连接代理...", profile.getName());
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }

        if (profile.getProtocolType() == ProtocolType.WIREGUARD) {
            activeEngine = new WireGuardEngine();
        } else {
            activeEngine = new OpenVpnEngine();
        }

        new Thread(() -> {
            try {
                activeEngine.start(this, profile, new IVpnEngine.EngineCallback() {
                    @Override
                    public void onConnected() {
                        updateState(ConnectionState.CONNECTED);
                        LogManager.log("UnifiedVpnService", "代理已成功建立连接");
                        updateNotification("已连接: " + profile.getName(), "节点: " + profile.getEndpointDisplay());
                        // Start /proc/net connection monitor after tunnel is confirmed up
                        startMonitor(profile);
                    }

                    @Override
                    public void onDisconnected() {
                        stopMonitor();
                        updateState(ConnectionState.DISCONNECTED);
                        LogManager.log("UnifiedVpnService", "代理连接已断开");
                        removeForegroundNotification();
                        stopSelf();
                    }

                    @Override
                    public void onError(String message) {
                        stopMonitor();
                        updateState(ConnectionState.ERROR);
                        LogManager.log("UnifiedVpnService", "代理发生错误: " + message);
                        updateNotification("代理连接出错", message);
                    }

                    @Override
                    public void onTrafficUpdate(long rxBytes, long txBytes) {
                        broadcastTraffic(rxBytes, txBytes);
                    }
                });
            } catch (Exception e) {
                stopMonitor();
                LogManager.log("UnifiedVpnService", "隧道启动失败: " + e.getMessage());
                updateState(ConnectionState.ERROR);
                removeForegroundNotification();
                stopSelf();
            }
        }).start();
    }

    private synchronized void stopTunnel() {
        stopMonitor();
        updateState(ConnectionState.DISCONNECTING);
        LogManager.log("UnifiedVpnService", "正在断开代理连接...");
        if (activeEngine != null) {
            activeEngine.stop();
            activeEngine = null;
        }
        updateState(ConnectionState.DISCONNECTED);
        currentProfile = null;
        removeForegroundNotification();
        stopSelf();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // /proc/net Connection Monitor
    //
    // /proc/net/tcp and /proc/net/udp expose the kernel's socket table.
    // Each line: sl local_addr rem_addr state ... uid ...
    //   local_addr / rem_addr format: HHHHHHHH:PPPP  (hex IP little-endian : hex port)
    //   For TCP state 01 = ESTABLISHED, 0A = LISTEN (skip LISTEN)
    //   For UDP all entries are "active" sockets
    //
    // We diff the set of remote endpoints against what we've already reported,
    // and for each new one:
    //   1. Async-resolve hostname via InetAddress.getByAddress().getHostName()
    //   2. Add a ConnectionRecord to TrafficStatsManager
    //   3. Broadcast ACTION_CONNECTION_CAPTURED so MainActivity refreshes its list
    // ─────────────────────────────────────────────────────────────────────────

    private void startMonitor(ProxyProfile profile) {
        String nodeName = profile.getName() != null ? profile.getName() : "活跃节点";

        // Get VPN tunnel endpoint port so we can skip it (it's internal WG/OVPN tunnel traffic)
        int vpnPort = 0;
        if (profile.getProtocolType() == ProtocolType.WIREGUARD && profile.getWireGuardConfig() != null) {
            vpnPort = profile.getWireGuardConfig().getEndpointPort();
        } else if (profile.getOpenVpnConfig() != null) {
            vpnPort = profile.getOpenVpnConfig().getRemotePort();
        }
        final int skipPort = vpnPort;

        monitorRunning.set(true);
        monitorThread = new Thread(() -> runMonitor(nodeName, skipPort), "ProcNetMonitor");
        monitorThread.setDaemon(true);
        monitorThread.start();
        LogManager.log("ProcNetMonitor", "网络连接监控器已启动 (基于 /proc/net/tcp+udp)");
    }

    private void stopMonitor() {
        monitorRunning.set(false);
        if (monitorThread != null) {
            monitorThread.interrupt();
            monitorThread = null;
        }
    }

    /**
     * Main monitor loop: polls /proc/net/tcp and /proc/net/udp every 500ms,
     * diffs against known connections, resolves new ones asynchronously.
     */
    private void runMonitor(String nodeName, int skipPort) {
        // Tracks remote endpoints we've already reported: "proto:ip:port"
        Set<String> knownKeys = new HashSet<>();
        // Async DNS resolver pool (limited threads to avoid flooding)
        ExecutorService dnsPool = Executors.newFixedThreadPool(4);

        try {
            while (monitorRunning.get()) {
                scanProcNet("/proc/net/tcp",  "tcp", knownKeys, nodeName, skipPort, dnsPool);
                scanProcNet("/proc/net/tcp6", "tcp", knownKeys, nodeName, skipPort, dnsPool);
                scanProcNet("/proc/net/udp",  "udp", knownKeys, nodeName, skipPort, dnsPool);
                scanProcNet("/proc/net/udp6", "udp", knownKeys, nodeName, skipPort, dnsPool);
                Thread.sleep(500);
            }
        } catch (InterruptedException ignored) {
        } catch (Exception e) {
            LogManager.log("ProcNetMonitor", "监控异常: " + e.getMessage());
        } finally {
            dnsPool.shutdownNow();
            LogManager.log("ProcNetMonitor", "网络连接监控器已停止");
        }
    }

    /**
     * Scan a single /proc/net file and emit ConnectionRecords for new remote endpoints.
     *
     * /proc/net/tcp line format (space-separated, after header):
     *   0: sl  1: local_addr  2: rem_addr  3: state  4: tx:rx  5: tr:tm  6: retrans  7: uid ...
     *
     * IP format: "HHHHHHHH:PPPP" where HHHHHHHH is 32-bit IPv4 in host (little-endian) byte order
     * Port format: 4 hex digits, big-endian
     *
     * TCP states we care about: 01=ESTABLISHED (skip 0A=LISTEN and others)
     * UDP: all non-zero remote ports are active
     */
    private void scanProcNet(String procFile, String proto, Set<String> knownKeys,
                             String nodeName, int skipPort, ExecutorService dnsPool) {
        try (BufferedReader br = new BufferedReader(new FileReader(procFile))) {
            String line;
            boolean firstLine = true;
            while ((line = br.readLine()) != null) {
                if (firstLine) { firstLine = false; continue; } // skip header
                line = line.trim();
                if (line.isEmpty()) continue;

                String[] parts = line.split("\\s+");
                if (parts.length < 4) continue;

                String remAddrField = parts[2]; // "HHHHHHHH:PPPP"
                String stateHex = parts[3];

                // For TCP: only ESTABLISHED (state 01)
                if ("tcp".equals(proto) && !"01".equalsIgnoreCase(stateHex)) continue;
                // For UDP: skip if remote address is all-zeros (unconnected socket)
                if ("udp".equals(proto) && remAddrField.startsWith("00000000:0000")) continue;

                String[] addrParts = remAddrField.split(":");
                if (addrParts.length < 2) continue;

                String hexIp = addrParts[0];
                String hexPort = addrParts[1];

                // Skip loopback and zero addresses
                if ("00000000".equalsIgnoreCase(hexIp) || "7F000001".equalsIgnoreCase(hexIp)
                        || "0100007F".equalsIgnoreCase(hexIp)) continue;

                int port;
                try {
                    port = Integer.parseInt(hexPort, 16);
                } catch (NumberFormatException e) { continue; }

                if (port == 0) continue;
                if (skipPort > 0 && port == skipPort) continue;

                // Parse IP from hex (IPv4 only for now; skip IPv6 128-bit addresses)
                String ip;
                if (hexIp.length() == 8) {
                    ip = hexIpv4ToStr(hexIp);
                } else {
                    continue; // skip IPv6 for simplicity
                }

                String key = proto + ":" + ip + ":" + port;
                if (knownKeys.contains(key)) continue;
                knownKeys.add(key);

                // Resolve hostname asynchronously and emit record
                final String finalIp = ip;
                final int finalPort = port;
                final String finalProto = proto;
                dnsPool.submit(() -> {
                    String hostname = resolveHostname(finalIp);
                    String targetUrl = finalProto + "://" + hostname + ":" + finalPort;

                    ConnectionRecord record = new ConnectionRecord(
                            targetUrl, "刚刚", 0, 0, "🌐", nodeName, "GLOBAL");
                    TrafficStatsManager.getInstance().addConnection(record);

                    // Cache for future lookups
                    if (!hostname.equals(finalIp)) {
                        TrafficStatsManager.getInstance().cacheDns(finalIp, hostname);
                    }

                    // Broadcast to MainActivity for real-time list refresh
                    Intent broadcast = new Intent(ACTION_CONNECTION_CAPTURED);
                    broadcast.setPackage(getPackageName());
                    sendBroadcast(broadcast);
                });
            }
        } catch (java.io.FileNotFoundException ignored) {
            // /proc/net/tcp6 may not exist on all devices — silently skip
        } catch (Exception e) {
            LogManager.log("ProcNetMonitor", "解析 " + procFile + " 异常: " + e.getMessage());
        }
    }

    /**
     * Convert a hex IPv4 address string from /proc/net to dotted-decimal.
     * /proc/net stores 32-bit IPv4 in host (little-endian on ARM) byte order.
     *
     * Example: "0100007F" → 127.0.0.1
     *          "0101A8C0" → 192.168.1.1
     */
    private static String hexIpv4ToStr(String hexIp) {
        // Parse as unsigned 32-bit, then extract bytes in little-endian order
        long val = Long.parseLong(hexIp, 16);
        int b0 = (int)(val & 0xFF);           // byte 0 (least significant)
        int b1 = (int)((val >> 8) & 0xFF);
        int b2 = (int)((val >> 16) & 0xFF);
        int b3 = (int)((val >> 24) & 0xFF);   // byte 3 (most significant)
        return String.format(Locale.US, "%d.%d.%d.%d", b0, b1, b2, b3);
    }

    /**
     * Resolve an IP address to hostname via reverse DNS.
     * Falls back to the raw IP string if lookup fails or times out.
     * Caches results to avoid repeated lookups.
     */
    private String resolveHostname(String ip) {
        // Check DNS cache first (populated from previous lookups)
        String cached = TrafficStatsManager.getInstance().resolveHostname(ip);
        if (!cached.equals(ip)) return cached; // cache hit

        try {
            // Parse IP bytes
            String[] parts = ip.split("\\.");
            byte[] addr = new byte[4];
            for (int i = 0; i < 4; i++) addr[i] = (byte) Integer.parseInt(parts[i]);
            InetAddress inetAddr = InetAddress.getByAddress(addr);

            // getHostName() performs a reverse DNS lookup (PTR record)
            String hostName = inetAddr.getHostName();
            if (hostName != null && !hostName.isEmpty() && !hostName.equals(ip)) {
                // Strip trailing dot from DNS name if present
                if (hostName.endsWith(".")) hostName = hostName.substring(0, hostName.length() - 1);
                TrafficStatsManager.getInstance().cacheDns(ip, hostName);
                return hostName;
            }
        } catch (Exception ignored) {}
        return ip; // fallback to raw IP
    }

    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public void onRevoke() {
        LogManager.log("UnifiedVpnService", "系统已撤回 VPN 权限");
        stopTunnel();
        super.onRevoke();
    }

    @Override
    public void onDestroy() {
        stopTunnel();
        super.onDestroy();
    }

    private void removeForegroundNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE);
        } else {
            stopForeground(true);
        }
    }

    private void updateState(ConnectionState state) {
        currentState = state;
        Intent intent = new Intent(ACTION_STATE_CHANGED);
        intent.setPackage(getPackageName());
        intent.putExtra(EXTRA_STATE, state.name());
        sendBroadcast(intent);
    }

    private void broadcastTraffic(long rxBytes, long txBytes) {
        Intent intent = new Intent(ACTION_TRAFFIC_UPDATE);
        intent.setPackage(getPackageName());
        intent.putExtra(EXTRA_RX_BYTES, rxBytes);
        intent.putExtra(EXTRA_TX_BYTES, txBytes);
        sendBroadcast(intent);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "VPN 代理运行状态", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("显示 WireGuard & OpenVPN 代理软件的后台运行状态与流量");
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(channel);
        }
    }

    private Notification buildNotification(String title, String content) {
        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, intent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        Intent stopIntent = new Intent(this, UnifiedVpnService.class);
        stopIntent.setAction(ACTION_STOP_VPN);
        PendingIntent stopPendingIntent = PendingIntent.getService(
                this, 1, stopIntent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(content)
                .setSmallIcon(com.proxy.wireopen.R.drawable.ic_launcher)
                .setContentIntent(pendingIntent)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "断开连接", stopPendingIntent)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }

    private void updateNotification(String title, String content) {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.notify(NOTIFICATION_ID, buildNotification(title, content));
    }
}
