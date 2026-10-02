package com.proxy.wireopen.model;

import com.google.gson.annotations.SerializedName;
import java.io.Serializable;

public class IpPureResult implements Serializable {

    @SerializedName("ip")
    private String ip;

    @SerializedName("asn")
    private String asn;

    @SerializedName("asOrganization")
    private String asOrganization;

    @SerializedName("country")
    private String country;

    @SerializedName("countryCode")
    private String countryCode;

    @SerializedName("region")
    private String region;

    @SerializedName("regionCode")
    private String regionCode;

    @SerializedName("city")
    private String city;

    @SerializedName("timezone")
    private String timezone;

    @SerializedName("longitude")
    private String longitude;

    @SerializedName("latitude")
    private String latitude;

    @SerializedName("fraudScore")
    private int fraudScore;

    @SerializedName("isResidential")
    private boolean isResidential;

    @SerializedName("isBroadcast")
    private boolean isBroadcast;

    public IpPureResult() {}

    public IpPureResult(String ip, String asn, String asOrganization, String country,
                        String countryCode, String city, int fraudScore,
                        boolean isResidential, boolean isBroadcast) {
        this.ip = ip;
        this.asn = asn;
        this.asOrganization = asOrganization;
        this.country = country;
        this.countryCode = countryCode;
        this.city = city;
        this.fraudScore = fraudScore;
        this.isResidential = isResidential;
        this.isBroadcast = isBroadcast;
    }

    public String getIp() {
        return ip != null ? ip : "-";
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public String getAsn() {
        if (asn == null || asn.isEmpty()) return "AS-";
        return asn.startsWith("AS") ? asn : "AS" + asn;
    }

    public void setAsn(String asn) {
        this.asn = asn;
    }

    public String getAsOrganization() {
        return asOrganization != null ? asOrganization : "未知运营商";
    }

    public void setAsOrganization(String asOrganization) {
        this.asOrganization = asOrganization;
    }

    public String getCountry() {
        return country != null ? country : "";
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public String getCountryCode() {
        return countryCode != null ? countryCode : "";
    }

    public void setCountryCode(String countryCode) {
        this.countryCode = countryCode;
    }

    public String getCity() {
        return city != null ? city : "";
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getTimezone() {
        return timezone != null ? timezone : "";
    }

    public void setTimezone(String timezone) {
        this.timezone = timezone;
    }

    public int getFraudScore() {
        return fraudScore;
    }

    public void setFraudScore(int fraudScore) {
        this.fraudScore = fraudScore;
    }

    public boolean isResidential() {
        return isResidential;
    }

    public void setResidential(boolean residential) {
        isResidential = residential;
    }

    public boolean isBroadcast() {
        return isBroadcast;
    }

    public void setBroadcast(boolean broadcast) {
        isBroadcast = broadcast;
    }

    public String getLocationDisplay() {
        StringBuilder sb = new StringBuilder();
        String flag = getFlagEmoji();
        if (!flag.isEmpty()) {
            sb.append(flag).append(" ");
        }
        if (country != null && !country.isEmpty()) {
            sb.append(country);
        }
        if (city != null && !city.isEmpty()) {
            sb.append(" · ").append(city);
        }
        return sb.length() > 0 ? sb.toString() : "未知归属地";
    }

    public String getIpTypeDisplay() {
        if (isResidential) {
            return "原生住宅 IP (家庭宽带高权重)";
        } else {
            return isBroadcast ? "优质机房 IP (原生广播)" : "数据中心机房 IP";
        }
    }

    public String getRiskEvaluation() {
        if (fraudScore <= 20) {
            return "极低风险 · 适合 AI / ChatGPT / 跨境电商";
        } else if (fraudScore <= 50) {
            return "中度风险 · 适合日常浏览与媒体播放";
        } else {
            return "高风险 · 部分敏感平台可能遭遇风控";
        }
    }

    public String getFlagEmoji() {
        if (countryCode == null || countryCode.length() != 2) return "🌐";
        char c1 = Character.toUpperCase(countryCode.charAt(0));
        char c2 = Character.toUpperCase(countryCode.charAt(1));
        if (c1 < 'A' || c1 > 'Z' || c2 < 'A' || c2 > 'Z') {
            return "🌐";
        }
        int firstChar = c1 - 'A' + 0x1F1E6;
        int secondChar = c2 - 'A' + 0x1F1E6;
        return new String(Character.toChars(firstChar)) + new String(Character.toChars(secondChar));
    }
}
