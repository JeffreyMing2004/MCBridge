package net.JeffreyMing.mcbridge.network;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

public class NodeSpeedTester {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int TIMEOUT_MS = 2000; // 2 seconds timeout for each connection test

    public static CompletableFuture<Node> findBestNode(List<Node> nodes) {
        if (nodes == null || nodes.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }

        ExecutorService executor = Executors.newFixedThreadPool(Math.min(nodes.size(), 10));

        List<CompletableFuture<NodeWithLatency>> futures = nodes.stream()
            .map(node -> CompletableFuture.supplyAsync(() -> testNodeLatency(node), executor))
            .collect(Collectors.toList());

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .thenApply(v -> {
                executor.shutdown();
                return futures.stream()
                    .map(CompletableFuture::join)
                    .filter(result -> result.latency < Long.MAX_VALUE)
                    .min(Comparator.comparingLong(result -> result.latency))
                    .map(result -> result.node)
                    .orElse(null); // Return null if all nodes failed
            });
    }

    private static NodeWithLatency testNodeLatency(Node node) {
        long startTime = System.nanoTime();
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(node.host(), node.port()), TIMEOUT_MS);
            long endTime = System.nanoTime();
            long latency = (endTime - startTime) / 1_000_000; // Convert to milliseconds
            LOGGER.info("测速成功: 节点 '{}' - 延迟: {}ms", node.name(), latency);
            return new NodeWithLatency(node, latency);
        } catch (Exception e) {
            LOGGER.warn("测速失败: 节点 '{}' - 原因: {}", node.name(), e.getMessage());
            return new NodeWithLatency(node, Long.MAX_VALUE); // Indicate failure
        }
    }

    private static class NodeWithLatency {
        final Node node;
        final long latency;

        NodeWithLatency(Node node, long latency) {
            this.node = node;
            this.latency = latency;
        }
    }
}
