package dev.mewltiplayer.fabric;

import net.minecraft.client.MinecraftClient;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.text.Text;
import net.minecraft.text.ClickEvent;
import net.minecraft.util.Formatting;

public final class MewClientCompat {
    private MewClientCompat() {
    }

    public static boolean published() {
        IntegratedServer server = MinecraftClient.getInstance().getServer();
        return server != null && server.isRemote();
    }

    public static int worldPort() {
        IntegratedServer server = MinecraftClient.getInstance().getServer();
        return server == null ? 0 : server.getServerPort();
    }

    public static void execute(Runnable task) {
        MinecraftClient.getInstance().execute(task);
    }

    public static void copyInvite(String code) {
        MinecraftClient client = MinecraftClient.getInstance();
        client.keyboard.setClipboard(code);
        if (client.player != null) {
            client.player.sendMessage(Text.translatable("mewltiplayer.inviteCopied").append(
                    Text.literal(" [Copy]").styled(style -> style.withClickEvent(
                            new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, code)))
                            .formatted(Formatting.AQUA, Formatting.UNDERLINE)), false);
        }
    }

    public static void notice(String message) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) {
            client.player.sendMessage(Text.translatable(message), false);
        }
    }
}
