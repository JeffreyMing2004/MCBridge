package net.JeffreyMing.mcbridge.network;

import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.function.Consumer;

public class RelayManager {
    private static final Logger LOGGER = LogUtils.getLogger();
    
    // 全局默认中转节点配置
    public static final Node DEFAULT_NODE = new Node("[雨云]", 100, 200, "103.236.55.246", 7000, "Minecraft-JeffreyMing-FRP");

    private static Process frpProcess;
    private static Node currentNode;
    private static boolean isServerRelay = false;
    private static int localPort = 25565;
    private static int remotePort = -1;
    private static Consumer<Integer> portDiscoveryCallback;

    public static void setPortDiscoveryCallback(Consumer<Integer> callback) {
        portDiscoveryCallback = callback;
    }

    public static void connect(Node node) {
        connect(node, false, 25565);
    }

    public static void connect(Node node, boolean isServer, int port) {
        if (frpProcess != null && frpProcess.isAlive()) {
            disconnect();
        }

        currentNode = node;
        isServerRelay = isServer;
        localPort = port;
        LOGGER.info("正在通过节点 {} 连接 (类型: {})...", node.name(), isServer ? "服务端" : "客户端");

        // 自动获取 frpc 二进制文件
        File binaryFile = FrpDownloader.getFrpcBinary();
        if (binaryFile == null) {
            LOGGER.error("无法获取 frpc 二进制文件，连接失败");
            return;
        }
        
        try {
            generateFrpConfig(node);
            startFrpProcess(binaryFile);
            LOGGER.info("成功开启节点映射: {}", node.name());
        } catch (Exception e) {
            LOGGER.error("开启映射失败: {}", node.name(), e);
        }
    }

    private static void startFrpProcess(File binaryFile) throws IOException {
        remotePort = -1; // Reset before starting
        ProcessBuilder pb = new ProcessBuilder(
            binaryFile.getAbsolutePath(),
            "-c",
            new File("mcbridge_frpc.toml").getAbsolutePath()
        );
        pb.redirectErrorStream(true);
        frpProcess = pb.start();
        
        // 启动一个线程读取日志，防止缓冲区满导致挂起
        new Thread(() -> {
            // 更加健壮的正则：独立抓取 remote_addr 中的端口
            Pattern addrPattern = Pattern.compile("remote_addr.*?:(\\d+)");
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(frpProcess.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    LOGGER.debug("[frpc] {}", line);
                    
                    // 1. 尝试从任何包含 remote_addr 的行中提取端口
                    Matcher addrMatcher = addrPattern.matcher(line);
                    if (addrMatcher.find()) {
                        try {
                            remotePort = Integer.parseInt(addrMatcher.group(1));
                            LOGGER.info("捕获到远程中转端口: {}", remotePort);
                            
                            // 一旦获取到端口，立即回调通知
                            if (portDiscoveryCallback != null) {
                                portDiscoveryCallback.accept(remotePort);
                                portDiscoveryCallback = null;
                            }
                        } catch (NumberFormatException e) {
                            LOGGER.error("解析端口失败: {}", addrMatcher.group(1));
                        }
                    }
                    
                    // 2. 如果看到成功标志但还没有端口（可能是固定端口模式，不带地址输出）
                    if (line.contains("start proxy success") && remotePort == -1) {
                        // 延迟一小会儿看后续有没有地址行，如果没有，如果是服务端中转， fallback 到 25565
                        if (isServerRelay) {
                            LOGGER.info("检测到启动成功，尝试使用默认服务端端口 25565");
                            remotePort = 25565;
                            if (portDiscoveryCallback != null) {
                                portDiscoveryCallback.accept(remotePort);
                                portDiscoveryCallback = null;
                            }
                        }
                    }
                }
            } catch (IOException e) {
                LOGGER.error("读取 frpc 日志出错", e);
            }
        }).start();
    }

    public static void disconnect() {
        if (frpProcess != null) {
            frpProcess.destroy();
            frpProcess = null;
            LOGGER.info("已断开节点 {} 的连接", currentNode != null ? currentNode.name() : "未知");
            currentNode = null;
            portDiscoveryCallback = null; // Clear callback
        }
    }

    private static void generateFrpConfig(Node node) throws IOException {
        File configFile = new File("mcbridge_frpc.toml");
        try (FileWriter writer = new FileWriter(configFile)) {
            writer.write("serverAddr = \"" + node.host() + "\"\n");
            writer.write("serverPort = " + node.port() + "\n");
            
            if (node.token() != null && !node.token().isEmpty()) {
                writer.write("auth.method = \"token\"\n");
                writer.write("auth.token = \"" + node.token() + "\"\n");
            }

            // 强制使用 TLS 连接
            writer.write("transport.tls.enable = true\n");
            
            writer.write("\n[[proxies]]\n");
            writer.write("name = \"mc_relay\"\n");
            writer.write("type = \"tcp\"\n");
            writer.write("localIP = \"127.0.0.1\"\n");
            writer.write("localPort = " + localPort + "\n");
            
            // 始终使用 0 以请求服务端随机分配端口，这样 frpc 也会在日志中输出分配到的端口
            writer.write("remotePort = 0\n");
        }
        LOGGER.debug("已生成 frp 配置文件: {}", configFile.getAbsolutePath());
    }

    public static boolean isConnected() {
        return frpProcess != null && frpProcess.isAlive();
    }

    public static boolean isServerRelay() {
        return isServerRelay;
    }

    public static int getLocalPort() {
        return localPort;
    }

    public static int getRemotePort() {
        return remotePort;
    }

    public static Node getCurrentNode() {
        return currentNode;
    }
}
