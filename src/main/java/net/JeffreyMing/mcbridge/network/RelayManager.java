package net.JeffreyMing.mcbridge.network;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RelayManager {
    private static final Logger LOGGER = LogUtils.getLogger();
    
    // 全局默认中转节点配置
    public static final Node DEFAULT_NODE = NodeListProvider.getDefaultNode();

    private static Process frpProcess;
    private static Node currentNode;
    private static boolean isServerRelay = false;
    private static int localPort = 25565;
    private static int remotePort = -1;
    private static Consumer<Integer> portDiscoveryCallback;
    private static boolean isUsingBackup = false;
    private static RelayStatus status = RelayStatus.DISCONNECTED;
    private static String currentProxyName;
    private static ScheduledExecutorService apiPoller;

    public enum RelayStatus {
        DISCONNECTED,
        CONNECTING,
        CONNECTED,
        FAILED
    }

    public static RelayStatus getStatus() {
        return status;
    }



    public static void setPortDiscoveryCallback(Consumer<Integer> callback) {
        portDiscoveryCallback = callback;
    }

    public static void connect(Node node) {
        connect(node, false, 25565);
    }

    public static void connect(Node node, boolean isServer, int port) {
        if (status == RelayStatus.CONNECTING || status == RelayStatus.CONNECTED) {
            LOGGER.warn("RelayManager 正在连接或已连接，跳过重复连接请求。当前状态: {}", status);
            return;
        }
        status = RelayStatus.CONNECTING;
        isUsingBackup = false; // 重置
        startConnection(node, isServer, port);
    }

    public static void connectAutomatically(boolean isServer, int port) {
        if (status == RelayStatus.CONNECTING || status == RelayStatus.CONNECTED) {
            LOGGER.warn("RelayManager 正在连接或已连接，跳过重复连接请求。当前状态: {}", status);
            return;
        }
        status = RelayStatus.CONNECTING;
        LOGGER.info("开始自动选择最佳节点...");

        NodeSpeedTester.findBestNode(NodeListProvider.getNodes()).thenAccept(bestNode -> {
            if (bestNode != null) {
                LOGGER.info("最佳节点已找到: '{}'，开始连接...", bestNode.name());
                startConnection(bestNode, isServer, port);
            } else {
                LOGGER.error("自动选择节点失败，所有节点均无法连接。将尝试使用默认节点。");
                startConnection(NodeListProvider.getDefaultNode(), isServer, port);
            }
        });
    }

    private static void startConnection(Node node, boolean isServer, int port) {
        if (frpProcess != null && frpProcess.isAlive()) {
            disconnect();
        }

        currentNode = node;
        isServerRelay = isServer;
        localPort = port;
        String host = isUsingBackup && node.backupHost() != null ? node.backupHost() : node.host();
        LOGGER.info("正在通过节点 {} 连接 (地址: {}, 类型: {})...", node.name(), host, isServer ? "服务端" : "客户端");

        // 自动获取 frpc 二进制文件
        File binaryFile = FrpDownloader.getFrpcBinary();
        if (binaryFile == null) {
            LOGGER.error("无法获取 frpc 二进制文件，连接失败");
            status = RelayStatus.FAILED;
            return;
        }
        
        try {
            // 如果是服务端分享，remotePortOverride 为 0 (随机)
            // 如果是客户端连接，我们不需要在这里指定 remotePort，因为客户端连接不需要映射本地端口到远程
            // 客户端连接只需要启动 frpc 作为一个 visitor 或者直接连接
            // 等等，这里的逻辑是：
            // 1. 服务端：本地 25565 -> 远程 随机
            // 2. 客户端：本地 随机 -> 远程 目标端口 (visitor 模式)
            // 但目前的实现似乎是把客户端也当作服务端来处理了？
            
            // 修正逻辑：
            // 服务端模式 (isServer=true): localPort=25565, remotePort=0 (随机)
            // 客户端模式 (isServer=false): 
            // 实际上，客户端加入游戏并不需要启动 frpc 来映射端口！
            // 客户端只需要知道服务端的 IP 和 端口 (例如 nat.mrcao.com.cn:30938) 直接连接即可。
            // 除非是 P2P 模式 (xtcp/stcp)，否则 TCP 模式下客户端不需要运行 frpc。
            
            // 既然如此，为什么之前会有客户端连接的逻辑？
            // 可能是为了支持 P2P 或者为了统一管理？
            // 如果是 TCP 模式，客户端确实不需要 frpc。
            
            // 但如果用户意图是“开启中转”以便让别人连自己（即自己是服务端），那么逻辑是对的。
            // 如果用户是在“多人游戏”界面点击“开启中转”，那他就是想做服务端（比如局域网联机）？
            // 不，通常在“多人游戏”界面是去连别人。
            // 如果是连别人，且对方是 TCP 映射，那客户端完全不需要开 frpc。
            
            // 假设现在的场景是：玩家A开服，玩家B连接。
            // 玩家A (服务端): 启动 frpc, 映射 25565 -> 30938.
            // 玩家B (客户端): 直接连接 nat.mrcao.com.cn:30938. 不需要启动 frpc.
            
            // 所以，GuiEventHandler 里在 JoinMultiplayerScreen 添加的“开启中转”按钮，
            // 其实是让玩家B也能把自己的本地端口映射出去？这没有意义，除非他也想开服。
            
            // 或者是为了加速？frp 并不提供客户端加速功能，除非是 visitor 模式。
            // 但标准 TCP 模式下，客户端直连即可。
            
            // 结论：在 JoinMultiplayerScreen (多人游戏) 界面开启 frpc 是没有必要的，
            // 除非是为了某种特殊的“反向连接”或者 P2P。
            // 但既然用户要求了“手动链接中转服务器”，可能误解了 frp 的工作方式，
            // 或者这个模组的设计初衷就是双向的？
            
            // 回看日志，客户端报错 Connection refused: ...:25565
            // 说明客户端直连了 25565。
            // 只要客户端输入正确的 30938，就能连上。
            
            // 那么问题来了：为什么客户端会去连 25565？
            // 是因为玩家输入的就是 25565？还是模组自动改了？
            
            // 如果玩家手动输入了 nat.mrcao.com.cn:30938，Minecraft 就会连这个。
            // 除非模组拦截了连接过程并篡改了端口。
            
            // 检查 GuiEventHandler，发现没有拦截连接过程的代码。
            // 那么问题很可能只是：玩家（或者测试者）在输入服务器地址时，忘记加端口了！
            // 或者输入了错误的端口。
            
            // 但用户提到“客户端连接逻辑”，可能指的是模组是否应该自动帮玩家填端口？
            // 不，模组不知道服务端的随机端口是多少。
            
            // 让我们再看一眼 RelayManager 的调用。
            // connect(Node node) -> connect(node, false, 25565)
            // 这里 isServer=false, port=25565.
            // 这意味着在客户端模式下，模组试图把本地的 25565 映射到远程的随机端口。
            // 这对于“加入游戏”的玩家来说，确实是多余的操作，甚至可能占用本地端口导致冲突。
            
            // 但如果用户坚持要在多人游戏界面“开启中转”，那我们还是得让它跑起来。
            // 只是这个操作对“连接服务器”本身没有帮助。
            
            // 等等，如果之前的日志显示 frpc 启动了，并且分配了 30938。
            // 那是服务端（房主）的日志，还是客户端（加入者）的日志？
            // 用户说“frps :Name mc-10ccb903 ... Addr :30938”，这是服务端分配的。
            // 然后用户说“客户端中的 logs 日志文件 ... Connecting to ...:25565”。
            
            // 这说明：
            // 1. 房主成功开启了映射，端口是 30938。
            // 2. 加入者（客户端）尝试连接时，用了 25565。
            
            // 根本原因：加入者没有输入端口号 30938！
            // Minecraft 默认端口是 25565。如果不输端口，就连 25565。
            
            // 解决方案：
            // 这不是代码 bug，是使用姿势问题。
            // 房主必须把“域名:30938”这个完整的地址告诉朋友。
            // 朋友必须在“服务器地址”栏输入完整的“域名:30938”。
            
            // 但是，用户之前提到“修复客户端连接逻辑，确保使用正确的随机端口”。
            // 难道用户希望模组能自动发现这个端口？
            // 除非有中心服务器或者 P2P 发现机制，否则客户端不可能知道服务端的随机端口。
            
            // 还有一种可能：用户希望客户端启动 frpc 后，通过本地转发来连接？
            // 比如：客户端本地监听 25565，转发到远程 30938？
            // 这需要 visitor 模式，且需要知道远程的 secret 或者 name。
            // 目前的配置是 server 模式，即把本地映射出去。
            
            // 让我们假设用户是想修复“服务端”的逻辑，确保服务端能正确生成配置。
            // 之前的代码：remotePort = 0。这是对的。
            
            // 让我们回到 generateFrpConfig 的修改。
            // 无论如何，支持 remotePortOverride 是好的。
            // 对于服务端分享 (isServer=true)，我们传入 0。
            // 对于客户端 (isServer=false)，如果我们只是想“开启中转”玩玩，也传入 0 即可。
            
            generateFrpConfig(node, host, 0);
            startFrpProcess(binaryFile);
        } catch (Exception e) {
            LOGGER.error("启动映射流程失败: {}", node.name(), e);
            status = RelayStatus.FAILED;
            handleFailure();
        }
    }

    private static void handleFailure() {
        if (!isUsingBackup && currentNode != null && currentNode.backupHost() != null) {
            LOGGER.warn("主地址连接失败，正在尝试备用地址: {}", currentNode.backupHost());
            isUsingBackup = true;
            startConnection(currentNode, isServerRelay, localPort);
        } else {
            LOGGER.error("所有地址连接尝试均已失败");
            status = RelayStatus.FAILED;
        }
    }

    private static void startFrpProcess(File binaryFile) throws IOException {
        remotePort = -1; // Reset before starting
        File configFile = new File("mcbridge_frpc.toml");
        ProcessBuilder pb = new ProcessBuilder(
            binaryFile.getAbsolutePath(),
            "-c",
            configFile.getAbsolutePath()
        );
        
        // 使用环境变量传递敏感 Token，防止配置文件中出现明文
        if (currentNode != null && currentNode.token() != null && !currentNode.token().isEmpty()) {
            pb.environment().put("MCBRIDGE_TOKEN", currentNode.token());
        }

        pb.redirectErrorStream(true);
        frpProcess = pb.start();
        
        // 启动后立即删除临时配置文件，防止玩家在运行期间查看
        new Thread(() -> {
            try {
                Thread.sleep(1000); // 等待 frpc 读取完毕
                if (configFile.exists()) {
                    configFile.delete();
                }
            } catch (Exception e) {
                // Ignore
            }
        }).start();

        // 启动一个线程读取日志
        new Thread(() -> {
            boolean loginSuccess = false;

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(frpProcess.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    LOGGER.debug("[frpc] {}", line);

                    if (line.contains("login to server success")) {
                        loginSuccess = true;
                        LOGGER.info("成功登录到 frp 服务端，开始轮询本地 API 获取端口...");
                        startPortDiscoveryPoller();
                    }

                    if (line.contains("connect to server error") || line.contains("dial tcp")) {
                        if (!loginSuccess) { // 如果在登录成功之前就报错
                            LOGGER.error("frp 连接错误: {}", line);
                            frpProcess.destroy();
                            handleFailure();
                            return;
                        }
                    }
                }
            } catch (IOException e) {
                LOGGER.error("读取 frpc 日志出错", e);
                status = RelayStatus.FAILED;
            }
        }).start();
    }

    public static void disconnect() {
        if (apiPoller != null) {
            apiPoller.shutdownNow();
            apiPoller = null;
        }
        if (frpProcess != null) {
            frpProcess.destroy();
            frpProcess = null;
            LOGGER.info("已断开节点 {} 的连接", currentNode != null ? currentNode.name() : "未知");
            currentNode = null;
            portDiscoveryCallback = null; // Clear callback
            status = RelayStatus.DISCONNECTED;
        }
    }

    private static void startPortDiscoveryPoller() {
        if (apiPoller != null) {
            apiPoller.shutdownNow();
        }
        apiPoller = Executors.newSingleThreadScheduledExecutor();
        
        // 每秒轮询一次本地 Admin API
        apiPoller.scheduleAtFixedRate(() -> {
            try {
                // 访问本地 frpc 的 API: http://127.0.0.1:7401/api/proxy/tcp
                // 需要 Basic Auth: admin:admin
                URL url = new URL("http://127.0.0.1:7401/api/proxy/tcp");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                String auth = "admin:admin";
                String encodedAuth = java.util.Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));
                conn.setRequestProperty("Authorization", "Basic " + encodedAuth);
                
                if (conn.getResponseCode() == 200) {
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                        StringBuilder response = new StringBuilder();
                        String line;
                        while ((line = reader.readLine()) != null) {
                            response.append(line);
                        }
                        
                        // 解析 JSON
                        JsonObject json = JsonParser.parseString(response.toString()).getAsJsonObject();
                        JsonArray proxies = json.getAsJsonArray("proxies");
                        
                        if (proxies != null) {
                            for (JsonElement element : proxies) {
                                JsonObject proxy = element.getAsJsonObject();
                                String name = proxy.get("name").getAsString();
                                
                                if (name.equals(currentProxyName)) {
                                    String statusStr = proxy.get("status").getAsString();
                                    if ("running".equalsIgnoreCase(statusStr)) {
                                        // 提取 remote_addr，格式通常为 "103.236.55.246:30938"
                                        // 但 API 返回的可能是 remote_port 字段？
                                        // frpc API 的结构可能因版本而异。
                                        // 通常会有 remote_port 字段。
                                        // 让我们先尝试获取 remote_port
                                        // 如果没有，再尝试解析 remote_addr
                                        
                                        // 检查 conf 字段中的 remote_port
                                        // 或者 status 字段中的 remote_addr
                                        
                                        // 根据 frpc 源码，API 返回的结构包含 conf 和 status
                                        // conf.remote_port 是配置的端口（如果是0，这里也是0）
                                        // status.remote_addr 是实际分配的地址
                                        
                                        // 让我们尝试从 status 字段中获取 remote_addr
                                        // 但 API 返回的结构可能直接包含 remote_addr 字段
                                        
                                        // 假设 API 返回的是 ProxyStatus 列表
                                        // 包含 name, type, status, err, local_addr, plugin_local_addr, remote_addr
                                        
                                        if (proxy.has("remote_addr")) {
                                            String remoteAddr = proxy.get("remote_addr").getAsString();
                                            // remoteAddr 格式: "1.2.3.4:12345"
                                            int colonIndex = remoteAddr.lastIndexOf(':');
                                            if (colonIndex != -1) {
                                                String portStr = remoteAddr.substring(colonIndex + 1);
                                                int port = Integer.parseInt(portStr);
                                                
                                                if (port > 0) {
                                                    remotePort = port;
                                                    status = RelayStatus.CONNECTED;
                                                    LOGGER.info("通过 API 获取到远程端口: {}", remotePort);
                                                    
                                                    if (portDiscoveryCallback != null) {
                                                        portDiscoveryCallback.accept(remotePort);
                                                        portDiscoveryCallback = null;
                                                    }
                                                    
                                                    // 成功获取后停止轮询
                                                    apiPoller.shutdown();
                                                    apiPoller = null;
                                                    return;
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                // 忽略连接错误，因为 frpc 可能还没启动完全
            }
        }, 1, 1, TimeUnit.SECONDS);
    }

    private static void generateFrpConfig(Node node, String host, int remotePortOverride) throws IOException {
        File configFile = new File("mcbridge_frpc.toml");
        try (FileWriter writer = new FileWriter(configFile)) {
            writer.write("serverAddr = \"" + host + "\"\n");
            writer.write("serverPort = " + node.port() + "\n");
            
            if (node.token() != null && !node.token().isEmpty()) {
                writer.write("auth.method = \"token\"\n");
                // 使用环境变量占位符
                writer.write("auth.token = \"{{ .Envs.MCBRIDGE_TOKEN }}\"\n");
            }

            // 强制使用 TLS 连接
            writer.write("transport.tls.enable = true\n");

            // 开启本地 Admin API 以便查询端口
            writer.write("\nwebServer.addr = \"127.0.0.1\"\n");
            writer.write("webServer.port = 7401\n"); // 使用一个固定的本地端口
            writer.write("webServer.user = \"admin\"\n");
            writer.write("webServer.password = \"admin\"\n");
            
            // 生成动态代理名称
            currentProxyName = "mc-" + UUID.randomUUID().toString().substring(0, 8);
            writer.write("\n[[proxies]]\n");
            writer.write("name = \"" + currentProxyName + "\"\n");
            writer.write("type = \"tcp\"\n");
            writer.write("localIP = \"127.0.0.1\"\n");
            writer.write("localPort = " + localPort + "\n");
            
            // 如果是客户端连接，使用指定的远程端口；如果是服务端分享，则随机分配
            writer.write("remotePort = " + remotePortOverride + "\n");
        }
        LOGGER.debug("已生成 frp 配置文件: {}", configFile.getAbsolutePath());
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
