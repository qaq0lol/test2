package com.proxy.wireopen;

import com.proxy.wireopen.model.WireGuardConfig;
import com.proxy.wireopen.parser.WireGuardConfigParser;
import org.junit.Test;

public class WireGuardConfigParserTest {

    public static void runAllTests() {
        System.out.println("--- Running WireGuardConfigParserTest ---");
        WireGuardConfigParserTest t = new WireGuardConfigParserTest();
        t.testValidWireGuardConfig();
        t.testIpv6Endpoint();
        t.testSerializationRoundtrip();
        t.testInvalidConfigs();
        t.testEdgeCases();
        t.testQuotedValuesAndBareIpv6();
        t.testMelbourneConfigAndWireGuardEngineConversion();
        System.out.println("WireGuardConfigParserTest: ALL PASSED!\n");
    }

    @Test
    public void testEdgeCases() {
        String edgeConf =
                "  # leading comment\r\n" +
                "[InTeRfAcE]  ; section comment\r\n" +
                "  pRiVaTeKeY =  aGVsbG93b3JsZHByaXZhdGVrZXlleGFtcGxlMTIzNDU2Nzg=   # inline comment\r\n" +
                "  AdDrEsS = 10.0.0.3/32 ,  192.168.10.2/24  \r\n" +
                "  Dns =  1.1.1.1  , 8.8.4.4 \r\n" +
                "  mtu =  1280 \r\n\r\n" +
                "[pEeR]\r\n" +
                "  PuBlIcKeY = d2lyZWd1YXJkcHVibGlja2V5ZXhhbXBsZTEyMzQ1Njc4OTA= \r\n" +
                "  EnDpOiNt =   wg-edge.domain.co:5555  \r\n" +
                "  AlLoWeDiPs =  0.0.0.0/0 \r\n";

        try {
            WireGuardConfig config = WireGuardConfigParser.parse(edgeConf);
            assertEq("Parsed host", "wg-edge.domain.co", config.getEndpointHost());
            assertEq("Parsed port", 5555, config.getEndpointPort());
            assertEq("MTU", 1280, config.getMtu());
            assertEq("Addresses count", 2, config.getAddresses().size());
            assertEq("First address", "10.0.0.3/32", config.getAddresses().get(0));
            assertEq("Second address", "192.168.10.2/24", config.getAddresses().get(1));
            assertEq("DNS count", 2, config.getDnsServers().size());
            System.out.println("  ✓ testEdgeCases passed");
        } catch (Exception e) {
            throw new RuntimeException("testEdgeCases failed", e);
        }
    }

    @Test
    public void testValidWireGuardConfig() {
        String conf =
                "[Interface]\n" +
                "PrivateKey = aGVsbG93b3JsZHByaXZhdGVrZXlleGFtcGxlMTIzNDU2Nzg=\n" +
                "Address = 10.0.0.2/32, fd86:ea04:1115::2/128\n" +
                "DNS = 1.1.1.1, 8.8.8.8\n" +
                "MTU = 1380\n" +
                "ListenPort = 51820\n\n" +
                "[Peer]\n" +
                "PublicKey = d2lyZWd1YXJkcHVibGlja2V5ZXhhbXBsZTEyMzQ1Njc4OTA=\n" +
                "PresharedKey = cHJlc2hhcmVka2V5ZXhhbXBsZTEyMzQ1Njc4OTAxMjM0NTY=\n" +
                "Endpoint = vpn.example.com:51820\n" +
                "AllowedIPs = 0.0.0.0/0, ::/0\n" +
                "PersistentKeepalive = 25\n";

        try {
            WireGuardConfig config = WireGuardConfigParser.parse(conf);
            assertEq("Endpoint host", "vpn.example.com", config.getEndpointHost());
            assertEq("Endpoint port", 51820, config.getEndpointPort());
            assertEq("Full endpoint", "vpn.example.com:51820", config.getFullEndpoint());
            assertEq("MTU", 1380, config.getMtu());
            assertEq("ListenPort", 51820, config.getListenPort());
            assertEq("PersistentKeepalive", 25, config.getPersistentKeepalive());
            assertEq("Address count", 2, config.getAddresses().size());
            assertEq("DNS count", 2, config.getDnsServers().size());
            assertEq("AllowedIPs count", 2, config.getAllowedIps().size());
            if (!config.isValid()) {
                throw new AssertionError("Config should be valid");
            }
            System.out.println("  ✓ testValidWireGuardConfig passed");
        } catch (Exception e) {
            throw new RuntimeException("testValidWireGuardConfig failed", e);
        }
    }

    @Test
    public void testIpv6Endpoint() {
        String conf =
                "[Interface]\n" +
                "PrivateKey = SLNudAS+xsRwYqxsHkkFe2lK1WETg1Ut4sDTCMAKAl0=\n" +
                "Address = 10.10.0.5/24\n\n" +
                "[Peer]\n" +
                "PublicKey = sDDXsvjyVqpB8fecUsjX0/Y8YdZye+oiV1Dy9BfUkwE=\n" +
                "Endpoint = [2001:db8::1]:51820\n" +
                "AllowedIPs = 10.0.0.0/8\n";

        try {
            WireGuardConfig config = WireGuardConfigParser.parse(conf);
            assertEq("IPv6 host", "2001:db8::1", config.getEndpointHost());
            assertEq("IPv6 port", 51820, config.getEndpointPort());
            assertEq("Full endpoint with brackets", "[2001:db8::1]:51820", config.getFullEndpoint());

            // Verify conversion to official WireGuard Config
            com.wireguard.config.Config wgConfig = com.proxy.wireopen.engine.WireGuardEngine.toWireGuardConfig(null, config);
            assertEq("Peer count", 1, wgConfig.getPeers().size());
            String uapi = wgConfig.toWgUserspaceString();
            if (!uapi.contains("endpoint=[2001:db8") || !uapi.contains(":51820")) {
                throw new AssertionError("UAPI userspace string missing valid IPv6 endpoint: " + uapi);
            }
            // Verify fallback DNS was applied since Interface had no DNS
            assertEq("Fallback DNS count", 2, wgConfig.getInterface().getDnsServers().size());

            System.out.println("  ✓ testIpv6Endpoint passed");
        } catch (Exception e) {
            throw new RuntimeException("testIpv6Endpoint failed", e);
        }
    }

    @Test
    public void testSerializationRoundtrip() {
        String conf =
                "[Interface]\n" +
                "PrivateKey = aGVsbG93b3JsZHByaXZhdGVrZXlleGFtcGxlMTIzNDU2Nzg=\n" +
                "Address = 10.0.0.2/32\n" +
                "DNS = 1.1.1.1\n" +
                "MTU = 1420\n\n" +
                "[Peer]\n" +
                "PublicKey = d2lyZWd1YXJkcHVibGlja2V5ZXhhbXBsZTEyMzQ1Njc4OTA=\n" +
                "Endpoint = wg.sample.net:51820\n" +
                "AllowedIPs = 0.0.0.0/0, ::/0\n" +
                "PersistentKeepalive = 25\n";

        try {
            WireGuardConfig c1 = WireGuardConfigParser.parse(conf);
            String serialized = WireGuardConfigParser.serialize(c1);
            WireGuardConfig c2 = WireGuardConfigParser.parse(serialized);

            assertEq("Roundtrip Endpoint", c1.getFullEndpoint(), c2.getFullEndpoint());
            assertEq("Roundtrip PrivateKey", c1.getPrivateKey(), c2.getPrivateKey());
            assertEq("Roundtrip PublicKey", c1.getPublicKey(), c2.getPublicKey());
            assertEq("Roundtrip MTU", c1.getMtu(), c2.getMtu());
            System.out.println("  ✓ testSerializationRoundtrip passed");
        } catch (Exception e) {
            throw new RuntimeException("testSerializationRoundtrip failed", e);
        }
    }

    @Test
    public void testInvalidConfigs() {
        // Missing [Peer]
        try {
            WireGuardConfigParser.parse("[Interface]\nPrivateKey = key\nAddress = 10.0.0.1/24\n");
            throw new AssertionError("Should fail for missing Peer");
        } catch (WireGuardConfigParser.ParseException e) {
            // expected
        }

        // Missing PrivateKey
        try {
            WireGuardConfigParser.parse("[Interface]\nAddress = 10.0.0.1/24\n[Peer]\nPublicKey = pub\nEndpoint = 1.1.1.1:51820\n");
            throw new AssertionError("Should fail for missing PrivateKey");
        } catch (WireGuardConfigParser.ParseException e) {
            // expected
        }

        System.out.println("  ✓ testInvalidConfigs passed");
    }

    @Test
    public void testQuotedValuesAndBareIpv6() {
        String quotedConf =
                "[Interface]\n" +
                "PrivateKey = \"aGVsbG93b3JsZHByaXZhdGVrZXlleGFtcGxlMTIzNDU2Nzg=\"\n" +
                "Address = \"10.0.0.9/32\"\n\n" +
                "[Peer]\n" +
                "PublicKey = 'd2lyZWd1YXJkcHVibGlja2V5ZXhhbXBsZTEyMzQ1Njc4OTA='\n" +
                "Endpoint = '2001:db8:85a3::8a2e:370:7334'\n";

        try {
            WireGuardConfig config = WireGuardConfigParser.parse(quotedConf);
            assertEq("Stripped private key", "aGVsbG93b3JsZHByaXZhdGVrZXlleGFtcGxlMTIzNDU2Nzg=", config.getPrivateKey());
            assertEq("Stripped public key", "d2lyZWd1YXJkcHVibGlja2V5ZXhhbXBsZTEyMzQ1Njc4OTA=", config.getPublicKey());
            assertEq("Bare IPv6 endpoint host", "2001:db8:85a3::8a2e:370:7334", config.getEndpointHost());
            assertEq("Default WireGuard port for bare IPv6", 51820, config.getEndpointPort());
            System.out.println("  ✓ testQuotedValuesAndBareIpv6 passed");
        } catch (Exception e) {
            throw new RuntimeException("testQuotedValuesAndBareIpv6 failed", e);
        }
    }

    @Test
    public void testMelbourneConfigAndWireGuardEngineConversion() {
        String melbourneConf =
                "[Interface]\n" +
                "PrivateKey = SLNudAS+xsRwYqxsHkkFe2lK1WETg1Ut4sDTCMAKAl0=\n" +
                "Address = 100.72.192.198/32, fd54:4::fb7b:f62a:f021:4b75/128\n" +
                "DNS = 10.255.255.3\n\n" +
                "[Peer]\n" +
                "PublicKey = sDDXsvjyVqpB8fecUsjX0/Y8YdZye+oiV1Dy9BfUkwE=\n" +
                "AllowedIPs = 0.0.0.0/0, ::/0\n" +
                "Endpoint = mel-224-wg.whiskergalaxy.com:1194\n" +
                "PresharedKey = C0cS6e6sQfVllaioxflXHuruZmjz+Ek3w5JCGf9Roq4=\n";

        try {
            WireGuardConfig config = WireGuardConfigParser.parse(melbourneConf);
            assertEq("Melbourne host", "mel-224-wg.whiskergalaxy.com", config.getEndpointHost());
            assertEq("Melbourne port", 1194, config.getEndpointPort());
            assertEq("Melbourne PresharedKey", "C0cS6e6sQfVllaioxflXHuruZmjz+Ek3w5JCGf9Roq4=", config.getPresharedKey());

            // Convert to official WireGuard Config
            com.wireguard.config.Config wgConfig = com.proxy.wireopen.engine.WireGuardEngine.toWireGuardConfig(null, config);
            if (wgConfig == null) {
                throw new AssertionError("wgConfig should not be null");
            }
            if (wgConfig.getPeers().isEmpty()) {
                throw new AssertionError("wgConfig must have at least one peer");
            }
            assertEq("Interface addresses count", 2, wgConfig.getInterface().getAddresses().size());
            assertEq("Interface dns count", 1, wgConfig.getInterface().getDnsServers().size());
            assertEq("Peer count", 1, wgConfig.getPeers().size());

            String uapi = wgConfig.toWgUserspaceString();
            if (!uapi.contains("public_key=") || !uapi.contains("preshared_key=")) {
                throw new AssertionError("Userspace string missing key configuration: " + uapi);
            }
            System.out.println("  ✓ testMelbourneConfigAndWireGuardEngineConversion passed");
        } catch (Exception e) {
            throw new RuntimeException("testMelbourneConfigAndWireGuardEngineConversion failed", e);
        }
    }

    private static void assertEq(String msg, Object expected, Object actual) {
        if (expected == null && actual == null) return;
        if (expected != null && expected.equals(actual)) return;
        throw new AssertionError(msg + " - Expected: <" + expected + ">, Got: <" + actual + ">");
    }
}
