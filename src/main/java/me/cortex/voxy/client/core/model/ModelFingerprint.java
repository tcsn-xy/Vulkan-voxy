package me.cortex.voxy.client.core.model;
import java.security.MessageDigest;
import java.nio.ByteBuffer;
/** Immutable texture/depth identity without retaining twelve KiB of arrays per baked model. */
public record ModelFingerprint(long a,long b,long c,long d) {
    public static ModelFingerprint of(ColourDepthTextureData[] faces){
        try{
            var digest=MessageDigest.getInstance("SHA-256");var bytes=ByteBuffer.allocate(8+ModelFactory.MODEL_TEXTURE_SIZE*ModelFactory.MODEL_TEXTURE_SIZE*8);
            for(var face:faces){bytes.clear();bytes.putInt(face.width()).putInt(face.height());for(int value:face.colour())bytes.putInt(value);for(int value:face.depth())bytes.putInt(value);digest.update(bytes.array(),0,bytes.position());}
            var hash=ByteBuffer.wrap(digest.digest());return new ModelFingerprint(hash.getLong(),hash.getLong(),hash.getLong(),hash.getLong());
        }catch(java.security.NoSuchAlgorithmException e){throw new AssertionError(e);}
    }
}
