package com.proxy.wireopen.model;

import java.io.Serializable;
import java.util.UUID;

/**
 * Model representing an active or recent network connection request (FlClash style).
 */
public class ConnectionRecord implements Serializable {
    private static final long serialVersionUID = 1L;

    private String id;
    private String targetUrl;       // e.g. "tcp://api.bilibili.com:443"
    private long timestamp;
    private String relativeTimeStr; // e.g. "刚刚", "1秒前"
    private long uploadBytes;       // e.g. 517
    private long downloadBytes;     // e.g. 0
    private String nodeFlag;        // e.g. "🇩🇪"
    private String nodeName;        // e.g. "德国A1 6 | 0.7倍 | V1"
    private String rule;            // e.g. "GLOBAL"
    private boolean active;

    public ConnectionRecord() {
        this.id = UUID.randomUUID().toString();
        this.timestamp = System.currentTimeMillis();
        this.relativeTimeStr = "刚刚";
        this.rule = "GLOBAL";
        this.active = true;
    }

    public ConnectionRecord(String targetUrl, String relativeTimeStr, long uploadBytes,
                            long downloadBytes, String nodeFlag, String nodeName, String rule) {
        this();
        this.targetUrl = targetUrl;
        this.relativeTimeStr = relativeTimeStr;
        this.uploadBytes = uploadBytes;
        this.downloadBytes = downloadBytes;
        this.nodeFlag = nodeFlag;
        this.nodeName = nodeName;
        this.rule = rule != null ? rule : "GLOBAL";
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTargetUrl() { return targetUrl; }
    public void setTargetUrl(String targetUrl) { this.targetUrl = targetUrl; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    public String getRelativeTimeStr() { return relativeTimeStr; }
    public void setRelativeTimeStr(String relativeTimeStr) { this.relativeTimeStr = relativeTimeStr; }

    public long getUploadBytes() { return uploadBytes; }
    public void setUploadBytes(long uploadBytes) { this.uploadBytes = uploadBytes; }

    public long getDownloadBytes() { return downloadBytes; }
    public void setDownloadBytes(long downloadBytes) { this.downloadBytes = downloadBytes; }

    // Aliases used by packet capture / TrafficStatsManager
    public long getTxBytes() { return uploadBytes; }
    public long getRxBytes() { return downloadBytes; }
    public void addTxBytes(long delta) { this.uploadBytes += delta; }
    public void addRxBytes(long delta) { this.downloadBytes += delta; }

    public String getNodeFlag() { return nodeFlag; }
    public void setNodeFlag(String nodeFlag) { this.nodeFlag = nodeFlag; }

    public String getNodeName() { return nodeName; }
    public void setNodeName(String nodeName) { this.nodeName = nodeName; }

    public String getRule() { return rule; }
    public void setRule(String rule) { this.rule = rule; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public String formatTrafficStr() {
        return relativeTimeStr + " · " + formatBytes(uploadBytes) + " ↑ " + formatBytes(downloadBytes) + " ↓";
    }

    private static String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + "B";
        if (bytes < 1024 * 1024) {
            double kb = bytes / 1024.0;
            if (Math.abs(kb - Math.round(kb)) < 0.05) {
                return String.format(java.util.Locale.US, "%dKB", Math.round(kb));
            }
            return String.format(java.util.Locale.US, "%.1fKB", kb);
        }
        if (bytes < 1024 * 1024 * 1024) {
            double mb = bytes / (1024.0 * 1024.0);
            if (Math.abs(mb - Math.round(mb)) < 0.05) {
                return String.format(java.util.Locale.US, "%dMB", Math.round(mb));
            }
            return String.format(java.util.Locale.US, "%.1fMB", mb);
        }
        double gb = bytes / (1024.0 * 1024.0 * 1024.0);
        return String.format(java.util.Locale.US, "%.2fGB", gb);
    }
}
