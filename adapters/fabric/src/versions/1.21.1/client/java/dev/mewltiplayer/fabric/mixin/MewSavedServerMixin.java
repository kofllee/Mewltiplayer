package dev.mewltiplayer.fabric.mixin;

import dev.mewltiplayer.core.transport.ShareCode;
import net.minecraft.client.gui.screen.multiplayer.AddServerScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AddServerScreen.class)
public abstract class MewSavedServerMixin {
    @Shadow private TextFieldWidget addressField;
    @Shadow private ButtonWidget addButton;

    @org.spongepowered.asm.mixin.injection.ModifyArg(
            method = "init",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/widget/TextFieldWidget;setMaxLength(I)V"),
            index = 0)
    private int mew$allowFullShareCode(int vanillaLimit) {
        return 8192;
    }

    @Inject(method = "updateAddButton", at = @At("TAIL"))
    private void mew$update(CallbackInfo ci) {
        if (ShareCode.looksLikeTailcatCode(addressField.getText())) {
            addButton.active = true;
        }
    }
}
