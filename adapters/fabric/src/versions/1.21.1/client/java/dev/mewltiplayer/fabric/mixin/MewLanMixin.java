package dev.mewltiplayer.fabric.mixin;

import dev.mewltiplayer.fabric.MewRuntime;
import net.minecraft.client.gui.screen.OpenToLanScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(OpenToLanScreen.class)
public abstract class MewLanMixin extends Screen {
    @Unique private boolean mew$online;

    protected MewLanMixin(Text title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void mew$addOption(CallbackInfo ci) {
        addDrawableChild(ButtonWidget.builder(mew$label(), button -> {
            mew$online = !mew$online;
            button.setMessage(mew$label());
        }).dimensions(width / 2 - 75, height - 55, 150, 20).build());
    }

    @Unique
    private Text mew$label() {
        return Text.translatable("mewltiplayer.lan", Text.translatable(mew$online ? "options.on" : "options.off"));
    }

    @Inject(method = "method_19851", at = @At("TAIL"))
    private void mew$published(IntegratedServer server, ButtonWidget startButton, CallbackInfo ci) {
        MewRuntime.INSTANCE.setOnlineSharingEnabled(mew$online && server.isRemote());
    }
}
