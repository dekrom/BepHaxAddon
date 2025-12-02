package bep.hax.modules;
import bep.hax.Bep;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
public class YawLock extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final Setting<Boolean> bypass2b2t = sgGeneral.add(new BoolSetting.Builder()
        .name("2b2t-bypass")
        .description("Adds tiny yaw variations to bypass anticheat.")
        .defaultValue(false)
        .build()
    );
    private final Setting<Double> jitterAmount = sgGeneral.add(new DoubleSetting.Builder()
        .name("jitter-amount")
        .description("Maximum jitter in degrees.")
        .defaultValue(0.1)
        .min(0.01)
        .max(0.5)
        .visible(bypass2b2t::get)
        .build()
    );
    private final Setting<Integer> jitterInterval = sgGeneral.add(new IntSetting.Builder()
        .name("jitter-interval")
        .description("Ticks between jitter changes.")
        .defaultValue(10)
        .min(1)
        .max(40)
        .visible(bypass2b2t::get)
        .build()
    );
    private float lockedYaw;
    private float jitter = 0;
    private int tickCounter = 0;
    public YawLock() {
        super(Bep.STASH, "yaw-lock", "Locks your yaw to the closest 45-degree increment.");
    }
    @Override
    public void onActivate() {
        float currentYaw = mc.player.getYaw();
        float normalizedYaw = (currentYaw % 360 + 360) % 360;
        int closestMultiple = Math.round(normalizedYaw / 45f);
        lockedYaw = (closestMultiple * 45) % 360;
        if (lockedYaw > 180) lockedYaw -= 360;
        tickCounter = 0;
        jitter = 0;
    }
    @EventHandler
    private void onTick(TickEvent.Pre event) {
        float yawToSet = lockedYaw;
        if (bypass2b2t.get()) {
            tickCounter++;
            if (tickCounter >= jitterInterval.get()) {
                tickCounter = 0;
                jitter = (float) ((Math.random() * 2 - 1) * jitterAmount.get());
            }
            yawToSet = lockedYaw + jitter;
        }
        mc.player.setYaw(yawToSet);
        if (mc.player.hasVehicle()) {
            mc.player.getVehicle().setYaw(yawToSet);
        }
    }
}