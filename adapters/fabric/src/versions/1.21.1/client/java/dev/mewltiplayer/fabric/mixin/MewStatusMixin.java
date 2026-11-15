package dev.mewltiplayer.fabric.mixin;

import dev.mewltiplayer.core.transport.ShareCode;
import dev.mewltiplayer.core.MewErrors;
import dev.mewltiplayer.fabric.MewServerPing;
import net.minecraft.SharedConstants;
import net.minecraft.client.network.MultiplayerServerListPinger;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MultiplayerServerListPinger.class)
public abstract class MewStatusMixin {
    @Inject(method = "add", at = @At("HEAD"), cancellable = true)
    private void mew$ping(ServerInfo server, Runnable changed, Runnable failed, CallbackInfo ci) {
        if (!ShareCode.looksLikeTailcatCode(server.address)) {
            return;
        }
        ci.cancel();
        server.setStatus(ServerInfo.Status.PINGING);
        server.label = Text.translatable("mewltiplayer.status.connecting");
        server.playerCountLabel = Text.empty();

        MewServerPing.query(server.address, result -> {
            server.label = Text.literal(result.motd);
            server.ping = result.latencyMs;
            server.version = Text.literal(result.version);
            server.protocolVersion = result.protocol;
            server.playerCountLabel = MultiplayerServerListPinger.createPlayerCountText(result.online, result.max);
            server.setStatus(result.protocol >= 0 && result.protocol != SharedConstants.getGameVersion().getProtocolVersion()
                    ? ServerInfo.Status.INCOMPATIBLE : ServerInfo.Status.SUCCESSFUL);
            if (result.favicon != null) {
                try {
                    server.setFavicon(ServerInfo.validateFavicon(result.favicon));
                    changed.run();
                } catch (RuntimeException ignored) {
                    // Invalid favicon must not hide a reachable server.
                }
            }
        }, error -> {
            server.label = Text.literal(MewErrors.describe(error));
            server.playerCountLabel = Text.empty();
            server.setStatus(ServerInfo.Status.UNREACHABLE);
            failed.run();
        });
    }
}
