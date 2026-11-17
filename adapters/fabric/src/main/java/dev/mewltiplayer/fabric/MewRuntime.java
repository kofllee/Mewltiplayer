package dev.mewltiplayer.fabric;

import dev.mewltiplayer.core.MewRuntimeBase;
import net.fabricmc.loader.api.FabricLoader;

public final class MewRuntime extends MewRuntimeBase {
    public static final MewRuntime INSTANCE = new MewRuntime();

    private MewRuntime() {
        super(FabricLoader.getInstance().getConfigDir().resolve("mewltiplayer"));
    }
}
