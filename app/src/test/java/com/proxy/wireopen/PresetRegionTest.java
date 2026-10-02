package com.proxy.wireopen;

import com.proxy.wireopen.model.PresetPortItem;
import com.proxy.wireopen.model.PresetRegion;
import com.proxy.wireopen.model.ProtocolType;
import com.proxy.wireopen.model.ProxyProfile;
import com.proxy.wireopen.repository.PresetRegionManager;
import com.proxy.wireopen.repository.ProfileRepository;
import com.proxy.wireopen.util.PortSpeedTester;
import com.proxy.wireopen.util.RegionDetector;

import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class PresetRegionTest {

    private static final Set<Integer> EXPECTED_PORTS = new HashSet<>(Arrays.asList(443, 80, 53, 123, 1194, 65142));

    @Test
    public void testPresetRegionManagerInitialState() {
        PresetRegionManager manager = PresetRegionManager.getInstance();
        manager.clear();
        List<PresetRegion> regions = manager.getRegions();
        Assert.assertNotNull(regions);
        Assert.assertEquals("PresetRegionManager must have NO hardcoded fake regions on startup", 0, regions.size());
    }

    @Test
    public void testRegionDetector() {
        // Australia
        Assert.assertEquals("🇦🇺", RegionDetector.detectFlag("Australia Melbourne Node"));
        Assert.assertTrue(RegionDetector.detectRegionName("Australia Melbourne").contains("澳大利亚"));
        Assert.assertEquals("🇦🇺", RegionDetector.detectFlag("sydney-server-01"));
        Assert.assertTrue(RegionDetector.detectRegionName("sydney-server-01").contains("澳大利亚"));

        // Japan
        Assert.assertEquals("🇯🇵", RegionDetector.detectFlag("Tokyo-JP-HighSpeed"));
        Assert.assertTrue(RegionDetector.detectRegionName("Tokyo-JP-HighSpeed").contains("日本"));
        Assert.assertEquals("🇯🇵", RegionDetector.detectFlag("大阪专线"));
        Assert.assertTrue(RegionDetector.detectRegionName("大阪专线").contains("日本"));

        // US
        Assert.assertEquals("🇺🇸", RegionDetector.detectFlag("US-Los Angeles-Cera"));
        Assert.assertTrue(RegionDetector.detectRegionName("US-Los Angeles-Cera").contains("美国"));
        Assert.assertEquals("🇺🇸", RegionDetector.detectFlag("Silicon Valley BGP"));
        Assert.assertTrue(RegionDetector.detectRegionName("Silicon Valley BGP").contains("美国"));

        // Singapore
        Assert.assertEquals("🇸🇬", RegionDetector.detectFlag("Singapore SG Premium"));
        Assert.assertTrue(RegionDetector.detectRegionName("Singapore SG Premium").contains("新加坡"));

        // Hong Kong
        Assert.assertEquals("🇭🇰", RegionDetector.detectFlag("HK BGP Direct"));
        Assert.assertTrue(RegionDetector.detectRegionName("HK BGP Direct").contains("香港"));

        // Germany & UK & Korea & Taiwan
        Assert.assertEquals("🇩🇪", RegionDetector.detectFlag("Frankfurt Germany"));
        Assert.assertEquals("🇬🇧", RegionDetector.detectFlag("London UK"));
        Assert.assertEquals("🇰🇷", RegionDetector.detectFlag("Seoul Korea"));
        Assert.assertEquals("🇹🇼", RegionDetector.detectFlag("Taipei Taiwan"));

        // Specific required patterns from task description
        Assert.assertEquals("🇦🇺", RegionDetector.detectFlag("WG1194-Melbourne-Yarra"));
        Assert.assertEquals("🇦🇺", RegionDetector.detectFlag("au.wireopen.com:443"));
        Assert.assertEquals("🇺🇸", RegionDetector.detectFlag("us.wireopen.com:443"));

        // Global fallback and boundary anti-false-positive tests
        Assert.assertEquals("🌐", RegionDetector.detectFlag("unknown-custom-node"));
        Assert.assertEquals("全球代理节点", RegionDetector.detectRegionName("unknown-custom-node"));
        Assert.assertEquals("🌐", RegionDetector.detectFlag("nexus-vpn.conf"));
        Assert.assertEquals("🌐", RegionDetector.detectFlag("status-node.conf"));
        Assert.assertEquals("🌐", RegionDetector.detectFlag("campus-vpn.conf"));
        Assert.assertEquals("🌐", RegionDetector.detectFlag("msg-queue.conf"));
    }

    @Test
    public void testWireGuardDerivedPorts() {
        String wgConfig =
                "[Interface]\n" +
                "PrivateKey = aGVsbG93b3JsZHByaXZhdGVrZXlleGFtcGxlMTIzNDU2Nzg=\n" +
                "Address = 10.0.0.2/32\n" +
                "[Peer]\n" +
                "PublicKey = d2lyZWd1YXJkcHVibGlja2V5ZXhhbXBsZTEyMzQ1Njc4OTA=\n" +
                "Endpoint = 198.51.100.1:51820\n" +
                "AllowedIPs = 0.0.0.0/0\n";

        ProxyProfile profile = new ProxyProfile("Melbourne WG 极速专线", ProtocolType.WIREGUARD, wgConfig);
        profile.setId("wg-1");
        PresetRegion region = PresetRegion.fromProfile(profile);

        Assert.assertNotNull(region);
        Assert.assertEquals("🇦🇺", region.getFlag());
        Assert.assertEquals("198.51.100.1", region.getHost());

        List<PresetPortItem> ports = region.getPorts();
        // WireGuard must derive strictly 6 UDP ports
        Assert.assertEquals("WireGuard profile must have exactly 6 derived ports", 6, ports.size());

        for (PresetPortItem p : ports) {
            Assert.assertEquals(ProtocolType.WIREGUARD, p.getProtocolType());
            Assert.assertEquals("UDP", p.getTransport());
            Assert.assertEquals("198.51.100.1", p.getHost());
            Assert.assertTrue("Port must be in expected set: " + p.getPort(), EXPECTED_PORTS.contains(p.getPort()));
        }

        // Verify portsByGroup
        List<PresetPortItem> wgUdp = region.getPortsByGroup(ProtocolType.WIREGUARD, "UDP");
        Assert.assertEquals(6, wgUdp.size());
        List<PresetPortItem> ovpnUdp = region.getPortsByGroup(ProtocolType.OPENVPN, "UDP");
        Assert.assertEquals(0, ovpnUdp.size());
        List<PresetPortItem> ovpnTcp = region.getPortsByGroup(ProtocolType.OPENVPN, "TCP");
        Assert.assertEquals(0, ovpnTcp.size());
    }

    @Test
    public void testOpenVpnDerivedPorts() {
        String ovpnConfig =
                "client\n" +
                "dev tun\n" +
                "proto udp\n" +
                "remote 203.0.113.5 1194\n" +
                "resolv-retry infinite\n" +
                "<ca>\n---BEGIN CERTIFICATE---\nfake\n---END CERTIFICATE---\n</ca>\n";

        ProxyProfile profile = new ProxyProfile("Tokyo Japan 专线", ProtocolType.OPENVPN, ovpnConfig);
        profile.setId("ovpn-1");
        PresetRegion region = PresetRegion.fromProfile(profile);

        Assert.assertNotNull(region);
        Assert.assertEquals("🇯🇵", region.getFlag());
        Assert.assertEquals("203.0.113.5", region.getHost());

        List<PresetPortItem> ports = region.getPorts();
        // OpenVPN must derive strictly 12 ports: 6 UDP + 6 TCP
        Assert.assertEquals("OpenVPN profile must have exactly 12 derived ports", 12, ports.size());

        List<PresetPortItem> ovpnUdp = region.getPortsByGroup(ProtocolType.OPENVPN, "UDP");
        Assert.assertEquals(6, ovpnUdp.size());
        List<PresetPortItem> ovpnTcp = region.getPortsByGroup(ProtocolType.OPENVPN, "TCP");
        Assert.assertEquals(6, ovpnTcp.size());
        List<PresetPortItem> wgUdp = region.getPortsByGroup(ProtocolType.WIREGUARD, "UDP");
        Assert.assertEquals(0, wgUdp.size());

        Set<Integer> udpPorts = new HashSet<>();
        for (PresetPortItem item : ovpnUdp) {
            udpPorts.add(item.getPort());
        }
        Assert.assertEquals(EXPECTED_PORTS, udpPorts);

        Set<Integer> tcpPorts = new HashSet<>();
        for (PresetPortItem item : ovpnTcp) {
            tcpPorts.add(item.getPort());
        }
        Assert.assertEquals(EXPECTED_PORTS, tcpPorts);
    }

    @Test
    public void testProfilePortSwitchingWireGuard() {
        ProfileRepository repo = new ProfileRepository(null);
        String wgConfig =
                "[Interface]\n" +
                "PrivateKey = aGVsbG93b3JsZHByaXZhdGVrZXlleGFtcGxlMTIzNDU2Nzg=\n" +
                "Address = 10.0.0.2/32\n" +
                "[Peer]\n" +
                "PublicKey = d2lyZWd1YXJkcHVibGlja2V5ZXhhbXBsZTEyMzQ1Njc4OTA=\n" +
                "Endpoint = 198.51.100.1:51820\n" +
                "AllowedIPs = 0.0.0.0/0\n";

        ProxyProfile profile = new ProxyProfile("US Cera 节点", ProtocolType.WIREGUARD, wgConfig);
        profile.setId("wg-test");
        repo.addProfile(profile);

        // Switch to port 65142 UDP
        ProxyProfile updated = repo.switchProfilePort("wg-test", 65142, "UDP");
        Assert.assertNotNull(updated);
        Assert.assertEquals(65142, updated.getWireGuardConfig().getEndpointPort());
        Assert.assertTrue("Raw config must reflect new port :65142", updated.getRawConfig().contains(":65142"));
        Assert.assertEquals("wg-test", repo.getActiveProfileId());
    }

    @Test
    public void testProfilePortSwitchingOpenVpn() {
        ProfileRepository repo = new ProfileRepository(null);
        String ovpnConfig =
                "client\n" +
                "dev tun\n" +
                "proto udp\n" +
                "remote 203.0.113.5 1194\n" +
                "resolv-retry infinite\n" +
                "<ca>\n---BEGIN CERTIFICATE---\nfake\n---END CERTIFICATE---\n</ca>\n";

        ProxyProfile profile = new ProxyProfile("Singapore SG", ProtocolType.OPENVPN, ovpnConfig);
        profile.setId("ovpn-test");
        repo.addProfile(profile);

        // Switch to port 443 TCP
        ProxyProfile updated = repo.switchProfilePort("ovpn-test", 443, "TCP");
        Assert.assertNotNull(updated);
        Assert.assertEquals(443, updated.getOpenVpnConfig().getRemotePort());
        Assert.assertEquals("tcp", updated.getOpenVpnConfig().getProtocol().toLowerCase());
        Assert.assertTrue("Raw config must contain 443", updated.getRawConfig().contains("443"));
        Assert.assertTrue("Raw config must contain tcp", updated.getRawConfig().toLowerCase().contains("tcp"));
    }

    @Test
    public void testSyncFromProfiles() {
        PresetRegionManager manager = PresetRegionManager.getInstance();
        manager.clear();

        List<ProxyProfile> profiles = new ArrayList<>();
        String wgConfig =
                "[Interface]\n" +
                "PrivateKey = aGVsbG93b3JsZHByaXZhdGVrZXlleGFtcGxlMTIzNDU2Nzg=\n" +
                "Address = 10.0.0.2/32\n" +
                "[Peer]\n" +
                "PublicKey = d2lyZWd1YXJkcHVibGlja2V5ZXhhbXBsZTEyMzQ1Njc4OTA=\n" +
                "Endpoint = 198.51.100.1:51820\n" +
                "AllowedIPs = 0.0.0.0/0\n";
        ProxyProfile p1 = new ProxyProfile("Hong Kong HK BGP", ProtocolType.WIREGUARD, wgConfig);
        p1.setId("p1");
        profiles.add(p1);

        manager.syncFromProfiles(profiles);
        List<PresetRegion> regions = manager.getRegions();
        Assert.assertEquals(1, regions.size());
        Assert.assertEquals("🇭🇰", regions.get(0).getFlag());
        Assert.assertEquals(6, regions.get(0).getPorts().size());

        PresetPortItem portItem = manager.findPortByUniqueId(regions.get(0).getPorts().get(0).getUniqueId());
        Assert.assertNotNull("Should find port by uid", portItem);
    }

    @Test
    public void testPortSpeedTesterAsync() throws InterruptedException {
        String wgConfig =
                "[Interface]\n" +
                "PrivateKey = aGVsbG93b3JsZHByaXZhdGVrZXlleGFtcGxlMTIzNDU2Nzg=\n" +
                "Address = 10.0.0.2/32\n" +
                "[Peer]\n" +
                "PublicKey = d2lyZWd1YXJkcHVibGlja2V5ZXhhbXBsZTEyMzQ1Njc4OTA=\n" +
                "Endpoint = 127.0.0.1:51820\n" +
                "AllowedIPs = 0.0.0.0/0\n";
        ProxyProfile profile = new ProxyProfile("Localhost Test", ProtocolType.WIREGUARD, wgConfig);
        profile.setId("test-p");
        PresetRegion region = PresetRegion.fromProfile(profile);
        PresetPortItem testItem = region.getPorts().get(0);

        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean callbackTriggered = new AtomicBoolean(false);

        PortSpeedTester.testSinglePort(testItem, new PortSpeedTester.SpeedTestListener() {
            @Override
            public void onPortTested(PresetPortItem item, long latencyMs) {
                callbackTriggered.set(true);
                latch.countDown();
            }

            @Override
            public void onAllCompleted(int successCount, int totalCount) {}
        });

        boolean finished = latch.await(4, TimeUnit.SECONDS);
        Assert.assertTrue("Speed test callback should finish within 4s", finished);
        Assert.assertTrue("Callback flag should be true", callbackTriggered.get());
        Assert.assertNotNull("Latency should be set", testItem.getLatencyMs());
    }

    @Test
    public void testOpenVpnPortSelectionDistinctUdpAndTcp() {
        PresetRegionManager manager = PresetRegionManager.getInstance();
        manager.clear();

        ProfileRepository repo = new ProfileRepository(null);
        String ovpnConfig =
                "client\n" +
                "dev tun\n" +
                "proto udp\n" +
                "remote 203.0.113.5 1194\n" +
                "resolv-retry infinite\n" +
                "<ca>\n---BEGIN CERTIFICATE---\nfake\n---END CERTIFICATE---\n</ca>\n";

        ProxyProfile profile = new ProxyProfile("Tokyo Japan", ProtocolType.OPENVPN, ovpnConfig);
        profile.setId("ovpn-tokyo");
        repo.addProfile(profile);

        List<PresetRegion> regions = manager.getRegions();
        Assert.assertEquals(1, regions.size());
        PresetRegion region = regions.get(0);

        List<PresetPortItem> udpPorts = region.getPortsByGroup(ProtocolType.OPENVPN, "UDP");
        List<PresetPortItem> tcpPorts = region.getPortsByGroup(ProtocolType.OPENVPN, "TCP");
        Assert.assertEquals(6, udpPorts.size());
        Assert.assertEquals(6, tcpPorts.size());

        PresetPortItem port443Udp = null;
        for (PresetPortItem item : udpPorts) {
            if (item.getPort() == 443) {
                port443Udp = item;
                break;
            }
        }
        PresetPortItem port443Tcp = null;
        for (PresetPortItem item : tcpPorts) {
            if (item.getPort() == 443) {
                port443Tcp = item;
                break;
            }
        }
        Assert.assertNotNull(port443Udp);
        Assert.assertNotNull(port443Tcp);
        Assert.assertNotEquals("Unique IDs must differ between UDP and TCP for port 443",
                port443Udp.getUniqueId(), port443Tcp.getUniqueId());

        // Test 1: Switch to 443 TCP
        ProxyProfile activeTcp = repo.switchProfilePort("ovpn-tokyo", 443, "TCP");
        Assert.assertNotNull(activeTcp);
        int activePortNumber = activeTcp.getOpenVpnConfig().getRemotePort();
        String activeTransport = activeTcp.getOpenVpnConfig().getProtocol().toUpperCase(java.util.Locale.ROOT);
        boolean isRegionActive = region.getId().equals(activeTcp.getId());

        boolean tcp443Selected = isRegionActive && (port443Tcp.getPort() == activePortNumber)
                && port443Tcp.getTransport().equalsIgnoreCase(activeTransport);
        boolean udp443Selected = isRegionActive && (port443Udp.getPort() == activePortNumber)
                && port443Udp.getTransport().equalsIgnoreCase(activeTransport);

        Assert.assertTrue("TCP 443 must be active when switched to 443 TCP", tcp443Selected);
        Assert.assertFalse("UDP 443 must NOT be active when switched to 443 TCP", udp443Selected);

        // Test 2: Switch to 443 UDP
        ProxyProfile activeUdp = repo.switchProfilePort("ovpn-tokyo", 443, "UDP");
        Assert.assertNotNull(activeUdp);
        activePortNumber = activeUdp.getOpenVpnConfig().getRemotePort();
        activeTransport = activeUdp.getOpenVpnConfig().getProtocol().toUpperCase(java.util.Locale.ROOT);

        tcp443Selected = isRegionActive && (port443Tcp.getPort() == activePortNumber)
                && port443Tcp.getTransport().equalsIgnoreCase(activeTransport);
        udp443Selected = isRegionActive && (port443Udp.getPort() == activePortNumber)
                && port443Udp.getTransport().equalsIgnoreCase(activeTransport);

        Assert.assertFalse("TCP 443 must NOT be active when switched to 443 UDP", tcp443Selected);
        Assert.assertTrue("UDP 443 must be active when switched to 443 UDP", udp443Selected);
    }

    public static void runAllTests() {
        System.out.println("--- Running PresetRegionTest ---");
        PresetRegionTest test = new PresetRegionTest();
        try {
            test.testPresetRegionManagerInitialState();
            System.out.println("  ✓ testPresetRegionManagerInitialState passed");
            test.testRegionDetector();
            System.out.println("  ✓ testRegionDetector passed");
            test.testWireGuardDerivedPorts();
            System.out.println("  ✓ testWireGuardDerivedPorts passed");
            test.testOpenVpnDerivedPorts();
            System.out.println("  ✓ testOpenVpnDerivedPorts passed");
            test.testProfilePortSwitchingWireGuard();
            System.out.println("  ✓ testProfilePortSwitchingWireGuard passed");
            test.testProfilePortSwitchingOpenVpn();
            System.out.println("  ✓ testProfilePortSwitchingOpenVpn passed");
            test.testSyncFromProfiles();
            System.out.println("  ✓ testSyncFromProfiles passed");
            test.testPortSpeedTesterAsync();
            System.out.println("  ✓ testPortSpeedTesterAsync passed");
            test.testOpenVpnPortSelectionDistinctUdpAndTcp();
            System.out.println("  ✓ testOpenVpnPortSelectionDistinctUdpAndTcp passed");
            System.out.println("PresetRegionTest: ALL PASSED!\n");
        } catch (Exception e) {
            System.err.println("PresetRegionTest FAILED: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }
}
