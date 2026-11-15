package dev.mewltiplayer.fabric.mixin;

import dev.mewltiplayer.core.transport.InviteCode;
import dev.mewltiplayer.fabric.MewClientActions;
import dev.mewltiplayer.fabric.MewJoinScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.TransferState;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ConnectScreen.class)
public abstract class MewConnectMixin {
    @Inject(method = "startConnecting", at = @At("HEAD"), cancellable = true)
    private static void mew$connect(Screen parent, Minecraft client, ServerAddress address, ServerData serverData,
                                    boolean quickPlay, TransferState transfer, CallbackInfo ci) {
        String code = serverData.ip.trim();
        if (!InviteCode.looksLikeTailcatCode(code)) {
            return;
        }

        ci.cancel();
        MewJoinScreen screen = new MewJoinScreen(parent);
        client.gui.setScreen(screen);
        MewClientActions.join(code, port -> {
            if (client.gui.screen() != screen) {
                MewClientActions.disconnect();
                return;
            }
            ServerData local = new ServerData(serverData.name, "127.0.0.1:" + port, serverData.type());
            local.setResourcePackStatus(serverData.getResourcePackStatus());
            ConnectScreen.startConnecting(parent, client, new ServerAddress("127.0.0.1", port), local, quickPlay, transfer);
        }, error -> {
            if (client.gui.screen() == screen) {
                screen.fail(error);
            }
        });
    }
}
