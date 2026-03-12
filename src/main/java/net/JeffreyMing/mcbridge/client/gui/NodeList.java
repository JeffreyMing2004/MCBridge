package net.JeffreyMing.mcbridge.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import net.JeffreyMing.mcbridge.network.Node;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.network.chat.Component;

import java.util.List;

public class NodeList extends ObjectSelectionList<NodeList.NodeEntry> {
    private final NodeSelectionScreen screen;

    public NodeList(NodeSelectionScreen screen, Minecraft minecraft, int width, int height, int top, int bottom, int itemHeight) {
        super(minecraft, width, height, top, bottom, itemHeight);
        this.screen = screen;
    }

    public void addNode(Node node) {
        this.addEntry(new NodeEntry(node));
    }

    public void setNodes(List<Node> nodes) {
        this.clearEntries();
        for (Node node : nodes) {
            this.addNode(node);
        }
    }

    @Override
    protected void renderBackground(GuiGraphics guiGraphics) {
        // You can draw a background if needed
    }

    public class NodeEntry extends ObjectSelectionList.Entry<NodeEntry> {
        private final Node node;

        public NodeEntry(Node node) {
            this.node = node;
        }

        @Override
        public void render(GuiGraphics guiGraphics, int index, int top, int left, int width, int height, int mouseX, int mouseY, boolean isHovered, float partialTick) {
            String nameText = node.name();
            String uploadLabel = Component.translatable("gui.mcbridge.node_selection.upload").getString();
            String downloadLabel = Component.translatable("gui.mcbridge.node_selection.download").getString();
            String infoText = String.format("%s %dMbps | %s %dMbps", uploadLabel, node.uploadMbps(), downloadLabel, node.downloadMbps());
            
            guiGraphics.drawString(Minecraft.getInstance().font, nameText, left + 5, top + 5, 0xFFFFFF);
            guiGraphics.drawString(Minecraft.getInstance().font, infoText, left + 5, top + 18, 0xAAAAAA);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            NodeList.this.setSelected(this);
            return true;
        }

        public Node getNode() {
            return node;
        }

        @Override
        public Component getNarration() {
            return Component.literal(node.name());
        }
    }
}
