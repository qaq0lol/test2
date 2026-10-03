package com.proxy.wireopen.engine;

import android.content.Context;
import android.content.Intent;
import android.net.VpnService;
import android.util.Log;

import com.proxy.wireopen.model.ProxyProfile;
import com.proxy.wireopen.util.CredentialManager;
import com.proxy.wireopen.util.LogManager;

import java.io.StringReader;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import de.blinkt.openvpn.VpnProfile;
import de.blinkt.openvpn.core.ConfigParser;
import de.blinkt.openvpn.core.ConnectionStatus;
import de.blinkt.openvpn.core.OpenVPNService;
import de.blinkt.openvpn.core.ProfileManager;
import de.blinkt.openvpn.core.VPNLaunchHelper;
import de.blinkt.openvpn.core.VpnStatus;

/**
 * Industrial-grade OpenVPN Engine powered by ics-openvpn (OpenVPN 2.7 / OpenSSL 3.4.1).
 * Supports full TLS handshakes, AES/ChaCha20 ciphers, and secure user/password authentication.
 *
 * Credentials from CredentialManager are strictly applied ONLY here, completely isolated from WireGuard.
 */
public class OpenVpnEngine implements IVpnEngine, VpnStatus.StateListener, VpnStatus.ByteCountListener {
    private static final String TAG = "OpenVpnEngine";

    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    private final AtomicLong rxBytes = new AtomicLong(0);
    private final AtomicLong txBytes = new AtomicLong(0);

    private Context appContext;
    private EngineCallback activeCallback;
    private boolean connectedNotified = false;

    @Override
    public synchronized void start(VpnService vpnService, ProxyProfile profile, EngineCallback callback) throws Exception {
        if (isRunning.get()) {
            return;
        }

        this.appContext = vpnService.getApplicationContext();
        this.activeCallback = callback;
        this.connectedNotified = false;
        this.rxBytes.set(0);
        this.txBytes.set(0);

        String rawConfig = profile.getRawConfig();
        if (rawConfig == null || rawConfig.trim().isEmpty()) {
            throw new IllegalArgumentException("OpenVPN 配置文件内容为空");
        }

        LogManager.log(TAG, "正在解析 OpenVPN 官方配置文件...");
        ConfigParser parser = new ConfigParser();
        try {
            parser.parseConfig(new StringReader(rawConfig));
        } catch (Exception e) {
            throw new IllegalArgumentException("OpenVPN 配置文件格式错误: " + e.getMessage(), e);
        }

        VpnProfile vpnProfile = parser.convertProfile();
        if (vpnProfile == null) {
            throw new IllegalStateException("无法从配置文件生成合法的 OpenVPN 运行配置");
        }

        String nodeName = profile.getName() != null ? profile.getName() : "OpenVPN Node";
        vpnProfile.mName = nodeName;

        // Apply OpenVPN credentials strictly from CredentialManager (Isolated from WireGuard)
        CredentialManager credMgr = CredentialManager.getInstance();
        if (credMgr != null && credMgr.hasCredentials()) {
            vpnProfile.mUsername = credMgr.getUsername();
            vpnProfile.mPassword = credMgr.getPassword();
            LogManager.log(TAG, "已为 OpenVPN 会话注入认证账号: " + vpnProfile.mUsername);
        }

        // Keep tun open across reconnects
        vpnProfile.mPersistTun = true;

        LogManager.log(TAG, "注册 OpenVPN 状态监听器与原生核心隧道...");
        VpnStatus.addStateListener(this);
        VpnStatus.addByteCountListener(this);

        isRunning.set(true);

        try {
            ProfileManager.setTemporaryProfile(appContext, vpnProfile);
            VPNLaunchHelper.startOpenVpn(vpnProfile, appContext, "WireOpenProxy", false);
            Log.i(TAG, "OpenVPN native tunnel launch requested for: " + nodeName);
            LogManager.log(TAG, "OpenVPN 官方原生引擎已启动 (OpenSSL 3.4 / OpenVPN 2.7 真实加密协商)");
        } catch (Exception e) {
            isRunning.set(false);
            VpnStatus.removeStateListener(this);
            VpnStatus.removeByteCountListener(this);
            throw new RuntimeException("启动 OpenVPN 原生引擎失败: " + e.getMessage(), e);
        }
    }

    @Override
    public synchronized void stop() {
        if (!isRunning.getAndSet(false)) {
            return;
        }

        VpnStatus.removeStateListener(this);
        VpnStatus.removeByteCountListener(this);

        if (appContext != null) {
            try {
                Intent disconnectIntent = new Intent(appContext, OpenVPNService.class);
                disconnectIntent.setAction(OpenVPNService.DISCONNECT_VPN);
                appContext.startService(disconnectIntent);
                Log.i(TAG, "OpenVPN disconnect intent sent");
            } catch (Exception e) {
                Log.e(TAG, "Error stopping OpenVPN service", e);
            }
        }

        LogManager.log(TAG, "OpenVPN 原生隧道已停止");
        if (activeCallback != null) {
            activeCallback.onDisconnected();
        }
    }

    @Override
    public boolean isRunning() {
        return isRunning.get();
    }

    @Override
    public long getRxBytes() {
        return rxBytes.get();
    }

    @Override
    public long getTxBytes() {
        return txBytes.get();
    }

    // ─── VpnStatus.StateListener Callbacks ───────────────────────────────────

    @Override
    public void updateState(String state, String logmessage, int localizedResId, ConnectionStatus level, Intent intent) {
        String msg = (logmessage != null && !logmessage.isEmpty()) ? logmessage : (state != null ? state : "");
        Log.i(TAG, "OpenVPN state: " + state + ", level: " + level + ", message: " + msg);
        if (!msg.isEmpty()) {
            LogManager.log(TAG, "核心日志: [" + state + "] " + msg);
        }

        if (activeCallback == null || !isRunning.get()) {
            return;
        }

        if (level == ConnectionStatus.LEVEL_CONNECTED) {
            if (!connectedNotified) {
                connectedNotified = true;
                LogManager.log(TAG, "🎉 OpenVPN 隧道握手完成，安全连接已正式建立");
                activeCallback.onConnected();
            }
        } else if (level == ConnectionStatus.LEVEL_NOTCONNECTED || level == ConnectionStatus.LEVEL_NONETWORK) {
            if (connectedNotified) {
                connectedNotified = false;
                LogManager.log(TAG, "OpenVPN 连接已中断: " + msg);
                activeCallback.onDisconnected();
            }
        } else if (level == ConnectionStatus.LEVEL_AUTH_FAILED) {
            LogManager.log(TAG, "❌ OpenVPN 认证失败 (账号/密码或证书错误): " + msg);
            activeCallback.onError("OpenVPN 认证失败，请检查账号密码或证书");
        }
    }

    @Override
    public void setConnectedVPN(String uuid) {
        // Tracked via updateState
    }

    // ─── VpnStatus.ByteCountListener Callbacks ───────────────────────────────

    @Override
    public void updateByteCount(long inBytes, long outBytes, long diffIn, long diffOut) {
        rxBytes.set(inBytes);
        txBytes.set(outBytes);

        if (activeCallback != null && isRunning.get()) {
            activeCallback.onTrafficUpdate(inBytes, outBytes);
        }
    }
}
