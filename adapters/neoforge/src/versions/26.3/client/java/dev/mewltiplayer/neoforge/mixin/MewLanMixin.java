package dev.mewltiplayer.neoforge.mixin;

import dev.mewltiplayer.core.MewWorldSettings;
import dev.mewltiplayer.neoforge.MewClientCompat;
import dev.mewltiplayer.neoforge.MewRuntime;
import dev.mewltiplayer.neoforge.MewSettingsScreen;
import java.io.IOException;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.FocusableTextWidget;
import net.minecraft.client.gui.layouts.GridLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.WorldOptionsScreen;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldOptionsScreen.class)
public abstract class MewLanMixin extends Screen {
    @Unique private boolean mew$enabled;

    protected MewLanMixin(Component title) {
        super(title);
    }

    @Inject(method = "multiplayerOptions", at = @At("TAIL"))
    private void mew$addOptions(LinearLayout layout, IntegratedServer server, CallbackInfo ci) {
        mew$enabled = MewWorldSettings.shareViaTailcat(MewClientCompat.worldDirectory());

        // A separate category gets the same layout spacing as vanilla Multiplayer.
        GridLayout grid = new GridLayout();
        grid.defaultCellSetting().alignHorizontallyCenter();
        grid.columnSpacing(8).rowSpacing(4);
        GridLayout.RowHelper rows = grid.createRowHelper(2);
        rows.addChild(FocusableTextWidget.builder(
                Component.literal("Mewltiplayer").withStyle(ChatFormatting.UNDERLINE, ChatFormatting.BOLD), font)
                .alwaysShowBorder(false)
                .backgroundFill(FocusableTextWidget.BackgroundFill.ON_FOCUS)
                .build(), 2);
        rows.addChild(Button.builder(mew$label(), button -> {
            boolean next = !mew$enabled;
            try {
                MewWorldSettings.setShareViaTailcat(MewClientCompat.worldDirectory(), next);
                mew$enabled = next;
                MewRuntime.INSTANCE.setOnlineSharingEnabled(
                        next && server.getMultiplayerScope() == MinecraftServer.MultiplayerScope.LAN);
                button.setMessage(mew$label());
            } catch (IOException error) {
                MewClientCompat.notice("mewltiplayer.settings.saveFailed");
            }
        }).width(150).build());
        rows.addChild(Button.builder(Component.translatable("mewltiplayer.settings.open"),
                button -> Minecraft.getInstance().gui.setScreen(new MewSettingsScreen(this)))
                .width(150).build());
        layout.addChild(grid);
    }

    @Unique
    private Component mew$label() {
        return Component.translatable("mewltiplayer.lan.short",
                Component.translatable(mew$enabled ? "options.on" : "options.off")
                        .withStyle(mew$enabled ? ChatFormatting.GREEN : ChatFormatting.RED));
    }

    @Inject(method = "applyChanges", at = @At("TAIL"))
    private void mew$apply(IntegratedServer server, CallbackInfo ci) {
        MewRuntime.INSTANCE.setOnlineSharingEnabled(mew$enabled
                && server != null && server.getMultiplayerScope() == MinecraftServer.MultiplayerScope.LAN);
    }
}
