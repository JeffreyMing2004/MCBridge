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
            // 新版 frp 日志格式解析远程端口
            Pattern portPattern = Pattern.compile("start proxy success.*remote_addr.*:(\\d+)");
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(frpProcess.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    LOGGER.debug("[frpc] {}", line);
                    
                    Matcher matcher = portPattern.matcher(line);
                    if (matcher.find()) {
                        try {
                            remotePort = Integer.parseInt(matcher.group(1));
                            LOGGER.info("已捕获远程中转端口: {}", remotePort);
                            if (portDiscoveryCallback != null) {
                                portDiscoveryCallback.accept(remotePort);
                            }
                        } catch (NumberFormatException e) {
                            LOGGER.error("解析远程端口失败: {}", matcher.group(1));
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
            
            // 如果是局域网分享模式，映射到 25565
            if (isServerRelay) {
                writer.write("remotePort = 25565\n");
            } else {
                // 客户端连接模式可以保持随机或根据需求调整
                writer.write("remotePort = 0\n");
            }
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
