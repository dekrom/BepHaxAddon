package bep.hax.modules;
import bep.hax.Bep;
import meteordevelopment.meteorclient.systems.modules.Module;
public class NoJumpDelay extends Module
{
    public NoJumpDelay()
    {
        super(
            Bep.CATEGORY,
            "NoJumpDelay",
            "Removes the delay between jumps."
        );
    }
}