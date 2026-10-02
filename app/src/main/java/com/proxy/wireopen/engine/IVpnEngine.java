package com.proxy.wireopen.engine;

import android.net.VpnService;
import com.proxy.wireopen.model.ProxyProfile;

/**
 * Common interface for VPN protocol tunnel engines.
 */
public interface IVpnEngine {

    interface EngineCallback {
        void onConnected();
        void onDisconnected();
        void onError(String message);
        void onTrafficUpdate(long rxBytes, long txBytes);
    }

    /**
     * Start the VPN tunnel on the given VpnService.
     */
    void start(VpnService vpnService, ProxyProfile profile, EngineCallback callback) throws Exception;

    /**
     * Stop and cleanup the tunnel.
     */
    void stop();

    /**
     * Whether the tunnel is currently running.
     */
    boolean isRunning();

    long getRxBytes();
    long getTxBytes();
}
