package me.cortex.voxy.client.vulkan;
import java.util.function.BooleanSupplier;
/** Never submit transfer/compute work inside a graphics pass, including third-party pass leases. */
final class ExternalCommands {
    static void prepare(Runnable suspendOwner,BooleanSupplier passActive){
        suspendOwner.run();
        if(passActive.getAsBoolean())throw new IllegalStateException("Voxy external commands require a closed graphics pass");
    }
}
