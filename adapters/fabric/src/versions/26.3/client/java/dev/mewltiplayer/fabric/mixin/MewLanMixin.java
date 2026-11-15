package dev.mewltiplayer.fabric.mixin;

import dev.mewltiplayer.fabric.MewRuntime;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.WorldOptionsScreen;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(WorldOptionsScreen.class)
public abstract class MewLanMixin extends Screen {
    @Unique private boolean mew$online = MewRuntime.INSTANCE.onlineSharingEnabled();

    protected MewLanMixin(Component title) {
        super(title);
    }

    @Shadow private void updateApplyChangesActiveState() {
    }

    @Inject(method = "multiplayerOptions", at = @At("TAIL"))
    private void mew$addOption(LinearLayout layout, IntegratedServer server, CallbackInfo ci) {
        layout.addChild(Button.builder(mew$label(), button -> {
            mew$online = !mew$online;
            button.setMessage(mew$label());
            updateApplyChangesActiveState();
        }).width(300).build());
    }

    @Unique
    private Component mew$label() {
        return Component.translatable("mewltiplayer.lan",
                Component.translatable(mew$online ? "options.on" : "options.off"));
    }

    @Inject(method = "hasChanges", at = @At("RETURN"), cancellable = true)
    private void mew$changes(CallbackInfoReturnable<Boolean> cir) {
        if (mew$online != MewRuntime.INSTANCE.onlineSharingEnabled()) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "applyChanges", at = @At("TAIL"))
    private void mew$apply(IntegratedServer server, CallbackInfo ci) {
        MewRuntime.INSTANCE.setOnlineSharingEnabled(
                mew$online && server != null && server.getMultiplayerScope() == MinecraftServer.MultiplayerScope.LAN);
    }
}
