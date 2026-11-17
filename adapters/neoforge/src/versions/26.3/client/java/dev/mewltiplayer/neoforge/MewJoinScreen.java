package dev.mewltiplayer.neoforge;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class MewJoinScreen extends Screen {
    private final Screen previous;

    public MewJoinScreen(Screen previous) {
        super(Component.translatable("mewltiplayer.connecting"));
        this.previous = previous;
    }

    @Override
    protected void init() {
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), button -> onClose())
                .bounds(width / 2 - 100, height / 2 + 30, 200, 20).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        graphics.centeredText(font, title, width / 2, height / 2 - 18, 0xffffffff);
    }

    public void fail(String error) {
        MewClientActions.disconnect();
        Minecraft.getInstance().gui.setScreen(new DisconnectedScreen(
                previous, Component.translatable("connect.failed"), Component.translatable("mewltiplayer.error." + error)));
    }

    @Override
    public void onClose() {
        MewClientActions.disconnect();
        Minecraft.getInstance().gui.setScreen(previous);
    }
}
