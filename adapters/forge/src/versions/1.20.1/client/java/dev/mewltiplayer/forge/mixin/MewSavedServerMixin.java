package dev.mewltiplayer.forge.mixin;

import dev.mewltiplayer.core.transport.ShareCode;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.EditServerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EditServerScreen.class)
public abstract class MewSavedServerMixin {
    @Shadow private EditBox ipEdit;
    @Shadow private Button addButton;

    @org.spongepowered.asm.mixin.injection.ModifyArg(
            method = "init",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/components/EditBox;setMaxLength(I)V"),
            index = 0)
    private int mew$allowFullShareCode(int vanillaLimit) {
        return 8192;
    }

    @Inject(method = "updateAddButtonStatus", at = @At("TAIL"))
    private void mew$update(CallbackInfo ci) {
        if (ShareCode.looksLikeTailcatCode(ipEdit.getValue())) {
            addButton.active = true;
        }
    }
}
