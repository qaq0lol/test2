package com.proxy.wireopen.repository;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.proxy.wireopen.model.PresetPortItem;
import com.proxy.wireopen.model.ProtocolType;
import com.proxy.wireopen.model.ProxyProfile;
import com.proxy.wireopen.parser.OpenVpnConfigParser;
import com.proxy.wireopen.parser.WireGuardConfigParser;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

/**
 * Repository for managing WireGuard and OpenVPN proxy profiles with derived port support.
 */
public class ProfileRepository {
    private static final String PREF_NAME = "wireopen_profiles";
    private static final String KEY_PROFILES = "saved_profiles";
    private static final String KEY_ACTIVE_ID = "active_profile_id";

    private final Context context;
    private final SharedPreferences prefs;
    private final Gson gson;
    private final List<ProxyProfile> profiles = new ArrayList<>();
    private String activeProfileId = null;

    public ProfileRepository(Context context) {
        this.context = context != null ? context.getApplicationContext() : null;
        if (context != null) {
            this.prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        } else {
            this.prefs = null;
        }
        this.gson = new Gson();
        load();
    }

    public synchronized List<ProxyProfile> getProfiles() {
        return new ArrayList<>(profiles);
    }

    public synchronized ProxyProfile getActiveProfile() {
        if (activeProfileId != null) {
            // Check direct profile match
            for (ProxyProfile p : profiles) {
                if (p.getId().equals(activeProfileId)) {
                    return p;
                }
            }

            // Check if activeProfileId is a derived port identifier
            if (activeProfileId.startsWith("port_") || activeProfileId.startsWith("preset_")) {
                PresetPortItem preset = PresetRegionManager.getInstance().findPortByUniqueId(activeProfileId);
                if (preset != null) {
                    if (preset.getProfileId() != null) {
                        for (ProxyProfile p : profiles) {
                            if (p.getId().equals(preset.getProfileId())) {
                                return p;
                            }
                        }
                    }
                    return preset.toProxyProfile();
                }
            }
        }

        return profiles.isEmpty() ? null : profiles.get(0);
    }

    public synchronized void setActiveProfileId(String id) {
        this.activeProfileId = id;
        save();
    }

    public synchronized String getActiveProfileId() {
        return activeProfileId;
    }

    public synchronized void addProfile(ProxyProfile profile) {
        profiles.add(profile);
        if (activeProfileId == null) {
            activeProfileId = profile.getId();
        }
        saveProfileToDisk(profile);
        PresetRegionManager.getInstance().syncFromProfiles(profiles);
        save();
    }

    public synchronized void updateProfile(ProxyProfile profile) {
        for (int i = 0; i < profiles.size(); i++) {
            if (profiles.get(i).getId().equals(profile.getId())) {
                profiles.set(i, profile);
                saveProfileToDisk(profile);
                PresetRegionManager.getInstance().syncFromProfiles(profiles);
                save();
                return;
            }
        }
    }

    /**
     * Dynamically switches the endpoint port of a target profile to the selected derived port.
     */
    public synchronized ProxyProfile switchProfilePort(String profileId, int newPort, String transport) {
        ProxyProfile target = null;
        for (ProxyProfile p : profiles) {
            if (p.getId().equals(profileId)) {
                target = p;
                break;
            }
        }
        if (target == null) return null;

        try {
            String raw = target.getRawConfig();
            if (target.getProtocolType() == ProtocolType.WIREGUARD) {
                if (raw != null && !raw.trim().isEmpty()) {
                    // Physical in-place replacement of Endpoint = host:port preserving all other lines and comments
                    String rewritten = raw.replaceAll("(?im)^(\\s*Endpoint\\s*=\\s*(\\[[^\\]]+\\]|[^:\\s\r\n]+))(:\\d+)?", "$1:" + newPort);
                    target.setRawConfig(rewritten);
                    target.setWireGuardConfig(WireGuardConfigParser.parse(rewritten));
                } else if (target.getWireGuardConfig() != null) {
                    target.getWireGuardConfig().setEndpointPort(newPort);
                    target.setRawConfig(WireGuardConfigParser.serialize(target.getWireGuardConfig()));
                }
                saveProfileToDisk(target);
            } else if (target.getProtocolType() == ProtocolType.OPENVPN) {
                if (raw != null && !raw.trim().isEmpty()) {
                    // Physical in-place replacement of remote host [port] preserving all certificates and options
                    // Only match numeric port and known OpenVPN proto keywords at end of remote line
                    String rewritten = raw.replaceAll("(?im)^(\\s*remote\\s+\\S+)(?:\\s+\\d+)?(?:\\s+(?:udp|tcp|udp4|tcp4|udp6|tcp6))?(?=\\s*$)", "$1 " + newPort);
                    if (transport != null && !transport.isEmpty()) {
                        String t = transport.toLowerCase(java.util.Locale.ROOT);
                        if (rewritten.matches("(?s).*(?im)^\\s*proto\\s+\\S+.*")) {
                            rewritten = rewritten.replaceAll("(?im)^(\\s*proto\\s+)\\S+", "$1" + t);
                        } else {
                            rewritten = "proto " + t + "\n" + rewritten;
                        }
                    }
                    target.setRawConfig(rewritten);
                    target.setOpenVpnConfig(OpenVpnConfigParser.parse(rewritten));
                } else if (target.getOpenVpnConfig() != null) {
                    target.getOpenVpnConfig().setRemotePort(newPort);
                    if (transport != null) {
                        target.getOpenVpnConfig().setProtocol(transport.toLowerCase(java.util.Locale.ROOT));
                    }
                    target.setRawConfig(OpenVpnConfigParser.serialize(target.getOpenVpnConfig()));
                }
                saveProfileToDisk(target);
            }
            activeProfileId = target.getId();
            updateProfile(target);
            return target;
        } catch (Exception e) {
            return target;
        }
    }

    private void saveProfileToDisk(ProxyProfile profile) {
        if (profile == null || profile.getRawConfig() == null) return;
        String ext = (profile.getProtocolType() == ProtocolType.WIREGUARD) ? ".conf" : ".ovpn";
        saveConfigFileToDisk(profile.getId() + ext, profile.getRawConfig());
        if (profile.getName() != null && !profile.getName().trim().isEmpty()) {
            String safeName = profile.getName().replaceAll("[\\\\/:*?\"<>|]", "_");
            if (!safeName.endsWith(ext)) {
                safeName = safeName + ext;
            }
            saveConfigFileToDisk(safeName, profile.getRawConfig());
        }
    }

    private void saveConfigFileToDisk(String filename, String content) {
        if (content == null) return;
        try {
            java.io.File baseDir;
            if (context != null) {
                baseDir = new java.io.File(context.getFilesDir(), "profiles");
            } else {
                baseDir = new java.io.File(System.getProperty("java.io.tmpdir", "."), "profiles");
            }
            if (!baseDir.exists()) {
                baseDir.mkdirs();
            }
            java.io.File targetFile = new java.io.File(baseDir, filename);
            try (java.io.FileOutputStream fos = new java.io.FileOutputStream(targetFile)) {
                fos.write(content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
        } catch (Exception ignored) {}
    }

    public synchronized ProxyProfile getProfileById(String id) {
        if (id == null) return null;
        for (ProxyProfile p : profiles) {
            if (p.getId().equals(id)) {
                return p;
            }
        }
        if (id.startsWith("port_") || id.startsWith("preset_")) {
            PresetPortItem preset = PresetRegionManager.getInstance().findPortByUniqueId(id);
            if (preset != null) {
                if (preset.getProfileId() != null) {
                    for (ProxyProfile p : profiles) {
                        if (p.getId().equals(preset.getProfileId())) {
                            return p;
                        }
                    }
                }
                return preset.toProxyProfile();
            }
        }
        return null;
    }

    public synchronized void deleteProfile(String id) {
        profiles.removeIf(p -> p.getId().equals(id));
        if (id.equals(activeProfileId)) {
            activeProfileId = profiles.isEmpty() ? null : profiles.get(0).getId();
        }
        PresetRegionManager.getInstance().syncFromProfiles(profiles);
        save();
    }

    /**
     * Imports a WireGuard configuration file content.
     */
    public ProxyProfile importWireGuard(String name, String content) throws Exception {
        ProxyProfile profile = new ProxyProfile(name, ProtocolType.WIREGUARD, content);
        profile.setWireGuardConfig(WireGuardConfigParser.parse(content));
        addProfile(profile);
        return profile;
    }

    /**
     * Imports an OpenVPN configuration file content.
     */
    public ProxyProfile importOpenVpn(String name, String content) throws Exception {
        ProxyProfile profile = new ProxyProfile(name, ProtocolType.OPENVPN, content);
        profile.setOpenVpnConfig(OpenVpnConfigParser.parse(content));
        addProfile(profile);
        return profile;
    }

    /**
     * Automatically detect protocol and import.
     */
    public ProxyProfile autoImport(String name, String content) throws Exception {
        if (content == null || content.trim().isEmpty()) {
            throw new IllegalArgumentException("配置内容不能为空");
        }
        String lower = content.toLowerCase(java.util.Locale.ROOT);
        if (lower.contains("[interface]") || lower.contains("[peer]")) {
            return importWireGuard(name, content);
        } else if (lower.contains("client") || lower.contains("dev tun") || lower.contains("dev tap") || lower.contains("<ca>") || lower.contains("remote ")) {
            return importOpenVpn(name, content);
        } else {
            try {
                return importWireGuard(name, content);
            } catch (Exception e) {
                return importOpenVpn(name, content);
            }
        }
    }

    private void load() {
        profiles.clear();
        if (prefs != null) {
            String json = prefs.getString(KEY_PROFILES, null);
            if (json != null) {
                try {
                    Type type = new TypeToken<List<ProxyProfile>>() {}.getType();
                    List<ProxyProfile> loaded = gson.fromJson(json, type);
                    if (loaded != null) {
                        profiles.addAll(loaded);
                    }
                    activeProfileId = prefs.getString(KEY_ACTIVE_ID, null);
                } catch (Exception ignored) {}
            }
        }

        if (activeProfileId == null && !profiles.isEmpty()) {
            activeProfileId = profiles.get(0).getId();
        }

        PresetRegionManager.getInstance().syncFromProfiles(profiles);
    }

    private void save() {
        if (prefs != null) {
            String json = gson.toJson(profiles);
            prefs.edit()
                    .putString(KEY_PROFILES, json)
                    .putString(KEY_ACTIVE_ID, activeProfileId)
                    .apply();
        }
    }
}
