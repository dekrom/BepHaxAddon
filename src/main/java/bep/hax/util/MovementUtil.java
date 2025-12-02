package bep.hax.util;
import bep.hax.accessor.InputAccessor;
import net.minecraft.client.input.Input;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec2f;
import static meteordevelopment.meteorclient.MeteorClient.mc;
public class MovementUtil {
    public static boolean isInputtingMovement() {
        return mc.options.forwardKey.isPressed()
                || mc.options.backKey.isPressed()
                || mc.options.leftKey.isPressed()
                || mc.options.rightKey.isPressed();
    }
    public static boolean isMovingInput() {
        return ((InputAccessor) mc.player.input).getMovementForward() != 0.0f
                || ((InputAccessor) mc.player.input).getMovementSideways() != 0.0f;
    }
    public static boolean isMoving() {
        double d = mc.player.getX() - mc.player.lastX;
        double e = mc.player.getY() - mc.player.lastY;
        double f = mc.player.getZ() - mc.player.lastZ;
        return MathHelper.squaredMagnitude(d, e, f) > MathHelper.square(2.0e-4);
    }
    public static void applySneak() {
        final float modifier = MathHelper.clamp(
            0.3f + (meteordevelopment.meteorclient.utils.Utils.getEnchantmentLevel(
                mc.player.getEquippedStack(EquipmentSlot.FEET), Enchantments.SWIFT_SNEAK) * 0.15F),
            0.0f, 1.0f);
        InputAccessor inputAccessor = (InputAccessor) mc.player.input;
        inputAccessor.setMovementForward(inputAccessor.getMovementForward() * modifier);
        inputAccessor.setMovementSideways(inputAccessor.getMovementSideways() * modifier);
    }
    public static Vec2f applySafewalk(final double motionX, final double motionZ) {
        final double offset = 0.05;
        double moveX = motionX;
        double moveZ = motionZ;
        float fallDist = -mc.player.getStepHeight();
        if (!mc.player.isOnGround()) {
            fallDist = -1.5f;
        }
        while (moveX != 0.0 && mc.world.isSpaceEmpty(mc.player, mc.player.getBoundingBox().offset(moveX, fallDist, 0.0))) {
            if (moveX < offset && moveX >= -offset) {
                moveX = 0.0;
            } else if (moveX > 0.0) {
                moveX -= offset;
            } else {
                moveX += offset;
            }
        }
        while (moveZ != 0.0 && mc.world.isSpaceEmpty(mc.player, mc.player.getBoundingBox().offset(0.0, fallDist, moveZ))) {
            if (moveZ < offset && moveZ >= -offset) {
                moveZ = 0.0;
            } else if (moveZ > 0.0) {
                moveZ -= offset;
            } else {
                moveZ += offset;
            }
        }
        while (moveX != 0.0 && moveZ != 0.0 && mc.world.isSpaceEmpty(mc.player, mc.player.getBoundingBox().offset(moveX, fallDist, moveZ))) {
            if (moveX < offset && moveX >= -offset) {
                moveX = 0.0;
            } else if (moveX > 0.0) {
                moveX -= offset;
            } else {
                moveX += offset;
            }
            if (moveZ < offset && moveZ >= -offset) {
                moveZ = 0.0;
            } else if (moveZ > 0.0) {
                moveZ -= offset;
            } else {
                moveZ += offset;
            }
        }
        return new Vec2f((float) moveX, (float) moveZ);
    }
    public static float getYawOffset(Input input, float rotationYaw) {
        InputAccessor inputAccessor = (InputAccessor) input;
        if (inputAccessor.getMovementForward() < 0.0f) rotationYaw += 180.0f;
        float forward = 1.0f;
        if (inputAccessor.getMovementForward() < 0.0f) {
            forward = -0.5f;
        } else if (inputAccessor.getMovementForward() > 0.0f) {
            forward = 0.5f;
        }
        float strafe = inputAccessor.getMovementSideways();
        if (strafe > 0.0f) rotationYaw -= 90.0f * forward;
        if (strafe < 0.0f) rotationYaw += 90.0f * forward;
        return rotationYaw;
    }
}