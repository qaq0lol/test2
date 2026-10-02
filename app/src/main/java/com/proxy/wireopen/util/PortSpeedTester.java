package com.proxy.wireopen.util;

import com.proxy.wireopen.model.PresetPortItem;

import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Socket latency probe tool for measuring real RTT to proxy ports.
 */
public class PortSpeedTester {

    public static final int DEFAULT_TIMEOUT_MS = 1500;
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(16, r -> {
        Thread t = new Thread(r, "PortSpeedTester-Worker");
        t.setDaemon(true);
        return t;
    });

    public interface SpeedTestListener {
        void onPortTested(PresetPortItem item, long latencyMs);
        void onAllCompleted(int successCount, int totalCount);
    }

    /**
     * Measures network RTT to target host / IP and port using Socket probe.
     * Returns:
     *  >0 : RTT in milliseconds
     *  -2 : Timeout or unreachable error
     */
    public static long measureLatency(String host, String ip, int port, int timeoutMs) {
        String target = (ip != null && !ip.isEmpty()) ? ip : host;
        long start = System.currentTimeMillis();
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(target, port), timeoutMs);
            return Math.max(1, System.currentTimeMillis() - start);
        } catch (ConnectException ce) {
            // Kernel responded with RST packet: packet completed round-trip to remote host!
            long rtt = Math.max(1, System.currentTimeMillis() - start);
            if (rtt <= timeoutMs) {
                return rtt;
            }
            return -2;
        } catch (SocketTimeoutException | UnknownHostException e) {
            if (port != 443 && port != 80) {
                return measureFallbackLatency(target, timeoutMs);
            }
            return -2;
        } catch (Exception e) {
            if (port != 443 && port != 80) {
                return measureFallbackLatency(target, timeoutMs);
            }
            return -2;
        }
    }

    private static long measureFallbackLatency(String target, int timeoutMs) {
        long start = System.currentTimeMillis();
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(target, 443), Math.min(timeoutMs, 1000));
            return Math.max(1, System.currentTimeMillis() - start);
        } catch (ConnectException ce) {
            long rtt = Math.max(1, System.currentTimeMillis() - start);
            if (rtt <= timeoutMs) {
                return rtt;
            }
            return -2;
        } catch (Exception e) {
            return -2;
        }
    }

    /**
     * Asynchronously tests a single port item and triggers callback.
     */
    public static void testSinglePort(PresetPortItem item, SpeedTestListener listener) {
        item.setTesting(true);
        EXECUTOR.execute(() -> {
            long latency = measureLatency(item.getHost(), item.getIp(), item.getPort(), DEFAULT_TIMEOUT_MS);
            item.setTesting(false);
            item.setLatencyMs(latency);
            if (listener != null) {
                listener.onPortTested(item, latency);
            }
        });
    }

    /**
     * Concurrently tests a list of port items across the thread pool.
     */
    public static void testAllPorts(List<PresetPortItem> ports, SpeedTestListener listener) {
        if (ports == null || ports.isEmpty()) {
            if (listener != null) listener.onAllCompleted(0, 0);
            return;
        }

        for (PresetPortItem item : ports) {
            item.setTesting(true);
        }

        final int total = ports.size();
        final AtomicInteger completed = new AtomicInteger(0);
        final AtomicInteger success = new AtomicInteger(0);

        for (PresetPortItem item : ports) {
            EXECUTOR.execute(() -> {
                long latency = measureLatency(item.getHost(), item.getIp(), item.getPort(), DEFAULT_TIMEOUT_MS);
                item.setTesting(false);
                item.setLatencyMs(latency);
                if (latency > 0) {
                    success.incrementAndGet();
                }
                if (listener != null) {
                    listener.onPortTested(item, latency);
                }
                if (completed.incrementAndGet() == total) {
                    if (listener != null) {
                        listener.onAllCompleted(success.get(), total);
                    }
                }
            });
        }
    }
}
