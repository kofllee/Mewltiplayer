package dev.mewltiplayer.neoforge;

import dev.mewltiplayer.neoforge.mixin.MewChatAccessor;
import java.nio.file.Path;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.GuiMessage;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.storage.LevelResource;

public final class MewClientCompat {
    private static boolean pendingSettings;
    private static GuiMessage sharingLine;
    private static final ResourceLocation CAT_FONT =
            ResourceLocation.fromNamespaceAndPath("mewltiplayer", "chat_cat");

    private static final ResourceLocation SPINNER_FONT =
            ResourceLocation.fromNamespaceAndPath("mewltiplayer", "spinner");

    private MewClientCompat() {}

    public static boolean published() {
        IntegratedServer server = Minecraft.getInstance().getSingleplayerServer();
        return server != null && server.isPublished();
    }

    public static int worldPort() {
        IntegratedServer server = Minecraft.getInstance().getSingleplayerServer();
        return server == null ? 0 : server.getPort();
    }

    public static Path worldDirectory() {
        IntegratedServer server = Minecraft.getInstance().getSingleplayerServer();
        return server == null ? null : server.getWorldPath(LevelResource.ROOT);
    }

    public static void showSettings() {
        pendingSettings = true;
    }

    public static void openPendingSettings() {
        if (!pendingSettings) return;
        pendingSettings = false;
        Minecraft client = Minecraft.getInstance();
        client.setScreen(new MewSettingsScreen(client.screen));
    }

    private static MutableComponent prefix() {
        return Component.empty()
                .append(Component.literal("\uE000").withStyle(style -> style.withFont(CAT_FONT)))
                .append(Component.literal(" "));
    }

    private static ChatComponent chat() {
        return Minecraft.getInstance().gui.getChat();
    }

    public static void message(String key, boolean positive, Object... args) {
        Minecraft client = Minecraft.getInstance();
        if (client.player != null) {
            client.player.sendSystemMessage(prefix().append(Component.translatable(key, args)
                    .withStyle(positive ? ChatFormatting.GREEN : ChatFormatting.GRAY)));
        }
    }

    public static void connectionStatus(String key, long latencyMs, Object... args) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return;
        MutableComponent line = prefix().append(Component.translatable(key, args).withStyle(ChatFormatting.GREEN));
        if (latencyMs >= 0) {
            line.append(Component.literal("  " + latencyMs + " ms").withStyle(ChatFormatting.GRAY));
        }
        client.player.sendSystemMessage(line);
    }

    public static void checkingRoute() {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return;
        client.player.sendSystemMessage(prefix()
                .append(Component.translatable("mewltiplayer.chat.connectionConnected").withStyle(ChatFormatting.GREEN))
                .append(Component.literal(" "))
                .append(Component.translatable("mewltiplayer.chat.connectionChecking").withStyle(ChatFormatting.GRAY)));
    }

    public static void execute(Runnable task) {
        Minecraft.getInstance().execute(task);
    }

    public static void publishedShareCode(String code) {
        message("mewltiplayer.chat.shared", true);
        copyShareCode(code);
    }

    public static void copyShareCode(String code) {
        Minecraft client = Minecraft.getInstance();
        if (client.player != null) {
            client.player.sendSystemMessage(prefix()
                    .append(Component.translatable("mewltiplayer.chat.shareCode").withStyle(ChatFormatting.GRAY))
                    .append(Component.literal(": " + code + " [").withStyle(ChatFormatting.GRAY))
                    .append(Component.literal("Copy").withStyle(Style.EMPTY
                            .withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, code))
                            .withUnderlined(true).withColor(0x55ffff)))
                    .append(Component.literal("]").withStyle(ChatFormatting.GRAY)));
        }
    }

    private static Component sharingText() {
        return prefix().append(Component.literal(MewSpinner.frame()).withStyle(style -> style.withFont(SPINNER_FONT)))
                .append(Component.literal("  "))
                .append(Component.translatable("mewltiplayer.chat.opening").withStyle(ChatFormatting.GRAY));
    }

    public static void beginSharing() {
        endSharing();
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return;
        ChatComponent chat = chat();
        chat.addMessage(sharingText());
        List<GuiMessage> lines = ((MewChatAccessor) chat).mew$messages();
        if (!lines.isEmpty()) sharingLine = lines.get(0);
    }

    public static void updateSharing() {
        if (sharingLine == null) return;
        ChatComponent chat = chat();
        List<GuiMessage> lines = ((MewChatAccessor) chat).mew$messages();
        for (int index = 0; index < lines.size(); index++) {
            GuiMessage old = lines.get(index);
            if (old == sharingLine) {
                sharingLine = new GuiMessage(old.addedTime(), sharingText(),
                        old.signature(), old.tag());
                lines.set(index, sharingLine);
                chat.rescaleChat();
                return;
            }
        }
        sharingLine = null;
    }

    public static void endSharing() {
        if (sharingLine == null) return;
        ChatComponent chat = chat();
        boolean removed = ((MewChatAccessor) chat).mew$messages().removeIf(line -> line == sharingLine);
        sharingLine = null;
        if (removed) chat.rescaleChat();
    }

    public static void notice(String key) {
        Minecraft client = Minecraft.getInstance();
        if (client.player != null) {
            client.player.sendSystemMessage(prefix().append(
                    Component.translatable(key).withStyle(ChatFormatting.RED)));
        }
    }
}
