package dev.mewltiplayer.fabric;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.DisconnectedScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

public final class MewJoinScreen extends Screen {
    private final Screen previous;

    public MewJoinScreen(Screen previous) {
        super(Text.translatable("mewltiplayer.connecting"));
        this.previous = previous;
    }

    @Override
    protected void init() {
        addDrawableChild(ButtonWidget.builder(Text.translatable("gui.cancel"), button -> close())
                .dimensions(width / 2 - 100, height / 2 + 30, 200, 20).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, height / 2 - 18, 0xffffffff);
    }

    public void fail(String error) {
        MewClientActions.disconnect();
        MinecraftClient.getInstance().setScreen(new DisconnectedScreen(
                previous, Text.translatable("connect.failed"), Text.translatable("mewltiplayer.error." + error)));
    }

    @Override
    public void close() {
        MewClientActions.disconnect();
        MinecraftClient.getInstance().setScreen(previous);
    }
}
