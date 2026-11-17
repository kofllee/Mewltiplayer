package dev.mewltiplayer.forge.mixin;

import java.util.List;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.GuiMessage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ChatComponent.class)
public interface MewChatAccessor {
    @Accessor("allMessages")
    List<GuiMessage> mew$messages();
}
