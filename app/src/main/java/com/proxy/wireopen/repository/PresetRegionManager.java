package com.proxy.wireopen.repository;

import com.proxy.wireopen.model.PresetPortItem;
import com.proxy.wireopen.model.PresetRegion;
import com.proxy.wireopen.model.ProxyProfile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Singleton repository manager for imported regions and their derived accelerator ports.
 * Contains NO hardcoded fake regions! Nodes are dynamically derived from imported profiles.
 */
public class PresetRegionManager {

    private static PresetRegionManager instance;
    private final List<PresetRegion> regions = new ArrayList<>();

    private PresetRegionManager() {
        // Absolutely NO hardcoded fake regions! Empty by default.
    }

    public static synchronized PresetRegionManager getInstance() {
        if (instance == null) {
            instance = new PresetRegionManager();
        }
        return instance;
    }

    /**
     * Synchronizes dynamic regions directly from the user's imported proxy profiles.
     */
    public synchronized void syncFromProfiles(List<ProxyProfile> profiles) {
        // Cache previous latencies and expanded states
        Map<String, Long> latencyCache = new HashMap<>();
        Map<String, Boolean> expandedCache = new HashMap<>();
        for (PresetRegion r : regions) {
            expandedCache.put(r.getId(), r.isExpanded());
            for (PresetPortItem p : r.getPorts()) {
                if (p.getLatencyMs() != null) {
                    latencyCache.put(p.getUniqueId(), p.getLatencyMs());
                }
            }
        }

        regions.clear();
        if (profiles != null) {
            for (ProxyProfile profile : profiles) {
                PresetRegion region = PresetRegion.fromProfile(profile);
                if (region != null) {
                    Boolean exp = expandedCache.get(region.getId());
                    if (exp != null) {
                        region.setExpanded(exp);
                    }
                    // Restore cached latencies
                    for (PresetPortItem p : region.getPorts()) {
                        Long lat = latencyCache.get(p.getUniqueId());
                        if (lat != null) {
                            p.setLatencyMs(lat);
                        }
                    }
                    regions.add(region);
                }
            }
        }
    }

    public synchronized void addRegion(PresetRegion region) {
        if (region != null) {
            regions.add(region);
        }
    }

    public synchronized void clear() {
        regions.clear();
    }

    public synchronized List<PresetRegion> getRegions() {
        return Collections.unmodifiableList(new ArrayList<>(regions));
    }

    public synchronized List<PresetPortItem> getAllPorts() {
        List<PresetPortItem> all = new ArrayList<>();
        for (PresetRegion r : regions) {
            all.addAll(r.getPorts());
        }
        return all;
    }

    public synchronized PresetPortItem findPortById(String regionId, String protoTag, int port) {
        for (PresetRegion r : regions) {
            if (r.getId().equalsIgnoreCase(regionId)) {
                for (PresetPortItem item : r.getPorts()) {
                    if (item.getPort() == port && item.getShortProtoTag().equalsIgnoreCase(protoTag)) {
                        return item;
                    }
                }
            }
        }
        return null;
    }

    public synchronized PresetPortItem findPortByEndpoint(String host, int port) {
        for (PresetRegion r : regions) {
            for (PresetPortItem item : r.getPorts()) {
                if (item.getPort() == port && (item.getHost().equalsIgnoreCase(host) || item.getIp().equalsIgnoreCase(host))) {
                    return item;
                }
            }
        }
        return null;
    }

    public synchronized PresetPortItem findPortByUniqueId(String uid) {
        if (uid == null || uid.isEmpty()) return null;
        for (PresetRegion r : regions) {
            for (PresetPortItem item : r.getPorts()) {
                if (item.getUniqueId().equalsIgnoreCase(uid)) {
                    return item;
                }
            }
        }
        return null;
    }
}
