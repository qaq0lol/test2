package com.proxy.wireopen.parser;

import com.proxy.wireopen.model.OpenVpnConfig;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

/**
 * Parser for OpenVPN configuration files (.ovpn).
 */
public class OpenVpnConfigParser {

    public static class ParseException extends Exception {
        public ParseException(String message) {
            super(message);
        }
    }

    /**
     * Parses an OpenVPN .ovpn content into OpenVpnConfig.
     */
    public static OpenVpnConfig parse(String content) throws ParseException {
        if (content == null || content.trim().isEmpty()) {
            throw new ParseException("OpenVPN 配置文件内容为空");
        }

        OpenVpnConfig config = new OpenVpnConfig();
        boolean insideTag = false;
        String currentTag = "";
        StringBuilder tagBuffer = new StringBuilder();

        try (BufferedReader reader = new BufferedReader(new StringReader(content))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();

                // Check for block closing tag </tag>
                if (insideTag) {
                    if (trimmed.equalsIgnoreCase("</" + currentTag + ">") || trimmed.toLowerCase().startsWith("</" + currentTag)) {
                        assignTagContent(config, currentTag, tagBuffer.toString().trim());
                        insideTag = false;
                        currentTag = "";
                        tagBuffer.setLength(0);
                    } else {
                        tagBuffer.append(line).append("\n");
                    }
                    continue;
                }

                // Skip comments and empty lines
                String stripped = stripComments(line).trim();
                if (stripped.isEmpty()) {
                    continue;
                }

                // Check for block opening tag <tag>
                if (stripped.startsWith("<") && stripped.endsWith(">") && !stripped.startsWith("</")) {
                    String tagContent = stripped.substring(1, stripped.length() - 1).trim();
                    int spaceIdx = tagContent.indexOf(' ');
                    currentTag = (spaceIdx != -1 ? tagContent.substring(0, spaceIdx) : tagContent).trim().toLowerCase();
                    insideTag = true;
                    tagBuffer.setLength(0);
                    continue;
                }

                // Parse standard OpenVPN directive: directive arg1 arg2 ...
                List<String> tokens = tokenize(stripped);
                if (tokens.isEmpty()) continue;

                String directive = tokens.get(0).toLowerCase();
                parseDirective(config, directive, tokens);
            }
        } catch (IOException e) {
            throw new ParseException("读取 OpenVPN 配置文件异常: " + e.getMessage());
        }

        if (!config.isValid()) {
            throw new ParseException("OpenVPN 配置缺少有效的 remote 远程服务器地址或端口");
        }

        return config;
    }

    private static String stripComments(String line) {
        boolean inQuote = false;
        char quoteChar = 0;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (inQuote) {
                if (c == quoteChar) {
                    inQuote = false;
                }
            } else {
                if (c == '"' || c == '\'') {
                    inQuote = true;
                    quoteChar = c;
                } else if (c == '#' || c == ';') {
                    return line.substring(0, i);
                }
            }
        }
        return line;
    }

    private static List<String> tokenize(String line) {
        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuote = false;
        char quoteChar = 0;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (inQuote) {
                if (c == quoteChar) {
                    inQuote = false;
                } else {
                    current.append(c);
                }
            } else {
                if (c == '"' || c == '\'') {
                    inQuote = true;
                    quoteChar = c;
                } else if (Character.isWhitespace(c)) {
                    if (current.length() > 0) {
                        tokens.add(current.toString());
                        current.setLength(0);
                    }
                } else {
                    current.append(c);
                }
            }
        }
        if (current.length() > 0) {
            tokens.add(current.toString());
        }
        return tokens;
    }

    private static void parseDirective(OpenVpnConfig config, String directive, List<String> tokens) {
        switch (directive) {
            case "remote":
                if (tokens.size() >= 2) {
                    config.setRemoteHost(tokens.get(1));
                }
                if (tokens.size() >= 3) {
                    try {
                        config.setRemotePort(Integer.parseInt(tokens.get(2)));
                    } catch (NumberFormatException ignored) {}
                }
                if (tokens.size() >= 4) {
                    config.setProtocol(normalizeProto(tokens.get(3)));
                }
                break;

            case "port":
                if (tokens.size() >= 2) {
                    try {
                        config.setRemotePort(Integer.parseInt(tokens.get(1)));
                    } catch (NumberFormatException ignored) {}
                }
                break;

            case "proto":
                if (tokens.size() >= 2) {
                    config.setProtocol(normalizeProto(tokens.get(1)));
                }
                break;

            case "dev":
                if (tokens.size() >= 2) {
                    String dev = tokens.get(1).toLowerCase();
                    config.setDeviceType(dev.startsWith("tun") ? "tun" : dev);
                }
                break;

            case "cipher":
                if (tokens.size() >= 2) {
                    config.setCipher(tokens.get(1));
                }
                break;

            case "auth":
                if (tokens.size() >= 2) {
                    config.setAuth(tokens.get(1));
                }
                break;

            case "tun-mtu":
                if (tokens.size() >= 2) {
                    try {
                        config.setMtu(Integer.parseInt(tokens.get(1)));
                    } catch (NumberFormatException ignored) {}
                }
                break;

            case "redirect-gateway":
                config.setRedirectGateway(true);
                break;

            case "dhcp-option":
                if (tokens.size() >= 3 && "dns".equalsIgnoreCase(tokens.get(1))) {
                    config.getDnsServers().add(tokens.get(2));
                }
                break;

            case "route":
                if (tokens.size() >= 2) {
                    String net = tokens.get(1);
                    if (net.contains("/")) {
                        config.getRoutes().add(net);
                    } else {
                        String mask = tokens.size() >= 3 ? tokens.get(2) : "255.255.255.255";
                        config.getRoutes().add(net + "/" + mask);
                    }
                }
                break;

            case "key-direction":
                if (tokens.size() >= 2) {
                    try {
                        config.setTlsAuthDirection(Integer.parseInt(tokens.get(1)));
                    } catch (NumberFormatException ignored) {}
                }
                break;

            case "auth-user-pass":
                config.setAuthUserPass(true);
                break;
        }
    }

    private static String normalizeProto(String rawProto) {
        String p = rawProto.toLowerCase();
        if (p.contains("tcp")) return "tcp";
        return "udp";
    }

    private static void assignTagContent(OpenVpnConfig config, String tag, String content) {
        switch (tag) {
            case "ca":
                config.setCaCert(content);
                break;
            case "cert":
                config.setClientCert(content);
                break;
            case "key":
                config.setClientKey(content);
                break;
            case "tls-auth":
            case "tls-crypt":
            case "tls-crypt-v2":
            case "secret":
                config.setTlsAuthKey(content);
                break;
        }
    }

    /**
     * Serializes OpenVpnConfig to .ovpn content string.
     */
    public static String serialize(OpenVpnConfig config) {
        StringBuilder sb = new StringBuilder();
        sb.append("client\n");
        sb.append("dev ").append(config.getDeviceType()).append("\n");
        sb.append("proto ").append(config.getProtocol()).append("\n");
        sb.append("remote ").append(config.getRemoteHost()).append(" ").append(config.getRemotePort()).append("\n");
        sb.append("resolv-retry infinite\n");
        sb.append("nobind\n");
        sb.append("persist-key\n");
        sb.append("persist-tun\n");
        sb.append("tun-mtu ").append(config.getMtu()).append("\n");
        sb.append("cipher ").append(config.getCipher()).append("\n");
        sb.append("auth ").append(config.getAuth()).append("\n");

        if (config.isRedirectGateway()) {
            sb.append("redirect-gateway def1\n");
        }

        for (String dns : config.getDnsServers()) {
            sb.append("dhcp-option DNS ").append(dns).append("\n");
        }

        for (String route : config.getRoutes()) {
            sb.append("route ").append(route.replace('/', ' ')).append("\n");
        }

        if (config.getTlsAuthDirection() != -1) {
            sb.append("key-direction ").append(config.getTlsAuthDirection()).append("\n");
        }

        if (config.isAuthUserPass()) {
            sb.append("auth-user-pass\n");
        }

        if (config.getCaCert() != null && !config.getCaCert().isEmpty()) {
            sb.append("<ca>\n").append(config.getCaCert()).append("\n</ca>\n");
        }
        if (config.getClientCert() != null && !config.getClientCert().isEmpty()) {
            sb.append("<cert>\n").append(config.getClientCert()).append("\n</cert>\n");
        }
        if (config.getClientKey() != null && !config.getClientKey().isEmpty()) {
            sb.append("<key>\n").append(config.getClientKey()).append("\n</key>\n");
        }
        if (config.getTlsAuthKey() != null && !config.getTlsAuthKey().isEmpty()) {
            sb.append("<tls-auth>\n").append(config.getTlsAuthKey()).append("\n</tls-auth>\n");
        }

        return sb.toString();
    }
}
