package dev.mewltiplayer.fabric.mixin;

import dev.mewltiplayer.fabric.MewMainScreen;
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
        addRenderableWidget(Button.builder(Component.translatable("mewltiplayer.menu"), button -> Minecraft.getInstance().gui.setScreen(new MewMainScreen((Screen) (Object) this)))
                .bounds(width - 130, 8, 120, 20).build());
    }
}
