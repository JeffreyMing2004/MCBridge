package net.JeffreyMing.mcbridge.client.keybind;

import com.mojang.blaze3d.platform.InputConstants;
import net.JeffreyMing.mcbridge.client.gui.NodeSelectionScreen;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid = "mcbridge", value = Dist.CLIENT)
public class Keybinds {
    public static final KeyMapping OPEN_NODE_GUI = new KeyMapping(
            "key.mcbridge.open_gui",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_O,
            "key.categories.mcbridge"
    );

    @Mod.EventBusSubscriber(modid = "mcbridge", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static class ModBusEvents {
        @SubscribeEvent
        public static void onKeyRegister(RegisterKeyMappingsEvent event) {
            event.register(OPEN_NODE_GUI);
        }
    }

    @SubscribeEvent
    public static void onInput(InputEvent.Key event) {
        if (OPEN_NODE_GUI.consumeClick()) {
            Minecraft.getInstance().setScreen(new NodeSelectionScreen());
        }
    }
}
