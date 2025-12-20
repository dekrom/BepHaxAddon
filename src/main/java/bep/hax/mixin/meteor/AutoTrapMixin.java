package bep.hax.mixin.meteor;
import bep.hax.mixin.accessor.PlayerInventoryAccessor;
import bep.hax.util.RotationUtils;
import bep.hax.util.RotationUtils;
import bep.hax.util.InventoryManager;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.combat.AutoTrap;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.EventPriority;
import net.minecraft.block.Block;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
@Mixin(value = AutoTrap.class, remap = false)
public abstract class AutoTrapMixin extends Module {
    public AutoTrapMixin(Category category, String name, String description) {
        super(category, name, description);
    }
    @Shadow @Final private SettingGroup sgGeneral;
    @Shadow @Final private Setting<Boolean> rotate;
    @Shadow private List<BlockPos> placePositions;
    @Shadow private PlayerEntity target;
    @Unique private SettingGroup bephax$sgTrapMode;
    @Unique private Setting<Boolean> bephax$useExpandPattern;
    @Unique private Setting<Boolean> bephax$feetOnly;
    @Unique private Setting<Integer> bephax$blocksPerTick;
    @Unique private SettingGroup bephax$sgGrimPlace;
    @Unique private Setting<Boolean> bephax$grimPlace;
    @Unique private Setting<Boolean> bephax$grimRotate;
    @Unique private Setting<Boolean> bephax$yawStep;
    @Unique private Setting<Integer> bephax$yawStepLimit;
    @Unique private RotationUtils bephax$rotationManager;
    @Unique private InventoryManager bephax$inventoryManager;
    @Unique private Vec3d bephax$targetRotation = null;
    @Unique private boolean bephax$rotated = true;
    @Unique private int bephax$blocksPlacedThisTick = 0;
    @Unique private final Set<BlockPos> bephax$placedPositions = new HashSet<>();
    @Inject(method = "<init>", at = @At("TAIL"))
    private void onInit(CallbackInfo ci) {
        bephax$rotationManager = RotationUtils.getInstance();
        bephax$inventoryManager = InventoryManager.getInstance();
        bephax$sgTrapMode = settings.createGroup("Trap Mode");
        bephax$useExpandPattern = bephax$sgTrapMode.add(new BoolSetting.Builder()
            .name("use-expand-pattern")
            .description("Use custom expand pattern (handles boundaries correctly). Disable to use Meteor's original patterns.")
            .defaultValue(false)
            .build()
        );
        bephax$feetOnly = bephax$sgTrapMode.add(new BoolSetting.Builder()
            .name("feet-only")
            .description("Only traps the feet level (no roof or head blocks)")
            .defaultValue(false)
            .visible(bephax$useExpandPattern::get)
            .build()
        );
        bephax$blocksPerTick = bephax$sgTrapMode.add(new IntSetting.Builder()
            .name("blocks-per-tick")
            .description("Maximum blocks to place per tick")
            .defaultValue(8)
            .min(1)
            .sliderRange(1, 20)
            .build()
        );
        bephax$sgGrimPlace = settings.createGroup("Grim Place");
        bephax$grimPlace = bephax$sgGrimPlace.add(new BoolSetting.Builder()
            .name("grim-place")
            .description("Uses GrimAirPlace exploit for block placement (bypass 2b2t anti-cheat)")
            .defaultValue(false)
            .build()
        );
        bephax$grimRotate = bephax$sgGrimPlace.add(new BoolSetting.Builder()
            .name("grim-rotate")
            .description("Rotation system with yaw stepping")
            .defaultValue(false)
            .visible(bephax$grimPlace::get)
            .build()
        );
        bephax$yawStep = bephax$sgGrimPlace.add(new BoolSetting.Builder()
            .name("yaw-step")
            .description("Rotates over multiple ticks (45-90° for GrimAC)")
            .defaultValue(true)
            .visible(() -> bephax$grimPlace.get() && bephax$grimRotate.get())
            .build()
        );
        bephax$yawStepLimit = bephax$sgGrimPlace.add(new IntSetting.Builder()
            .name("yaw-step-limit")
            .description("Max yaw rotation per tick")
            .defaultValue(90)
            .min(1)
            .max(180)
            .sliderRange(1, 180)
            .visible(() -> bephax$grimPlace.get() && bephax$grimRotate.get() && bephax$yawStep.get())
            .build()
        );
    }
    @Inject(method = "onActivate", at = @At("TAIL"))
    private void onActivateInject(CallbackInfo ci) {
        bephax$targetRotation = null;
        bephax$rotated = true;
        bephax$placedPositions.clear();
    }
    @Inject(method = "onDeactivate", at = @At("TAIL"))
    private void onDeactivateInject(CallbackInfo ci) {
        bephax$targetRotation = null;
        bephax$rotated = true;
        bephax$placedPositions.clear();
        if (bephax$inventoryManager != null) {
            bephax$inventoryManager.syncToClient();
        }
    }
    @Unique
    @EventHandler(priority = EventPriority.HIGHEST)
    private void onPreTickHighPriority(TickEvent.Pre event) {
        bephax$blocksPlacedThisTick = 0;
        if (!isActive() || mc.player == null) {
            bephax$targetRotation = null;
            bephax$rotated = true;
            return;
        }
        if (bephax$grimRotate.get()) {
            if (rotate.get()) rotate.set(false);
            if (bephax$targetRotation != null && !bephax$rotated && bephax$yawStep.get()) {
                bephax$continueYawStep();
            }
        }
    }
    @Inject(method = "fillPlaceArray", at = @At("HEAD"), cancellable = true)
    private void replaceFillPlaceArray(PlayerEntity targetPlayer, CallbackInfo ci) {
        if (!bephax$useExpandPattern.get()) return;
        ci.cancel();
        placePositions.clear();
        if (targetPlayer == null) return;
        bephax$improveTrapping(targetPlayer);
    }
    @Unique
    private void bephax$improveTrapping(PlayerEntity target) {
        Set<BlockPos> newPositions = new HashSet<>();
        Box box = target.getBoundingBox();
        int minX = (int) Math.floor(box.minX);
        int maxX = (int) Math.floor(box.maxX - 0.0001);
        int minZ = (int) Math.floor(box.minZ);
        int maxZ = (int) Math.floor(box.maxZ - 0.0001);
        int footY = target.getBlockY();
        Set<BlockPos> footBlocks = new HashSet<>();
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                footBlocks.add(new BlockPos(x, footY, z));
            }
        }
        for (BlockPos foot : footBlocks) {
            BlockPos floor = foot.down();
            if (BlockUtils.canPlace(floor)) {
                newPositions.add(floor);
            }
        }
        for (BlockPos foot : footBlocks) {
            newPositions.add(foot.north());
            newPositions.add(foot.south());
            newPositions.add(foot.east());
            newPositions.add(foot.west());
        }
        newPositions.removeAll(footBlocks);
        if (!bephax$feetOnly.get()) {
            for (BlockPos foot : footBlocks) {
                BlockPos up = foot.up();
                newPositions.add(up.north());
                newPositions.add(up.south());
                newPositions.add(up.east());
                newPositions.add(up.west());
            }
            for (BlockPos foot : footBlocks) {
                newPositions.remove(foot.up());
            }
            for (BlockPos foot : footBlocks) {
                newPositions.add(foot.up(2));
            }
        }
        newPositions.removeIf(pos -> !BlockUtils.canPlace(pos));
        placePositions.clear();
        placePositions.addAll(newPositions);
    }
    @Unique
    @EventHandler
    private void onSendPacket(PacketEvent.Send event) {
        if (!isActive() || !Utils.canUpdate() || mc.player == null) return;
        if (event.packet instanceof PlayerInteractBlockC2SPacket packet) {
            Hand hand = packet.getHand();
            if (hand == null) return;
            if (mc.player.getStackInHand(hand).getItem() instanceof BlockItem) {
                BlockPos targetPos = packet.getBlockHitResult().getBlockPos();
                if (bephax$placedPositions.contains(targetPos)) {
                    event.cancel();
                    return;
                }
                if (bephax$blocksPlacedThisTick >= bephax$blocksPerTick.get()) {
                    event.cancel();
                    return;
                }
                if (bephax$grimPlace.get()) {
                    event.cancel();
                    bephax$placeGrimBlock(packet);
                    bephax$blocksPlacedThisTick++;
                    bephax$placedPositions.add(targetPos);
                } else {
                    bephax$blocksPlacedThisTick++;
                    bephax$placedPositions.add(targetPos);
                }
            }
        }
    }
    @Unique
    private void bephax$placeGrimBlock(PlayerInteractBlockC2SPacket packet) {
        BlockHitResult hitResult = packet.getBlockHitResult();
        int currentSlot = ((PlayerInventoryAccessor) mc.player.getInventory()).getSelectedSlot();
        bephax$inventoryManager.setSlot(currentSlot);
        if (bephax$grimRotate.get()) {
            Vec3d blockPos = Vec3d.ofCenter(hitResult.getBlockPos());
            bephax$applyRotation(blockPos);
        }
        mc.player.networkHandler.sendPacket(new PlayerActionC2SPacket(
            PlayerActionC2SPacket.Action.SWAP_ITEM_WITH_OFFHAND,
            BlockPos.ORIGIN,
            Direction.DOWN
        ));
        mc.player.networkHandler.sendPacket(new PlayerInteractBlockC2SPacket(
            Hand.OFF_HAND,
            hitResult,
            mc.player.currentScreenHandler.getRevision() + 2
        ));
        mc.player.networkHandler.sendPacket(new PlayerActionC2SPacket(
            PlayerActionC2SPacket.Action.SWAP_ITEM_WITH_OFFHAND,
            BlockPos.ORIGIN,
            Direction.DOWN
        ));
        mc.player.swingHand(Hand.MAIN_HAND);
    }
    @Unique
    private void bephax$applyRotation(Vec3d target) {
        if (mc.player == null) return;
        bephax$targetRotation = target;
        float[] rotation = RotationUtils.getRotationsTo(mc.player.getEyePos(), target);
        if (bephax$yawStep.get()) {
            float serverYaw = bephax$rotationManager.getWrappedYaw();
            float targetYaw = rotation[0];
            float diff = serverYaw - targetYaw;
            while (diff > 180.0f) diff -= 360.0f;
            while (diff < -180.0f) diff += 360.0f;
            float diff1 = Math.abs(diff);
            int stepLimit = bephax$yawStepLimit.get();
            if (diff1 > stepLimit) {
                float deltaYaw = diff > 0.0f ? -stepLimit : stepLimit;
                float yaw = serverYaw + deltaYaw;
                bephax$rotationManager.setRotationSilent(yaw, rotation[1]);
                bephax$rotated = false;
            } else {
                bephax$rotationManager.setRotationSilent(targetYaw, rotation[1]);
                bephax$rotated = true;
                bephax$targetRotation = null;
            }
        } else {
            bephax$rotationManager.setRotationSilent(rotation[0], rotation[1]);
            bephax$rotated = true;
            bephax$targetRotation = null;
        }
    }
    @Unique
    private void bephax$continueYawStep() {
        if (mc.player == null || bephax$targetRotation == null) {
            bephax$rotated = true;
            return;
        }
        float[] rotation = RotationUtils.getRotationsTo(mc.player.getEyePos(), bephax$targetRotation);
        float serverYaw = bephax$rotationManager.getWrappedYaw();
        float targetYaw = rotation[0];
        float diff = serverYaw - targetYaw;
        while (diff > 180.0f) diff -= 360.0f;
        while (diff < -180.0f) diff += 360.0f;
        float diff1 = Math.abs(diff);
        int stepLimit = bephax$yawStepLimit.get();
        if (diff1 > stepLimit) {
            float deltaYaw = diff > 0.0f ? -stepLimit : stepLimit;
            float yaw = serverYaw + deltaYaw;
            bephax$rotationManager.setRotationSilent(yaw, rotation[1]);
            bephax$rotated = false;
        } else {
            bephax$rotationManager.setRotationSilent(targetYaw, rotation[1]);
            bephax$rotated = true;
            bephax$targetRotation = null;
        }
    }
}