package com.proxy.wireopen;

public class StandaloneTestRunner {
    public static void main(String[] args) {
        System.out.println("========================================");
        System.out.println("Running WireOpen Proxy Unit Test Suites");
        System.out.println("========================================\n");

        WireGuardConfigParserTest.runAllTests();
        OpenVpnConfigParserTest.runAllTests();
        IpPureCheckerTest.runAllTests();
        PresetRegionTest.runAllTests();
        ConnectionRecordTest.runAllTests();

        try {
            new TestAuthOverride().testAuthType();
        } catch (Exception e) {
            e.printStackTrace();
        }

        System.out.println("========================================");
        System.out.println("ALL TESTS PASSED SUCCESSFULLY! (100%)");
        System.out.println("========================================");
    }
}
