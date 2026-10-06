package me.cortex.voxy.client.vulkan;
import org.junit.jupiter.api.Test;
import java.util.Random;
import static org.junit.jupiter.api.Assertions.*;
class RasterCoverageTest {
    @Test void rejectsOnlyBoxesWithoutAnySample(){
        var random=new Random(42);
        for(int i=0;i<100000;i++){
            double x=random.nextDouble()*100,y=random.nextDouble()*100,w=random.nextDouble()*3,h=random.nextDouble()*3;
            boolean covers=false;for(int sy=(int)Math.floor(y)-1;sy<=Math.ceil(y+h)+1;sy++)for(int sx=(int)Math.floor(x)-1;sx<=Math.ceil(x+w)+1;sx++)covers|=sx+.5>=x&&sx+.5<=x+w&&sy+.5>=y&&sy+.5<=y+h;
            if(covers)assertTrue(RasterCoverage.canCover(x,y,x+w,y+h));
        }
    }
    @Test void preservesSamplesAndBoundaryGuard(){assertFalse(RasterCoverage.canCover(3.1,2.1,3.2,2.2));assertTrue(RasterCoverage.canCover(3.49,2.49,3.51,2.51));assertTrue(RasterCoverage.canCover(3.485,2.485,3.49,2.49));}
}
