package dev.mewltiplayer.fabric.mixin;

import dev.mewltiplayer.core.transport.InviteCode;
import dev.mewltiplayer.fabric.MewClientActions;
import dev.mewltiplayer.fabric.MewJoinScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.network.CookieStorage;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ConnectScreen.class)
public abstract class MewConnectMixin {
    @Inject(method = "connect(Lnet/minecraft/client/gui/screen/Screen;Lnet/minecraft/client/MinecraftClient;Lnet/minecraft/client/network/ServerAddress;Lnet/minecraft/client/network/ServerInfo;ZLnet/minecraft/client/network/CookieStorage;)V", at = @At("HEAD"), cancellable = true)
    private static void mew$connect(Screen parent, MinecraftClient client, ServerAddress address,
                                    ServerInfo serverInfo, boolean quickPlay, CookieStorage cookies, CallbackInfo ci) {
        String code = serverInfo.address.trim();
        if (!InviteCode.looksLikeTailcatCode(code)) {
            return;
        }

        ci.cancel();
        MewJoinScreen screen = new MewJoinScreen(parent);
        client.setScreen(screen);
        MewClientActions.join(code, port -> {
            if (client.currentScreen != screen) {
                MewClientActions.disconnect();
                return;
            }
            ServerInfo local = new ServerInfo(serverInfo.name, "127.0.0.1:" + port, serverInfo.getServerType());
            local.setResourcePackPolicy(serverInfo.getResourcePackPolicy());
            ConnectScreen.connect(parent, client, new ServerAddress("127.0.0.1", port), local, quickPlay, cookies);
        }, error -> {
            if (client.currentScreen == screen) {
                screen.fail(error);
            }
        });
    }
}
