package dev.mewltiplayer.fabric.mixin;

import dev.mewltiplayer.fabric.MewMainScreen;
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
        addDrawableChild(ButtonWidget.builder(Text.translatable("mewltiplayer.menu"), button -> MinecraftClient.getInstance().setScreen(new MewMainScreen((Screen) (Object) this)))
                .dimensions(width - 130, 8, 120, 20).build());
    }
}
