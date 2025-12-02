package bep.hax.modules;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Module;
import bep.hax.util.RotationUtils;
import java.util.Random;
public abstract class PVPModule extends Module {
    protected static final Random RANDOM = new Random();
    protected PVPModule(Category category, String name, String description) {
        super(category, name, description);
    }
    @Override
    public String getInfoString() {
        return null;
    }
    protected boolean isValidPlayer() {
        return mc.player != null && mc.world != null && !mc.player.isRemoved();
    }
    protected boolean isInWorld() {
        return mc.world != null && mc.player != null;
    }
    protected void safeToggle() {
        if (isActive()) {
            toggle();
        }
    }
    protected void setRotation(float yaw, float pitch) {
        RotationUtils.getInstance().setRotationClient(yaw, pitch);
    }
    protected void setRotationSilent(float yaw, float pitch) {
        RotationUtils.getInstance().setRotationSilent(yaw, pitch);
    }
    protected void setRotationClient(float yaw, float pitch) {
        RotationUtils.getInstance().setRotationClient(yaw, pitch);
    }
    protected void setRotationSilentSync() {
        RotationUtils.getInstance().setRotationSilentSync();
    }
}