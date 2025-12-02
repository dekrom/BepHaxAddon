package bep.hax.modules;
import bep.hax.Bep;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.movement.elytrafly.ElytraFly;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.util.Hand;
import meteordevelopment.meteorclient.systems.modules.movement.elytrafly.ElytraFlightModes;
import static bep.hax.util.Utils.firework;
public class Pitch40Util extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    public final Setting<Boolean> autoBoundAdjust = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-adjust-bounds")
        .description("Adjusts your bounds to make you continue to gain height. Good for fixing falling on reconnect or lag, etc.")
        .defaultValue(true)
        .build()
    );
    public final Setting<Double> boundGap = sgGeneral.add(new DoubleSetting.Builder()
        .name("bound-gap")
        .description("The gap between the upper and lower bounds. Used when reconnecting, or when at max height if Auto Adjust Bounds is enabled.")
        .defaultValue(60)
        .sliderRange(50, 100)
        .build()
    );
    public final Setting<Boolean> autoFirework = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-firework")
        .description("Uses a firework automatically if your velocity is too low.")
        .defaultValue(true)
        .build()
    );
    public final Setting<Double> velocityThreshold = sgGeneral.add(new DoubleSetting.Builder()
        .name("velocity-threshold")
        .description("Velocity must be below this value when going up for firework to activate.")
        .defaultValue(-0.05)
        .sliderRange(-0.5, 1)
        .visible(autoFirework::get)
        .build()
    );
    public final Setting<Integer> fireworkCooldownTicks = sgGeneral.add(new IntSetting.Builder()
        .name("cooldown-ticks")
        .description("Cooldown after using a firework in ticks.")
        .defaultValue(10)
        .sliderRange(0, 100)
        .visible(autoFirework::get)
        .build()
    );
    public Pitch40Util() {
        super(Bep.STASH, "Pitch40Util", "Makes sure pitch 40 stays on when reconnecting to 2b2t, and sets your bounds as you reach highest point each climb.");
    }
    private Module elytraFly;
    private ElytraFlightModes oldValue;
    private Setting<ElytraFlightModes> elytraFlyMode;
    private Module getElytraFly() {
        if (elytraFly == null) elytraFly = Modules.get().get(ElytraFly.class);
        return elytraFly;
    }
    @SuppressWarnings("unchecked")
    private Setting<ElytraFlightModes> getElytraFlyMode() {
        if (elytraFlyMode == null) elytraFlyMode = (Setting<ElytraFlightModes>) getElytraFly().settings.get("mode");
        return elytraFlyMode;
    }
    @Override
    public void onActivate()
    {
        oldValue = getElytraFlyMode().get();
        getElytraFlyMode().set(ElytraFlightModes.Pitch40);
    }
    @Override
    public void onDeactivate()
    {
        if (getElytraFly().isActive())
        {
            getElytraFly().toggle();
        }
        getElytraFlyMode().set(oldValue);
    }
    int fireworkCooldown = 0;
    boolean goingUp = true;
    int elytraSwapSlot = -1;
    @SuppressWarnings("unchecked")
    private void resetBounds()
    {
        Setting<Double> upperBounds = (Setting<Double>) getElytraFly().settings.get("pitch40-upper-bounds");
        upperBounds.set(mc.player.getY() - 5);
        Setting<Double> lowerBounds = (Setting<Double>) getElytraFly().settings.get("pitch40-lower-bounds");
        lowerBounds.set(mc.player.getY() - 5 - boundGap.get());
    }
    @EventHandler
    private void onTick(TickEvent.Pre event)
    {
        if (getElytraFly().isActive())
        {
            if (fireworkCooldown > 0) {
                fireworkCooldown--;
            }
            if (elytraSwapSlot != -1)
            {
                InvUtils.swap(elytraSwapSlot, true);
                mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
                InvUtils.swapBack();
                elytraSwapSlot = -1;
            }
            if (autoBoundAdjust.get() && mc.player.getY() <= (double)getElytraFly().settings.get("pitch40-lower-bounds").get() - 10)
            {
                resetBounds();
                return;
            }
            if (mc.player.getPitch() == -40)
            {
                goingUp = true;
                if (autoFirework.get() && mc.player.getVelocity().y < velocityThreshold.get() && mc.player.getY() < (double)getElytraFly().settings.get("pitch40-upper-bounds").get())
                {
                    if (fireworkCooldown == 0) {
                        int launchStatus = firework(mc, false);
                        if (launchStatus >= 0)
                        {
                            fireworkCooldown = fireworkCooldownTicks.get();
                            if (launchStatus != 200) elytraSwapSlot = launchStatus;
                        }
                    }
                }
            }
            else if (autoBoundAdjust.get() && goingUp && mc.player.getVelocity().y <= 0) {
                goingUp = false;
                resetBounds();
            }
        }
        else
        {
            if (!mc.player.getAbilities().allowFlying)
            {
                getElytraFly().toggle();
                resetBounds();
            }
        }
    }
}