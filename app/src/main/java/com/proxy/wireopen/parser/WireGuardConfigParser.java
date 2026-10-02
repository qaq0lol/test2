package com.proxy.wireopen.parser;

import com.proxy.wireopen.model.WireGuardConfig;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

/**
 * Parser for WireGuard INI configuration files (.conf).
 */
public class WireGuardConfigParser {

    public static class ParseException extends Exception {
        public ParseException(String message) {
            super(message);
        }
    }

    /**
     * Parses a WireGuard .conf content into WireGuardConfig.
     */
    public static WireGuardConfig parse(String content) throws ParseException {
        if (content == null || content.trim().isEmpty()) {
            throw new ParseException("配置文件内容为空");
        }

        WireGuardConfig config = new WireGuardConfig();
        String currentSection = "";
        boolean hasInterfaceSection = false;
        boolean hasPeerSection = false;

        try (BufferedReader reader = new BufferedReader(new StringReader(content))) {
            String line;
            int lineNumber = 0;

            while ((line = reader.readLine()) != null) {
                lineNumber++;
                line = line.trim();

                // Skip comments and empty lines
                if (line.isEmpty() || line.startsWith("#") || line.startsWith(";")) {
                    continue;
                }

                // Check section headers
                if (line.startsWith("[")) {
                    int closeIdx = line.indexOf(']');
                    if (closeIdx != -1) {
                        currentSection = line.substring(1, closeIdx).trim().toLowerCase();
                        if ("interface".equals(currentSection)) {
                            hasInterfaceSection = true;
                        } else if ("peer".equals(currentSection)) {
                            hasPeerSection = true;
                        }
                        continue;
                    }
                }

                // Parse key = value
                int eqIdx = line.indexOf('=');
                if (eqIdx == -1) {
                    continue;
                }

                String key = line.substring(0, eqIdx).trim().toLowerCase();
                String value = line.substring(eqIdx + 1).trim();

                // Strip inline comments if any (# or ;)
                int hashIdx = value.indexOf('#');
                int semiIdx = value.indexOf(';');
                int firstComment = -1;
                if (hashIdx != -1 && semiIdx != -1) {
                    firstComment = Math.min(hashIdx, semiIdx);
                } else if (hashIdx != -1) {
                    firstComment = hashIdx;
                } else if (semiIdx != -1) {
                    firstComment = semiIdx;
                }

                if (firstComment != -1) {
                    value = value.substring(0, firstComment).trim();
                }

                // Strip optional surrounding quotes
                if ((value.startsWith("\"") && value.endsWith("\"") && value.length() >= 2) ||
                    (value.startsWith("'") && value.endsWith("'") && value.length() >= 2)) {
                    value = value.substring(1, value.length() - 1).trim();
                }

                if ("interface".equals(currentSection)) {
                    parseInterfaceField(config, key, value);
                } else if ("peer".equals(currentSection)) {
                    parsePeerField(config, key, value);
                }
            }
        } catch (IOException e) {
            throw new ParseException("读取配置文件异常: " + e.getMessage());
        }

        if (!hasInterfaceSection) {
            throw new ParseException("缺少必需的 [Interface] 配置节");
        }
        if (!hasPeerSection) {
            throw new ParseException("缺少必需的 [Peer] 配置节");
        }
        if (config.getPrivateKey().isEmpty()) {
            throw new ParseException("缺少 Interface.PrivateKey 私钥");
        }
        if (config.getAddresses().isEmpty()) {
            throw new ParseException("缺少 Interface.Address 客户端IP地址");
        }
        if (config.getPublicKey().isEmpty()) {
            throw new ParseException("缺少 Peer.PublicKey 服务端公钥");
        }
        if (config.getEndpointHost().isEmpty()) {
            throw new ParseException("缺少 Peer.Endpoint 服务端连接节点");
        }

        return config;
    }

    private static void parseInterfaceField(WireGuardConfig config, String key, String value) throws ParseException {
        switch (key) {
            case "privatekey":
                config.setPrivateKey(value);
                break;
            case "address":
                for (String part : value.split(",")) {
                    String addr = part.trim();
                    if (!addr.isEmpty()) {
                        config.getAddresses().add(addr);
                    }
                }
                break;
            case "dns":
                for (String part : value.split(",")) {
                    String dns = part.trim();
                    if (!dns.isEmpty()) {
                        config.getDnsServers().add(dns);
                    }
                }
                break;
            case "listenport":
                try {
                    config.setListenPort(Integer.parseInt(value));
                } catch (NumberFormatException ignored) {}
                break;
            case "mtu":
                try {
                    config.setMtu(Integer.parseInt(value));
                } catch (NumberFormatException ignored) {}
                break;
        }
    }

    private static void parsePeerField(WireGuardConfig config, String key, String value) {
        switch (key) {
            case "publickey":
                config.setPublicKey(value);
                break;
            case "presharedkey":
                config.setPresharedKey(value);
                break;
            case "endpoint":
                parseEndpoint(config, value);
                break;
            case "allowedips":
                for (String part : value.split(",")) {
                    String ip = part.trim();
                    if (!ip.isEmpty()) {
                        config.getAllowedIps().add(ip);
                    }
                }
                break;
            case "persistentkeepalive":
                try {
                    config.setPersistentKeepalive(Integer.parseInt(value));
                } catch (NumberFormatException ignored) {}
                break;
        }
    }

    private static void parseEndpoint(WireGuardConfig config, String endpoint) {
        endpoint = endpoint.trim();
        if ((endpoint.startsWith("\"") && endpoint.endsWith("\"") && endpoint.length() >= 2) ||
            (endpoint.startsWith("'") && endpoint.endsWith("'") && endpoint.length() >= 2)) {
            endpoint = endpoint.substring(1, endpoint.length() - 1).trim();
        }

        if (endpoint.startsWith("[")) {
            // IPv6 [2001:db8::1]:51820
            int closeBracket = endpoint.indexOf(']');
            if (closeBracket != -1) {
                String host = endpoint.substring(1, closeBracket);
                int colon = endpoint.indexOf(':', closeBracket);
                if (colon != -1) {
                    try {
                        int port = Integer.parseInt(endpoint.substring(colon + 1).trim());
                        config.setEndpointHost(host);
                        config.setEndpointPort(port);
                        return;
                    } catch (NumberFormatException ignored) {}
                }
                config.setEndpointHost(host);
                config.setEndpointPort(51820);
                return;
            }
        }

        // Check if unbracketed IPv6 (contains multiple colons)
        int firstColon = endpoint.indexOf(':');
        int lastColon = endpoint.lastIndexOf(':');
        if (firstColon != -1 && firstColon != lastColon) {
            config.setEndpointHost(endpoint);
            config.setEndpointPort(51820);
            return;
        }

        if (lastColon != -1) {
            String host = endpoint.substring(0, lastColon).trim();
            try {
                int port = Integer.parseInt(endpoint.substring(lastColon + 1).trim());
                config.setEndpointHost(host);
                config.setEndpointPort(port);
                return;
            } catch (NumberFormatException ignored) {}
        }
        config.setEndpointHost(endpoint);
        config.setEndpointPort(51820);
    }

    /**
     * Serializes a WireGuardConfig back into standard .conf string.
     */
    public static String serialize(WireGuardConfig config) {
        StringBuilder sb = new StringBuilder();
        sb.append("[Interface]\n");
        sb.append("PrivateKey = ").append(config.getPrivateKey()).append("\n");
        if (!config.getAddresses().isEmpty()) {
            sb.append("Address = ").append(String.join(", ", config.getAddresses())).append("\n");
        }
        if (!config.getDnsServers().isEmpty()) {
            sb.append("DNS = ").append(String.join(", ", config.getDnsServers())).append("\n");
        }
        if (config.getListenPort() > 0) {
            sb.append("ListenPort = ").append(config.getListenPort()).append("\n");
        }
        if (config.getMtu() > 0) {
            sb.append("MTU = ").append(config.getMtu()).append("\n");
        }

        sb.append("\n[Peer]\n");
        sb.append("PublicKey = ").append(config.getPublicKey()).append("\n");
        if (config.getPresharedKey() != null && !config.getPresharedKey().isEmpty()) {
            sb.append("PresharedKey = ").append(config.getPresharedKey()).append("\n");
        }
        sb.append("Endpoint = ").append(config.getFullEndpoint()).append("\n");
        if (!config.getAllowedIps().isEmpty()) {
            sb.append("AllowedIPs = ").append(String.join(", ", config.getAllowedIps())).append("\n");
        } else {
            sb.append("AllowedIPs = 0.0.0.0/0, ::/0\n");
        }
        if (config.getPersistentKeepalive() > 0) {
            sb.append("PersistentKeepalive = ").append(config.getPersistentKeepalive()).append("\n");
        }

        return sb.toString();
    }
}
