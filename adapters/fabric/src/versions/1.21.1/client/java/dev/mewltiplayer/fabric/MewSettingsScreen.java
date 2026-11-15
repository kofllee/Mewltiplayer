package dev.mewltiplayer.fabric;

import dev.mewltiplayer.core.MewSettings;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

public final class MewSettingsScreen extends Screen {
    private final Screen previous;
    private MewSettings draft;
    private TextFieldWidget region;
    private TextFieldWidget mapUrl;
    private ButtonWidget autoShareButton;
    private ButtonWidget autoStartButton;
    private ButtonWidget notificationsButton;
    private ButtonWidget retryButton;
    private String error = "";
    private int top;

    public MewSettingsScreen(Screen previous) {
        super(Text.translatable("mewltiplayer.settingsTitle"));
        this.previous = previous;
        draft = MewRuntime.INSTANCE.settings();
    }

    @Override
    protected void init() {
        int left = width / 2 - 154;
        top = Math.max(8, (height - 220) / 2);
        autoShareButton = addDrawableChild(button(left, top + 22, 308, () -> {
            draft.autoShare = !draft.autoShare;
            labels();
        }));
        autoStartButton = addDrawableChild(button(left, top + 45, 308, () -> {
            draft.autoStartHelper = !draft.autoStartHelper;
            labels();
        }));
        notificationsButton = addDrawableChild(button(left, top + 68, 308, () -> {
            draft.notifications = !draft.notifications;
            labels();
        }));
        retryButton = addDrawableChild(button(left, top + 91, 308, () -> {
            draft.retrySeconds = draft.retrySeconds == 15 ? 30
                    : draft.retrySeconds == 30 ? 60
                    : draft.retrySeconds == 60 ? 120 : 15;
            labels();
        }));
        region = addDrawableChild(new TextFieldWidget(textRenderer, left, top + 132, 308, 20,
                Text.translatable("mewltiplayer.region")));
        region.setMaxLength(5);
        region.setText(Integer.toString(draft.regionId));

        mapUrl = addDrawableChild(new TextFieldWidget(textRenderer, left, top + 173, 308, 20,
                Text.translatable("mewltiplayer.derp")));
        mapUrl.setMaxLength(1024);
        mapUrl.setText(draft.derpMapUrl);

        addDrawableChild(ButtonWidget.builder(Text.translatable("mewltiplayer.save"), widget -> save())
                .dimensions(left, top + 196, 98, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.translatable("mewltiplayer.defaults"), widget -> {
            draft = new MewSettings();
            region.setText("0");
            mapUrl.setText("");
            labels();
        }).dimensions(left + 104, top + 196, 98, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.translatable("mewltiplayer.back"), widget -> close())
                .dimensions(left + 210, top + 196, 98, 20).build());
        labels();
    }

    private ButtonWidget button(int x, int y, int w, Runnable action) {
        return ButtonWidget.builder(Text.empty(), button -> action.run())
                .dimensions(x, y, w, 20).build();
    }

    private void labels() {
        autoShareButton.setMessage(label("mewltiplayer.autoShare", draft.autoShare));
        autoStartButton.setMessage(label("mewltiplayer.autoStart", draft.autoStartHelper));
        notificationsButton.setMessage(label("mewltiplayer.notifications", draft.notifications));
        retryButton.setMessage(Text.translatable("mewltiplayer.retries", draft.retrySeconds));
    }

    private static Text label(String key, boolean value) {
        return Text.translatable(key, Text.translatable(value ? "mewltiplayer.on" : "mewltiplayer.off"));
    }

    private void save() {
        try {
            draft.regionId = Integer.parseInt(region.getText().trim());
            draft.derpMapUrl = mapUrl.getText().trim();
            MewRuntime.INSTANCE.saveSettings(draft);
            close();
        } catch (Exception failure) {
            error = failure.getMessage() == null ? "Invalid settings" : failure.getMessage();
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, top, 0xffffffff);
        context.drawCenteredTextWithShadow(textRenderer, Text.translatable("mewltiplayer.region"),
                width / 2, top + 120, 0xffbbc8d6);
        context.drawCenteredTextWithShadow(textRenderer, Text.translatable("mewltiplayer.derp"),
                width / 2, top + 161, 0xffbbc8d6);
        String hint = error.isEmpty() ? Text.translatable("mewltiplayer.settingsHint").getString() : error;
        if (hint.length() > 60) {
            hint = hint.substring(0, 57) + "...";
        }
        context.drawCenteredTextWithShadow(textRenderer, hint, width / 2, top + 218, error.isEmpty() ? 0xff9bb3c6 : 0xfff5a1a1);
    }

    @Override
    public void close() {
        MinecraftClient.getInstance().setScreen(previous);
    }
}
