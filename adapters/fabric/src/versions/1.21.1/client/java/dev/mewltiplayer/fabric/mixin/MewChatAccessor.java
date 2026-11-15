package dev.mewltiplayer.fabric.mixin;

import java.util.List;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.ChatHudLine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ChatHud.class)
public interface MewChatAccessor {
    @Accessor("messages")
    List<ChatHudLine> mew$messages();

    @Invoker("refresh")
    void mew$refresh();
}
