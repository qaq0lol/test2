package com.proxy.wireopen.util;

import java.io.Serializable;
import java.util.Locale;

/**
 * Intelligent detector that automatically identifies geographical region and flag emoji
 * from profile filename, profile name, endpoint host, or configuration text.
 */
public class RegionDetector {

    public static class RegionInfo implements Serializable {
        private static final long serialVersionUID = 1L;

        private final String flag;
        private final String name;
        private final String countryCode;

        public RegionInfo(String flag, String name, String countryCode) {
            this.flag = flag;
            this.name = name;
            this.countryCode = countryCode;
        }

        public String getFlag() { return flag; }
        public String getName() { return name; }
        public String getCountryCode() { return countryCode; }

        @Override
        public String toString() {
            return flag + " " + name;
        }
    }

    /**
     * Inspects search texts and returns the best matching geographical region information.
     */
    public static RegionInfo detect(String... searchTexts) {
        StringBuilder combined = new StringBuilder();
        if (searchTexts != null) {
            for (String text : searchTexts) {
                if (text != null) {
                    combined.append(" ").append(text.toLowerCase(Locale.ROOT));
                }
            }
        }
        combined.append(" ");
        String s = combined.toString();

        java.util.Set<String> tokens = new java.util.HashSet<>();
        if (searchTexts != null) {
            for (String text : searchTexts) {
                if (text != null) {
                    for (String part : text.toLowerCase(Locale.ROOT).split("[^a-z0-9\\u4e00-\\u9fa5]+")) {
                        if (!part.isEmpty()) {
                            tokens.add(part);
                        }
                    }
                }
            }
        }

        // 1. Australia / Melbourne / Sydney / Yarra
        if (s.contains("melbourne") || s.contains("sydney") || s.contains("australia")
                || s.contains("澳大利亚") || s.contains("墨尔本") || s.contains("悉尼") || s.contains("🇦🇺")
                || s.contains("yarra") || s.contains("wg1194-melbourne")
                || tokens.contains("au") || tokens.contains("aus")
                || s.contains(".au.") || s.contains(".au:") || s.contains(".com.au")) {
            return new RegionInfo("🇦🇺", "澳大利亚 · 墨尔本", "au");
        }

        // 2. Japan / Tokyo / Osaka
        if (s.contains("tokyo") || s.contains("osaka") || s.contains("japan")
                || s.contains("日本") || s.contains("东京") || s.contains("大阪") || s.contains("🇯🇵")
                || tokens.contains("jp") || tokens.contains("jpn")
                || s.contains(".jp.") || s.contains(".jp:") || s.contains(".co.jp")) {
            return new RegionInfo("🇯🇵", "日本 · 东京", "jp");
        }

        // 3. United States / Los Angeles / Cera / America
        if (s.contains("los angeles") || s.contains("cera") || s.contains("america")
                || s.contains("us-cera") || s.contains("united states") || s.contains("california")
                || s.contains("silicon valley") || s.contains("san jose") || s.contains("seattle")
                || s.contains("new york") || s.contains("dallas") || s.contains("chicago")
                || s.contains("美国") || s.contains("洛杉矶") || s.contains("硅谷") || s.contains("🇺🇸")
                || tokens.contains("us") || tokens.contains("usa")
                || s.contains(".us.") || s.contains(".us:")) {
            return new RegionInfo("🇺🇸", "美国 · 洛杉矶", "us");
        }

        // 4. Singapore
        if (s.contains("singapore") || s.contains("新加坡") || s.contains("狮城") || s.contains("🇸🇬")
                || tokens.contains("sg") || tokens.contains("sgp")
                || s.contains(".sg.") || s.contains(".sg:") || s.contains(".com.sg")) {
            return new RegionInfo("🇸🇬", "新加坡", "sg");
        }

        // 5. Hong Kong
        if (s.contains("hong kong") || s.contains("hongkong") || s.contains("香港") || s.contains("🇭🇰")
                || tokens.contains("hk") || tokens.contains("hkg")
                || s.contains(".hk.") || s.contains(".hk:") || s.contains(".com.hk")) {
            return new RegionInfo("🇭🇰", "中国 · 香港", "hk");
        }

        // 6. Germany / Frankfurt
        if (s.contains("germany") || s.contains("frankfurt") || s.contains("deutschland")
                || s.contains("德国") || s.contains("法兰克福") || s.contains("🇩🇪")
                || tokens.contains("de") || tokens.contains("deu")
                || s.contains(".de.") || s.contains(".de:")) {
            return new RegionInfo("🇩🇪", "德国 · 法兰克福", "de");
        }

        // 7. United Kingdom / London
        if (s.contains("london") || s.contains("united kingdom") || s.contains("great britain")
                || s.contains("英国") || s.contains("伦敦") || s.contains("🇬🇧")
                || tokens.contains("uk") || tokens.contains("gb") || tokens.contains("gbr")
                || s.contains(".uk.") || s.contains(".uk:") || s.contains(".co.uk")) {
            return new RegionInfo("🇬🇧", "英国 · 伦敦", "uk");
        }

        // 8. Korea / Seoul
        if (s.contains("korea") || s.contains("seoul")
                || s.contains("韩国") || s.contains("首尔") || s.contains("🇰🇷")
                || tokens.contains("kr") || tokens.contains("kor")
                || s.contains(".kr.") || s.contains(".kr:") || s.contains(".co.kr")) {
            return new RegionInfo("🇰🇷", "韩国 · 首尔", "kr");
        }

        // 9. Taiwan
        if (s.contains("taiwan") || s.contains("taipei")
                || s.contains("台湾") || s.contains("台北") || s.contains("🇹🇼")
                || tokens.contains("tw") || tokens.contains("twn")
                || s.contains(".tw.") || s.contains(".tw:") || s.contains(".com.tw")) {
            return new RegionInfo("🇹🇼", "中国 · 台湾", "tw");
        }

        // Fallback: Global
        return new RegionInfo("🌐", "全球代理节点", "global");
    }

    public static String detectFlag(String... searchTexts) {
        return detect(searchTexts).getFlag();
    }

    public static String detectRegionName(String... searchTexts) {
        return detect(searchTexts).getName();
    }
}
