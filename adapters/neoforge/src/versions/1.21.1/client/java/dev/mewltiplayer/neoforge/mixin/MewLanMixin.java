package dev.mewltiplayer.neoforge.mixin;

import dev.mewltiplayer.core.MewWorldSettings;
import dev.mewltiplayer.neoforge.MewClientCompat;
import dev.mewltiplayer.neoforge.MewRuntime;
import dev.mewltiplayer.neoforge.MewSettingsScreen;
import java.io.IOException;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.ShareToLanScreen;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ShareToLanScreen.class)
public abstract class MewLanMixin extends Screen {
    @Unique private boolean mew$enabled;

    protected MewLanMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void mew$addOptions(CallbackInfo ci) {
        mew$enabled = MewWorldSettings.shareViaTailcat(MewClientCompat.worldDirectory());
        int buttonY = Math.min(206, Math.max(185, height - 62));
        if (height >= 270) {
            addRenderableWidget(new StringWidget(width / 2 - 150, buttonY - 19, 300, 15,
                    Component.literal("Mewltiplayer").withStyle(ChatFormatting.GRAY), font).alignCenter());
        }
        addRenderableWidget(Button.builder(mew$label(), button -> {
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
        }).bounds(width / 2 - 155, buttonY, 150, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("mewltiplayer.settings.open"),
                button -> Minecraft.getInstance().setScreen(new MewSettingsScreen(this)))
                .bounds(width / 2 + 5, buttonY, 150, 20).build());
    }

    @Unique
    private Component mew$label() {
        return Component.translatable("mewltiplayer.lan.short",
                Component.translatable(mew$enabled ? "options.on" : "options.off")
                        .withStyle(mew$enabled ? ChatFormatting.GREEN : ChatFormatting.RED));
    }
}
