package com.proxy.wireopen;

import com.proxy.wireopen.model.OpenVpnConfig;
import com.proxy.wireopen.parser.OpenVpnConfigParser;
import org.junit.Test;

public class OpenVpnConfigParserTest {

    public static void runAllTests() {
        System.out.println("--- Running OpenVpnConfigParserTest ---");
        OpenVpnConfigParserTest t = new OpenVpnConfigParserTest();
        t.testValidOpenVpnConfig();
        t.testInlineCertificates();
        t.testSerializationRoundtrip();
        t.testInvalidConfigs();
        t.testEdgeCases();
        t.testInlineCommentsAndQuotedParameters();
        System.out.println("OpenVpnConfigParserTest: ALL PASSED!\n");
    }

    @Test
    public void testEdgeCases() {
        String ovpnEdge =
                "client\r\n" +
                "dev   tun0\r\n" +
                "PROTO   UDP4\r\n" +
                "REMOTE   gateway.myserver.org\r\n" + // no port specified, should default to 1194
                "cipher   AES-128-CBC\r\n" +
                "auth   SHA1\r\n" +
                "<ca>\r\n" +
                "line 1\r\n" +
                "line 2\r\n" +
                "</ca>\r\n";

        try {
            OpenVpnConfig config = OpenVpnConfigParser.parse(ovpnEdge);
            assertEq("Remote host", "gateway.myserver.org", config.getRemoteHost());
            assertEq("Default remote port", 1194, config.getRemotePort());
            assertEq("Normalized proto", "udp", config.getProtocol());
            assertEq("Normalized dev", "tun", config.getDeviceType());
            assertEq("Cipher", "AES-128-CBC", config.getCipher());
            assertEq("Auth", "SHA1", config.getAuth());
            if (!config.getCaCert().contains("line 1\nline 2")) {
                throw new AssertionError("CA cert lines mismatch: " + config.getCaCert());
            }
            System.out.println("  ✓ testEdgeCases passed");
        } catch (Exception e) {
            throw new RuntimeException("testEdgeCases failed", e);
        }
    }

    @Test
    public void testValidOpenVpnConfig() {
        String ovpn =
                "client\n" +
                "dev tun\n" +
                "proto tcp\n" +
                "remote ovpn.example.com 443\n" +
                "resolv-retry infinite\n" +
                "nobind\n" +
                "persist-key\n" +
                "persist-tun\n" +
                "tun-mtu 1400\n" +
                "cipher AES-256-GCM\n" +
                "auth SHA512\n" +
                "redirect-gateway def1\n" +
                "dhcp-option DNS 8.8.8.8\n" +
                "dhcp-option DNS 1.1.1.1\n";

        try {
            OpenVpnConfig config = OpenVpnConfigParser.parse(ovpn);
            assertEq("Remote host", "ovpn.example.com", config.getRemoteHost());
            assertEq("Remote port", 443, config.getRemotePort());
            assertEq("Protocol", "tcp", config.getProtocol());
            assertEq("Dev type", "tun", config.getDeviceType());
            assertEq("MTU", 1400, config.getMtu());
            assertEq("Cipher", "AES-256-GCM", config.getCipher());
            assertEq("Auth", "SHA512", config.getAuth());
            assertEq("RedirectGateway", true, config.isRedirectGateway());
            assertEq("DNS count", 2, config.getDnsServers().size());
            if (!config.isValid()) {
                throw new AssertionError("Config should be valid");
            }
            System.out.println("  ✓ testValidOpenVpnConfig passed");
        } catch (Exception e) {
            throw new RuntimeException("testValidOpenVpnConfig failed", e);
        }
    }

    @Test
    public void testInlineCertificates() {
        String ovpn =
                "client\n" +
                "dev tun\n" +
                "proto udp\n" +
                "remote myvpn.org 1194\n" +
                "<ca>\n" +
                "-----BEGIN CERTIFICATE-----\n" +
                "FAKE_CA_CONTENT\n" +
                "-----END CERTIFICATE-----\n" +
                "</ca>\n" +
                "<cert>\n" +
                "-----BEGIN CERTIFICATE-----\n" +
                "FAKE_CLIENT_CERT\n" +
                "-----END CERTIFICATE-----\n" +
                "</cert>\n" +
                "<key>\n" +
                "-----BEGIN PRIVATE KEY-----\n" +
                "FAKE_CLIENT_KEY\n" +
                "-----END PRIVATE KEY-----\n" +
                "</key>\n";

        try {
            OpenVpnConfig config = OpenVpnConfigParser.parse(ovpn);
            if (!config.getCaCert().contains("FAKE_CA_CONTENT")) {
                throw new AssertionError("CA cert not parsed properly");
            }
            if (!config.getClientCert().contains("FAKE_CLIENT_CERT")) {
                throw new AssertionError("Client cert not parsed properly");
            }
            if (!config.getClientKey().contains("FAKE_CLIENT_KEY")) {
                throw new AssertionError("Client key not parsed properly");
            }
            System.out.println("  ✓ testInlineCertificates passed");
        } catch (Exception e) {
            throw new RuntimeException("testInlineCertificates failed", e);
        }
    }

    @Test
    public void testSerializationRoundtrip() {
        String ovpn =
                "client\n" +
                "dev tun\n" +
                "proto udp\n" +
                "remote test.server.net 1194\n" +
                "tun-mtu 1500\n" +
                "cipher AES-128-GCM\n" +
                "auth SHA256\n" +
                "redirect-gateway def1\n" +
                "dhcp-option DNS 9.9.9.9\n";

        try {
            OpenVpnConfig c1 = OpenVpnConfigParser.parse(ovpn);
            String serialized = OpenVpnConfigParser.serialize(c1);
            OpenVpnConfig c2 = OpenVpnConfigParser.parse(serialized);

            assertEq("Roundtrip Remote", c1.getFullRemote(), c2.getFullRemote());
            assertEq("Roundtrip Proto", c1.getProtocol(), c2.getProtocol());
            assertEq("Roundtrip MTU", c1.getMtu(), c2.getMtu());
            assertEq("Roundtrip Cipher", c1.getCipher(), c2.getCipher());
            System.out.println("  ✓ testSerializationRoundtrip passed");
        } catch (Exception e) {
            throw new RuntimeException("testSerializationRoundtrip failed", e);
        }
    }

    @Test
    public void testInvalidConfigs() {
        // Missing remote
        try {
            OpenVpnConfigParser.parse("client\ndev tun\nproto udp\n");
            throw new AssertionError("Should fail for missing remote");
        } catch (OpenVpnConfigParser.ParseException e) {
            // expected
        }

        System.out.println("  ✓ testInvalidConfigs passed");
    }

    @Test
    public void testInlineCommentsAndQuotedParameters() {
        String complexOvpn =
                "client\n" +
                "dev \"tun\"\n" +
                "proto \"udp\" # use udp for faster speed\n" +
                "remote \"my.ovpn-server.net\" 1195 udp # primary cluster endpoint\n" +
                "cipher \"AES-256-GCM\" ; secure cipher\n" +
                "auth \"SHA256\"\n" +
                "key-direction 1\n" +
                "route 192.168.100.0/24\n" +
                "route 10.50.0.0 255.255.0.0\n" +
                "<tls-auth direction=\"1\">\n" +
                "-----BEGIN OpenVPN Static key V1-----\n" +
                "TEST_TLS_AUTH_CONTENT\n" +
                "-----END OpenVPN Static key V1-----\n" +
                "</tls-auth>\n";

        try {
            OpenVpnConfig config = OpenVpnConfigParser.parse(complexOvpn);
            assertEq("Remote host without quotes", "my.ovpn-server.net", config.getRemoteHost());
            assertEq("Remote port", 1195, config.getRemotePort());
            assertEq("Protocol without comment contamination", "udp", config.getProtocol());
            assertEq("Cipher unquoted", "AES-256-GCM", config.getCipher());
            assertEq("TlsAuthDirection", 1, config.getTlsAuthDirection());
            assertEq("Route CIDR count", 2, config.getRoutes().size());
            assertEq("First route", "192.168.100.0/24", config.getRoutes().get(0));
            assertEq("Second route", "10.50.0.0/255.255.0.0", config.getRoutes().get(1));
            if (!config.getTlsAuthKey().contains("TEST_TLS_AUTH_CONTENT")) {
                throw new AssertionError("tls-auth key content missing");
            }
            System.out.println("  ✓ testInlineCommentsAndQuotedParameters passed");
        } catch (Exception e) {
            throw new RuntimeException("testInlineCommentsAndQuotedParameters failed", e);
        }
    }

    private static void assertEq(String msg, Object expected, Object actual) {
        if (expected == null && actual == null) return;
        if (expected != null && expected.equals(actual)) return;
        throw new AssertionError(msg + " - Expected: <" + expected + ">, Got: <" + actual + ">");
    }
}
