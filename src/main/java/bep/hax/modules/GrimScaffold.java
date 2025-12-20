package bep.hax.modules;
import bep.hax.accessor.InputAccessor;
import bep.hax.Bep;
import bep.hax.util.RotationUtils;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.FallingBlock;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import java.util.ArrayList;
import java.util.List;
public class GrimScaffold extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgSpeed = settings.createGroup("Speed");
    private final SettingGroup sgRender = settings.createGroup("Render");
    private final Setting<List<Block>> blocks = sgGeneral.add(new BlockListSetting.Builder()
        .name("blocks")
        .description("Selected blocks.")
        .build()
    );
    private final Setting<ListMode> blocksFilter = sgGeneral.add(new EnumSetting.Builder<ListMode>()
        .name("blocks-filter")
        .description("How to use the block list setting")
        .defaultValue(ListMode.Blacklist)
        .build()
    );
    private final Setting<Boolean> tower = sgGeneral.add(new BoolSetting.Builder()
        .name("tower")
        .description("Automatically towers up when holding jump.")
        .defaultValue(false)
        .build()
    );
    private final Setting<Double> towerSpeed = sgGeneral.add(new DoubleSetting.Builder()
        .name("tower-speed")
        .description("The speed at which to tower.")
        .defaultValue(0.42)
        .min(0)
        .max(1)
        .sliderMax(1)
        .visible(tower::get)
        .build()
    );
    private final Setting<Boolean> onlyOnClick = sgGeneral.add(new BoolSetting.Builder()
        .name("only-on-click")
        .description("Only places blocks when holding right click.")
        .defaultValue(false)
        .build()
    );
    private final Setting<Integer> placeDelay = sgGeneral.add(new IntSetting.Builder()
        .name("place-delay")
        .description("Delay in ticks between placements.")
        .defaultValue(0)
        .min(0)
        .max(10)
        .build()
    );
    private final Setting<Boolean> autoSwitch = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-switch")
        .description("Automatically swaps to a block before placing.")
        .defaultValue(true)
        .build()
    );
    private final Setting<RotationMode> rotationMode = sgGeneral.add(new EnumSetting.Builder<RotationMode>()
        .name("rotation-mode")
        .description("How to handle rotations.")
        .defaultValue(RotationMode.None)
        .build()
    );
    private final Setting<Double> extendDistance = sgGeneral.add(new DoubleSetting.Builder()
        .name("extend-distance")
        .description("How far ahead to place blocks when moving.")
        .defaultValue(0.3)
        .min(0)
        .max(2)
        .sliderMax(2)
        .build()
    );
    private final Setting<Boolean> velocityPredict = sgSpeed.add(new BoolSetting.Builder()
        .name("velocity-predict")
        .description("Predicts position based on velocity when falling fast.")
        .defaultValue(true)
        .build()
    );
    private final Setting<Double> velocityMultiplier = sgSpeed.add(new DoubleSetting.Builder()
        .name("velocity-multiplier")
        .description("Multiplier for velocity prediction.")
        .defaultValue(1.0)
        .min(0.5)
        .max(5.0)
        .sliderMax(5.0)
        .visible(velocityPredict::get)
        .build()
    );
    private final Setting<Boolean> adaptiveSpeed = sgSpeed.add(new BoolSetting.Builder()
        .name("adaptive-speed")
        .description("Places more blocks per tick when falling fast.")
        .defaultValue(true)
        .build()
    );
    private final Setting<Integer> maxBlocksPerTick = sgSpeed.add(new IntSetting.Builder()
        .name("max-blocks-per-tick")
        .description("Maximum blocks to place per tick when falling fast.")
        .defaultValue(3)
        .min(1)
        .max(5)
        .visible(adaptiveSpeed::get)
        .build()
    );
    private final Setting<Boolean> safeWalk = sgGeneral.add(new BoolSetting.Builder()
        .name("safewalk")
        .description("Prevents you from walking off edges.")
        .defaultValue(false)
        .build()
    );
    private final Setting<Boolean> render = sgRender.add(new BoolSetting.Builder()
        .name("render")
        .description("Renders blocks being placed.")
        .defaultValue(true)
        .build()
    );
    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode")
        .description("How the shapes are rendered.")
        .defaultValue(ShapeMode.Both)
        .visible(render::get)
        .build()
    );
    private final Setting<SettingColor> sideColor = sgRender.add(new ColorSetting.Builder()
        .name("side-color")
        .description("Side color of rendered blocks.")
        .defaultValue(new SettingColor(197, 137, 232, 10))
        .visible(render::get)
        .build()
    );
    private final Setting<SettingColor> lineColor = sgRender.add(new ColorSetting.Builder()
        .name("line-color")
        .description("Line color of rendered blocks.")
        .defaultValue(new SettingColor(197, 137, 232))
        .visible(render::get)
        .build()
    );
    private final BlockPos.Mutable targetPos = new BlockPos.Mutable();
    private final List<BlockPos> renderedBlocks = new ArrayList<>();
    private int tickDelay = 0;
    public GrimScaffold() {
        super(Bep.CATEGORY, "grim-scaffold", "Places blocks under you using GrimAC bypass.");
    }
    @Override
    public void onActivate() {
        tickDelay = 0;
        renderedBlocks.clear();
    }
    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;
        if (onlyOnClick.get() && !mc.options.useKey.isPressed()) return;
        tickDelay++;
        if (tickDelay < placeDelay.get()) return;
        FindItemResult blockItem = findBlock();
        if (!blockItem.found()) return;
        if (!autoSwitch.get() && blockItem.getHand() == null) return;
        List<BlockPos> placementPositions = getPlacementPositions();
        if (placementPositions.isEmpty()) return;
        if (autoSwitch.get() && blockItem.getHand() == null) {
            InvUtils.swap(blockItem.slot(), true);
        }
        int blocksToPlace = getBlocksPerTick();
        int placed = 0;
        for (BlockPos pos : placementPositions) {
            if (placed >= blocksToPlace) break;
            if (rotationMode.get() == RotationMode.Precise) {
                Vec3d hitVec = Vec3d.ofCenter(pos).add(0, 0.5, 0);
                float[] rotations = RotationUtils.getRotationsTo(mc.player.getEyePos(), hitVec);
                Rotations.rotate(rotations[0], rotations[1], () -> placeBlock(pos));
            } else if (rotationMode.get() == RotationMode.Simple) {
                Vec3d hitVec = Vec3d.ofCenter(pos).add(0, 0.5, 0);
                float[] rotations = calculateSimpleRotations(hitVec);
                Rotations.rotate(rotations[0], rotations[1], () -> placeBlock(pos));
            } else {
                placeBlock(pos);
            }
            placed++;
        }
        if (tower.get() && shouldTower()) {
            mc.player.setVelocity(mc.player.getVelocity().x, towerSpeed.get(), mc.player.getVelocity().z);
        }
    }
    private int getBlocksPerTick() {
        if (!adaptiveSpeed.get()) return 1;
        double velocity = Math.abs(mc.player.getVelocity().y);
        if (velocity > 0.5) return maxBlocksPerTick.get();
        if (velocity > 0.3) return Math.min(2, maxBlocksPerTick.get());
        return 1;
    }
    private List<BlockPos> getPlacementPositions() {
        List<BlockPos> positions = new ArrayList<>();
        Vec3d playerPos = mc.player.getEntityPos();
        Vec3d velocity = mc.player.getVelocity();
        Vec3d predictedPos = playerPos;
        if (velocityPredict.get() && Math.abs(velocity.y) > 0.1) {
            double multiplier = velocityMultiplier.get();
            predictedPos = playerPos.add(velocity.x * multiplier, velocity.y * multiplier, velocity.z * multiplier);
        }
        if (isMovingHorizontally() && extendDistance.get() > 0) {
            Vec3d moveVec = getMovementVector();
            predictedPos = predictedPos.add(moveVec.multiply(extendDistance.get()));
        }
        targetPos.set(predictedPos.x, playerPos.y - 1, predictedPos.z);
        if (mc.world.getBlockState(targetPos).isReplaceable()) {
            positions.add(targetPos.toImmutable());
        }
        if (Math.abs(velocity.y) > 0.4) {
            BlockPos aboveTarget = targetPos.up().toImmutable();
            if (mc.world.getBlockState(aboveTarget).isReplaceable()) {
                positions.add(aboveTarget);
            }
        }
        return positions;
    }
    private Vec3d getMovementVector() {
        Vec3d velocity = Vec3d.ZERO;
        float yaw = mc.player.getYaw();
        if (mc.options.forwardKey.isPressed()) {
            velocity = velocity.add(Vec3d.fromPolar(0, yaw));
        }
        if (mc.options.backKey.isPressed()) {
            velocity = velocity.add(Vec3d.fromPolar(0, yaw + 180));
        }
        if (mc.options.leftKey.isPressed()) {
            velocity = velocity.add(Vec3d.fromPolar(0, yaw - 90));
        }
        if (mc.options.rightKey.isPressed()) {
            velocity = velocity.add(Vec3d.fromPolar(0, yaw + 90));
        }
        if (velocity.lengthSquared() > 0) {
            return velocity.normalize();
        }
        return velocity;
    }
    private boolean isMovingHorizontally() {
        return mc.options.forwardKey.isPressed() ||
               mc.options.backKey.isPressed() ||
               mc.options.leftKey.isPressed() ||
               mc.options.rightKey.isPressed();
    }
    private boolean shouldTower() {
        return mc.options.jumpKey.isPressed() &&
               !mc.options.sneakKey.isPressed() &&
               !isMovingHorizontally();
    }
    private void placeBlock(BlockPos pos) {
        Direction side = Direction.UP;
        BlockHitResult hitResult = new BlockHitResult(
            Vec3d.ofCenter(pos),
            side,
            pos,
            false
        );
        mc.player.networkHandler.sendPacket(
            new PlayerActionC2SPacket(
                PlayerActionC2SPacket.Action.SWAP_ITEM_WITH_OFFHAND,
                BlockPos.ORIGIN,
                Direction.DOWN
            )
        );
        mc.player.networkHandler.sendPacket(
            new PlayerInteractBlockC2SPacket(
                Hand.OFF_HAND,
                hitResult,
                mc.player.currentScreenHandler.getRevision() + 2
            )
        );
        mc.player.networkHandler.sendPacket(
            new PlayerActionC2SPacket(
                PlayerActionC2SPacket.Action.SWAP_ITEM_WITH_OFFHAND,
                BlockPos.ORIGIN,
                Direction.DOWN
            )
        );
        mc.player.swingHand(Hand.MAIN_HAND);
        renderedBlocks.add(pos.toImmutable());
        tickDelay = 0;
    }
    private float[] calculateSimpleRotations(Vec3d target) {
        Vec3d eyePos = mc.player.getEyePos();
        Vec3d diff = target.subtract(eyePos);
        double diffX = diff.x;
        double diffY = diff.y;
        double diffZ = diff.z;
        double diffXZ = Math.sqrt(diffX * diffX + diffZ * diffZ);
        float yaw = (float) Math.toDegrees(Math.atan2(diffZ, diffX)) - 90.0f;
        float pitch = (float) -Math.toDegrees(Math.atan2(diffY, diffXZ));
        return new float[]{MathHelper.wrapDegrees(yaw), MathHelper.clamp(pitch, -90.0f, 90.0f)};
    }
    private FindItemResult findBlock() {
        return InvUtils.findInHotbar(itemStack -> {
            if (!(itemStack.getItem() instanceof BlockItem blockItem)) return false;
            Block block = blockItem.getBlock();
            if (blocksFilter.get() == ListMode.Blacklist && blocks.get().contains(block)) return false;
            if (blocksFilter.get() == ListMode.Whitelist && !blocks.get().contains(block)) return false;
            if (!Block.isShapeFullCube(block.getDefaultState().getCollisionShape(mc.world, targetPos))) return false;
            if (block instanceof FallingBlock && FallingBlock.canFallThrough(mc.world.getBlockState(targetPos))) return false;
            return true;
        });
    }
    @EventHandler
    private void onRender(Render3DEvent event) {
        if (!render.get() || renderedBlocks.isEmpty()) return;
        renderedBlocks.removeIf(pos -> !mc.world.getBlockState(pos).isReplaceable());
        for (BlockPos pos : renderedBlocks) {
            event.renderer.box(pos, sideColor.get(), lineColor.get(), shapeMode.get(), 0);
        }
    }
    public boolean isSafeWalking() {
        return isActive() && safeWalk.get();
    }
    public enum ListMode {
        Whitelist,
        Blacklist
    }
    public enum RotationMode {
        None,
        Simple,
        Precise
    }
}