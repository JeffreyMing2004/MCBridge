package net.JeffreyMing.mcbridge.network;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class ServerNodeManager {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final File CONFIG_FILE = new File("config/mcbridge_servers.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    
    // Server IP -> Node Name
    private static Map<String, String> serverNodeMap = new HashMap<>();

    static {
        load();
    }

    public static void setNodeForServer(String serverIp, String nodeName) {
        if (nodeName == null) {
            serverNodeMap.remove(serverIp);
        } else {
            serverNodeMap.put(serverIp, nodeName);
        }
        save();
    }

    public static String getNodeForServer(String serverIp) {
        return serverNodeMap.get(serverIp);
    }

    private static void load() {
        if (CONFIG_FILE.exists()) {
            try (FileReader reader = new FileReader(CONFIG_FILE)) {
                serverNodeMap = GSON.fromJson(reader, new TypeToken<Map<String, String>>(){}.getType());
            } catch (IOException e) {
                LOGGER.error("加载服务器节点映射失败", e);
            }
        }
    }

    private static void save() {
        try {
            if (!CONFIG_FILE.getParentFile().exists()) {
                CONFIG_FILE.getParentFile().mkdirs();
            }
            try (FileWriter writer = new FileWriter(CONFIG_FILE)) {
                GSON.toJson(serverNodeMap, writer);
            }
        } catch (IOException e) {
            LOGGER.error("保存服务器节点映射失败", e);
        }
    }
}
