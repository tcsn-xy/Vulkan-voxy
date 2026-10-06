package me.cortex.voxy.commonImpl.importers;

import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

/** Mirrors26.3 SkyLightSectionStorage's missing-layer lookup without loading a world/light engine. */
public final class ImportedSkyLight {
    private final boolean hasSkyLight, lightCorrect;
    private final NavigableMap<Integer, byte[]> explicitLayers;
    private final int[] firstAvailableSurface;

    public ImportedSkyLight(boolean hasSkyLight, boolean lightCorrect, Map<Integer, byte[]> layers, int[] firstAvailableSurface) {
        this.hasSkyLight = hasSkyLight;
        this.lightCorrect = lightCorrect;
        this.explicitLayers = new TreeMap<>(layers);
        if (firstAvailableSurface != null && firstAvailableSurface.length != 256) throw new IllegalArgumentException("Heightmap must contain256 columns");
        this.firstAvailableSurface = firstAvailableSurface;
        for (byte[] layer : layers.values()) if (layer.length != 2048) throw new IllegalArgumentException("SkyLight must contain2048 bytes");
    }
    public final class Layer {
        private final int baseY;
        private final byte[] values;
        private final boolean repeatBottom, fullAbove;
        private Layer(int sectionY, byte[] values, boolean repeatBottom, boolean fullAbove) {
            this.baseY = sectionY << 4;
            this.values = values;
            this.repeatBottom = repeatBottom;
            this.fullAbove = fullAbove;
        }
        public int get(int x, int y, int z) {
            if (this.values != null) {
                int index = (this.repeatBottom ? 0 : y << 8) | (z << 4) | x;
                return (Byte.toUnsignedInt(this.values[index >>> 1]) >>> ((index & 1) << 2)) & 15;
            }
            if (!this.fullAbove) return 0;
            // With no explicit layer, a heightmap is the minimum evidence for exposed sky.
            return firstAvailableSurface == null || this.baseY + y >= firstAvailableSurface[(z << 4) | x] ? 15 : 0;
        }
    }
    public Layer resolve(int sectionY) {
        if (!this.hasSkyLight || !this.lightCorrect) return new Layer(sectionY, null, false, false);
        byte[] explicit = this.explicitLayers.get(sectionY);
        if (explicit != null) return new Layer(sectionY, explicit, false, false);
        var above = this.explicitLayers.higherEntry(sectionY);
        if (above != null) return new Layer(sectionY, above.getValue(), true, false);
        return new Layer(sectionY, null, false, !this.explicitLayers.isEmpty() || this.firstAvailableSurface != null);
    }
}
