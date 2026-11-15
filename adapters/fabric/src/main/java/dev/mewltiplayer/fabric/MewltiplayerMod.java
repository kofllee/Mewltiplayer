package dev.mewltiplayer.fabric;

import dev.mewltiplayer.core.MewltiplayerCore;
import net.fabricmc.api.ModInitializer;

public final class MewltiplayerMod implements ModInitializer {
    @Override
    public void onInitialize() {
        MewltiplayerCore.initialize();
    }
}
