package bep.hax.mixin.meteor;
import net.minecraft.client.world.ClientChunkManager;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkStatus;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.world.Timer;
import meteordevelopment.meteorclient.utils.Utils;
import net.minecraft.util.math.ChunkPos;
@Mixin(value = Timer.class, remap = false)
public abstract class TimerMixin extends Module {
    public TimerMixin(Category category, String name, String description) {
        super(category, name, description);
    }
    @Shadow
    @Final
    private SettingGroup sgGeneral;
    @Shadow
    @Final
    private Setting<Double> multiplier;
    @Unique
    private Setting<Boolean> autoAdjust;
    @Unique
    private Setting<Boolean> onlyWhenTraveling;
    @Unique
    private Setting<Double> travelSpeedThreshold;
    @Unique
    private Setting<Double> minSpeed;
    @Unique
    private Setting<Double> maxSpeed;
    @Unique
    private Setting<Integer> checkRadius;
    @Unique
    private Setting<Integer> unloadedThreshold;
    @Unique
    private Setting<Double> adjustSpeed;
    @Unique
    private Setting<Integer> checkInterval;
    @Unique
    private double targetSpeed = 1.0;
    @Unique
    private double currentAutoSpeed = 1.0;
    @Unique
    private int tickCounter = 0;
    @Unique
    private int lastUnloadedCount = 0;
    @Unique
    private net.minecraft.util.math.Vec3d lastPlayerPos = null;
    @Unique
    private int speedCheckTicks = 0;
    @Unique
    private double currentSpeed = 0;
    @Inject(method = "<init>", at = @At("TAIL"))
    private void onInit(CallbackInfo ci) {
        autoAdjust = sgGeneral.add(new BoolSetting.Builder()
            .name("auto-adjust")
            .description("Automatically adjust timer speed based on chunk loading (for 2b2t).")
            .defaultValue(false)
            .build()
        );
        onlyWhenTraveling = sgGeneral.add(new BoolSetting.Builder()
            .name("only-when-traveling")
            .description("Only use auto-adjust when moving faster than threshold speed. Sets timer to 1.0 when slower.")
            .defaultValue(true)
            .visible(autoAdjust::get)
            .build()
        );
        travelSpeedThreshold = sgGeneral.add(new DoubleSetting.Builder()
            .name("travel-speed-threshold")
            .description("Minimum speed (km/h) required for auto-adjust to activate.")
            .defaultValue(10.0)
            .min(1.0)
            .sliderRange(1.0, 100.0)
            .visible(() -> autoAdjust.get() && onlyWhenTraveling.get())
            .build()
        );
        minSpeed = sgGeneral.add(new DoubleSetting.Builder()
            .name("min-speed")
            .description("Minimum timer speed when chunks aren't loading (slows down time).")
            .defaultValue(0.4)
            .min(0.1)
            .sliderRange(0.1, 1.0)
            .visible(autoAdjust::get)
            .build()
        );
        maxSpeed = sgGeneral.add(new DoubleSetting.Builder()
            .name("max-speed")
            .description("Maximum timer speed when chunks load fine (1.0 = vanilla).")
            .defaultValue(1.0)
            .min(0.1)
            .sliderRange(0.1, 2.0)
            .visible(autoAdjust::get)
            .build()
        );
        checkRadius = sgGeneral.add(new IntSetting.Builder()
            .name("check-radius")
            .description("Radius in chunks to check for loading (higher = more conservative).")
            .defaultValue(3)
            .min(1)
            .sliderRange(1, 8)
            .visible(autoAdjust::get)
            .build()
        );
        unloadedThreshold = sgGeneral.add(new IntSetting.Builder()
            .name("unloaded-threshold")
            .description("Number of unloaded chunks before slowing down.")
            .defaultValue(6)
            .min(1)
            .sliderRange(1, 20)
            .visible(autoAdjust::get)
            .build()
        );
        adjustSpeed = sgGeneral.add(new DoubleSetting.Builder()
            .name("adjust-speed")
            .description("How quickly timer adjusts to chunk loading (higher = faster).")
            .defaultValue(0.15)
            .min(0.01)
            .sliderRange(0.01, 1.0)
            .visible(autoAdjust::get)
            .build()
        );
        checkInterval = sgGeneral.add(new IntSetting.Builder()
            .name("check-interval")
            .description("Ticks between chunk load checks (lower = more responsive).")
            .defaultValue(5)
            .min(1)
            .sliderRange(1, 40)
            .visible(autoAdjust::get)
            .build()
        );
    }
    @Override
    public void onActivate() {
        currentAutoSpeed = multiplier.get();
        targetSpeed = multiplier.get();
        tickCounter = 0;
        lastUnloadedCount = 0;
        lastPlayerPos = null;
        speedCheckTicks = 0;
        currentSpeed = 0;
    }
    @Unique
    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (!Utils.canUpdate() || !autoAdjust.get() || mc.player == null) return;
        if (lastPlayerPos != null) {
            net.minecraft.util.math.Vec3d currentPos = mc.player.getEntityPos();
            double distanceTraveled = currentPos.subtract(lastPlayerPos).multiply(1, 0, 1).length();
            double speedBPS = distanceTraveled * 20.0;
            currentSpeed = speedBPS * 3.6;
        }
        lastPlayerPos = mc.player.getEntityPos();
        tickCounter++;
        if (tickCounter < checkInterval.get()) return;
        tickCounter = 0;
        if (onlyWhenTraveling.get()) {
            if (currentSpeed < travelSpeedThreshold.get()) {
                targetSpeed = 1.0;
                currentAutoSpeed = 1.0;
                multiplier.set(1.0);
                lastUnloadedCount = 0;
                return;
            }
        }
        int unloadedChunks = countUnloadedChunks();
        if (unloadedChunks > unloadedThreshold.get()) {
            double severity = Math.min(1.0, (double) unloadedChunks / (unloadedThreshold.get() * 2.0));
            targetSpeed = minSpeed.get() + (maxSpeed.get() - minSpeed.get()) * (1.0 - severity);
        } else {
            targetSpeed = maxSpeed.get();
        }
        double diff = targetSpeed - currentAutoSpeed;
        if (Math.abs(diff) > 0.01) {
            currentAutoSpeed += diff * adjustSpeed.get();
            multiplier.set(Math.max(minSpeed.get(), Math.min(maxSpeed.get(), currentAutoSpeed)));
        }
        lastUnloadedCount = unloadedChunks;
    }
    @Unique
    private int countUnloadedChunks() {
        if (mc.player == null || mc.world == null) return 0;
        ClientChunkManager chunkManager = mc.world.getChunkManager();
        ChunkPos playerChunkPos = mc.player.getChunkPos();
        int radius = checkRadius.get();
        int unloadedCount = 0;
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                int chunkX = playerChunkPos.x + x;
                int chunkZ = playerChunkPos.z + z;
                Chunk chunk = chunkManager.getChunk(chunkX, chunkZ, ChunkStatus.FULL, false);
                if (chunk == null) {
                    unloadedCount++;
                }
            }
        }
        return unloadedCount;
    }
    @Unique
    public boolean isAutoAdjustEnabled() {
        return autoAdjust != null && autoAdjust.get();
    }
    @Unique
    public double getCurrentAutoSpeed() {
        return currentAutoSpeed;
    }
    @Unique
    public int getLastUnloadedCount() {
        return lastUnloadedCount;
    }
}