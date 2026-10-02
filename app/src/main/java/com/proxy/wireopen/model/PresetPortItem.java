package com.proxy.wireopen.model;

import com.proxy.wireopen.parser.OpenVpnConfigParser;
import com.proxy.wireopen.parser.WireGuardConfigParser;

import java.io.Serializable;
import java.util.Locale;

/**
 * Model representing an accelerator port derived from a profile.
 */
public class PresetPortItem implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String regionId;
    private final String regionName;
    private final String regionFlag;
    private final ProtocolType protocolType; // WIREGUARD or OPENVPN
    private final String transport; // "UDP" or "TCP"
    private final int port;
    private final String host;
    private final String ip;
    private String profileId;

    private Long latencyMs = null; // null: unmeasured, -2: timeout/error, >0: latency in ms
    private boolean isTesting = false;

    public PresetPortItem(String regionId, String regionName, String regionFlag,
                          ProtocolType protocolType, String transport, int port,
                          String host, String ip) {
        this(regionId, regionName, regionFlag, protocolType, transport, port, host, ip, null);
    }

    public PresetPortItem(String regionId, String regionName, String regionFlag,
                          ProtocolType protocolType, String transport, int port,
                          String host, String ip, String profileId) {
        this.regionId = regionId;
        this.regionName = regionName;
        this.regionFlag = regionFlag;
        this.protocolType = protocolType;
        this.transport = transport;
        this.port = port;
        this.host = host;
        this.ip = ip;
        this.profileId = profileId;
    }

    public String getRegionId() { return regionId; }
    public String getRegionName() { return regionName; }
    public String getRegionFlag() { return regionFlag; }
    public ProtocolType getProtocolType() { return protocolType; }
    public String getTransport() { return transport; }
    public int getPort() { return port; }
    public String getHost() { return host; }
    public String getIp() { return ip; }
    public String getProfileId() { return profileId; }
    public void setProfileId(String profileId) { this.profileId = profileId; }

    public Long getLatencyMs() { return latencyMs; }
    public void setLatencyMs(Long latencyMs) { this.latencyMs = latencyMs; }

    public boolean isTesting() { return isTesting; }
    public void setTesting(boolean testing) { isTesting = testing; }

    public String getDisplayName() {
        return regionName + " " + getProtocolLabel() + " :" + port;
    }

    public String getProtocolLabel() {
        if (protocolType == ProtocolType.WIREGUARD) {
            return "WireGuard (" + transport + ")";
        } else {
            return "OpenVPN (" + transport + ")";
        }
    }

    public String getShortProtoTag() {
        if (protocolType == ProtocolType.WIREGUARD) {
            return "WG";
        } else if ("UDP".equalsIgnoreCase(transport)) {
            return "OVPN-UDP";
        } else {
            return "OVPN-TCP";
        }
    }

    public String getFullEndpoint() {
        return host + ":" + port;
    }

    public String getUniqueId() {
        if (profileId != null && !profileId.isEmpty()) {
            return "port_" + profileId + "_" + getShortProtoTag().toLowerCase(Locale.ROOT) + "_" + port;
        }
        return "preset_" + regionId + "_" + getShortProtoTag().toLowerCase(Locale.ROOT) + "_" + port;
    }

    /**
     * Converts this port item into a fully formed ProxyProfile ready for tunneling.
     */
    public ProxyProfile toProxyProfile() {
        String profileName = regionFlag + " " + regionName + " · " + getShortProtoTag() + " :" + port;
        if (protocolType == ProtocolType.WIREGUARD) {
            String rawConfig =
                    "[Interface]\n" +
                    "PrivateKey = aGVsbG93b3JsZHByaXZhdGVrZXlleGFtcGxlMTIzNDU2Nzg=\n" +
                    "Address = 10.8.0.2/24, fd86:ea04:1115::2/64\n" +
                    "DNS = 1.1.1.1, 8.8.8.8\n" +
                    "MTU = 1420\n\n" +
                    "[Peer]\n" +
                    "PublicKey = d2lyZWd1YXJkcHVibGlja2V5ZXhhbXBsZTEyMzQ1Njc4OTA=\n" +
                    "Endpoint = " + host + ":" + port + "\n" +
                    "AllowedIPs = 0.0.0.0/0, ::/0\n" +
                    "PersistentKeepalive = 25\n";

            ProxyProfile profile = new ProxyProfile(profileName, ProtocolType.WIREGUARD, rawConfig);
            profile.setId(getUniqueId());
            try {
                profile.setWireGuardConfig(WireGuardConfigParser.parse(rawConfig));
            } catch (Exception ignored) {}
            return profile;
        } else {
            String protoLower = transport.toLowerCase(Locale.ROOT);
            String rawConfig =
                    "client\n" +
                    "dev tun\n" +
                    "proto " + protoLower + "\n" +
                    "remote " + host + " " + port + "\n" +
                    "resolv-retry infinite\n" +
                    "nobind\n" +
                    "persist-key\n" +
                    "persist-tun\n" +
                    "cipher AES-256-GCM\n" +
                    "auth SHA256\n" +
                    "redirect-gateway def1\n" +
                    "dhcp-option DNS 8.8.8.8\n" +
                    "dhcp-option DNS 1.1.1.1\n" +
                    "<ca>\n" +
                    "-----BEGIN CERTIFICATE-----\n" +
                    "MIIB/DCCAWWgAwIBAgIU0000000000000000000000000000000000000001\n" +
                    "-----END CERTIFICATE-----\n" +
                    "</ca>\n";

            ProxyProfile profile = new ProxyProfile(profileName, ProtocolType.OPENVPN, rawConfig);
            profile.setId(getUniqueId());
            try {
                profile.setOpenVpnConfig(OpenVpnConfigParser.parse(rawConfig));
            } catch (Exception ignored) {}
            return profile;
        }
    }
}
