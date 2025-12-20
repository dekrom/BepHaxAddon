package bep.hax.modules;
import baritone.api.BaritoneAPI;
import baritone.api.pathing.goals.GoalXZ;
import bep.hax.Bep;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import bep.hax.util.Utils;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.packet.s2c.common.DisconnectS2CPacket;
import net.minecraft.registry.RegistryKey;
import net.minecraft.text.Text;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.item.Items;
import xaeroplus.XaeroPlus;
import xaeroplus.event.ChunkDataEvent;
import xaeroplus.module.ModuleManager;
import xaeroplus.module.impl.OldChunks;
import xaeroplus.module.impl.PaletteNewChunks;
import java.time.Duration;
import java.util.ArrayDeque;
import static bep.hax.util.Utils.positionInDirection;
import static bep.hax.util.Utils.sendWebhook;
public class TrailFollower extends Module
{
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    public final Setting<Integer> maxTrailLength = sgGeneral.add(new IntSetting.Builder()
        .name("max-trail-length")
        .description("The number of trail points to keep for the average. Adjust to change how quickly the average will change. More does not necessarily equal better because if the list is too long it will contain chunks behind you.")
        .defaultValue(20)
        .sliderRange(1, 100)
        .build()
    );
    public final Setting<Integer> chunksBeforeStarting = sgGeneral.add(new IntSetting.Builder()
        .name("chunks-before-starting")
        .description("Useful for afking looking for a trail. The amount of chunks before it gets detected as a trail.")
        .defaultValue(10)
        .sliderRange(1, 50)
        .build()
    );
    public final Setting<Integer> chunkConsiderationWindow = sgGeneral.add(new IntSetting.Builder()
        .name("chunk-timeframe")
        .description("The amount of time in seconds that the chunks must be found in before starting.")
        .defaultValue(5)
        .sliderRange(1, 20)
        .build()
    );
    public final Setting<TrailEndBehavior> trailEndBehavior = sgGeneral.add(new EnumSetting.Builder<TrailEndBehavior>()
        .name("trail-end-behavior")
        .description("What to do when the trail ends.")
        .defaultValue(TrailEndBehavior.DISABLE)
        .build()
    );
    public final Setting<Double> trailEndYaw = sgGeneral.add(new DoubleSetting.Builder()
        .name("trail-end-yaw")
        .description("The direction to go after the trail is abandoned.")
        .defaultValue(0.0)
        .sliderRange(0.0, 359.9)
        .visible(() -> trailEndBehavior.get() == TrailEndBehavior.FLY_TOWARDS_YAW)
        .build()
    );
    public enum OverworldFlightMode {
        VANILLA,
        PITCH40,
        OTHER
    }
    public enum NetherPathMode {
        AVERAGE,
        OTHER
    }
    public final Setting<OverworldFlightMode> overworldFlightMode = sgGeneral.add(new EnumSetting.Builder<OverworldFlightMode>()
        .name("overworld-flight-mode")
        .description("Choose how TrailFollower flies in Overworld. If other is selected then nothing will be automatically enabled, instead just your yaw will be changed to point towards the trail.")
        .defaultValue(OverworldFlightMode.PITCH40)
        .build()
    );
    public final Setting<NetherPathMode> netherPathMode = sgGeneral.add(new EnumSetting.Builder<NetherPathMode>()
        .name("nether-path-mode")
        .description("Choose how TrailFollower does baritone pathing in Nether. If other is selected then nothing will be automatically enabled, instead just your yaw will be changed to point towards the trail.")
        .defaultValue(NetherPathMode.AVERAGE)
        .build()
    );
    public final Setting<Boolean> pitch40Firework = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-firework")
        .description("Uses a firework automatically if your velocity is too low.")
        .defaultValue(true)
        .visible(() -> overworldFlightMode.get() == OverworldFlightMode.PITCH40)
        .build()
    );
    public final Setting<Double> rotateScaling = sgGeneral.add(new DoubleSetting.Builder()
        .name("rotate-scaling")
        .description("Scaling of how fast the yaw changes. 1 = instant, 0 = doesn't change")
        .defaultValue(0.1)
        .sliderRange(0.0, 1.0)
        .build()
    );
    public final Setting<Boolean> oppositeDimension = sgGeneral.add(new BoolSetting.Builder()
        .name("opposite-dimension")
        .description("Follows trails from the opposite dimension (Requires that you've already loaded the other dimension with XP).")
        .defaultValue(false)
        .build()
    );
    public final Setting<Boolean> autoElytra = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-start-baritone-elytra")
        .description("Starts baritone elytra for you.")
        .defaultValue(false)
        .build()
    );
    private final SettingGroup sgAdvanced = settings.createGroup("Advanced", false);
    public final Setting<Double> pathDistance = sgAdvanced.add(new DoubleSetting.Builder()
        .name("path-distance")
        .description("The distance to add trail positions in the direction the player is facing. (Ignored when following overworld from nether)")
        .defaultValue(500)
        .sliderRange(100, 2000)
        .onChanged(value -> pathDistanceActual = value)
        .build()
    );
    public final Setting<FollowMode> flightMethod = sgAdvanced.add(new EnumSetting.Builder<FollowMode>()
        .name("flight-method")
        .description("Decided how the goals will be used. Leave this on AUTO unless you want to use yaw lock in the nether for example.")
        .defaultValue(FollowMode.AUTO)
        .build()
    );
    public final Setting<Double> startDirectionWeighting = sgAdvanced.add(new DoubleSetting.Builder()
        .name("start-direction-weight")
        .description("The weighting of the direction the player is facing when starting the trail. 0 for no weighting (not recommended) 1 for max weighting (will take a bit for direction to change)")
        .defaultValue(0.5)
        .min(0)
        .sliderMax(1)
        .build()
    );
    public final Setting<DirectionWeighting> directionWeighting = sgAdvanced.add(new EnumSetting.Builder<DirectionWeighting>()
        .name("direction-weighting")
        .description("How the chunks found should be weighted. Useful for path splits. Left will weight chunks to the left of the player higher, right will weigh chunks to the right higher, and none will be in the middle/random. ")
        .defaultValue(DirectionWeighting.NONE)
        .build()
    );
    public final Setting<Integer> directionWeightingMultiplier = sgAdvanced.add(new IntSetting.Builder()
        .name("direction-weighting-multiplier")
        .description("The multiplier for how much weight should be given to chunks in the direction specified. Values are capped to be in the range [2, maxTrailLength].")
        .defaultValue(2)
        .min(2)
        .sliderMax(10)
        .visible(() -> directionWeighting.get() != DirectionWeighting.NONE)
        .build()
    );
    public final Setting<Boolean> only112 = sgAdvanced.add(new BoolSetting.Builder()
        .name("follow-only-1.12")
        .description("Will only follow 1.12 chunks and will ignore other ones.")
        .defaultValue(false)
        .build()
    );
    public final Setting<Double> chunkFoundTimeout = sgAdvanced.add(new DoubleSetting.Builder()
        .name("chunk-found-timeout")
        .description("The amount of MS without a chunk found to trigger circling.")
        .defaultValue(1000 * 5)
        .min(1000)
        .sliderMax(1000 * 10)
        .build()
    );
    public final Setting<Double> circlingDegPerTick = sgAdvanced.add(new DoubleSetting.Builder()
        .name("Circling-degrees-per-tick")
        .description("The amount of degrees to change per tick while circling.")
        .defaultValue(2.0)
        .min(1.0)
        .sliderMax(20.0)
        .build()
    );
    public final Setting<Double> trailTimeout = sgAdvanced.add(new DoubleSetting.Builder()
        .name("trail-timeout")
        .description("The amount of MS without a chunk found to stop following the trail.")
        .defaultValue(1000 * 30)
        .min(1000 * 10)
        .sliderMax(1000 * 60)
        .build()
    );
    public final Setting<Double> maxTrailDeviation = sgAdvanced.add(new DoubleSetting.Builder()
        .name("max-trail-deviation")
        .description("Maximum allowed angle (in degrees) from the original trail direction. Helps avoid switching to intersecting trails.")
        .defaultValue(180.0)
        .min(1.0)
        .sliderMax(270.0)
        .build()
    );
    public final Setting<Integer> chunkCacheLength = sgAdvanced.add(new IntSetting.Builder()
        .name("chunk-cache-length")
        .description("The amount of chunks to keep in the cache. (Won't be applied until deactivating)")
        .defaultValue(100_000)
        .sliderRange(0, 10_000_000)
        .build()
    );
    public final Setting<String> webhookLink = sgGeneral.add(new StringSetting.Builder()
        .name("webhook-link")
        .description("Will send all updates to the webhook link. Leave blank to disable.")
        .defaultValue("")
        .build()
    );
    public final Setting<Integer> baritoneUpdateTicks = sgAdvanced.add(new IntSetting.Builder()
        .name("baritone-path-update-ticks")
        .description("The amount of ticks between updates to the baritone goal. Low values may cause high instability.")
        .defaultValue(5 * 20)
        .sliderRange(20, 30 * 20)
        .build()
    );
    public final Setting<Boolean> debug = sgAdvanced.add(new BoolSetting.Builder()
        .name("debug")
        .description("Debug mode.")
        .defaultValue(false)
        .build()
    );
    private boolean oldAutoFireworkValue;
    private boolean oldAutoBoundAdjustValue;
    private FollowMode followMode;
    private boolean followingTrail = false;
    private ArrayDeque<Vec3d> trail = new ArrayDeque<>();
    private ArrayDeque<Vec3d> possibleTrail = new ArrayDeque<>();
    private long lastFoundTrailTime;
    private long lastFoundPossibleTrailTime;
    private double pathDistanceActual = pathDistance.get();
    private boolean started = false;
    private Cache<Long, Byte> seenChunksCache = Caffeine.newBuilder()
        .maximumSize(chunkCacheLength.get())
        .expireAfterWrite(Duration.ofMinutes(5))
        .build();
    public TrailFollower()
    {
        super(Bep.STASH, "TrailFollower", "Automatically follows trails in all dimensions.");
    }
    void resetTrail()
    {
        baritoneSetGoalTicks = 0;
        followingTrail = false;
        trail = new ArrayDeque<>();
        possibleTrail = new ArrayDeque<>();
    }
    @SuppressWarnings("unchecked")
    @Override
    public void onActivate()
    {
        resetTrail();
        XaeroPlus.EVENT_BUS.register(this);
        if (started)
        {
            if (mc.player != null && mc.world != null)
            {
                RegistryKey<World> currentDimension = mc.world.getRegistryKey();
                if (oppositeDimension.get())
                {
                    if (currentDimension.equals(World.END))
                    {
                        info("There is no opposite dimension to the end. Disabling TrailFollower");
                        this.toggle();
                        return;
                    }
                    else if (currentDimension.equals(World.NETHER))
                    {
                        info("Following overworld trails from the nether is not supported yet, sorry. Disabling TrailFollower");
                        this.toggle();
                        return;
                    }
                }
                if (flightMethod.get() != FollowMode.AUTO)
                {
                    followMode = flightMethod.get();
                }
                else
                {
                    if (!currentDimension.equals(World.NETHER))
                    {
                        followMode = FollowMode.YAWLOCK;
                        info("You are in the overworld or end, basic yaw mode will be used.");
                    }
                    else
                    {
                        try {
                            Class.forName("baritone.api.BaritoneAPI");
                            followMode = FollowMode.BARITONE;
                            info("You are in the nether, baritone mode will be used.");
                        } catch (ClassNotFoundException e) {
                            info("Baritone is required to trail follow in the nether. Disabling TrailFollower");
                            this.toggle();
                            return;
                        }
                    }
                }
                if (followMode == FollowMode.YAWLOCK && !mc.world.getRegistryKey().equals(World.NETHER)) {
                    if (overworldFlightMode.get() == OverworldFlightMode.PITCH40) {
                        Class<? extends Module> pitch40Util = Pitch40Util.class;
                        Module pitch40UtilModule = Modules.get().get(pitch40Util);
                        if (!pitch40UtilModule.isActive()) {
                            pitch40UtilModule.toggle();
                            if (pitch40Firework.get()) {
                                Setting<Boolean> setting = ((Setting<Boolean>) pitch40UtilModule.settings.get("auto-firework"));
                                if (setting != null) {
                                    info("Auto Firework enabled, if you want to change the velocity threshold or the firework cooldown check the settings under Pitch40Util.");
                                    oldAutoFireworkValue = setting.get();
                                    setting.set(true);
                                }
                            }
                            Setting<Boolean> autoBoundAdjustSetting = ((Setting<Boolean>) pitch40UtilModule.settings.get("auto-bound-adjust"));
                            if (autoBoundAdjustSetting != null) {
                                oldAutoBoundAdjustValue = autoBoundAdjustSetting.get();
                                autoBoundAdjustSetting.set(false);
                            }
                        }
                    } else if (overworldFlightMode.get() == OverworldFlightMode.VANILLA) {
                        AFKVanillaFly afkVanillaFly = Modules.get().get(AFKVanillaFly.class);
                        if (!afkVanillaFly.isActive()) {
                            afkVanillaFly.toggle();
                        }
                    }
                }
                Vec3d offset = (new Vec3d(Math.sin(-mc.player.getYaw() * Math.PI / 180), 0, Math.cos(-mc.player.getYaw() * Math.PI / 180)).normalize()).multiply(pathDistance.get());
                Vec3d targetPos = mc.player.getEntityPos().add(offset);
                for (int i = 0; i < (maxTrailLength.get() * startDirectionWeighting.get()); i++)
                {
                    trail.add(targetPos);
                }
                targetYaw = getActualYaw(mc.player.getYaw());
            }
            else
            {
                this.toggle();
            }
        }
    }
    @SuppressWarnings("unchecked")
    @Override
    public void onDeactivate()
    {
        started = false;
        seenChunksCache = Caffeine.newBuilder()
            .maximumSize(chunkCacheLength.get())
            .expireAfterWrite(Duration.ofMinutes(5))
            .build();
        XaeroPlus.EVENT_BUS.unregister(this);
        trail.clear();
        if (followMode == null) return;
        switch (followMode)
        {
            case BARITONE:
            {
                BaritoneAPI.getProvider().getPrimaryBaritone().getCommandManager().execute("cancel");
                break;
            }
            case YAWLOCK: {
                if (mc.world == null || mc.world.getRegistryKey().equals(World.NETHER)) return;
                if (overworldFlightMode.get() == OverworldFlightMode.VANILLA) {
                    AFKVanillaFly afkVanillaFly = Modules.get().get(AFKVanillaFly.class);
                    if (afkVanillaFly != null) {
                        afkVanillaFly.resetYLock();
                        if (afkVanillaFly.isActive()) afkVanillaFly.toggle();
                    }
                } else if (overworldFlightMode.get() == OverworldFlightMode.PITCH40) {
                    Class<? extends Module> pitch40Util = Pitch40Util.class;
                    Module pitch40UtilModule = Modules.get().get(pitch40Util);
                    if (pitch40UtilModule != null) {
                        if (pitch40UtilModule.isActive()) {
                            pitch40UtilModule.toggle();
                        }
                        Setting<Boolean> autoFireworkSetting = ((Setting<Boolean>) pitch40UtilModule.settings.get("auto-firework"));
                        if (autoFireworkSetting != null) {
                            autoFireworkSetting.set(oldAutoFireworkValue);
                        }
                        Setting<Boolean> autoBoundAdjustSetting = ((Setting<Boolean>) pitch40UtilModule.settings.get("auto-bound-adjust"));
                        if (autoBoundAdjustSetting != null) {
                            autoBoundAdjustSetting.set(oldAutoBoundAdjustValue);
                        }
                    }
                }
                break;
            }
        }
    }
    private double targetYaw;
    private int baritoneSetGoalTicks = 0;
    private void circle()
    {
        if (followMode == FollowMode.BARITONE) return;
        mc.player.setYaw(getActualYaw((float) (mc.player.getYaw() + circlingDegPerTick.get())));
        if (mc.player.age % 100 == 0)
        {
            log("Circling to look for new chunks, abandoning trail in " + (trailTimeout.get() - (System.currentTimeMillis() - lastFoundTrailTime)) / 1000 + " seconds.");
        }
    }
    @EventHandler
    private void onTick(TickEvent.Post event)
    {
        if (mc.player == null || mc.world == null) return;
        if (!started)
        {
            started = true;
            onActivate();
        }
        if (followingTrail && System.currentTimeMillis() - lastFoundTrailTime > trailTimeout.get())
        {
            resetTrail();
            log("Trail timed out, stopping.");
            switch (trailEndBehavior.get())
            {
                case DISABLE:
                {
                    this.toggle();
                    break;
                }
                case FLY_TOWARDS_YAW:
                {
                    targetYaw = trailEndYaw.get();
                    break;
                }
                case DISCONNECT:
                {
                    mc.player.networkHandler.onDisconnect(new DisconnectS2CPacket(Text.literal("[TrailFollower] Trail timed out.")));
                    break;
                }
            }
        }
        if (followingTrail && System.currentTimeMillis() - lastFoundTrailTime > chunkFoundTimeout.get())
        {
            circle();
            return;
        }
        switch (followMode)
        {
            case BARITONE:
            {
                if (baritoneSetGoalTicks > 0)
                {
                    baritoneSetGoalTicks--;
                }
                else if (baritoneSetGoalTicks == 0)
                {
                    baritoneSetGoalTicks = baritoneUpdateTicks.get();
                    if (mc.world.getRegistryKey().equals(World.NETHER)) {
                        if (!trail.isEmpty()) {
                            Vec3d baritoneTarget;
                            if (netherPathMode.get() == NetherPathMode.AVERAGE) {
                                Vec3d averagePos = calculateAveragePosition(trail);
                                Vec3d directionVec = averagePos.subtract(mc.player.getEntityPos()).normalize();
                                Vec3d predictedPos = mc.player.getEntityPos().add(directionVec.multiply(10));
                                targetYaw = Rotations.getYaw(predictedPos);
                                baritoneTarget = positionInDirection(mc.player.getEntityPos(), targetYaw, pathDistanceActual);
                            } else {
                                Vec3d lastPos = trail.getLast();
                                baritoneTarget = lastPos;
                            }
                            BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess()
                                .setGoalAndPath(new GoalXZ((int) baritoneTarget.x, (int) baritoneTarget.z));
                        }
                    } else {
                        Vec3d targetPos = positionInDirection(mc.player.getEntityPos(), targetYaw, pathDistanceActual);
                        BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess().setGoalAndPath(new GoalXZ((int) targetPos.x, (int) targetPos.z));
                        targetYaw = Rotations.getYaw(targetPos);
                    }
                    if (autoElytra.get() && (BaritoneAPI.getProvider().getPrimaryBaritone().getElytraProcess().currentDestination() == null))
                    {
                        BaritoneAPI.getSettings().elytraTermsAccepted.value = true;
                        BaritoneAPI.getProvider().getPrimaryBaritone().getCommandManager().execute("elytra");
                    }
                }
                break;
            }
            case YAWLOCK: {
                mc.player.setYaw(Utils.smoothRotation(getActualYaw(mc.player.getYaw()), targetYaw, rotateScaling.get()));
                break;
            }
        }
    }
    Vec3d posDebug;
    @EventHandler
    private void onRender(Render3DEvent event)
    {
        if (!debug.get()) return;
        Vec3d targetPos = positionInDirection(mc.player.getEntityPos(), targetYaw, 10);
        event.renderer.line(mc.player.getX(), mc.player.getY(), mc.player.getZ(), targetPos.x, targetPos.y, targetPos.z, new Color(255, 0, 0));
        if (posDebug != null) event.renderer.line(mc.player.getX(), mc.player.getY(), mc.player.getZ(), posDebug.x, targetPos.y, posDebug.z, new Color(0, 0, 255));
    }
    @net.lenni0451.lambdaevents.EventHandler(priority = -1)
    public void onChunkData(ChunkDataEvent event)
    {
        if (event.seenChunk()) return;
        RegistryKey<World> currentDimension = mc.world.getRegistryKey();
        WorldChunk chunk = event.chunk();
        ChunkPos chunkPos = chunk.getPos();
        long chunkLong = chunkPos.toLong();
        if (seenChunksCache.getIfPresent(chunkLong) != null) return;
        ChunkPos chunkDelta = new ChunkPos(chunkPos.x - mc.player.getChunkPos().x, chunkPos.z - mc.player.getChunkPos().z);
        if (oppositeDimension.get())
        {
            if (currentDimension.equals(World.OVERWORLD))
            {
                chunkPos = new ChunkPos(mc.player.getChunkPos().x / 8 + chunkDelta.x, mc.player.getChunkPos().z / 8 + chunkDelta.z);
                currentDimension = World.NETHER;
            }
            else if (currentDimension.equals(World.NETHER))
            {
                chunkPos = new ChunkPos(mc.player.getChunkPos().x * 8 + chunkDelta.x, mc.player.getChunkPos().z * 8 + chunkDelta.z);
                currentDimension = World.OVERWORLD;
            }
        }
        if (!isValidChunk(chunkPos, currentDimension)) return;
        seenChunksCache.put(chunkLong, Byte.MAX_VALUE);
        Vec3d pos = chunk.getPos().getCenterAtY(0).toCenterPos();
        posDebug = pos;
        if (!followingTrail)
        {
            if (System.currentTimeMillis() - lastFoundPossibleTrailTime > chunkConsiderationWindow.get() * 1000)
            {
                possibleTrail.clear();
            }
            possibleTrail.add(pos);
            lastFoundPossibleTrailTime = System.currentTimeMillis();
            if (possibleTrail.size() > chunksBeforeStarting.get())
            {
                log("Trail found, starting to follow.");
                followingTrail = true;
                lastFoundTrailTime = System.currentTimeMillis();
                trail.addAll(possibleTrail);
                possibleTrail.clear();
            }
            return;
        }
        double chunkAngle = Rotations.getYaw(pos);
        double angleDiff = Utils.angleDifference(targetYaw, chunkAngle);
        if (followingTrail && Math.abs(angleDiff) > maxTrailDeviation.get())
        {
            return;
        }
        lastFoundTrailTime = System.currentTimeMillis();
        while(trail.size() >= maxTrailLength.get())
        {
            trail.pollFirst();
        }
        if (angleDiff > 0 && angleDiff < 90 && directionWeighting.get() == DirectionWeighting.LEFT)
        {
            for (int i = 0; i < directionWeightingMultiplier.get() - 1; i++)
            {
                trail.pollFirst();
                trail.add(pos);
            }
            trail.add(pos);
        }
        else if (angleDiff < 0 && angleDiff > -90 && directionWeighting.get() == DirectionWeighting.RIGHT)
        {
            for (int i = 0; i < directionWeightingMultiplier.get() - 1; i++)
            {
                trail.pollFirst();
                trail.add(pos);
            }
            trail.add(pos);
        }
        else
        {
            trail.add(pos);
        }
        if (!trail.isEmpty()) {
            if (followMode == FollowMode.YAWLOCK) {
                Vec3d averagePos = calculateAveragePosition(trail);
                Vec3d positionVec = averagePos.subtract(mc.player.getEntityPos()).normalize();
                Vec3d targetPos = mc.player.getEntityPos().add(positionVec.multiply(10));
                targetYaw = Rotations.getYaw(targetPos);
            } else {
                Vec3d lastTrailPoint = trail.getLast();
                targetYaw = Rotations.getYaw(lastTrailPoint);
            }
        }
    }
    private boolean isValidChunk(ChunkPos chunkPos, RegistryKey<World> currentDimension)
    {
        PaletteNewChunks paletteNewChunks = ModuleManager.getModule(PaletteNewChunks.class);
        boolean is119NewChunk = paletteNewChunks
            .isNewChunk(
                chunkPos.x,
                chunkPos.z,
                currentDimension
            );
        boolean is112OldChunk = ModuleManager.getModule(OldChunks.class)
            .isOldChunk(
                chunkPos.x,
                chunkPos.z,
                currentDimension
            );
        boolean isHighlighted = is119NewChunk || paletteNewChunks
            .isInverseNewChunk(
                chunkPos.x,
                chunkPos.z,
                currentDimension
            );
        return isHighlighted && ((!is119NewChunk && !only112.get()) || is112OldChunk);
    }
    private Vec3d calculateAveragePosition(ArrayDeque<Vec3d> positions)
    {
        double sumX = 0, sumZ = 0;
        for (Vec3d pos : positions) {
            sumX += pos.x;
            sumZ += pos.z;
        }
        return new Vec3d(sumX / positions.size(), 0, sumZ / positions.size());
    }
    private float getActualYaw(float yaw)
    {
        return (yaw % 360 + 360) % 360;
    }
    private void log(String message)
    {
        info(message);
        if (!webhookLink.get().isEmpty())
        {
            sendWebhook(webhookLink.get(), "TrailFollower", message, null, mc.player.getGameProfile().name());
        }
    }
    public enum FollowMode
    {
        AUTO,
        BARITONE,
        YAWLOCK
    }
    public enum DirectionWeighting
    {
        LEFT,
        NONE,
        RIGHT
    }
    public enum TrailEndBehavior
    {
        DISABLE,
        FLY_TOWARDS_YAW,
        DISCONNECT
    }
}