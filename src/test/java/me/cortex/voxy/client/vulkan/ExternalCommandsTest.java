package me.cortex.voxy.client.vulkan;
import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;
class ExternalCommandsTest {
    @Test void graphicsOwnerIsSuspendedBeforeTransferSubmission(){var active=new AtomicBoolean(true);ExternalCommands.prepare(()->active.set(false),active::get);assertFalse(active.get());}
    @Test void unownedPassIsNeverSilentlySubmittedThrough(){assertThrows(IllegalStateException.class,()->ExternalCommands.prepare(()->{},()->true));}
}
