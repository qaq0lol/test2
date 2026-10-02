package com.proxy.wireopen.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Representation of a parsed WireGuard configuration.
 */
public class WireGuardConfig implements Serializable {
    private static final long serialVersionUID = 1L;

    // Interface section
    private String privateKey = "";
    private List<String> addresses = new ArrayList<>();
    private List<String> dnsServers = new ArrayList<>();
    private int listenPort = 0;
    private int mtu = 1420;

    // Peer section
    private String publicKey = "";
    private String presharedKey = "";
    private String endpointHost = "";
    private int endpointPort = 51820;
    private List<String> allowedIps = new ArrayList<>();
    private int persistentKeepalive = 25;

    public WireGuardConfig() {}

    public String getPrivateKey() { return privateKey; }
    public void setPrivateKey(String privateKey) { this.privateKey = privateKey; }

    public List<String> getAddresses() { return addresses; }
    public void setAddresses(List<String> addresses) { this.addresses = addresses; }

    public List<String> getDnsServers() { return dnsServers; }
    public void setDnsServers(List<String> dnsServers) { this.dnsServers = dnsServers; }

    public int getListenPort() { return listenPort; }
    public void setListenPort(int listenPort) { this.listenPort = listenPort; }

    public int getMtu() { return mtu; }
    public void setMtu(int mtu) { this.mtu = mtu; }

    public String getPublicKey() { return publicKey; }
    public void setPublicKey(String publicKey) { this.publicKey = publicKey; }

    public String getPresharedKey() { return presharedKey; }
    public void setPresharedKey(String presharedKey) { this.presharedKey = presharedKey; }

    public String getEndpointHost() { return endpointHost; }
    public void setEndpointHost(String endpointHost) { this.endpointHost = endpointHost; }

    public int getEndpointPort() { return endpointPort; }
    public void setEndpointPort(int endpointPort) { this.endpointPort = endpointPort; }

    public String getFullEndpoint() {
        if (endpointHost == null || endpointHost.isEmpty()) return "";
        if (endpointHost.contains(":") && !endpointHost.startsWith("[")) {
            return "[" + endpointHost + "]:" + endpointPort;
        }
        return endpointHost + ":" + endpointPort;
    }

    public List<String> getAllowedIps() { return allowedIps; }
    public void setAllowedIps(List<String> allowedIps) { this.allowedIps = allowedIps; }

    public int getPersistentKeepalive() { return persistentKeepalive; }
    public void setPersistentKeepalive(int persistentKeepalive) { this.persistentKeepalive = persistentKeepalive; }

    public boolean isValid() {
        return privateKey != null && !privateKey.isEmpty()
                && !addresses.isEmpty()
                && publicKey != null && !publicKey.isEmpty()
                && endpointHost != null && !endpointHost.isEmpty()
                && endpointPort > 0;
    }

    @Override
    public String toString() {
        return "WireGuardConfig{" +
                "endpoint='" + getFullEndpoint() + '\'' +
                ", addresses=" + addresses +
                ", dns=" + dnsServers +
                ", allowedIps=" + allowedIps +
                ", mtu=" + mtu +
                '}';
    }
}
