package com.proxy.wireopen.model;

/**
 * Supported VPN proxy protocol types.
 */
public enum ProtocolType {
    WIREGUARD("WireGuard", ".conf"),
    OPENVPN("OpenVPN", ".ovpn");

    private final String displayName;
    private final String fileExtension;

    ProtocolType(String displayName, String fileExtension) {
        this.displayName = displayName;
        this.fileExtension = fileExtension;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getFileExtension() {
        return fileExtension;
    }

    public static ProtocolType fromExtension(String filename) {
        if (filename == null) return null;
        String lower = filename.toLowerCase();
        if (lower.endsWith(".conf")) return WIREGUARD;
        if (lower.endsWith(".ovpn")) return OPENVPN;
        return null;
    }
}
