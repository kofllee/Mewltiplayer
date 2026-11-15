package dev.mewltiplayer.fabric;

import dev.mewltiplayer.core.transport.InviteCode;
import dev.mewltiplayer.core.transport.NetworkStatus;
import dev.mewltiplayer.core.transport.ConnectionMode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

public final class MewMainScreen extends Screen {
    private final Screen previous;
    private EditBox inviteInput;
    private Button shareButton;
    private Button copyButton;
    private Button rotateButton;
    private Button stopButton;
    private Button connectButton;
    private String message = "";
    private int top;

    public MewMainScreen(Screen previous) {
        super(Component.translatable("mewltiplayer.title"));
        this.previous = previous;
    }

    @Override
    protected void init() {
        int left = width / 2 - 154;
        top = Math.max(8, (height - 204) / 2);
        shareButton = addRenderableWidget(button("mewltiplayer.host", left, top + 28, 150,
                () -> MewClientActions.share()));
        copyButton = addRenderableWidget(button("mewltiplayer.copy", left + 158, top + 28, 150, () -> {
            String code = MewRuntime.INSTANCE.invite();
            if (code != null) {
                MewClientCompat.copyInvite(code);
                message = Component.translatable("mewltiplayer.copy").getString();
            }
        }));
        rotateButton = addRenderableWidget(button("mewltiplayer.rotate", left, top + 51, 150, () ->
                MewRuntime.INSTANCE.refreshInvite().whenComplete((code, error) ->
                        MewClientCompat.execute(() -> {
                            if (error == null && code.equals(MewRuntime.INSTANCE.invite())) {
                                MewClientCompat.copyInvite(code);
                            }
                        }))));
        stopButton = addRenderableWidget(button("mewltiplayer.stop", left + 158, top + 51, 150,
                () -> MewRuntime.INSTANCE.pauseSharing()));

        inviteInput = addRenderableWidget(new EditBox(font, left, top + 86, 308, 20,
                Component.translatable("mewltiplayer.joinHint")));
        inviteInput.setMaxLength(8192);
        inviteInput.setHint(Component.translatable("mewltiplayer.joinHint"));
        inviteInput.setResponder(value -> connectButton.active = InviteCode.looksLikeTailcatCode(value));

        addRenderableWidget(button("mewltiplayer.paste", left, top + 109, 150,
                () -> inviteInput.setValue(MewClientCompat.clipboard().trim())));
        connectButton = addRenderableWidget(button("mewltiplayer.connect", left + 158, top + 109, 150, () -> {
            String code = inviteInput.getValue().trim();
            if (InviteCode.looksLikeTailcatCode(code)) {
                MewClientCompat.connectToInvite(this, code);
            }
        }));
        addRenderableWidget(button("mewltiplayer.settings", left, top + 177, 150,
                () -> Minecraft.getInstance().gui.setScreen(new MewSettingsScreen(this))));
        addRenderableWidget(button("mewltiplayer.back", left + 158, top + 177, 150, this::onClose));
        tick();
    }

    private Button button(String key, int x, int y, int w, Runnable click) {
        return Button.builder(Component.translatable(key), widget -> click.run())
                .bounds(x, y, w, 20).build();
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
        connectButton.active = InviteCode.looksLikeTailcatCode(inviteInput.getValue());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        MewRuntime runtime = MewRuntime.INSTANCE;
        graphics.centeredText(font, title, width / 2, top, 0xffffffff);
        String state = !MewClientCompat.worldAvailable() ? text("mewltiplayer.hostUnavailable")
                : runtime.sharingPaused() ? text("mewltiplayer.paused")
                : runtime.invite() != null ? text("mewltiplayer.ready")
                : runtime.sharing() ? text("mewltiplayer.starting")
                : text("mewltiplayer.offline");
        graphics.centeredText(font, state, width / 2, top + 14, 0xffaabacc);
        graphics.centeredText(font, text("mewltiplayer.joinHint"), width / 2, top + 76, 0xffaabacc);
        String network = text("mewltiplayer.networkIdle");
        NetworkStatus route = runtime.network();
        if (route != null) {
            network = text(route.mode() == ConnectionMode.DIRECT ? "mewltiplayer.networkDirect"
                    : route.mode() == ConnectionMode.RELAY ? "mewltiplayer.networkRelay"
                    : "mewltiplayer.networkConnecting") + " · " + route.latencyMs() + "ms";
        }
        String helper = text(runtime.helperOnline() ? "mewltiplayer.helperReady" : "mewltiplayer.helperStopped");
        graphics.centeredText(font, helper + " · " + network, width / 2, top + 137, 0xffb5beca);
        String peers = Component.translatable("mewltiplayer.serverPlayers", runtime.status().connections()).getString();
        graphics.centeredText(font, peers, width / 2, top + 149, 0xffb5beca);
        String detail = runtime.error().isEmpty() ? message : runtime.error();
        if (detail.isEmpty() && runtime.invite() != null) {
            detail = Component.translatable("mewltiplayer.inviteLength", runtime.invite().length()).getString();
        }
        if (detail.length() > 65) {
            detail = detail.substring(0, 62) + "...";
        }
        graphics.centeredText(font, detail, width / 2, top + 162, 0xfff6b5a6);
    }

    private static String text(String key) {
        return Component.translatable(key).getString();
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().gui.setScreen(previous);
    }
}
