package dev.mewltiplayer.fabric.mixin;

import dev.mewltiplayer.core.transport.InviteCode;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.DirectJoinServerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(DirectJoinServerScreen.class)
public abstract class MewDirectConnectMixin {
    @Shadow private EditBox ipEdit;
    @Shadow private Button selectButton;

    @Inject(method = "init", at = @At("TAIL"))
    private void mew$init(CallbackInfo ci) {
        ipEdit.setMaxLength(8192);
    }

    @Inject(method = "updateSelectButtonStatus", at = @At("TAIL"))
    private void mew$update(CallbackInfo ci) {
        if (InviteCode.looksLikeTailcatCode(ipEdit.getValue())) {
            selectButton.active = true;
        }
    }
}
