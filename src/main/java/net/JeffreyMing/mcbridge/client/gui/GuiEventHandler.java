package net.JeffreyMing.mcbridge.client.gui;

import net.JeffreyMing.mcbridge.Config;
import net.JeffreyMing.mcbridge.client.gui.NodeSelectionScreen;
import net.JeffreyMing.mcbridge.network.RelayManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.ShareToLanScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "mcbridge", value = Dist.CLIENT)
public class GuiEventHandler {
    @SubscribeEvent
    public static void onClientLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        RelayManager.disconnect();
    }

    public static boolean isUseRelay() {
        return Config.useRelay;
    }

    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        if (event.getScreen() instanceof JoinMultiplayerScreen) {
            // 移除自动连接和手动控制按钮
            // 客户端加入游戏不需要启动 frpc，直接连接房主给的地址即可
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
                Component.literal("使用"), 
                Component.literal("不使用")
            ).withInitialValue(Config.useRelay).create(
                x, y, 150, 20, 
                Component.literal("使用中转映射"), 
                (button, value) -> {
                    Config.setUseRelay(value);
                }
            );
            
            event.addListener(relayButton);
        }
    }

    @SubscribeEvent
    public static void onScreenOpening(ScreenEvent.Opening event) {
        // 当从多人游戏界面返回主菜单时，断开连接
        if (event.getCurrentScreen() instanceof JoinMultiplayerScreen && event.getNewScreen() instanceof net.minecraft.client.gui.screens.TitleScreen) {
            RelayManager.disconnect();
        }
    }

    @SubscribeEvent
    public static void onScreenRender(ScreenEvent.Render.Post event) {
        // 移除多人游戏界面的状态渲染
    }
}
