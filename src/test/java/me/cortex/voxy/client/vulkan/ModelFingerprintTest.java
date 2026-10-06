package me.cortex.voxy.client.vulkan;
import me.cortex.voxy.client.core.model.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ModelFingerprintTest {
    private static ColourDepthTextureData[] faces(){var f=new ColourDepthTextureData[6];for(int i=0;i<6;i++)f[i]=new ColourDepthTextureData(new int[256],new int[256],16,16);return f;}
    @Test void equalBakesShareIdentity(){assertEquals(ModelFingerprint.of(faces()),ModelFingerprint.of(faces()));}
    @Test void depthChangesCannotAliasIdenticalColour(){var a=faces();var original=ModelFingerprint.of(a);a[4].depth()[33]=123;assertNotEquals(original,ModelFingerprint.of(a));}
    @Test void fingerprintDoesNotRetainMutablePixelArrays(){var f=faces();var before=ModelFingerprint.of(f);f[3].colour()[0]=0xff00aaff;assertEquals(before,ModelFingerprint.of(faces()));assertNotEquals(before,ModelFingerprint.of(f));}
}
