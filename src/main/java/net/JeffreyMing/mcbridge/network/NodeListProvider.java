package net.JeffreyMing.mcbridge.network;

import java.util.ArrayList;
import java.util.List;

public class NodeListProvider {

    public static List<Node> getNodes() {
        List<Node> nodes = new ArrayList<>();

        // 默认节点
        nodes.add(new Node("广东深圳【新】 - 🚀深圳电信", 100, 200, "nat.mrcao.com.cn", 7000, "Minecraft-JeffreyMing-FRP", "103.236.55.246"));

        // 在此添加更多从 NODES.md 或其他配置中加载的节点

        return nodes;
    }

    public static Node getDefaultNode() {
        return getNodes().get(0);
    }
}
