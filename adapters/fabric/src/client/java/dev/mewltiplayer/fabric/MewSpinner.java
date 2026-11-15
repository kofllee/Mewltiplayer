package dev.mewltiplayer.fabric;

public final class MewSpinner {
    private static final int FRAME_COUNT = 9;
    private static final long FRAME_NANOS = 110_000_000L;

    private MewSpinner() {}

    public static String frame() {
        int frame = (int) Math.floorMod(System.nanoTime() / FRAME_NANOS, FRAME_COUNT);
        return String.valueOf((char) ('\uE100' + frame));
    }
}
