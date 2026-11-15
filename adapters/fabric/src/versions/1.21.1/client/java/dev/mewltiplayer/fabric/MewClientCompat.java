package dev.mewltiplayer.fabric;

import dev.mewltiplayer.fabric.mixin.MewChatAccessor;
import java.nio.file.Path;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.ChatHudLine;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.WorldSavePath;

public final class MewClientCompat {
    private static boolean pendingSettings;
    private static ChatHudLine sharingLine;
    private static final Identifier CAT_FONT = Identifier.of("mewltiplayer", "chat_cat");

    private MewClientCompat() {}

    public static boolean published() {
        IntegratedServer server = MinecraftClient.getInstance().getServer();
        return server != null && server.isRemote();
    }

    public static int worldPort() {
        IntegratedServer server = MinecraftClient.getInstance().getServer();
        return server == null ? 0 : server.getServerPort();
    }

    public static Path worldDirectory() {
        IntegratedServer server = MinecraftClient.getInstance().getServer();
        return server == null ? null : server.getSavePath(WorldSavePath.ROOT);
    }

    public static void showSettings() {
        pendingSettings = true;
    }

    public static void openPendingSettings() {
        if (!pendingSettings) return;
        pendingSettings = false;
        MinecraftClient client = MinecraftClient.getInstance();
        client.setScreen(new MewSettingsScreen(client.currentScreen));
    }

    private static MutableText prefix() {
        return Text.empty()
                .append(Text.literal("\uE000").styled(style -> style.withFont(CAT_FONT)))
                .append(Text.literal(" "));
    }

    private static ChatHud chat() {
        return MinecraftClient.getInstance().inGameHud.getChatHud();
    }

    public static void message(String key, boolean positive, Object... args) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) {
            client.player.sendMessage(prefix().append(Text.translatable(key, args)
                    .formatted(positive ? Formatting.GREEN : Formatting.GRAY)), false);
        }
    }

    public static void execute(Runnable task) {
        MinecraftClient.getInstance().execute(task);
    }

    public static void publishedInvite(String code) {
        message("mewltiplayer.chat.shared", true);
        copyInvite(code);
    }

    public static void copyInvite(String code) {
        MinecraftClient client = MinecraftClient.getInstance();
        client.keyboard.setClipboard(code);
        if (client.player != null) {
            client.player.sendMessage(prefix()
                    .append(Text.translatable("mewltiplayer.chat.invite").formatted(Formatting.GRAY))
                    .append(Text.literal(" [").formatted(Formatting.GRAY))
                    .append(Text.literal("Copy").styled(style -> style.withClickEvent(
                            new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, code)))
                            .formatted(Formatting.AQUA, Formatting.UNDERLINE))
                    .append(Text.literal("]").formatted(Formatting.GRAY)), false);
        }
    }

    private static Text sharingText() {
        return prefix().append(Text.literal(MewSpinner.frame() + "  ").formatted(Formatting.GOLD))
                .append(Text.translatable("mewltiplayer.chat.opening").formatted(Formatting.GRAY));
    }

    public static void beginSharing() {
        endSharing();
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;
        ChatHud chat = chat();
        chat.addMessage(sharingText());
        List<ChatHudLine> lines = ((MewChatAccessor) chat).mew$messages();
        if (!lines.isEmpty()) sharingLine = lines.get(0);
    }

    public static void updateSharing() {
        if (sharingLine == null) return;
        ChatHud chat = chat();
        MewChatAccessor accessor = (MewChatAccessor) chat;
        List<ChatHudLine> lines = accessor.mew$messages();
        for (int index = 0; index < lines.size(); index++) {
            ChatHudLine old = lines.get(index);
            if (old == sharingLine) {
                sharingLine = new ChatHudLine(old.creationTick(), sharingText(),
                        old.signature(), old.indicator());
                lines.set(index, sharingLine);
                accessor.mew$refresh();
                return;
            }
        }
        sharingLine = null;
    }

    public static void endSharing() {
        if (sharingLine == null) return;
        ChatHud chat = chat();
        MewChatAccessor accessor = (MewChatAccessor) chat;
        boolean removed = accessor.mew$messages().removeIf(line -> line == sharingLine);
        sharingLine = null;
        if (removed) accessor.mew$refresh();
    }

    public static void notice(String key) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) {
            client.player.sendMessage(prefix().append(
                    Text.translatable(key).formatted(Formatting.RED)), false);
        }
    }
}
