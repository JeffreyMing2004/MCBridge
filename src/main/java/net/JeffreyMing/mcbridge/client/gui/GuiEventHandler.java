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
            // 当玩家进入多人游戏列表时，自动连接默认中转节点
            if (!RelayManager.isConnected()) {
                RelayManager.connect(RelayManager.DEFAULT_NODE);
            }
        } else if (event.getScreen() instanceof ShareToLanScreen || event.getScreen().getClass().getSimpleName().equals("LanServerScreen")) {
            // Add a toggle button to the "Open to LAN" screen
            // Position it to avoid conflicts with mcwifipnp's buttons
            int x = event.getScreen().width / 2 - 155;
            int y = event.getScreen().height - 54;
            
            // If it's mcwifipnp's screen, adjust position if needed
            if (event.getScreen().getClass().getSimpleName().equals("LanServerScreen")) {
                // mcwifipnp usually has many buttons in the middle, 
                // we'll try to place it at the bottom left or somewhere safe
                x = event.getScreen().width / 2 - 155;
                y = event.getScreen().height - 82; // Slightly higher than the "Start" button
            }

            CycleButton<Boolean> relayButton = CycleButton.booleanBuilder(
                Component.literal("使用中转映射：使用"), 
                Component.literal("使用中转映射：不使用")
            ).withInitialValue(useRelay).create(
                x, y, 150, 20, 
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
            // 确保在连接服务器前，如果已经开启了中转，则使用它
            // 客户端连接模式通常使用随机远程端口 (remotePort=0)
            if (!RelayManager.isConnected()) {
                RelayManager.connect(RelayManager.DEFAULT_NODE);
            }
        }
    }
}
