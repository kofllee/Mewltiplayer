package dev.mewltiplayer.fabric;

import dev.mewltiplayer.core.MewSettings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

public final class MewSettingsScreen extends Screen {
    private final Screen previous;
    private MewSettings draft;
    private EditBox region;
    private EditBox mapUrl;
    private Button autoShareButton;
    private Button autoStartButton;
    private Button notificationsButton;
    private Button retryButton;
    private String error = "";
    private int top;

    public MewSettingsScreen(Screen previous) {
        super(Component.translatable("mewltiplayer.settingsTitle"));
        this.previous = previous;
        draft = MewRuntime.INSTANCE.settings();
    }

    @Override
    protected void init() {
        int left = width / 2 - 154;
        top = Math.max(8, (height - 220) / 2);
        autoShareButton = addRenderableWidget(button(left, top + 22, 308, () -> {
            draft.autoShare = !draft.autoShare;
            labels();
        }));
        autoStartButton = addRenderableWidget(button(left, top + 45, 308, () -> {
            draft.autoStartHelper = !draft.autoStartHelper;
            labels();
        }));
        notificationsButton = addRenderableWidget(button(left, top + 68, 308, () -> {
            draft.notifications = !draft.notifications;
            labels();
        }));
        retryButton = addRenderableWidget(button(left, top + 91, 308, () -> {
            draft.retrySeconds = draft.retrySeconds == 15 ? 30
                    : draft.retrySeconds == 30 ? 60
                    : draft.retrySeconds == 60 ? 120 : 15;
            labels();
        }));
        region = addRenderableWidget(new EditBox(font, left, top + 132, 308, 20,
                Component.translatable("mewltiplayer.region")));
        region.setMaxLength(5);
        region.setValue(Integer.toString(draft.regionId));

        mapUrl = addRenderableWidget(new EditBox(font, left, top + 173, 308, 20,
                Component.translatable("mewltiplayer.derp")));
        mapUrl.setMaxLength(1024);
        mapUrl.setValue(draft.derpMapUrl);

        addRenderableWidget(Button.builder(Component.translatable("mewltiplayer.save"), widget -> save())
                .bounds(left, top + 196, 98, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("mewltiplayer.defaults"), widget -> {
            draft = new MewSettings();
            region.setValue("0");
            mapUrl.setValue("");
            labels();
        }).bounds(left + 104, top + 196, 98, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("mewltiplayer.back"), widget -> onClose())
                .bounds(left + 210, top + 196, 98, 20).build());
        labels();
    }

    private Button button(int x, int y, int w, Runnable action) {
        return Button.builder(Component.empty(), button -> action.run())
                .bounds(x, y, w, 20).build();
    }

    private void labels() {
        autoShareButton.setMessage(label("mewltiplayer.autoShare", draft.autoShare));
        autoStartButton.setMessage(label("mewltiplayer.autoStart", draft.autoStartHelper));
        notificationsButton.setMessage(label("mewltiplayer.notifications", draft.notifications));
        retryButton.setMessage(Component.translatable("mewltiplayer.retries", draft.retrySeconds));
    }

    private static Component label(String key, boolean value) {
        return Component.translatable(key, Component.translatable(value ? "mewltiplayer.on" : "mewltiplayer.off"));
    }

    private void save() {
        try {
            draft.regionId = Integer.parseInt(region.getValue().trim());
            draft.derpMapUrl = mapUrl.getValue().trim();
            MewRuntime.INSTANCE.saveSettings(draft);
            onClose();
        } catch (Exception failure) {
            error = failure.getMessage() == null ? "Invalid settings" : failure.getMessage();
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        graphics.centeredText(font, title, width / 2, top, 0xffffffff);
        graphics.centeredText(font, Component.translatable("mewltiplayer.region"), width / 2, top + 120, 0xffbbc8d6);
        graphics.centeredText(font, Component.translatable("mewltiplayer.derp"), width / 2, top + 161, 0xffbbc8d6);
        String hint = error.isEmpty() ? Component.translatable("mewltiplayer.settingsHint").getString() : error;
        if (hint.length() > 60) {
            hint = hint.substring(0, 57) + "...";
        }
        graphics.centeredText(font, hint, width / 2, top + 218, error.isEmpty() ? 0xff9bb3c6 : 0xfff5a1a1);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().gui.setScreen(previous);
    }
}
