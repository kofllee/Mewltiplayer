package dev.mewltiplayer.fabric.mixin;

import dev.mewltiplayer.core.MewWorldSettings;
import dev.mewltiplayer.fabric.MewClientCompat;
import dev.mewltiplayer.fabric.MewRuntime;
import dev.mewltiplayer.fabric.MewSettingsScreen;
import java.io.IOException;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.OpenToLanScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextWidget;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(OpenToLanScreen.class)
public abstract class MewLanMixin extends Screen {
    @Unique private boolean mew$enabled;

    protected MewLanMixin(Text title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void mew$addOptions(CallbackInfo ci) {
        mew$enabled = MewWorldSettings.shareViaTailcat(MewClientCompat.worldDirectory());
        // Directly below Minecraft's own LAN options, above Start/Cancel.
        int buttonY = Math.min(206, Math.max(185, height - 62));
        if (height >= 270) {
            addDrawableChild(new TextWidget(width / 2 - 150, buttonY - 19, 300, 15,
                    Text.literal("Mewltiplayer").formatted(Formatting.GRAY), textRenderer).alignCenter());
        }
        addDrawableChild(ButtonWidget.builder(mew$label(), button -> {
            boolean next = !mew$enabled;
            try {
                MewWorldSettings.setShareViaTailcat(MewClientCompat.worldDirectory(), next);
                mew$enabled = next;
                button.setMessage(mew$label());
                if (MewClientCompat.published()) {
                    MewRuntime.INSTANCE.setOnlineSharingEnabled(next);
                }
            } catch (IOException error) {
                MewClientCompat.notice("mewltiplayer.settings.saveFailed");
            }
        }).dimensions(width / 2 - 155, buttonY, 150, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.translatable("mewltiplayer.settings.open"),
                button -> MinecraftClient.getInstance().setScreen(new MewSettingsScreen(this)))
                .dimensions(width / 2 + 5, buttonY, 150, 20).build());
    }

    @Unique
    private Text mew$label() {
        return Text.translatable("mewltiplayer.lan.short",
                Text.translatable(mew$enabled ? "options.on" : "options.off")
                        .formatted(mew$enabled ? Formatting.GREEN : Formatting.RED));
    }

    @Inject(method = "method_19851", at = @At("TAIL"))
    private void mew$published(IntegratedServer server, ButtonWidget startButton, CallbackInfo ci) {
        MewRuntime.INSTANCE.setOnlineSharingEnabled(mew$enabled && server.isRemote());
    }
}
