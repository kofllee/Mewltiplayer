package dev.mewltiplayer.fabric;

public final class MewSpinner {
    private static final String[] FRAMES = {"|", "/", "-", "\\"};

    private MewSpinner() {}

    public static String frame() {
        return FRAMES[Math.floorMod(System.nanoTime() / 130_000_000L, FRAMES.length)];
    }
}
