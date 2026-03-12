package net.JeffreyMing.mcbridge.client.gui;

import net.JeffreyMing.mcbridge.network.Node;
import net.JeffreyMing.mcbridge.network.NodeListProvider;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public class NodeSelectionScreen extends Screen {
    private NodeList nodeList;
    private Button connectButton;
    private final List<Node> nodes = new ArrayList<>();
    private String targetServerIp;

    public NodeSelectionScreen() {
        this(null);
    }

    public NodeSelectionScreen(String targetServerIp) {
        super(Component.translatable("gui.mcbridge.node_selection.title"));
        this.targetServerIp = targetServerIp;
        this.nodes.addAll(NodeListProvider.getNodes());
    }

    @Override
    protected void init() {
        // Create the node list component
        // width, height, top, bottom, itemHeight
        this.nodeList = new NodeList(this, this.minecraft, this.width, this.height, 32, this.height - 32, 36);
        this.nodeList.setNodes(nodes);
        this.addWidget(this.nodeList);

        // Connect button
        this.connectButton = this.addRenderableWidget(Button.builder(Component.translatable("gui.mcbridge.node_selection.connect"), (button) -> {
            NodeList.NodeEntry selected = this.nodeList.getSelected();
            if (selected != null) {
                this.connectToNode(selected.getNode());
            }
        }).bounds(this.width / 2 - 154, this.height - 28, 150, 20).build());

        // Cancel/Back button
        this.addRenderableWidget(Button.builder(Component.translatable("gui.mcbridge.node_selection.cancel"), (button) -> {
            this.onClose();
        }).bounds(this.width / 2 + 4, this.height - 28, 150, 20).build());

        this.updateButtonStates();
    }

    private void updateButtonStates() {
        this.connectButton.active = this.nodeList.getSelected() != null;
    }

    private void connectToNode(Node node) {
        if (targetServerIp != null) {
            if (targetServerIp.equals("LAN_RELAY")) {
                // Special case for LAN sharing
                net.JeffreyMing.mcbridge.network.RelayManager.connect(node, true, 25565); // Default port, will be updated if changed
                this.onClose();
            } else {
                // If we're choosing a node for a specific server
                net.JeffreyMing.mcbridge.network.ServerNodeManager.setNodeForServer(targetServerIp, node.name());
                this.onClose();
            }
        } else {
            // Normal manual connection
            net.JeffreyMing.mcbridge.network.RelayManager.connect(node);
            this.onClose();
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics);
        this.nodeList.render(guiGraphics, mouseX, mouseY, partialTick);
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 15, 0xFFFFFF);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        
        this.updateButtonStates();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
