package dev.mewltiplayer.fabric;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class MewJoinScreen extends Screen {
    private final Screen previous;
    private Button status;
    private Button cancel;

    public MewJoinScreen(Screen previous) {
        super(Component.literal("Mewltiplayer"));
        this.previous = previous;
    }

    @Override
    protected void init() {
        status = addRenderableWidget(Button.builder(Component.literal("Connecting through Tailcat..."), button -> {})
                .bounds(width / 2 - 150, height / 2 - 18, 300, 20).build());
        status.active = false;
        cancel = addRenderableWidget(Button.builder(Component.literal("Cancel"), button -> onClose())
                .bounds(width / 2 - 100, height / 2 + 12, 200, 20).build());
    }

    public void fail(String error) {
        if (status != null) {
            status.setMessage(Component.literal(error.length() > 48 ? error.substring(0, 48) : error));
            cancel.setMessage(Component.literal("Back"));
        }
    }

    @Override
    public void onClose() {
        MewClientActions.disconnect();
        Minecraft.getInstance().gui.setScreen(previous);
    }
}
