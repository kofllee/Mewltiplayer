package dev.mewltiplayer.fabric.mixin;

import dev.mewltiplayer.fabric.MewClientActions;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PauseScreen.class)
public abstract class MewPauseMixin extends Screen {
    protected MewPauseMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void mew$init(CallbackInfo ci) {
        if (Minecraft.getInstance().getSingleplayerServer() == null) {
            return;
        }
        addRenderableWidget(Button.builder(Component.literal("Copy Tailcat Invite"), button -> MewClientActions.share())
                .bounds(width - 180, 8, 170, 20).build());
    }
}
