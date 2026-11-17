package dev.mewltiplayer.forge;

import dev.mewltiplayer.core.MewRuntimeBase;
import net.minecraftforge.fml.loading.FMLPaths;

public final class MewRuntime extends MewRuntimeBase {
    public static final MewRuntime INSTANCE = new MewRuntime();

    private MewRuntime() {
        super(FMLPaths.CONFIGDIR.get().resolve("mewltiplayer"));
    }
}