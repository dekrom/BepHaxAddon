package bep.hax.util;
import net.minecraft.entity.Entity;
public class PushEntityEvent {
    private final Entity pushed;
    private final Entity pusher;
    private boolean canceled = false;
    public PushEntityEvent(Entity pushed, Entity pusher) {
        this.pushed = pushed;
        this.pusher = pusher;
    }
    public Entity getPushed() {
        return pushed;
    }
    public Entity getPusher() {
        return pusher;
    }
    public boolean isCanceled() {
        return canceled;
    }
    public void cancel() {
        this.canceled = true;
    }
    public void setCanceled(boolean canceled) {
        this.canceled = canceled;
    }
}