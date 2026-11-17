package dev.mewltiplayer.forge.mixin;

import dev.mewltiplayer.core.transport.ShareCode;
import dev.mewltiplayer.core.MewErrors;
import dev.mewltiplayer.forge.MewServerPing;
import net.minecraft.SharedConstants;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerStatusPinger;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerStatusPinger.class)
public abstract class MewStatusMixin {
    @Inject(method = "pingServer", at = @At("HEAD"), cancellable = true)
    private void mew$ping(ServerData server, Runnable changed, CallbackInfo ci) {
        if (!ShareCode.looksLikeTailcatCode(server.ip)) {
            return;
        }
        ci.cancel();
        server.pinged = true;
        server.ping = -1;
        server.motd = Component.translatable("mewltiplayer.status.connecting");
        server.status = Component.empty();

        MewServerPing.query(server.ip, result -> {
            server.motd = Component.literal(result.motd);
            server.ping = result.latencyMs;
            server.version = Component.literal(result.version);
            server.protocol = result.protocol;
            server.status = Component.literal(result.online + "/" + result.max);
            if (result.favicon != null) {
                try {
                    server.setIconBytes(result.favicon);
                } catch (RuntimeException ignored) {
                    // Invalid favicon must not hide a reachable server.
                }
            }
            changed.run();
        }, error -> {
            server.motd = Component.literal(MewErrors.describe(error));
            server.status = Component.empty();
            server.ping = -1;
            changed.run();
        });
    }
}
