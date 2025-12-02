package bep.hax.modules;
import bep.hax.mixin.accessor.PlayerInventoryAccessor;
import bep.hax.Bep;
import bep.hax.util.*;
import com.google.common.collect.Lists;
import meteordevelopment.meteorclient.events.entity.EntityAddedEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.renderer.text.TextRenderer;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.render.NametagUtils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ShapeContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ExperienceOrbEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.EndCrystalItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.s2c.play.ExplosionS2CPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.RaycastContext;
import org.joml.Vector3d;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
public class BepCrystal extends Module {
    SettingGroup sgPlace = settings.createGroup("Place");
    SettingGroup sgBasePlace = settings.createGroup("Base Place");
    SettingGroup sgBreak = settings.createGroup("Break");
    SettingGroup sgTargeting = settings.createGroup("Targeting");
    SettingGroup sgMisc = settings.createGroup("Misc");
    SettingGroup sgRender = settings.createGroup("Render");
    private final Setting<Boolean> place = SettingBuilder.booleanSetting(sgPlace, "Place Crystals", "Place Crystals automatically.", true);
    private final Setting<Boolean> placeRotate = SettingBuilder.booleanSetting(sgPlace, "PlaceRotate", "Rotate to placing end crystals", true);
    private final Setting<Double> placeDelay = SettingBuilder.doubleSetting(sgPlace, "Place Delay", "Delay between placing crystals (ms)", 0.0, 0.0, 1000.0);
    public final Setting<Double> placeRange = SettingBuilder.doubleSetting(sgPlace, "Place Range", "Range to place crystals", 4.0, 1.0, 6.0);
    private final Setting<Boolean> strictDirection = SettingBuilder.booleanSetting(sgPlace, "StrictDirection", "Place only on visible sides", false);
    private final Setting<SequentialModes> sequential = SettingBuilder.enumSetting(sgPlace, "Sequential", "", SequentialModes.Off);
    private final Setting<ForcePlaceModes> forcePlace = SettingBuilder.enumSetting(sgPlace, "ForcePlace", "Attempt to place on automine positions.", ForcePlaceModes.Off);
    private final Setting<Boolean> await = SettingBuilder.booleanSetting(sgPlace, "Await", "Wait for the crystal to be placed before attacking.", false);
    Setting<Boolean> basePlace = SettingBuilder.booleanSetting(sgBasePlace, "Base Place Crystals", "Place Crystals automatically.", true);
    private final Setting<Boolean> airBasePlace = SettingBuilder.booleanSetting(sgBasePlace, "Air", "Place blocks in air", true);
    private final Setting<Double> basePlaceDelay = SettingBuilder.doubleSetting(sgBasePlace, "BasePlace Delay", "Delay between placing obsidian", 10.0, 0.0, 1000.0);
    private final Setting<Double> basePlaceRange = SettingBuilder.doubleSetting(sgBasePlace, "BasePlace Range", "Range to place obsidian", 5.0, 1.0, 8.0);
    Setting<Boolean> breakCrystals = SettingBuilder.booleanSetting(sgBreak, "Break Crystals", "Break Crystals automatically.", true);
    private final Setting<AntiWeaknessModes> antiWeakness = SettingBuilder.enumSetting(sgBreak, "AntiWeakness", "Break crystals using sword if you have weakness", AntiWeaknessModes.Off);
    private final Setting<Boolean> breakRotate = SettingBuilder.booleanSetting(sgBreak, "BreakRotate", "Rotate to breaking end crystals", true);
    private final Setting<Double> breakDelay = SettingBuilder.doubleSetting(sgBreak, "Break Delay", "Delay between breaking crystals (ms)", 0.0f, 0.0f, 1000.0f);
    private final Setting<Double> breakRange = SettingBuilder.doubleSetting(sgBreak, "Break Range", "Range to break crystals", 4.0f, 1.0f, 6.0f);
    private final Setting<InstantModes> instant = SettingBuilder.enumSetting(sgBreak, "Instant", "Break crystals without delay", InstantModes.Off);
    private final Setting<Boolean> inhibit = SettingBuilder.booleanSetting(sgBreak, "Inhibit", "Prevent excessive attacks", false);
    private final Setting<Boolean> multitask = SettingBuilder.booleanSetting(sgMisc, "Multitask", "Allow actions while eating", false);
    private final Setting<SwapModes> swap = SettingBuilder.enumSetting(sgMisc, "Swap", "Swap end crystals with the target.", SwapModes.Off);
    private final Setting<Boolean> swing = SettingBuilder.booleanSetting(sgMisc, "Swing", "Swing hand", false);
    private final Setting<Boolean> oldVersion = SettingBuilder.booleanSetting(sgMisc, "1.12", "Check for air above place spots", false);
    private final Setting<Double> targetRange = SettingBuilder.doubleSetting(sgTargeting, "Target Range", "Range to target players from", 8.0f, 1.0f, 15.0f);
    private final Setting<Boolean> blockDestruction = SettingBuilder.booleanSetting(sgTargeting, "BlockDestruction", "Ignore almost broken blocks", false);
    private final Setting<Double> minDamage = SettingBuilder.doubleSetting(sgTargeting, "MinDamage", "Minimum damage to place a crystal", 4.0f, 0.0f, 20.0f);
    private final Setting<Double> maxSelfDamage = SettingBuilder.doubleSetting(sgTargeting, "MaxSelfDamage", "Maximum self damage to place a crystal", 10.0f, 0.0f, 20.0f);
    private final Setting<Boolean> lethal = SettingBuilder.booleanSetting(sgTargeting, "Lethal", "Ignore maximum self if the damage is enough to kill the target", false);
    private final Setting<Integer> extrapolation = SettingBuilder.intSetting(sgTargeting, "Extrapolation", "Ticks to compensate for player movement (1-4 recommended for low-mid ping)", 0, 0, 20);
    Setting<Boolean> render = SettingBuilder.booleanSetting(sgRender, "Render", "Render Crystal Placements.", true);
    private final Setting<SettingColor> fill = SettingBuilder.colorSetting(sgRender, "Fill", "Color of the box fill", new Color(0, 255, 255, 50));
    private final Setting<SettingColor> line = SettingBuilder.colorSetting(sgRender, "Line", "Color of the box outline", new Color(Color.CYAN));
    private final Setting<Boolean> damageRender = SettingBuilder.booleanSetting(sgRender, "DamageRender", "Render the damage at the box.", true);
    private final Setting<Boolean> movement = SettingBuilder.booleanSetting(sgRender, "Movement", "Render the movement of the box.", true);
    public BepCrystal() {
        super(Bep.CATEGORY, "BepCrystal", "Automatically place and break end crystals to deal damage to enemies. -jaxui");
    }
    @Override
    public void onDeactivate() {
        RotationUtils.getInstance().clearRotationsByPriority(ROTATION_PRIORITY);
        RotationUtils.getInstance().clearRotationsByPriority(ROTATION_PRIORITY + 10);
        if (InventoryManager.getInstance().isDesynced()) {
            InventoryManager.getInstance().syncToClient();
        }
        targetInfo = null;
        renderPos = null;
    }
    private final CacheTimer basePlaceTimer = new CacheTimer();
    private final CacheTimer placeTimer = new CacheTimer();
    private final CacheTimer breakTimer = new CacheTimer();
    private final CacheTimer swapTimer = new CacheTimer();
    private final Deque<Long> attackLatency = new EvictingQueue<>(20);
    public TargetInfo targetInfo = null;
    private Vec3d renderPos = null;
    private Vec3d prevRenderPos = null;
    private Vec3d lerpRenderPos = null;
    private final Queue<Long> explosionTimes = new ConcurrentLinkedQueue<>();
    private int currentCPS = 0;
    private final Map<Integer, Integer> antiStuckCrystals = new HashMap<>();
    private final List<AntiStuckData> stuckCrystals = new CopyOnWriteArrayList<>();
    private static final int ROTATION_PRIORITY = 90;
    private boolean sync = false;
    @EventHandler
    public void onPlayerUpdate(TickEvent.Pre event) {
        if (mc.world == null || mc.player == null) return;
        update();
    }
    private void update() {
        if (mc.player.getInventory().count(Items.END_CRYSTAL) == 0) {
            targetInfo = null;
            renderPos = null;
            return;
        }
        Surround surroundModule = Modules.get().get(Surround.class);
        if (surroundModule != null && surroundModule.isActive() && surroundModule.isPlacing()) {
            targetInfo = null;
            renderPos = null;
            return;
        }
        if (basePlace.get() && !isPlacing() && getTarget() != null && getCrystalBase(getTarget()) != null) {
            if (!checkMultitask(true)) {
                BlockPos crystalBase = getCrystalBase(getTarget());
                BlockState state = mc.world.getBlockState(crystalBase);
                int slot = getResistantBlockItem();
                if (slot != -1 && state.isReplaceable() && basePlaceTimer.passed(basePlaceDelay.get().longValue())) {
                    placeBlock(crystalBase, slot);
                }
            }
        }
        ArrayList<Entity> entities = Lists.newArrayList(mc.world.getEntities());
        List<BlockPos> blocks = getSphere(mc.player.getEntityPos());
        DamageData<BlockPos> placeCrystal = calculatePlaceCrystal(blocks, entities);
        DamageData<EndCrystalEntity> breakCrystal = calculateAttackCrystal(entities);
        if (placeCrystal != null && place.get()) {
            targetInfo = new TargetInfo(placeCrystal.damageData, (PlayerEntity) placeCrystal.attackTarget, (float) placeCrystal.damage, (float) placeCrystal.selfDamage, false);
            renderPos = placeCrystal.getBlockPos().toCenterPos();
            placeCrystal(placeCrystal.damageData);
        } else targetInfo = null;
        if (breakCrystal != null && breakCrystals.get()) {
            breakCrystal(breakCrystal.damageData);
        }
        if (sync) {
            RotationUtils.getInstance().setRotationSilentSync();
            sync = false;
        }
    }
    @EventHandler
    public void onRender3D(Render3DEvent event) {
        if (render.get() && renderPos != null && targetInfo != null) {
            if (prevRenderPos == null) {
                prevRenderPos = renderPos;
                lerpRenderPos = renderPos;
            }
            if (movement.get()) {
                lerpRenderPos = new Vec3d(MathHelper.lerp(0.15f / 10f, lerpRenderPos.x, renderPos.x), MathHelper.lerp(0.15f / 10f, lerpRenderPos.y, renderPos.y), MathHelper.lerp(0.15f / 10f, lerpRenderPos.z, renderPos.z));
            } else {
                lerpRenderPos = renderPos;
            }
            Box renderBox = new Box(lerpRenderPos.x - 0.5, lerpRenderPos.y + 0.5, lerpRenderPos.z - 0.5, lerpRenderPos.x + 0.5, lerpRenderPos.y - 0.5, lerpRenderPos.z + 0.5);
            event.renderer.box(renderBox, fill.get(), line.get(), ShapeMode.Both, 0);
            if (damageRender.get() && targetInfo != null) {
                NametagUtils.begin(new Vector3d(lerpRenderPos.x, lerpRenderPos.y - 0.25, lerpRenderPos.z));
                TextRenderer.get().begin(1.05, false, false);
                String text = String.format("%.1f", targetInfo.damage);
                double w = TextRenderer.get().getWidth(text) / 2;
                TextRenderer.get().render(text, -w, 0, Color.WHITE, true);
                TextRenderer.get().end();
                NametagUtils.end();
            }
            prevRenderPos = renderPos;
        } else {
            prevRenderPos = null;
            lerpRenderPos = null;
        }
    }
    @EventHandler
    public void onReceivePacket(PacketEvent.Receive event) {
        if (event.packet instanceof ExplosionS2CPacket) {
            explosionTimes.offer(System.currentTimeMillis());
            updateCPS();
        }
    }
    @EventHandler
    public void onAddEntity(EntityAddedEvent event) {
        if (!(event.entity instanceof EndCrystalEntity crystalEntity)) {
            return;
        }
        boolean attackRotate = targetInfo != null && breakDelay.get().floatValue() <= 0.0 && breakTimer.passed(breakDelay.get().longValue());
        if (attackRotate) {
            if (sequential.get() != SequentialModes.Off) {
                breakCrystalInternal(crystalEntity);
            }
            if (sequential.get() == SequentialModes.Strong && (mc.getNetworkHandler().getServerInfo() != null && !mc.getNetworkHandler().getServerInfo().address.equalsIgnoreCase("2b2t.org"))) {
                placeSequentialCrystal(crystalEntity.getBlockPos().down());
            }
        }
    }
    @Override
    public String getInfoString() {
        if (targetInfo == null) return "";
        updateCPS();
        return currentCPS + " CPS, " + targetInfo.target.getName().getLiteralString();
    }
    private void updateCPS() {
        if (explosionTimes.isEmpty()) return;
        long currentTime = System.currentTimeMillis();
        while (!explosionTimes.isEmpty() && explosionTimes.peek() < currentTime - 1000) {
            explosionTimes.poll();
        }
        currentCPS = explosionTimes.size();
    }
    private DamageData<BlockPos> calculatePlaceCrystal(List<BlockPos> placeBlocks, List<Entity> entities) {
        if (placeBlocks.isEmpty() || entities.isEmpty()) {
            return null;
        }
        final List<DamageData<BlockPos>> validData = new ArrayList<>();
        DamageData<BlockPos> data = null;
        for (BlockPos pos : placeBlocks) {
            if (!canUseCrystalOnBlock(pos) || placeRangeCheck(pos)) {
                continue;
            }
            double selfDamage = ExplosionUtil.getDamageTo(mc.player, crystalDamageVec(pos), blockDestruction.get(), extrapolation.get().intValue(), false);
            boolean unsafeToPlayer = playerDamageCheck(selfDamage);
            if (unsafeToPlayer && !lethal.get()) {
                continue;
            }
            for (Entity entity : entities) {
                if (entity == null || !entity.isAlive() || entity == mc.player || !entity.isAlive() || !(entity instanceof PlayerEntity) || Friends.get().isFriend((PlayerEntity) entity)) {
                    continue;
                }
                double blockDist = pos.getSquaredDistance(entity.getEntityPos());
                if (blockDist > 144.0f) {
                    continue;
                }
                double dist = mc.player.squaredDistanceTo(entity);
                if (dist > targetRange.get().floatValue() * targetRange.get().floatValue()) {
                    continue;
                }
                boolean antiSurrounda = false;
                if (forcePlace.get() != ForcePlaceModes.Off && entity instanceof PlayerEntity player && !BlastResistantBlocks.isUnbreakable(player.getBlockPos())) {
                    Set<BlockPos> miningPositions = new HashSet<>();
                    BlockPos miningBlock = Modules.get().get(BepMine.class).getLastAutoMineBlock();
                    if (Modules.get().get(BepMine.class).isActive() && miningBlock != null) {
                        miningPositions.add(miningBlock);
                    }
                    for (BlockPos miningBlockPos : miningPositions) {
                        if (!Modules.get().get(Surround.class).calculateSurround(player).contains(miningBlockPos)) {
                            continue;
                        }
                        for (Direction direction : Direction.values()) {
                            BlockPos pos1 = miningBlockPos.offset(direction);
                            if (pos.equals(pos1.down())) {
                                antiSurrounda = true;
                            }
                        }
                    }
                }
                double damage;
                damage = ExplosionUtil.getDamageTo(entity, crystalDamageVec(pos), blockDestruction.get(), extrapolation.get().intValue(), true);
                if (checkOverrideSafety(unsafeToPlayer, damage, entity)) {
                    continue;
                }
                DamageData<BlockPos> currentData = new DamageData<>(pos, entity, damage, selfDamage, antiSurrounda);
                validData.add(currentData);
                if (data == null || damage > data.getDamage()) {
                    data = currentData;
                }
            }
        }
        if (data == null || targetDamageCheck(data)) {
            if (forcePlace.get() != ForcePlaceModes.Off) {
                return validData.stream().filter(DamageData::isAntiSurround).min(Comparator.comparingDouble(d -> mc.player.squaredDistanceTo(d.getBlockPos().toCenterPos()))).orElse(null);
            }
            return null;
        }
        return data;
    }
    private DamageData<EndCrystalEntity> calculateAttackCrystal(List<Entity> entities) {
        if (entities.isEmpty()) {
            return null;
        }
        final List<DamageData<EndCrystalEntity>> validData = new ArrayList<>();
        DamageData<EndCrystalEntity> data = null;
        for (Entity crystal : entities) {
            if (!(crystal instanceof EndCrystalEntity crystal1) || !crystal.isAlive()) {
                continue;
            }
            boolean attacked = crystal.age < getBreakMs();
            if (attacked && inhibit.get()) {
                continue;
            }
            if (attackRangeCheck(crystal1)) {
                continue;
            }
            double selfDamage = ExplosionUtil.getDamageTo(mc.player, crystal.getEntityPos(), blockDestruction.get(), extrapolation.get().intValue(), false);
            boolean unsafeToPlayer = playerDamageCheck(selfDamage);
            if (unsafeToPlayer && !lethal.get()) {
                continue;
            }
            for (Entity entity : entities) {
                if (entity == null || !entity.isAlive() || entity == mc.player || !(entity instanceof PlayerEntity) || Friends.get().isFriend((PlayerEntity) entity)) {
                    continue;
                }
                double crystalDist = crystal.squaredDistanceTo(entity);
                if (crystalDist > 144.0f) {
                    continue;
                }
                double dist = mc.player.squaredDistanceTo(entity);
                if (dist > targetRange.get().floatValue() * targetRange.get().floatValue()) {
                    continue;
                }
                boolean antiSurround = false;
                if (forcePlace.get() != ForcePlaceModes.Off && entity instanceof PlayerEntity player && !BlastResistantBlocks.isUnbreakable(player.getBlockPos())) {
                    Set<BlockPos> miningPositions = new HashSet<>();
                    BlockPos miningBlock = Modules.get().get(BepMine.class).getLastAutoMineBlock();
                    if (Modules.get().get(BepMine.class).isActive() && miningBlock != null) {
                        miningPositions.add(miningBlock);
                    }
                    for (BlockPos miningBlockPos : miningPositions) {
                        if (!Modules.get().get(Surround.class).calculateSurround(player).contains(miningBlockPos)) {
                            continue;
                        }
                        for (Direction direction : Direction.values()) {
                            BlockPos pos1 = miningBlockPos.offset(direction);
                            if (crystal.getBlockPos().equals(pos1.down())) {
                                antiSurround = true;
                            }
                        }
                    }
                }
                double damage = ExplosionUtil.getDamageTo(entity, crystal.getEntityPos(), blockDestruction.get(), extrapolation.get().intValue(), true);
                if (checkOverrideSafety(unsafeToPlayer, damage, entity)) {
                    continue;
                }
                DamageData<EndCrystalEntity> currentData = new DamageData<>(crystal1, entity, damage, selfDamage, crystal1.getBlockPos().down(), antiSurround);
                validData.add(currentData);
                if (data == null || damage > data.getDamage()) {
                    data = currentData;
                }
            }
        }
        if (data == null || targetDamageCheck(data)) {
            if (forcePlace.get() != ForcePlaceModes.Off) {
                return validData.stream().filter(DamageData::isAntiSurround).min(Comparator.comparingDouble(d -> mc.player.squaredDistanceTo(d.getBlockPos().toCenterPos()))).orElse(null);
            }
            return null;
        }
        return data;
    }
    public boolean canUseCrystalOnBlock(BlockPos pos) {
        BlockState state = mc.world.getBlockState(pos);
        if (!state.isOf(Blocks.OBSIDIAN) && !state.isOf(Blocks.BEDROCK)) {
            return false;
        }
        return isCrystalHitboxClear(pos);
    }
    private boolean targetDamageCheck(DamageData<?> crystal) {
        double minDmg = minDamage.get().floatValue();
        if (crystal.getAttackTarget() instanceof LivingEntity entity && isCrystalLethalTo(crystal, entity)) {
            minDmg = 2.0f;
        }
        return crystal.getDamage() < minDmg;
    }
    private boolean playerDamageCheck(double playerDamage) {
        if (!mc.player.isCreative()) {
            float health = mc.player.getHealth() + mc.player.getAbsorptionAmount();
            if (playerDamage >= health + 0.5f) {
                return true;
            }
            return playerDamage > maxSelfDamage.get().floatValue();
        }
        return false;
    }
    private boolean isCrystalLethalTo(DamageData<?> crystal, LivingEntity entity) {
        return isCrystalLethalTo(crystal.getDamage(), entity);
    }
    private boolean isCrystalLethalTo(double damage, LivingEntity entity) {
        float health = entity.getHealth() + entity.getAbsorptionAmount();
        if (damage * (2.5f) >= health + 0.5f) {
            return true;
        }
        java.util.List<ItemStack> armorStacks = java.util.Arrays.asList(
            entity.getEquippedStack(net.minecraft.entity.EquipmentSlot.FEET),
            entity.getEquippedStack(net.minecraft.entity.EquipmentSlot.LEGS),
            entity.getEquippedStack(net.minecraft.entity.EquipmentSlot.CHEST),
            entity.getEquippedStack(net.minecraft.entity.EquipmentSlot.HEAD)
        );
        for (ItemStack armorStack : armorStacks) {
            int n = armorStack.getDamage();
            int n1 = armorStack.getMaxDamage();
            float durability = ((n1 - n) / (float) n1) * 100.0f;
            if (durability < 5.0f) {
                return true;
            }
        }
        return false;
    }
    private boolean checkOverrideSafety(boolean unsafeToPlayer, double damage, Entity entity) {
        return lethal.get() && unsafeToPlayer && damage < EntityUtil.getHealth(entity) + 0.5;
    }
    private boolean placeRangeCheck(BlockPos pos) {
        double placeR = placeRange.get().floatValue();
        Vec3d player = mc.player.getEntityPos();
        double dist = pos.getSquaredDistance(player.x, player.y, player.z);
        if (dist > placeR * placeR) {
            return true;
        }
        Vec3d raytrac = Vec3d.of(pos).add(0.5, 2.70000004768372, 0.5);
        BlockHitResult result = mc.world.raycast(new RaycastContext(mc.player.getEyePos(), raytrac, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, mc.player));
        return false;
    }
    public boolean isCrystalHitboxClear(BlockPos pos) {
        BlockPos p2 = pos.up();
        BlockState state2 = mc.world.getBlockState(p2);
        if (oldVersion.get() && !mc.world.isAir(p2.up())) {
            return false;
        }
        if (!mc.world.isAir(p2) && !state2.isOf(Blocks.FIRE)) {
            return false;
        } else {
            final Box bb = new Box(0.0, 0.0, 0.0, 1.0, 2.0, 1.0);
            double d = p2.getX();
            double e = p2.getY();
            double f = p2.getZ();
            List<Entity> list = getEntitiesBlockingCrystal(new Box(d, e, f, d + bb.maxX, e + bb.maxY, f + bb.maxZ));
            return list.isEmpty();
        }
    }
    private List<Entity> getEntitiesBlockingCrystal(Box box) {
        List<Entity> entities = new CopyOnWriteArrayList<>(mc.world.getOtherEntities(null, box));
        for (Entity entity : entities) {
            if (entity == null || !entity.isAlive() || entity instanceof ExperienceOrbEntity || forcePlace.get() != ForcePlaceModes.Off && entity instanceof ItemEntity && entity.age <= 10) {
                entities.remove(entity);
            } else if (entity instanceof EndCrystalEntity entity1 && entity1.getBoundingBox().intersects(box)) {
                Integer antiStuckAttacks = antiStuckCrystals.get(entity1.getId());
                if (!attackRangeCheck(entity1) && (antiStuckAttacks == null || antiStuckAttacks <= 1.5f * 10.0f)) {
                    entities.remove(entity);
                } else {
                    double dist = mc.player.squaredDistanceTo(entity1);
                    stuckCrystals.add(new AntiStuckData(entity1.getId(), entity1.getBlockPos(), entity1.getEntityPos(), dist));
                }
            }
        }
        return entities;
    }
    private java.util.List<BlockPos> getSphere(Vec3d origin) {
        double rad = Math.ceil(placeRange.get().floatValue());
        List<BlockPos> sphere = new ArrayList<>();
        for (double x = -rad; x <= rad; ++x) {
            for (double y = -rad; y <= rad; ++y) {
                for (double z = -rad; z <= rad; ++z) {
                    Vec3i pos = new Vec3i((int) (origin.getX() + x), (int) (origin.getY() + y), (int) (origin.getZ() + z));
                    final BlockPos p = new BlockPos(pos);
                    sphere.add(p);
                }
            }
        }
        return sphere;
    }
    private PlayerEntity getTarget() {
        return mc.world.getPlayers().stream().filter(player -> player != mc.player).filter(player -> !player.isSpectator()).filter(player -> !Friends.get().isFriend(player)).filter(player -> mc.player.distanceTo(player) <= targetRange.get().floatValue() && !player.isDead()).min(Comparator.comparingDouble(e -> mc.player.squaredDistanceTo(e))).orElse(null);
    }
    public void breakCrystal(EndCrystalEntity crystal) {
        if (checkMultitask()) return;
        if (instant.get() == InstantModes.Strict && !breakTimer.passed(10L)) return;
        if (!breakTimer.passed(breakDelay.get().longValue())) return;
        boolean swapBack = false;
        FindItemResult swordResult = getSword();
        if (mc.player.getStatusEffects().contains(StatusEffects.WEAKNESS) && !mc.player.getStatusEffects().contains(StatusEffects.STRENGTH) && antiWeakness.get() != AntiWeaknessModes.Off && swordResult.found()) {
            if (antiWeakness.get().name().equals("Normal")) {
                InventoryManager.getInstance().setClientSlot(swordResult.slot());
            } else {
                InventoryManager.getInstance().setSlot(swordResult.slot());
            }
            swapBack = true;
        }
        breakCrystalInternal(crystal);
        if (swapBack) InventoryManager.getInstance().syncToClient();
        breakTimer.reset();
    }
    public void breakCrystalInternal(EndCrystalEntity crystal) {
        EndCrystalEntity entity = new EndCrystalEntity(mc.world, 0, 0, 0);
        entity.setId(crystal.getId());
        PlayerInteractEntityC2SPacket packet = PlayerInteractEntityC2SPacket.attack(crystal, mc.player.isSneaking());
        if (breakRotate.get()) {
            float[] rotations = RotationUtils.getRotationsTo(mc.player.getEntityPos(), crystal.getEyePos());
            RotationUtils.getInstance().setRotationSilent(rotations[0], rotations[1], ROTATION_PRIORITY);
            sync = true;
        }
        mc.getNetworkHandler().sendPacket(packet);
        if (swing.get()) mc.player.swingHand(Hand.MAIN_HAND);
        else mc.getNetworkHandler().sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));
    }
    public void placeCrystal(BlockPos blockPos) {
        if (checkMultitask()) return;
        if (!placeTimer.passed(placeDelay.get().longValue())) return;
        if (await.get() && mc.world.getOtherEntities(null, new Box(blockPos.up())).stream().anyMatch(entity -> entity instanceof EndCrystalEntity))
            return;
        Direction sidePlace = getPlaceDirection(blockPos);
        BlockHitResult result = new BlockHitResult(blockPos.toCenterPos(), sidePlace, blockPos, false);
        if (swap.get() == SwapModes.Off && !isHoldingCrystal()) {
            return;
        }
        FindItemResult crystalResult = InvUtils.findInHotbar(Items.END_CRYSTAL);
        if (swap.get() != SwapModes.Off) {
            if (!crystalResult.found() || (swap.get() == SwapModes.Silent && mc.player.getInventory().count(Items.END_CRYSTAL) == 0)) {
                return;
            }
            boolean canSwap = crystalResult.slot() != ((PlayerInventoryAccessor) mc.player.getInventory()).getSelectedSlot() && (swap.get() != SwapModes.Client || swapTimer.passed(500));
            if (canSwap) {
                if (swap.get() == SwapModes.Silent) {
                    InventoryManager.getInstance().setSlotForced(crystalResult.slot());
                } else {
                    InventoryManager.getInstance().setClientSlot(crystalResult.slot());
                }
                placeInternal(result, getCrystalHand());
                placeTimer.reset();
                if (swap.get() == SwapModes.Silent) {
                    InventoryManager.getInstance().syncToClient();
                }
            } else {
                placeInternal(result, getCrystalHand());
                placeTimer.reset();
            }
        } else {
            placeInternal(result, getCrystalHand());
            placeTimer.reset();
        }
    }
    private void placeInternal(BlockHitResult result, Hand hand) {
        if (placeRotate.get()) {
            float[] rotations = RotationUtils.getRotationsTo(mc.player.getEntityPos(), result.getBlockPos().toCenterPos());
            RotationUtils.getInstance().setRotationSilent(rotations[0], rotations[1], ROTATION_PRIORITY);
            sync = true;
        }
        mc.interactionManager.interactBlock(mc.player, hand, result);
        if (swing.get()) mc.player.swingHand(hand);
        else mc.getNetworkHandler().sendPacket(new HandSwingC2SPacket(hand));
    }
    public void placeCrystalForTarget(PlayerEntity target, BlockPos blockPos) {
        if (target == null || target.isDead() || placeRangeCheck(blockPos) || !canUseCrystalOnBlock(blockPos)) {
            return;
        }
        double selfDamage = ExplosionUtil.getDamageTo(mc.player, crystalDamageVec(blockPos), blockDestruction.get(), Set.of(blockPos), extrapolation.get().intValue(), false);
        if (playerDamageCheck(selfDamage)) {
            return;
        }
        double damage = ExplosionUtil.getDamageTo(target, crystalDamageVec(blockPos), blockDestruction.get(), Set.of(blockPos), extrapolation.get().intValue(), true);
        if (damage < minDamage.get().floatValue() && !isCrystalLethalTo(damage, target) || targetInfo != null && targetInfo.damage >= damage) {
            return;
        }
        float[] rotations = RotationUtils.getRotationsTo(mc.player.getEntityPos(), blockPos.toCenterPos());
        RotationUtils.getInstance().setRotationSilent(rotations[0], rotations[1], ROTATION_PRIORITY + 10);
        sync = true;
        placeCrystal(blockPos);
    }
    private void placeSequentialCrystal(BlockPos blockPos) {
        if (targetInfo == null) return;
        if (sequential.get() == SequentialModes.Off) return;
        if (sequential.get() == SequentialModes.Strong) {
            placeCrystal(targetInfo.pos);
        } else if (sequential.get() == SequentialModes.Strict) {
            if (mc.getNetworkHandler().getServerInfo() != null && !mc.getNetworkHandler().getServerInfo().address.equalsIgnoreCase("2b2t.org")) {
                placeCrystal(targetInfo.pos);
            }
        }
    }
    private Direction getPlaceDirection(BlockPos blockPos) {
        int x = blockPos.getX();
        int y = blockPos.getY();
        int z = blockPos.getZ();
        if (strictDirection.get()) {
            if (mc.player.getY() >= blockPos.getY()) {
                return Direction.UP;
            }
            BlockHitResult result = mc.world.raycast(new RaycastContext(mc.player.getEyePos(), new Vec3d(x + 0.5, y + 0.5, z + 0.5), RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, mc.player));
            if (result != null && result.getType() == HitResult.Type.BLOCK) {
                return result.getSide();
            }
        } else {
            if (mc.world.isInBuildLimit(blockPos)) {
                return Direction.DOWN;
            }
            BlockHitResult result = mc.world.raycast(new RaycastContext(mc.player.getEyePos(), new Vec3d(x + 0.5, y + 0.5, z + 0.5), RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, mc.player));
            if (result != null && result.getType() == HitResult.Type.BLOCK) {
                return result.getSide();
            }
        }
        return Direction.UP;
    }
    private boolean attackRangeCheck(EndCrystalEntity entity) {
        return attackRangeCheck(entity.getEntityPos());
    }
    private boolean attackRangeCheck(Vec3d entityPos) {
        double breakR = breakRange.get().floatValue();
        Vec3d playerPos = mc.player.getEyePos();
        double dist = playerPos.distanceTo(entityPos);
        if (dist > breakR) {
            return true;
        }
        return false;
    }
    private boolean isHoldingCrystal() {
        if (!checkCanUseCrystal() && (swap.get() == SwapModes.Silent)) {
            return true;
        }
        return getCrystalHand() != null;
    }
    private boolean checkCanUseCrystal() {
        return !multitask.get() && checkMultitask();
    }
    public boolean checkMultitask() {
        return checkMultitask(false);
    }
    public boolean checkMultitask(boolean checkOffhand) {
        if (checkOffhand && mc.player.getActiveHand() != Hand.MAIN_HAND) {
            return false;
        }
        return mc.player.isUsingItem();
    }
    private Hand getCrystalHand() {
        final ItemStack offhand = mc.player.getOffHandStack();
        if (offhand.getItem() instanceof EndCrystalItem) {
            return Hand.OFF_HAND;
        }
        return Hand.MAIN_HAND;
    }
    public boolean isPlacing() {
        return targetInfo != null && isHoldingCrystal();
    }
    private BlockPos getCrystalBase(PlayerEntity player) {
        List<BlockPos> targetBlocks = getSphere(mc.player.getEyePos());
        double damage = 0.0f;
        BlockPos crystalBase = null;
        for (BlockPos pos : targetBlocks) {
            final BlockPos basePos = pos.down();
            if (basePos.getY() >= EntityUtil.getRoundedBlockPos(player).getY()) {
                continue;
            }
            if (mc.world.isOutOfHeightLimit(basePos)) {
                continue;
            }
            if (mc.getNetworkHandler().getServerInfo() != null && mc.getNetworkHandler().getServerInfo().address.equalsIgnoreCase("grim.crystalpvp.cc") && basePos.getY() >= 100) {
                continue;
            }
            if (!isCrystalHitboxClear(pos)) {
                continue;
            }
            double dist = mc.player.squaredDistanceTo(basePos.toCenterPos());
            if (dist > basePlaceRange.get().floatValue() * basePlaceRange.get().floatValue()) {
                continue;
            }
            double dmg1 = ExplosionUtil.getDamageTo(player, pos.toCenterPos(), true);
            if (dmg1 < minDamage.get().floatValue()) {
                continue;
            }
            if (PlacementUtils.getPlaceSide(basePos) == null) {
                continue;
            }
            if (!mc.world.canPlace(Blocks.OBSIDIAN.getDefaultState(), basePos, ShapeContext.absent())) {
                continue;
            }
            if (dmg1 > damage) {
                crystalBase = basePos;
                damage = dmg1;
            }
        }
        return crystalBase;
    }
    protected int getResistantBlockItem() {
        FindItemResult obsidianResult = InvUtils.findInHotbar(Items.OBSIDIAN);
        FindItemResult cryingResult = InvUtils.findInHotbar(Items.CRYING_OBSIDIAN);
        FindItemResult enderChestResult = InvUtils.findInHotbar(Items.ENDER_CHEST);
        if (obsidianResult.found()) {
            return obsidianResult.slot();
        } else if (cryingResult.found()) {
            return cryingResult.slot();
        } else if (enderChestResult.found()) {
            return enderChestResult.slot();
        }
        return -1;
    }
    private void placeBlock(BlockPos pos, int slot) {
        float[] rotations = RotationUtils.getRotationsTo(mc.player.getEntityPos(), pos.toCenterPos());
        RotationUtils.getInstance().setRotationSilent(rotations[0], rotations[1], ROTATION_PRIORITY);
        InventoryManager.getInstance().setSlot(slot);
        PlacementUtils.placeBlock(pos, new FindItemResult(slot, 64), false, true, true);
        InventoryManager.getInstance().syncToClient();
        RotationUtils.getInstance().setRotationSilentSync();
        basePlaceTimer.reset();
    }
    public FindItemResult getSword() {
        FindItemResult nethResult = InvUtils.findInHotbar(Items.NETHERITE_SWORD);
        FindItemResult diamondResult = InvUtils.findInHotbar(Items.DIAMOND_SWORD);
        if (nethResult.found()) {
            return nethResult;
        } else if (diamondResult.found()) {
            return diamondResult;
        }
        return null;
    }
    private Vec3d crystalDamageVec(BlockPos pos) {
        return Vec3d.of(pos).add(0.5, 1.0, 0.5);
    }
    private static class DamageData<T> {
        private T damageData;
        private Entity attackTarget;
        private BlockPos blockPos;
        private double damage, selfDamage;
        private boolean antiSurround;
        @SuppressWarnings("unchecked")
        public DamageData(BlockPos damageData, Entity attackTarget, double damage, double selfDamage, boolean antiSurround) {
            this.damageData = (T) damageData;
            this.attackTarget = attackTarget;
            this.damage = damage;
            this.selfDamage = selfDamage;
            this.blockPos = damageData;
            this.antiSurround = antiSurround;
        }
        public DamageData(T damageData, Entity attackTarget, double damage, double selfDamage, BlockPos blockPos, boolean antiSurround) {
            this.damageData = damageData;
            this.attackTarget = attackTarget;
            this.damage = damage;
            this.selfDamage = selfDamage;
            this.blockPos = blockPos;
            this.antiSurround = antiSurround;
        }
        public void setDamageData(T damageData, Entity attackTarget, double damage, double selfDamage) {
            this.damageData = damageData;
            this.attackTarget = attackTarget;
            this.damage = damage;
            this.selfDamage = selfDamage;
        }
        public T getDamageData() {
            return damageData;
        }
        public Entity getAttackTarget() {
            return attackTarget;
        }
        public double getDamage() {
            return damage;
        }
        public double getSelfDamage() {
            return selfDamage;
        }
        public BlockPos getBlockPos() {
            return blockPos;
        }
        public boolean isAntiSurround() {
            return antiSurround;
        }
    }
    public boolean shouldPreForcePlace() {
        return forcePlace.get() == ForcePlaceModes.Pre;
    }
    public int getBreakMs() {
        if (attackLatency.isEmpty()) {
            return 0;
        }
        float avg = 0.0f;
        ArrayList<Long> latencyCopy = Lists.newArrayList(attackLatency);
        if (!latencyCopy.isEmpty()) {
            for (float t : latencyCopy) {
                avg += t;
            }
            avg /= latencyCopy.size();
        }
        return (int) avg;
    }
    private enum SequentialModes {
        Off, Strict, Strong
    }
    private enum InstantModes {
        Off, Always, Strict
    }
    private enum ForcePlaceModes {
        Off, Pre, Post
    }
    private enum SwapModes {
        Off, Client, Silent
    }
    private enum SwingModes {
        Place, Break, Both
    }
    private enum AntiWeaknessModes {
        Off, Normal, Silent
    }
    public record TargetInfo(BlockPos pos, PlayerEntity target, float damage, float selfDamage, boolean lethal) {
    }
    private record AntiStuckData(int id, BlockPos blockPos, Vec3d pos, double stuckDist) {
    }
}