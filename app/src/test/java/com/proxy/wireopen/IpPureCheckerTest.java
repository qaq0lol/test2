package com.proxy.wireopen;

import com.proxy.wireopen.model.IpPureResult;
import com.proxy.wireopen.service.IpPureChecker;
import org.junit.Test;

public class IpPureCheckerTest {

    public static void runAllTests() {
        System.out.println("--- Running IpPureCheckerTest ---");
        IpPureCheckerTest t = new IpPureCheckerTest();
        t.testParseIpPureResult();
        t.testResidentialIpPureResult();
        t.testHighRiskIpPureResult();
        t.testInvalidCountryCodeFlagEmoji();
        System.out.println("IpPureCheckerTest: ALL PASSED!\n");
    }

    @Test
    public void testParseIpPureResult() {
        String json = "{\n" +
                "    \"ip\": \"103.217.163.61\",\n" +
                "    \"asn\": 41095,\n" +
                "    \"asOrganization\": \"IPTP LIMITED\",\n" +
                "    \"country\": \"Taiwan\",\n" +
                "    \"countryCode\": \"TW\",\n" +
                "    \"region\": \"Taiwan\",\n" +
                "    \"regionCode\": \"04\",\n" +
                "    \"city\": \"Taipei\",\n" +
                "    \"timezone\": \"Asia/Taipei\",\n" +
                "    \"longitude\": \"121.52639\",\n" +
                "    \"latitude\": \"25.05306\",\n" +
                "    \"fraudScore\": 13,\n" +
                "    \"isResidential\": false,\n" +
                "    \"isBroadcast\": true,\n" +
                "    \"userAgent\": \"Go-http-client/2.0\"\n" +
                "}";

        IpPureResult result = IpPureChecker.parseResult(json);
        assertNotNull("result", result);
        assertEq("ip", "103.217.163.61", result.getIp());
        assertEq("asn", "AS41095", result.getAsn());
        assertEq("asOrganization", "IPTP LIMITED", result.getAsOrganization());
        assertEq("country", "Taiwan", result.getCountry());
        assertEq("countryCode", "TW", result.getCountryCode());
        assertEq("city", "Taipei", result.getCity());
        assertEq("fraudScore", 13, result.getFraudScore());
        assertFalse("isResidential", result.isResidential());
        assertTrue("isBroadcast", result.isBroadcast());

        assertTrue("locationDisplay", result.getLocationDisplay().contains("Taipei"));
        assertTrue("ipTypeDisplay", result.getIpTypeDisplay().contains("优质机房 IP") || result.getIpTypeDisplay().contains("机房"));
        assertTrue("riskEvaluation", result.getRiskEvaluation().contains("极低风险"));
    }

    @Test
    public void testResidentialIpPureResult() {
        String json = "{\n" +
                "    \"ip\": \"114.36.88.22\",\n" +
                "    \"asn\": \"AS3462\",\n" +
                "    \"asOrganization\": \"Chunghwa Telecom\",\n" +
                "    \"country\": \"Taiwan\",\n" +
                "    \"countryCode\": \"TW\",\n" +
                "    \"city\": \"Kaohsiung\",\n" +
                "    \"fraudScore\": 5,\n" +
                "    \"isResidential\": true,\n" +
                "    \"isBroadcast\": false\n" +
                "}";

        IpPureResult result = IpPureChecker.parseResult(json);
        assertNotNull("result", result);
        assertEq("ip", "114.36.88.22", result.getIp());
        assertEq("asn", "AS3462", result.getAsn());
        assertTrue("isResidential", result.isResidential());
        assertTrue("residential display", result.getIpTypeDisplay().contains("原生住宅 IP"));
        assertTrue("risk evaluation", result.getRiskEvaluation().contains("极低风险"));
    }

    @Test
    public void testHighRiskIpPureResult() {
        String json = "{\n" +
                "    \"ip\": \"45.133.1.99\",\n" +
                "    \"asn\": \"AS9999\",\n" +
                "    \"asOrganization\": \"Bad Hosting Corp\",\n" +
                "    \"country\": \"Seychelles\",\n" +
                "    \"countryCode\": \"SC\",\n" +
                "    \"city\": \"Victoria\",\n" +
                "    \"fraudScore\": 85,\n" +
                "    \"isResidential\": false,\n" +
                "    \"isBroadcast\": false\n" +
                "}";

        IpPureResult result = IpPureChecker.parseResult(json);
        assertNotNull("result", result);
        assertEq("fraudScore", 85, result.getFraudScore());
        assertTrue("high risk", result.getRiskEvaluation().contains("高风险"));
    }

    @Test
    public void testInvalidCountryCodeFlagEmoji() {
        IpPureResult r1 = new IpPureResult();
        r1.setCountryCode("12");
        assertEq("Numeric countryCode", "🌐", r1.getFlagEmoji());

        IpPureResult r2 = new IpPureResult();
        r2.setCountryCode("TW");
        assertTrue("Valid countryCode flag", !r2.getFlagEmoji().equals("🌐"));
    }

    private static void assertNotNull(String name, Object o) {
        if (o == null) throw new AssertionError("Expected " + name + " to be non-null");
    }

    private static void assertEq(String name, Object expected, Object actual) {
        if (expected == null && actual == null) return;
        if (expected != null && expected.equals(actual)) return;
        throw new AssertionError("Mismatch in " + name + ": expected <" + expected + "> but got <" + actual + ">");
    }

    private static void assertTrue(String name, boolean condition) {
        if (!condition) throw new AssertionError("Assertion failed for: " + name);
    }

    private static void assertFalse(String name, boolean condition) {
        if (condition) throw new AssertionError("Assertion failed for: " + name);
    }
}
