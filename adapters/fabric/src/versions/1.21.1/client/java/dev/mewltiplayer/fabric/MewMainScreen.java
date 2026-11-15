package dev.mewltiplayer.fabric;

import dev.mewltiplayer.core.transport.InviteCode;
import dev.mewltiplayer.core.transport.NetworkStatus;
import dev.mewltiplayer.core.transport.ConnectionMode;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

public final class MewMainScreen extends Screen {
    private final Screen previous;
    private TextFieldWidget inviteInput;
    private ButtonWidget shareButton;
    private ButtonWidget copyButton;
    private ButtonWidget rotateButton;
    private ButtonWidget stopButton;
    private ButtonWidget connectButton;
    private String message = "";
    private int top;

    public MewMainScreen(Screen previous) {
        super(Text.translatable("mewltiplayer.title"));
        this.previous = previous;
    }

    @Override
    protected void init() {
        int left = width / 2 - 154;
        top = Math.max(8, (height - 204) / 2);
        shareButton = addDrawableChild(button("mewltiplayer.host", left, top + 28, 150,
                () -> MewClientActions.share()));
        copyButton = addDrawableChild(button("mewltiplayer.copy", left + 158, top + 28, 150, () -> {
            String code = MewRuntime.INSTANCE.invite();
            if (code != null) {
                MewClientCompat.copyInvite(code);
                message = Text.translatable("mewltiplayer.copy").getString();
            }
        }));
        rotateButton = addDrawableChild(button("mewltiplayer.rotate", left, top + 51, 150, () ->
                MewRuntime.INSTANCE.refreshInvite().whenComplete((code, error) ->
                        MewClientCompat.execute(() -> {
                            if (error == null && code.equals(MewRuntime.INSTANCE.invite())) {
                                MewClientCompat.copyInvite(code);
                            }
                        }))));
        stopButton = addDrawableChild(button("mewltiplayer.stop", left + 158, top + 51, 150,
                () -> MewRuntime.INSTANCE.pauseSharing()));

        inviteInput = addDrawableChild(new TextFieldWidget(textRenderer, left, top + 86, 308, 20,
                Text.translatable("mewltiplayer.joinHint")));
        inviteInput.setMaxLength(8192);
        inviteInput.setPlaceholder(Text.translatable("mewltiplayer.joinHint"));
        inviteInput.setChangedListener(value -> connectButton.active = InviteCode.looksLikeTailcatCode(value));

        addDrawableChild(button("mewltiplayer.paste", left, top + 109, 150,
                () -> inviteInput.setText(MewClientCompat.clipboard().trim())));
        connectButton = addDrawableChild(button("mewltiplayer.connect", left + 158, top + 109, 150, () -> {
            String code = inviteInput.getText().trim();
            if (InviteCode.looksLikeTailcatCode(code)) {
                MewClientCompat.connectToInvite(this, code);
            }
        }));
        addDrawableChild(button("mewltiplayer.settings", left, top + 177, 150,
                () -> MinecraftClient.getInstance().setScreen(new MewSettingsScreen(this))));
        addDrawableChild(button("mewltiplayer.back", left + 158, top + 177, 150, this::close));
        tick();
    }

    private ButtonWidget button(String key, int x, int y, int w, Runnable click) {
        return ButtonWidget.builder(Text.translatable(key), widget -> click.run())
                .dimensions(x, y, w, 20).build();
    }

    @Override
    public void tick() {
        super.tick();
        MewRuntime runtime = MewRuntime.INSTANCE;
        shareButton.active = MewClientCompat.worldAvailable();
        boolean active = runtime.sharing();
        copyButton.active = runtime.invite() != null;
        rotateButton.active = copyButton.active;
        stopButton.active = active;
        connectButton.active = InviteCode.looksLikeTailcatCode(inviteInput.getText());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);
        super.render(context, mouseX, mouseY, delta);
        MewRuntime runtime = MewRuntime.INSTANCE;
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, top, 0xffffffff);
        String state = !MewClientCompat.worldAvailable() ? text("mewltiplayer.hostUnavailable")
                : runtime.sharingPaused() ? text("mewltiplayer.paused")
                : runtime.invite() != null ? text("mewltiplayer.ready")
                : runtime.sharing() ? text("mewltiplayer.starting")
                : text("mewltiplayer.offline");
        context.drawCenteredTextWithShadow(textRenderer, state, width / 2, top + 14, 0xffaabacc);
        context.drawCenteredTextWithShadow(textRenderer, text("mewltiplayer.joinHint"), width / 2, top + 76, 0xffaabacc);
        String network = text("mewltiplayer.networkIdle");
        NetworkStatus route = runtime.network();
        if (route != null) {
            network = text(route.mode() == ConnectionMode.DIRECT ? "mewltiplayer.networkDirect"
                    : route.mode() == ConnectionMode.RELAY ? "mewltiplayer.networkRelay"
                    : "mewltiplayer.networkConnecting") + " · " + route.latencyMs() + "ms";
        }
        String helper = text(runtime.helperOnline() ? "mewltiplayer.helperReady" : "mewltiplayer.helperStopped");
        context.drawCenteredTextWithShadow(textRenderer, helper + " · " + network,
                width / 2, top + 137, 0xffb5beca);
        String peers = Text.translatable("mewltiplayer.serverPlayers", runtime.status().connections()).getString();
        context.drawCenteredTextWithShadow(textRenderer, peers, width / 2, top + 149, 0xffb5beca);
        String detail = runtime.error().isEmpty() ? message : runtime.error();
        if (detail.isEmpty() && runtime.invite() != null) {
            detail = Text.translatable("mewltiplayer.inviteLength", runtime.invite().length()).getString();
        }
        if (detail.length() > 65) {
            detail = detail.substring(0, 62) + "...";
        }
        context.drawCenteredTextWithShadow(textRenderer, detail, width / 2, top + 162, 0xfff6b5a6);
    }

    private static String text(String key) {
        return Text.translatable(key).getString();
    }

    @Override
    public void close() {
        MinecraftClient.getInstance().setScreen(previous);
    }
}
