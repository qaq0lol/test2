package com.proxy.wireopen;

import com.proxy.wireopen.model.ConnectionRecord;
import com.proxy.wireopen.util.TrafficStatsManager;

import org.junit.Assert;
import org.junit.Test;

public class ConnectionRecordTest {

    @Test
    public void testConnectionRecordCreationAndDefaults() {
        ConnectionRecord record = new ConnectionRecord();
        Assert.assertNotNull(record.getId());
        Assert.assertFalse(record.getId().isEmpty());
        Assert.assertTrue(record.getTimestamp() > 0);
        Assert.assertEquals("GLOBAL", record.getRule());
        Assert.assertEquals("刚刚", record.getRelativeTimeStr());
        Assert.assertTrue(record.isActive());
    }

    @Test
    public void testConnectionRecordParameterizedConstructor() {
        ConnectionRecord record = new ConnectionRecord(
                "tcp://api.bilibili.com:443",
                "刚刚",
                517,
                0,
                "🇩🇪",
                "德国A1 6 | 0.7倍 | V1",
                "DIRECT"
        );
        Assert.assertEquals("tcp://api.bilibili.com:443", record.getTargetUrl());
        Assert.assertEquals("刚刚", record.getRelativeTimeStr());
        Assert.assertEquals(517, record.getUploadBytes());
        Assert.assertEquals(0, record.getDownloadBytes());
        Assert.assertEquals("🇩🇪", record.getNodeFlag());
        Assert.assertEquals("德国A1 6 | 0.7倍 | V1", record.getNodeName());
        Assert.assertEquals("DIRECT", record.getRule());
        Assert.assertTrue(record.isActive());
    }

    @Test
    public void testFormatTrafficStr() {
        ConnectionRecord record1 = new ConnectionRecord(
                "tcp://github.com:443", "1秒前", 12697, 188416, "🇦🇺", "澳大利亚 WG :1194", "GLOBAL"
        );
        String formatted1 = record1.formatTrafficStr();
        // 12697 B -> 12.4KB, 188416 B -> 184.0KB
        Assert.assertTrue(formatted1.contains("1秒前"));
        Assert.assertTrue(formatted1.contains("↑"));
        Assert.assertTrue(formatted1.contains("↓"));
        Assert.assertTrue(formatted1.contains("KB"));

        ConnectionRecord record2 = new ConnectionRecord(
                "tcp://youtube.com:443", "6秒前", 65536, 15518976, "🇦🇺", "澳大利亚 WG :1194", "GLOBAL"
        );
        String formatted2 = record2.formatTrafficStr();
        Assert.assertTrue(formatted2.contains("MB"));
    }

    @Test
    public void testTrafficStatsManagerFormatBytesStatic() {
        Assert.assertEquals("500 B", TrafficStatsManager.formatBytesStatic(500));
        Assert.assertEquals("1.00 KB", TrafficStatsManager.formatBytesStatic(1024));
        Assert.assertEquals("1.50 MB", TrafficStatsManager.formatBytesStatic((long)(1.5 * 1024 * 1024)));
        Assert.assertEquals("2.00 GB", TrafficStatsManager.formatBytesStatic(2L * 1024 * 1024 * 1024));
    }

    @Test
    public void testConnectionRecordMutators() {
        ConnectionRecord record = new ConnectionRecord();
        record.setId("custom-id-99");
        record.setTargetUrl("udp://8.8.8.8:53");
        record.setTimestamp(123456789L);
        record.setRelativeTimeStr("10秒前");
        record.setUploadBytes(100);
        record.setDownloadBytes(200);
        record.setNodeFlag("🇯🇵");
        record.setNodeName("日本专线");
        record.setRule("REJECT");
        record.setActive(false);

        Assert.assertEquals("custom-id-99", record.getId());
        Assert.assertEquals("udp://8.8.8.8:53", record.getTargetUrl());
        Assert.assertEquals(123456789L, record.getTimestamp());
        Assert.assertEquals("10秒前", record.getRelativeTimeStr());
        Assert.assertEquals(100, record.getUploadBytes());
        Assert.assertEquals(200, record.getDownloadBytes());
        Assert.assertEquals("🇯🇵", record.getNodeFlag());
        Assert.assertEquals("日本专线", record.getNodeName());
        Assert.assertEquals("REJECT", record.getRule());
        Assert.assertFalse(record.isActive());
    }

    public static void runAllTests() {
        System.out.println("--- Running ConnectionRecordTest ---");
        ConnectionRecordTest test = new ConnectionRecordTest();
        try {
            test.testConnectionRecordCreationAndDefaults();
            System.out.println("  ✓ testConnectionRecordCreationAndDefaults passed");
            test.testConnectionRecordParameterizedConstructor();
            System.out.println("  ✓ testConnectionRecordParameterizedConstructor passed");
            test.testFormatTrafficStr();
            System.out.println("  ✓ testFormatTrafficStr passed");
            test.testTrafficStatsManagerFormatBytesStatic();
            System.out.println("  ✓ testTrafficStatsManagerFormatBytesStatic passed");
            test.testConnectionRecordMutators();
            System.out.println("  ✓ testConnectionRecordMutators passed");
            System.out.println("ConnectionRecordTest: ALL PASSED!\n");
        } catch (Exception e) {
            System.err.println("ConnectionRecordTest FAILED: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }
}
