package com.proxy.wireopen.model;

import java.io.Serializable;
import java.util.UUID;

/**
 * Unified proxy profile encompassing both WireGuard and OpenVPN configs.
 */
public class ProxyProfile implements Serializable {
    private static final long serialVersionUID = 1L;

    private String id;
    private String name;
    private ProtocolType protocolType;
    private String rawConfig;
    private long createdAt;

    private WireGuardConfig wireGuardConfig;
    private OpenVpnConfig openVpnConfig;

    public ProxyProfile() {
        this.id = UUID.randomUUID().toString();
        this.createdAt = System.currentTimeMillis();
    }

    public ProxyProfile(String name, ProtocolType protocolType, String rawConfig) {
        this();
        this.name = name;
        this.protocolType = protocolType;
        this.rawConfig = rawConfig;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public ProtocolType getProtocolType() { return protocolType; }
    public void setProtocolType(ProtocolType protocolType) { this.protocolType = protocolType; }

    public String getRawConfig() { return rawConfig; }
    public void setRawConfig(String rawConfig) { this.rawConfig = rawConfig; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }

    public WireGuardConfig getWireGuardConfig() { return wireGuardConfig; }
    public void setWireGuardConfig(WireGuardConfig wireGuardConfig) { this.wireGuardConfig = wireGuardConfig; }

    public OpenVpnConfig getOpenVpnConfig() { return openVpnConfig; }
    public void setOpenVpnConfig(OpenVpnConfig openVpnConfig) { this.openVpnConfig = openVpnConfig; }

    public String getEndpointDisplay() {
        if (protocolType == ProtocolType.WIREGUARD) {
            if (wireGuardConfig == null && rawConfig != null) {
                try {
                    wireGuardConfig = com.proxy.wireopen.parser.WireGuardConfigParser.parse(rawConfig);
                } catch (Exception ignored) {}
            }
            if (wireGuardConfig != null) {
                return wireGuardConfig.getFullEndpoint();
            }
        } else if (protocolType == ProtocolType.OPENVPN) {
            if (openVpnConfig == null && rawConfig != null) {
                try {
                    openVpnConfig = com.proxy.wireopen.parser.OpenVpnConfigParser.parse(rawConfig);
                } catch (Exception ignored) {}
            }
            if (openVpnConfig != null) {
                return openVpnConfig.getFullRemote();
            }
        }
        return "未指定节点";
    }

    public boolean isValid() {
        if (protocolType == ProtocolType.WIREGUARD) {
            return wireGuardConfig != null && wireGuardConfig.isValid();
        } else if (protocolType == ProtocolType.OPENVPN) {
            return openVpnConfig != null && openVpnConfig.isValid();
        }
        return false;
    }
}
