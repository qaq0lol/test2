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

        currentProfile = profile;
        updateState(ConnectionState.CONNECTING);
        LogManager.log("UnifiedVpnService", "正在启动代理服务: " + profile.getName() + " [" + profile.getProtocolType() + "]");

        // ── OpenVPN: 完全交由 ics-openvpn 的 OpenVPNService 独立管理 TUN ────────
        // Android 同一时刻只允许一个 VpnService 持有 TUN 接口。
        // 如果我们同时持有前台 VpnService 并再启动 OpenVPNService，
        // 系统会对我们发 onRevoke() 撤回授权，导致 OpenVPNService 永远拿不到 TUN，
        // 进而陷入"一直在连接中"死锁。
        // 正确做法：对 OpenVPN 协议，UnifiedVpnService 不持有 TUN，
        // 只做配置准备后立即退出，由 OpenVPNService 独立接管整个 VPN 生命周期。
        if (profile.getProtocolType() == ProtocolType.OPENVPN) {
            activeEngine = new OpenVpnEngine();
            new Thread(() -> {
                try {
                    activeEngine.start(this, profile, new IVpnEngine.EngineCallback() {
                        @Override
                        public void onConnected() {
                            currentState = ConnectionState.CONNECTED;
                            updateState(ConnectionState.CONNECTED);
                            LogManager.log("UnifiedVpnService", "OpenVPN 连接成功建立");
                        }

                        @Override
                        public void onDisconnected() {
                            currentState = ConnectionState.DISCONNECTED;
                            currentProfile = null;
                            updateState(ConnectionState.DISCONNECTED);
                            LogManager.log("UnifiedVpnService", "OpenVPN 连接已断开");
                        }

                        @Override
                        public void onError(String message) {
                            currentState = ConnectionState.ERROR;
                            updateState(ConnectionState.ERROR);
                            LogManager.log("UnifiedVpnService", "OpenVPN 错误: " + message);
                        }

                        @Override
                        public void onTrafficUpdate(long rxBytes, long txBytes) {
                            broadcastTraffic(rxBytes, txBytes);
                        }
                    });
                } catch (Exception e) {
                    LogManager.log("UnifiedVpnService", "OpenVPN 启动失败: " + e.getMessage());
                    updateState(ConnectionState.ERROR);
                }
                // OpenVPN 协议：由 OpenVPNService 独立持有 TUN 与前台服务
                // 将 activeEngine 置空，防止随后 stopSelf() 触发 onDestroy() 时误杀 OpenVPN 隧道
                activeEngine = null;
                stopSelf();
            }).start();
            return;
        }

        // ── WireGuard: 正常流程，UnifiedVpnService 持有 TUN ──────────────────────
        Notification notification = buildNotification("正在连接代理...", profile.getName());
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
            } else {
                startForeground(NOTIFICATION_ID, notification);
            }
        } catch (Exception fgsEx) {
            LogManager.log("UnifiedVpnService", "前台服务启动受限 (Android 14+): " + fgsEx.getMessage());
        }

        activeEngine = new WireGuardEngine();

        new Thread(() -> {
            try {
                activeEngine.start(this, profile, new IVpnEngine.EngineCallback() {
                    @Override
                    public void onConnected() {
                        updateState(ConnectionState.CONNECTED);
                        LogManager.log("UnifiedVpnService", "WireGuard 代理已成功建立连接");
                        updateNotification("已连接: " + profile.getName(), "节点: " + profile.getEndpointDisplay());
                    }

                    @Override
                    public void onDisconnected() {
                        updateState(ConnectionState.DISCONNECTED);
                        LogManager.log("UnifiedVpnService", "WireGuard 代理连接已断开");
                        removeForegroundNotification();
                        stopSelf();
                    }

                    @Override
                    public void onError(String message) {
                        updateState(ConnectionState.ERROR);
                        LogManager.log("UnifiedVpnService", "WireGuard 代理发生错误: " + message);
                        updateNotification("代理连接出错", message);
                    }

                    @Override
                    public void onTrafficUpdate(long rxBytes, long txBytes) {
                        broadcastTraffic(rxBytes, txBytes);
                    }
                });
            } catch (Exception e) {
                LogManager.log("UnifiedVpnService", "WireGuard 隧道启动失败: " + e.getMessage());
                updateState(ConnectionState.ERROR);
                removeForegroundNotification();
                stopSelf();
            }
        }).start();
    }

    private synchronized void stopTunnel() {
        updateState(ConnectionState.DISCONNECTING);
        LogManager.log("UnifiedVpnService", "正在断开代理连接...");
        if (activeEngine != null) {
            activeEngine.stop();
            activeEngine = null;
        }
        try {
            Intent disconnectIntent = new Intent();
            disconnectIntent.setClassName(getPackageName(), "de.blinkt.openvpn.core.OpenVPNService");
            disconnectIntent.setAction("de.blinkt.openvpn.DISCONNECT_VPN");
            startService(disconnectIntent);
        } catch (Exception ignored) {}

        updateState(ConnectionState.DISCONNECTED);
        currentProfile = null;
        removeForegroundNotification();
        stopSelf();
    }

    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public void onRevoke() {
        LogManager.log("UnifiedVpnService", "系统 VPN 权限接口回调");
        // 当为 OpenVPN 协议时，TUN 接口由 OpenVPN 官方原生服务接管，收到 onRevoke 属于正常交接，不能调用 stopTunnel 误杀连接
        if (activeEngine instanceof OpenVpnEngine) {
            LogManager.log("UnifiedVpnService", "底层 TUN 接口已由 OpenVPN 原生服务顺利接管");
            return;
        }
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
