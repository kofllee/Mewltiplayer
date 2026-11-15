package dev.mewltiplayer.fabric;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

public final class MewJoinScreen extends Screen {
    private final Screen previous;
    private ButtonWidget status;
    private ButtonWidget cancel;

    public MewJoinScreen(Screen previous) {
        super(Text.literal("Mewltiplayer"));
        this.previous = previous;
    }

    @Override
    protected void init() {
        status = addDrawableChild(ButtonWidget.builder(Text.literal("Connecting through Tailcat..."), button -> {})
                .dimensions(width / 2 - 150, height / 2 - 18, 300, 20).build());
        status.active = false;
        cancel = addDrawableChild(ButtonWidget.builder(Text.literal("Cancel"), button -> close())
                .dimensions(width / 2 - 100, height / 2 + 12, 200, 20).build());
    }

    public void fail(String error) {
        if (status != null) {
            status.setMessage(Text.literal(error.length() > 48 ? error.substring(0, 48) : error));
            cancel.setMessage(Text.literal("Back"));
        }
    }

    @Override
    public void close() {
        MewClientActions.disconnect();
        MinecraftClient.getInstance().setScreen(previous);
    }
}
