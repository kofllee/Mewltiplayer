package dev.mewltiplayer.fabric.mixin;

import dev.mewltiplayer.fabric.MewClientActions;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameMenuScreen.class)
public abstract class MewPauseMixin extends Screen {
    protected MewPauseMixin(Text title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void mew$init(CallbackInfo ci) {
        if (MinecraftClient.getInstance().getServer() == null) {
            return;
        }
        addDrawableChild(ButtonWidget.builder(Text.literal("Copy Tailcat Invite"), button -> MewClientActions.share())
                .dimensions(width - 180, 8, 170, 20).build());
    }
}
