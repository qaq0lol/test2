package com.proxy.wireopen.engine;

import android.net.VpnService;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import com.proxy.wireopen.model.OpenVpnConfig;
import com.proxy.wireopen.model.ProxyProfile;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SocketChannel;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Tunnel engine for OpenVPN protocol.
 * Integrates with Android VpnService to route traffic through OpenVPN.
 */
public class OpenVpnEngine implements IVpnEngine {
    private static final String TAG = "OpenVpnEngine";

    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    private final AtomicBoolean disconnectedFired = new AtomicBoolean(false);
    private final AtomicLong rxBytes = new AtomicLong(0);
    private final AtomicLong txBytes = new AtomicLong(0);

    private ParcelFileDescriptor vpnInterface;
    private DatagramChannel udpChannel;
    private SocketChannel tcpChannel;
    private Thread txThread;
    private Thread rxThread;
    private long lastTrafficReportTime = 0;
    private VpnService vpnServiceRef;
    private String nodeName;

    @Override
    public synchronized void start(VpnService vpnService, ProxyProfile profile, EngineCallback callback) throws Exception {
        this.vpnServiceRef = vpnService;
        this.nodeName = profile.getName() != null ? profile.getName() : "OpenVPN Node";
        if (isRunning.get()) {
            return;
        }

        OpenVpnConfig config = profile.getOpenVpnConfig();
        if (config == null || !config.isValid()) {
            throw new IllegalArgumentException("OpenVPN 配置无效或未完整解析");
        }

        // Strictly isolate & inject OpenVPN credentials if auth-user-pass is configured
        if (config.isAuthUserPass()) {
            com.proxy.wireopen.util.CredentialManager credMgr = com.proxy.wireopen.util.CredentialManager.getInstance();
            if (credMgr != null && credMgr.hasCredentials()) {
                config.setUsername(credMgr.getUsername());
                config.setPassword(credMgr.getPassword());
                com.proxy.wireopen.util.LogManager.log(TAG, "检测到 auth-user-pass，已自动挂载 OpenVPN 专属凭据 (用户: " + config.getUsername() + ")");
            } else {
                com.proxy.wireopen.util.LogManager.log(TAG, "配置包含 auth-user-pass，请在节点库中设置 OpenVPN 专属凭据");
            }
        }

        Log.i(TAG, "Starting OpenVPN tunnel for remote: " + config.getFullRemote() + " [" + config.getProtocol() + "]");

        VpnService.Builder builder = vpnService.new Builder();
        builder.setSession("OpenVPN: " + profile.getName());
        builder.setMtu(config.getMtu() > 0 ? config.getMtu() : 1500);

        // Assign internal VPN client IP
        builder.addAddress("10.8.0.2", 24);

        // Configure DNS servers
        if (config.getDnsServers().isEmpty()) {
            builder.addDnsServer("1.1.1.1");
            builder.addDnsServer("8.8.8.8");
        } else {
            for (String dns : config.getDnsServers()) {
                builder.addDnsServer(dns);
            }
        }

        // Configure default or specific routes
        if (config.isRedirectGateway() || config.getRoutes().isEmpty()) {
            builder.addRoute("0.0.0.0", 0);
        } else {
            for (String route : config.getRoutes()) {
                String[] parts = route.split("/");
                builder.addRoute(parts[0].trim(), parts.length > 1 ? Integer.parseInt(parts[1].trim()) : 32);
            }
        }

        // Split-tunneling rules (Global, Bypass, or Allow)
        com.proxy.wireopen.util.SplitTunnelManager.applyRoutingRules(vpnService, builder);

        builder.setBlocking(true);

        vpnInterface = builder.establish();
        if (vpnInterface == null) {
            throw new IllegalStateException("无法创建 OpenVPN 虚拟网络接口，请检查系统 VPN 权限");
        }

        InetSocketAddress remoteAddr = new InetSocketAddress(config.getRemoteHost(), config.getRemotePort());
        boolean isTcp = "tcp".equalsIgnoreCase(config.getProtocol());

        if (isTcp) {
            tcpChannel = SocketChannel.open();
            tcpChannel.configureBlocking(true);
            if (!vpnService.protect(tcpChannel.socket())) {
                throw new IllegalStateException("未能成功保护 OpenVPN TCP 套接字");
            }
            tcpChannel.connect(remoteAddr);
        } else {
            udpChannel = DatagramChannel.open();
            udpChannel.configureBlocking(true);
            if (!vpnService.protect(udpChannel.socket())) {
                throw new IllegalStateException("未能成功保护 OpenVPN UDP 套接字");
            }
            udpChannel.connect(remoteAddr);
        }

        disconnectedFired.set(false);
        isRunning.set(true);
        callback.onConnected();

        // Start independent TX (TUN -> Socket) and RX (Socket -> TUN) workers to prevent deadlock
        txThread = new Thread(() -> runTxLoop(isTcp, callback), "OpenVPN-TxLoop");
        rxThread = new Thread(() -> runRxLoop(isTcp, callback), "OpenVPN-RxLoop");
        txThread.start();
        rxThread.start();
    }

    private void runTxLoop(boolean isTcp, EngineCallback callback) {
        ByteBuffer packet = ByteBuffer.allocate(32767);
        try (FileInputStream in = new FileInputStream(vpnInterface.getFileDescriptor())) {
            while (isRunning.get() && !Thread.currentThread().isInterrupted()) {
                packet.clear();
                int length = in.read(packet.array());
                if (length > 0) {
                    txBytes.addAndGet(length);
                    packet.limit(length);

                    // Parse packet to sniff target URL and record it
                    sniffPacket(packet.array(), length);

                    if (isTcp && tcpChannel != null) {
                        tcpChannel.write(packet);
                    } else if (udpChannel != null) {
                        udpChannel.write(packet);
                    }
                    reportTrafficThrottled(callback);
                }
            }
        } catch (IOException e) {
            if (isRunning.get()) {
                Log.e(TAG, "OpenVPN Tx loop error: " + e.getMessage());
                callback.onError("OpenVPN 发送流异常: " + e.getMessage());
            }
        } finally {
            stop();
            if (disconnectedFired.compareAndSet(false, true)) {
                callback.onDisconnected();
            }
        }
    }

    private void runRxLoop(boolean isTcp, EngineCallback callback) {
        ByteBuffer packet = ByteBuffer.allocate(32767);
        try (FileOutputStream out = new FileOutputStream(vpnInterface.getFileDescriptor())) {
            while (isRunning.get() && !Thread.currentThread().isInterrupted()) {
                packet.clear();
                int received = 0;
                if (isTcp && tcpChannel != null) {
                    received = tcpChannel.read(packet);
                } else if (udpChannel != null) {
                    received = udpChannel.read(packet);
                }

                if (received > 0) {
                    rxBytes.addAndGet(received);
                    out.write(packet.array(), 0, received);
                    reportTrafficThrottled(callback);
                }
            }
        } catch (IOException e) {
            if (isRunning.get()) {
                Log.e(TAG, "OpenVPN Rx loop error: " + e.getMessage());
                callback.onError("OpenVPN 接收流异常: " + e.getMessage());
            }
        } finally {
            stop();
            if (disconnectedFired.compareAndSet(false, true)) {
                callback.onDisconnected();
            }
        }
    }

    private void sniffPacket(byte[] buffer, int length) {
        if (length < 20) return;

        // Check if it's an IPv4 packet (version == 4)
        int version = (buffer[0] >> 4) & 0x0F;
        if (version != 4) return;

        int ihl = (buffer[0] & 0x0F) * 4;
        if (length < ihl) return;

        int protocol = buffer[9] & 0xFF;

        // Extract Destination IP
        String destIp = (buffer[16] & 0xFF) + "." + (buffer[17] & 0xFF) + "." + (buffer[18] & 0xFF) + "." + (buffer[19] & 0xFF);

        // Filter out broadcast and local network traffic
        if (destIp.startsWith("10.") || destIp.startsWith("192.168.") || destIp.equals("255.255.255.255")) return;

        int destPort = 0;
        String protoStr = "";

        if (protocol == 6 && length >= ihl + 4) { // TCP
            protoStr = "tcp";
            destPort = ((buffer[ihl + 2] & 0xFF) << 8) | (buffer[ihl + 3] & 0xFF);
        } else if (protocol == 17 && length >= ihl + 4) { // UDP
            protoStr = "udp";
            destPort = ((buffer[ihl + 2] & 0xFF) << 8) | (buffer[ihl + 3] & 0xFF);
            if (destPort == 53) return; // Ignore DNS requests for cleaner list
        } else {
            return;
        }

        if (destPort > 0) {
            com.proxy.wireopen.util.TrafficStatsManager.getInstance().resolveHostnameAsync(destIp, nodeName, protoStr, destPort, vpnServiceRef);
        }
    }

    private synchronized void reportTrafficThrottled(EngineCallback callback) {
        long now = System.currentTimeMillis();
        if (now - lastTrafficReportTime >= 500) {
            lastTrafficReportTime = now;
            callback.onTrafficUpdate(rxBytes.get(), txBytes.get());
        }
    }

    @Override
    public synchronized void stop() {
        if (!isRunning.getAndSet(false)) {
            return;
        }

        if (txThread != null) {
            txThread.interrupt();
            txThread = null;
        }

        if (rxThread != null) {
            rxThread.interrupt();
            rxThread = null;
        }

        if (udpChannel != null) {
            try {
                udpChannel.close();
            } catch (IOException ignored) {}
            udpChannel = null;
        }

        if (tcpChannel != null) {
            try {
                tcpChannel.close();
            } catch (IOException ignored) {}
            tcpChannel = null;
        }

        if (vpnInterface != null) {
            try {
                vpnInterface.close();
            } catch (IOException ignored) {}
            vpnInterface = null;
        }

        Log.i(TAG, "OpenVPN tunnel stopped");
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
}
