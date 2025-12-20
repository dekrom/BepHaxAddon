package bep.hax.modules;
import bep.hax.Bep;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
public class NoHurtCam extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final Setting<Boolean> disableHurtCam = sgGeneral.add(new BoolSetting.Builder()
        .name("Disable Hurt Cam")
        .description("Disables the camera shake/tilt when taking damage.")
        .defaultValue(true)
        .build()
    );
    private final Setting<Boolean> disableRedOverlay = sgGeneral.add(new BoolSetting.Builder()
        .name("Disable Red Overlay")
        .description("Disables the red overlay when taking damage.")
        .defaultValue(false)
        .build()
    );
    public NoHurtCam() {
        super(
            Bep.CATEGORY,
            "NoHurtCam",
            "Removes the hurt camera tilt and shake effect when taking damage."
        );
    }
    public boolean shouldDisableHurtCam() {
        return isActive() && disableHurtCam.get();
    }
    public boolean shouldDisableRedOverlay() {
        return isActive() && disableRedOverlay.get();
    }
}