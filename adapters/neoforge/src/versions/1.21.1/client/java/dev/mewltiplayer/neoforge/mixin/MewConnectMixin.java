package dev.mewltiplayer.neoforge.mixin;

import dev.mewltiplayer.core.transport.ShareCode;
import dev.mewltiplayer.neoforge.MewClientActions;
import dev.mewltiplayer.neoforge.MewJoinScreen;
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
        if (!ShareCode.looksLikeTailcatCode(code)) {
            return;
        }

        ci.cancel();
        MewJoinScreen screen = new MewJoinScreen(parent);
        client.setScreen(screen);
        MewClientActions.join(code, port -> {
            if (client.screen != screen) {
                MewClientActions.disconnect();
                return;
            }
            ServerData tunnel = new ServerData(serverData.name, ShareCode.localAddress(port), ServerData.Type.LAN);
            tunnel.setResourcePackStatus(serverData.getResourcePackStatus());
            MewClientActions.forwardConnection(() -> ConnectScreen.startConnecting(parent, client, new ServerAddress("127.0.0.1", port), tunnel, quickPlay, null));
        }, error -> {
            if (client.screen == screen) {
                screen.fail(error);
            }
        });
    }
}
