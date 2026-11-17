package dev.mewltiplayer.forge.mixin;

import dev.mewltiplayer.core.transport.ShareCode;
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

    @org.spongepowered.asm.mixin.injection.ModifyArg(
            method = "init",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/components/EditBox;setMaxLength(I)V"),
            index = 0)
    private int mew$allowFullShareCode(int vanillaLimit) {
        return 8192;
    }

    @Inject(method = "updateSelectButtonStatus", at = @At("TAIL"))
    private void mew$update(CallbackInfo ci) {
        if (ShareCode.looksLikeTailcatCode(ipEdit.getValue())) {
            selectButton.active = true;
        }
    }
}
