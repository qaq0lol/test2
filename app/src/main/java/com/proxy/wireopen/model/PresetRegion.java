package com.proxy.wireopen.model;

import com.proxy.wireopen.util.RegionDetector;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Model representing a geographical region node derived from an imported proxy profile,
 * containing accelerated ports strictly corresponding to the profile's protocol.
 */
public class PresetRegion implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String id;
    private final String name;
    private final String englishName;
    private final String flag;
    private final String host;
    private final String ip;
    private final ProtocolType protocolType;
    private final String profileId;
    private boolean isExpanded = true;
    private final List<PresetPortItem> ports = new ArrayList<>();

    public static final int[] STANDARD_PORTS = {443, 80, 53, 123, 1194, 65142};

    public PresetRegion(String id, String name, String englishName, String flag, String host, String ip) {
        this(id, name, englishName, flag, host, ip, ProtocolType.WIREGUARD, null);
    }

    public PresetRegion(String id, String name, String englishName, String flag, String host, String ip,
                        ProtocolType protocolType, String profileId) {
        this.id = id;
        this.name = name;
        this.englishName = englishName;
        this.flag = flag;
        this.host = host;
        this.ip = ip;
        this.protocolType = protocolType;
        this.profileId = profileId;
        initDerivedPorts();
    }

    /**
     * Constructs a PresetRegion directly from an imported ProxyProfile,
     * automatically recognizing region & flag and deriving only protocol-matching ports.
     */
    public static PresetRegion fromProfile(ProxyProfile profile) {
        if (profile == null) return null;

        String endpointHost = "";
        String ip = "";

        if (profile.getProtocolType() == ProtocolType.WIREGUARD) {
            if (profile.getWireGuardConfig() == null && profile.getRawConfig() != null) {
                try {
                    profile.setWireGuardConfig(com.proxy.wireopen.parser.WireGuardConfigParser.parse(profile.getRawConfig()));
                } catch (Exception ignored) {}
            }
            if (profile.getWireGuardConfig() != null) {
                endpointHost = profile.getWireGuardConfig().getEndpointHost();
            }
        } else if (profile.getProtocolType() == ProtocolType.OPENVPN) {
            if (profile.getOpenVpnConfig() == null && profile.getRawConfig() != null) {
                try {
                    profile.setOpenVpnConfig(com.proxy.wireopen.parser.OpenVpnConfigParser.parse(profile.getRawConfig()));
                } catch (Exception ignored) {}
            }
            if (profile.getOpenVpnConfig() != null) {
                endpointHost = profile.getOpenVpnConfig().getRemoteHost();
            }
        }

        if (endpointHost == null || endpointHost.isEmpty()) {
            endpointHost = "127.0.0.1";
        }

        RegionDetector.RegionInfo regionInfo = RegionDetector.detect(
                profile.getName(),
                endpointHost,
                profile.getEndpointDisplay(),
                profile.getRawConfig()
        );

        return new PresetRegion(
                profile.getId(),
                regionInfo.getName(),
                profile.getName(),
                regionInfo.getFlag(),
                endpointHost,
                ip,
                profile.getProtocolType(),
                profile.getId()
        );
    }

    private void initDerivedPorts() {
        ports.clear();
        if (protocolType == ProtocolType.WIREGUARD) {
            // Strictly 6 WireGuard (UDP) ports only!
            for (int p : STANDARD_PORTS) {
                ports.add(new PresetPortItem(id, name, flag, ProtocolType.WIREGUARD, "UDP", p, host, ip, profileId));
            }
        } else {
            // OpenVPN UDP 6 ports + TCP 6 ports
            for (int p : STANDARD_PORTS) {
                ports.add(new PresetPortItem(id, name, flag, ProtocolType.OPENVPN, "UDP", p, host, ip, profileId));
            }
            for (int p : STANDARD_PORTS) {
                ports.add(new PresetPortItem(id, name, flag, ProtocolType.OPENVPN, "TCP", p, host, ip, profileId));
            }
        }
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getEnglishName() { return englishName; }
    public String getFlag() { return flag; }
    public String getHost() { return host; }
    public String getIp() { return ip; }
    public ProtocolType getProtocolType() { return protocolType; }
    public String getProfileId() { return profileId; }

    public boolean isExpanded() { return isExpanded; }
    public void setExpanded(boolean expanded) { isExpanded = expanded; }

    public List<PresetPortItem> getPorts() { return ports; }

    public List<PresetPortItem> getPortsByGroup(ProtocolType type, String transport) {
        List<PresetPortItem> filtered = new ArrayList<>();
        for (PresetPortItem item : ports) {
            if (item.getProtocolType() == type && item.getTransport().equalsIgnoreCase(transport)) {
                filtered.add(item);
            }
        }
        return filtered;
    }
}
