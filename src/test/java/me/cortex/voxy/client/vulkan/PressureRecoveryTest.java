package me.cortex.voxy.client.vulkan;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class PressureRecoveryTest {
    private static final long M=1024*1024;
    @Test void lowBudgetCanRecoverWithoutRequiringTwoWholePages(){assertTrue(PressureRecovery.canResume(60*M,12*M,128*M));}
    @Test void externallyOccupiedBudgetCannotOscillateBackToRefinement(){assertFalse(PressureRecovery.canResume(96*M,M/2,128*M));}
    @Test void normalBudgetRequiresHeadroomForInFlightResources(){assertFalse(PressureRecovery.canResume(100*M,8*M,768*M));assertTrue(PressureRecovery.canResume(100*M,32*M,768*M));}
}
