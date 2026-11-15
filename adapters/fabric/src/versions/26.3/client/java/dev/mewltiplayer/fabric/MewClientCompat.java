package dev.mewltiplayer.fabric;

import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Style;

public final class MewClientCompat {
    private MewClientCompat() {
    }

    public static boolean published() {
        IntegratedServer server = Minecraft.getInstance().getSingleplayerServer();
        return server != null && server.isPublished();
    }

    public static int worldPort() {
        IntegratedServer server = Minecraft.getInstance().getSingleplayerServer();
        return server == null ? 0 : server.getPort();
    }

    public static void execute(Runnable task) {
        Minecraft.getInstance().execute(task);
    }

    public static void copyInvite(String code) {
        Minecraft client = Minecraft.getInstance();
        client.keyboardHandler.setClipboard(code);
        if (client.player != null) {
            client.player.sendSystemMessage(Component.translatable("mewltiplayer.inviteCopied").append(
                    Component.literal(" [Copy]").withStyle(Style.EMPTY
                            .withClickEvent(new ClickEvent.CopyToClipboard(code))
                            .withUnderlined(true).withColor(0x55ffff))));
        }
    }

    public static void notice(String message) {
        Minecraft client = Minecraft.getInstance();
        if (client.player != null) {
            client.player.sendSystemMessage(Component.translatable(message));
        }
    }
}
