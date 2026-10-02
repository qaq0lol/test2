package com.proxy.wireopen.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Representation of a parsed OpenVPN configuration.
 */
public class OpenVpnConfig implements Serializable {
    private static final long serialVersionUID = 1L;

    private String remoteHost = "";
    private int remotePort = 1194;
    private String protocol = "udp"; // udp or tcp
    private String deviceType = "tun"; // tun or tap
    private int mtu = 1500;
    private String cipher = "AES-256-GCM";
    private String auth = "SHA256";
    private boolean redirectGateway = true;
    private List<String> dnsServers = new ArrayList<>();
    private List<String> routes = new ArrayList<>();

    // Certificates / Keys (support both inline or external paths)
    private String caCert = "";
    private String clientCert = "";
    private String clientKey = "";
    private String tlsAuthKey = "";
    private int tlsAuthDirection = -1;

    // Authentication flags
    private boolean authUserPass = false;
    private String username = "";
    private String password = "";

    public OpenVpnConfig() {}

    public String getRemoteHost() { return remoteHost; }
    public void setRemoteHost(String remoteHost) { this.remoteHost = remoteHost; }

    public int getRemotePort() { return remotePort; }
    public void setRemotePort(int remotePort) { this.remotePort = remotePort; }

    public String getFullRemote() {
        if (remoteHost == null || remoteHost.isEmpty()) return "";
        return remoteHost + ":" + remotePort;
    }

    public String getProtocol() { return protocol; }
    public void setProtocol(String protocol) { this.protocol = protocol; }

    public String getDeviceType() { return deviceType; }
    public void setDeviceType(String deviceType) { this.deviceType = deviceType; }

    public int getMtu() { return mtu; }
    public void setMtu(int mtu) { this.mtu = mtu; }

    public String getCipher() { return cipher; }
    public void setCipher(String cipher) { this.cipher = cipher; }

    public String getAuth() { return auth; }
    public void setAuth(String auth) { this.auth = auth; }

    public boolean isRedirectGateway() { return redirectGateway; }
    public void setRedirectGateway(boolean redirectGateway) { this.redirectGateway = redirectGateway; }

    public List<String> getDnsServers() { return dnsServers; }
    public void setDnsServers(List<String> dnsServers) { this.dnsServers = dnsServers; }

    public List<String> getRoutes() { return routes; }
    public void setRoutes(List<String> routes) { this.routes = routes; }

    public String getCaCert() { return caCert; }
    public void setCaCert(String caCert) { this.caCert = caCert; }

    public String getClientCert() { return clientCert; }
    public void setClientCert(String clientCert) { this.clientCert = clientCert; }

    public String getClientKey() { return clientKey; }
    public void setClientKey(String clientKey) { this.clientKey = clientKey; }

    public String getTlsAuthKey() { return tlsAuthKey; }
    public void setTlsAuthKey(String tlsAuthKey) { this.tlsAuthKey = tlsAuthKey; }

    public int getTlsAuthDirection() { return tlsAuthDirection; }
    public void setTlsAuthDirection(int tlsAuthDirection) { this.tlsAuthDirection = tlsAuthDirection; }

    public boolean isAuthUserPass() { return authUserPass; }
    public void setAuthUserPass(boolean authUserPass) { this.authUserPass = authUserPass; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public boolean isValid() {
        return remoteHost != null && !remoteHost.isEmpty() && remotePort > 0;
    }

    @Override
    public String toString() {
        return "OpenVpnConfig{" +
                "remote='" + getFullRemote() + '\'' +
                ", proto='" + protocol + '\'' +
                ", dev='" + deviceType + '\'' +
                ", cipher='" + cipher + '\'' +
                ", mtu=" + mtu +
                ", hasCa=" + (!caCert.isEmpty()) +
                ", hasCert=" + (!clientCert.isEmpty()) +
                '}';
    }
}
