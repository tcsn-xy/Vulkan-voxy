package me.cortex.voxy.common.world;

/** Missing unload lighting reuses recorded channels; explicit zero is a real replacement. */
public final class StoredLighting {
    private StoredLighting() {}
    public static byte compose(int previous, int block, int sky, boolean hasSkyLight, boolean exposedSky) {
        int resultBlock = block >= 0 ? block : previous >= 0 ? previous >>> 4 : 0;
        int resultSky = !hasSkyLight ? 0 : sky >= 0 ? sky : previous >= 0 ? previous & 15 : exposedSky ? 15 : 0;
        return (byte) ((resultSky & 15) | ((resultBlock & 15) << 4));
    }
}
