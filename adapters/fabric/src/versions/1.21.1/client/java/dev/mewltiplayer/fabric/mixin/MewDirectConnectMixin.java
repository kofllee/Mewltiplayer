package dev.mewltiplayer.fabric.mixin;

import dev.mewltiplayer.core.transport.InviteCode;
import net.minecraft.client.gui.screen.multiplayer.DirectConnectScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(DirectConnectScreen.class)
public abstract class MewDirectConnectMixin {
    @Shadow private TextFieldWidget addressField;
    @Shadow private ButtonWidget selectServerButton;

    @Inject(method = "init", at = @At("TAIL"))
    private void mew$init(CallbackInfo ci) {
        addressField.setMaxLength(8192);
    }

    @Inject(method = "onAddressFieldChanged", at = @At("TAIL"))
    private void mew$update(CallbackInfo ci) {
        if (InviteCode.looksLikeTailcatCode(addressField.getText())) {
            selectServerButton.active = true;
        }
    }
}
