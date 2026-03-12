package net.JeffreyMing.mcbridge.client.gui;

import net.JeffreyMing.mcbridge.network.RelayManager;
import net.JeffreyMing.mcbridge.network.ServerNodeManager;
import net.JeffreyMing.mcbridge.network.Node;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.lang.reflect.Field;

import net.minecraftforge.client.event.ClientPlayerNetworkEvent;

import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.ShareToLanScreen;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;

@Mod.EventBusSubscriber(modid = "mcbridge", value = Dist.CLIENT)
public class GuiEventHandler {
    private static boolean useRelay = false;

    @SubscribeEvent
    public static void onClientLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        RelayManager.disconnect();
        useRelay = false;
    }

    public static boolean isUseRelay() {
        return useRelay;
    }

    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        if (event.getScreen() instanceof JoinMultiplayerScreen) {
            // Add a button to the multiplayer screen to open the node selection
            Button button = Button.builder(Component.translatable("gui.mcbridge.node_selection.title"), (b) -> {
                event.getScreen().getMinecraft().setScreen(new NodeSelectionScreen());
            }).bounds(event.getScreen().width / 2 - 50, 5, 100, 20).build();
            
            event.addListener(button);
        } else if (event.getScreen().getClass().getSimpleName().equals("EditServerScreen")) {
            // Add a button to the edit server screen to choose a node
            Button nodeButton = Button.builder(Component.literal("选择中转节点"), (button) -> {
                try {
                    // Try to get the server data being edited via reflection
                    // Official field name is usually 'serverData'
                    Field field = event.getScreen().getClass().getDeclaredField("serverData");
                    field.setAccessible(true);
                    ServerData serverData = (ServerData) field.get(event.getScreen());
                    if (serverData != null) {
                        event.getScreen().getMinecraft().setScreen(new NodeSelectionScreen(serverData.ip));
                    }
                } catch (Exception e) {
                    // Fallback if reflection fails
                    event.getScreen().getMinecraft().setScreen(new NodeSelectionScreen());
                }
            }).bounds(event.getScreen().width / 2 - 100, event.getScreen().height / 4 + 72 + 24, 200, 20).build();
            
            event.addListener(nodeButton);
        } else if (event.getScreen() instanceof ShareToLanScreen shareScreen) {
            // Add a toggle button to the "Open to LAN" screen
            CycleButton<Boolean> relayButton = CycleButton.booleanBuilder(
                Component.literal("使用中转映射：使用"), 
                Component.literal("使用中转映射：不使用")
            ).withInitialValue(useRelay).create(
                event.getScreen().width / 2 - 155, 
                event.getScreen().height - 54, 
                150, 20, 
                Component.literal("使用中转映射"), 
                (button, value) -> {
                    useRelay = value;
                }
            );
            
            event.addListener(relayButton);
        }
    }

    @SubscribeEvent
    public static void onScreenOpening(ScreenEvent.Opening event) {
        if (event.getScreen() instanceof ConnectScreen connectScreen) {
            // Always disconnect before a new connection attempt
            RelayManager.disconnect();
            
            // Check if we are connecting to a server that has an associated node
            try {
                // ConnectScreen usually has a ServerData or IP/Port
                // Let's try to get the server data or address
                // In official mappings, 'serverData' is the field for ServerData
                Field field = connectScreen.getClass().getDeclaredField("serverData");
                field.setAccessible(true);
                ServerData serverData = (ServerData) field.get(connectScreen);
                
                if (serverData != null) {
                    String nodeName = ServerNodeManager.getNodeForServer(serverData.ip);
                    if (nodeName != null) {
                        // Find the node object by name (for now we only have one)
                        // In a real app, we'd have a registry of nodes
                        Node node = new Node("[雨云]", 100, 200, "relay.rainyun.com", 12345);
                        if (node.name().equals(nodeName)) {
                            RelayManager.connect(node);
                        }
                    }
                }
            } catch (Exception e) {
                // Reflection failed, maybe it's a direct connection or the field name is different
            }
        }
    }
}
