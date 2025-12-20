package bep.hax.modules;
import bep.hax.mixin.accessor.PlayerInventoryAccessor;
import bep.hax.Bep;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.item.AxeItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import java.util.Set;
public class Stripper extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final Setting<Integer> axeSlot = sgGeneral.add(new IntSetting.Builder()
        .name("axe-slot")
        .description("Hotbar slot for axe (1-9)")
        .defaultValue(1)
        .range(1, 9)
        .sliderRange(1, 9)
        .build()
    );
    private final Setting<Integer> stripDelay = sgGeneral.add(new IntSetting.Builder()
        .name("strip-delay")
        .description("Ticks to wait before stripping")
        .defaultValue(0)
        .range(0, 40)
        .sliderRange(0, 40)
        .build()
    );
    private final Setting<Integer> breakDelay = sgGeneral.add(new IntSetting.Builder()
        .name("break-delay")
        .description("Ticks to wait before breaking")
        .defaultValue(0)
        .range(0, 40)
        .sliderRange(0, 40)
        .build()
    );
    private final Setting<Integer> placeDelay = sgGeneral.add(new IntSetting.Builder()
        .name("place-delay")
        .description("Ticks to wait before placing next log")
        .defaultValue(0)
        .range(0, 40)
        .sliderRange(0, 40)
        .build()
    );
    private final Setting<Integer> rotationTime = sgGeneral.add(new IntSetting.Builder()
        .name("rotation-time")
        .description("Ticks to hold rotation before action")
        .defaultValue(0)
        .range(0, 20)
        .sliderRange(0, 20)
        .build()
    );
    private final Setting<Boolean> autoMine = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-mine")
        .description("Automatically mine the stripped log")
        .defaultValue(true)
        .build()
    );
    private enum State {
        WAITING_FOR_FIRST_LOG,
        ROTATING_TO_PLACE,
        PLACING_LOG,
        WAIT_AFTER_PLACE,
        ROTATING_TO_STRIP,
        STRIPPING,
        WAIT_AFTER_STRIP,
        ROTATING_TO_BREAK,
        BREAKING,
        WAIT_AFTER_BREAK,
        WAIT_BEFORE_NEXT
    }
    private State state = State.WAITING_FOR_FIRST_LOG;
    private BlockPos targetPos = null;
    private BlockPos workingPos = null;
    private int tickTimer = 0;
    private int rotationTimer = 0;
    private boolean firstLogDetected = false;
    private static final Set<Block> LOGS = Set.of(
        Blocks.OAK_LOG, Blocks.SPRUCE_LOG, Blocks.BIRCH_LOG, Blocks.JUNGLE_LOG,
        Blocks.ACACIA_LOG, Blocks.DARK_OAK_LOG, Blocks.MANGROVE_LOG, Blocks.CHERRY_LOG,
        Blocks.CRIMSON_STEM, Blocks.WARPED_STEM,
        Blocks.OAK_WOOD, Blocks.SPRUCE_WOOD, Blocks.BIRCH_WOOD, Blocks.JUNGLE_WOOD,
        Blocks.ACACIA_WOOD, Blocks.DARK_OAK_WOOD, Blocks.MANGROVE_WOOD, Blocks.CHERRY_WOOD,
        Blocks.CRIMSON_HYPHAE, Blocks.WARPED_HYPHAE
    );
    private static final Set<Block> STRIPPED_LOGS = Set.of(
        Blocks.STRIPPED_OAK_LOG, Blocks.STRIPPED_SPRUCE_LOG, Blocks.STRIPPED_BIRCH_LOG,
        Blocks.STRIPPED_JUNGLE_LOG, Blocks.STRIPPED_ACACIA_LOG, Blocks.STRIPPED_DARK_OAK_LOG,
        Blocks.STRIPPED_MANGROVE_LOG, Blocks.STRIPPED_CHERRY_LOG,
        Blocks.STRIPPED_CRIMSON_STEM, Blocks.STRIPPED_WARPED_STEM,
        Blocks.STRIPPED_OAK_WOOD, Blocks.STRIPPED_SPRUCE_WOOD, Blocks.STRIPPED_BIRCH_WOOD,
        Blocks.STRIPPED_JUNGLE_WOOD, Blocks.STRIPPED_ACACIA_WOOD, Blocks.STRIPPED_DARK_OAK_WOOD,
        Blocks.STRIPPED_MANGROVE_WOOD, Blocks.STRIPPED_CHERRY_WOOD,
        Blocks.STRIPPED_CRIMSON_HYPHAE, Blocks.STRIPPED_WARPED_HYPHAE
    );
    public Stripper() {
        super(Bep.CATEGORY, "stripper", "Strips and breaks logs after you place the first one");
    }
    @Override
    public void onActivate() {
        state = State.WAITING_FOR_FIRST_LOG;
        targetPos = null;
        workingPos = null;
        tickTimer = 0;
        rotationTimer = 0;
        firstLogDetected = false;
        info("Place a log to set the working position");
    }
    @Override
    public void onDeactivate() {
        targetPos = null;
        workingPos = null;
        state = State.WAITING_FOR_FIRST_LOG;
        firstLogDetected = false;
    }
    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;
        if (tickTimer > 0) {
            tickTimer--;
            return;
        }
        switch (state) {
            case WAITING_FOR_FIRST_LOG -> {
                BlockPos playerPos = mc.player.getBlockPos();
                for (int x = -3; x <= 3; x++) {
                    for (int y = -1; y <= 2; y++) {
                        for (int z = -3; z <= 3; z++) {
                            BlockPos checkPos = playerPos.add(x, y, z);
                            Block block = mc.world.getBlockState(checkPos).getBlock();
                            if (LOGS.contains(block) && !firstLogDetected) {
                                workingPos = checkPos;
                                targetPos = checkPos;
                                firstLogDetected = true;
                                info("Working position set");
                                state = State.ROTATING_TO_STRIP;
                                rotationTimer = rotationTime.get();
                                return;
                            }
                        }
                    }
                }
            }
            case ROTATING_TO_PLACE -> {
                if (workingPos == null) {
                    state = State.WAITING_FOR_FIRST_LOG;
                    return;
                }
                Vec3d target = workingPos.toCenterPos();
                Rotations.rotate(getYaw(target), getPitch(target));
                rotationTimer--;
                if (rotationTimer <= 0) {
                    state = State.PLACING_LOG;
                }
            }
            case PLACING_LOG -> {
                if (workingPos == null) {
                    state = State.WAITING_FOR_FIRST_LOG;
                    return;
                }
                int logSlot = findLogInInventory();
                if (logSlot == -1) {
                    error("No logs in inventory");
                    toggle();
                    return;
                }
                ((PlayerInventoryAccessor) mc.player.getInventory()).setSelectedSlot(logSlot);
                BlockPos placeAgainst = workingPos.down();
                Vec3d target = placeAgainst.toCenterPos().add(0, 0.5, 0);
                Rotations.rotate(getYaw(target), getPitch(target));
                BlockHitResult hitResult = new BlockHitResult(
                    target,
                    Direction.UP,
                    placeAgainst,
                    false
                );
                mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hitResult);
                mc.player.swingHand(Hand.MAIN_HAND);
                state = State.WAIT_AFTER_PLACE;
                tickTimer = placeDelay.get();
                targetPos = workingPos;
            }
            case WAIT_AFTER_PLACE -> {
                if (workingPos != null && LOGS.contains(mc.world.getBlockState(workingPos).getBlock())) {
                    state = State.ROTATING_TO_STRIP;
                    rotationTimer = rotationTime.get();
                } else {
                    state = State.ROTATING_TO_PLACE;
                    rotationTimer = rotationTime.get();
                }
            }
            case ROTATING_TO_STRIP -> {
                if (targetPos == null || workingPos == null) {
                    state = State.WAITING_FOR_FIRST_LOG;
                    return;
                }
                Vec3d target = targetPos.toCenterPos();
                Rotations.rotate(getYaw(target), getPitch(target));
                rotationTimer--;
                if (rotationTimer <= 0) {
                    state = State.STRIPPING;
                }
            }
            case STRIPPING -> {
                if (targetPos == null || workingPos == null) {
                    state = State.WAITING_FOR_FIRST_LOG;
                    return;
                }
                int slot = axeSlot.get() - 1;
                ItemStack stack = mc.player.getInventory().getStack(slot);
                if (stack.isEmpty() || !(stack.getItem() instanceof AxeItem)) {
                    error("No axe in slot " + axeSlot.get());
                    toggle();
                    return;
                }
                ((PlayerInventoryAccessor) mc.player.getInventory()).setSelectedSlot(slot);
                Vec3d target = targetPos.toCenterPos();
                Rotations.rotate(getYaw(target), getPitch(target));
                BlockHitResult hitResult = new BlockHitResult(
                    target,
                    Direction.UP,
                    targetPos,
                    false
                );
                mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hitResult);
                mc.player.swingHand(Hand.MAIN_HAND);
                state = State.WAIT_AFTER_STRIP;
                tickTimer = stripDelay.get();
            }
            case WAIT_AFTER_STRIP -> {
                if (targetPos != null && STRIPPED_LOGS.contains(mc.world.getBlockState(targetPos).getBlock())) {
                    if (autoMine.get()) {
                        state = State.ROTATING_TO_BREAK;
                        rotationTimer = rotationTime.get();
                    } else {
                        state = State.WAIT_BEFORE_NEXT;
                        tickTimer = breakDelay.get();
                    }
                } else if (targetPos != null && LOGS.contains(mc.world.getBlockState(targetPos).getBlock())) {
                    state = State.ROTATING_TO_STRIP;
                    rotationTimer = rotationTime.get();
                } else {
                    state = State.WAIT_BEFORE_NEXT;
                    tickTimer = breakDelay.get();
                }
            }
            case ROTATING_TO_BREAK -> {
                if (targetPos == null || workingPos == null) {
                    state = State.WAITING_FOR_FIRST_LOG;
                    return;
                }
                Vec3d target = targetPos.toCenterPos();
                Rotations.rotate(getYaw(target), getPitch(target));
                rotationTimer--;
                if (rotationTimer <= 0) {
                    state = State.BREAKING;
                }
            }
            case BREAKING -> {
                if (targetPos == null || workingPos == null) {
                    state = State.WAITING_FOR_FIRST_LOG;
                    return;
                }
                int slot = axeSlot.get() - 1;
                ((PlayerInventoryAccessor) mc.player.getInventory()).setSelectedSlot(slot);
                Vec3d target = targetPos.toCenterPos();
                Rotations.rotate(getYaw(target), getPitch(target));
                mc.interactionManager.updateBlockBreakingProgress(targetPos, Direction.UP);
                mc.player.swingHand(Hand.MAIN_HAND);
                state = State.WAIT_AFTER_BREAK;
                tickTimer = 2;
            }
            case WAIT_AFTER_BREAK -> {
                if (targetPos == null || mc.world.getBlockState(targetPos).isAir()) {
                    state = State.WAIT_BEFORE_NEXT;
                    tickTimer = breakDelay.get();
                } else {
                    state = State.BREAKING;
                    tickTimer = 1;
                }
            }
            case WAIT_BEFORE_NEXT -> {
                if (findLogInInventory() != -1) {
                    state = State.ROTATING_TO_PLACE;
                    rotationTimer = rotationTime.get();
                } else {
                    info("No more logs in inventory");
                    toggle();
                }
            }
        }
    }
    private float getYaw(Vec3d target) {
        Vec3d playerPos = mc.player.getEyePos();
        double deltaX = target.x - playerPos.x;
        double deltaZ = target.z - playerPos.z;
        return (float) Math.toDegrees(Math.atan2(-deltaX, deltaZ));
    }
    private float getPitch(Vec3d target) {
        Vec3d playerPos = mc.player.getEyePos();
        double deltaX = target.x - playerPos.x;
        double deltaY = target.y - playerPos.y;
        double deltaZ = target.z - playerPos.z;
        double horizontalDistance = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
        return (float) -Math.toDegrees(Math.atan2(deltaY, horizontalDistance));
    }
    private int findLogInInventory() {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (!stack.isEmpty()) {
                Block block = Block.getBlockFromItem(stack.getItem());
                if (LOGS.contains(block)) {
                    return i;
                }
            }
        }
        return -1;
    }
    @Override
    public String getInfoString() {
        return state.toString().replace("_", " ");
    }
}