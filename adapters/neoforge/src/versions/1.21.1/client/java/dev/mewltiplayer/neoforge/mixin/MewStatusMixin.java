package dev.mewltiplayer.neoforge.mixin;

import dev.mewltiplayer.core.transport.ShareCode;
import dev.mewltiplayer.core.MewErrors;
import dev.mewltiplayer.neoforge.MewServerPing;
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
    private void mew$ping(ServerData server, Runnable changed, Runnable failed, CallbackInfo ci) {
        if (!ShareCode.looksLikeTailcatCode(server.ip)) {
            return;
        }
        ci.cancel();
        server.setState(ServerData.State.PINGING);
        server.motd = Component.translatable("mewltiplayer.status.connecting");
        server.status = Component.empty();

        MewServerPing.query(server.ip, result -> {
            server.motd = Component.literal(result.motd);
            server.ping = result.latencyMs;
            server.version = Component.literal(result.version);
            server.protocol = result.protocol;
            server.status = ServerStatusPinger.formatPlayerCount(result.online, result.max);
            server.setState(result.protocol >= 0 && result.protocol != SharedConstants.getProtocolVersion()
                    ? ServerData.State.INCOMPATIBLE : ServerData.State.SUCCESSFUL);
            if (result.favicon != null) {
                try {
                    server.setIconBytes(ServerData.validateIcon(result.favicon));
                } catch (RuntimeException ignored) {
                    // Invalid favicon must not hide a reachable server.
                }
            }
            changed.run();
        }, error -> {
            server.motd = Component.literal(MewErrors.describe(error));
            server.status = Component.empty();
            server.setState(ServerData.State.UNREACHABLE);
            failed.run();
        });
    }
}
