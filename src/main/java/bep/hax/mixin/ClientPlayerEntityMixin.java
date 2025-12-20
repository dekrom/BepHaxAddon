package bep.hax.mixin;
import bep.hax.accessor.InputAccessor;
import bep.hax.mixin.accessor.PlayerInventoryAccessor;
import bep.hax.modules.RocketMan;
import bep.hax.util.InventoryManager;
import bep.hax.util.PushOutOfBlocksEvent;
import meteordevelopment.meteorclient.systems.modules.movement.NoSlow;
import net.minecraft.client.input.Input;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import org.spongepowered.asm.mixin.Mixin;
import net.minecraft.sound.SoundCategory;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import net.minecraft.client.network.ClientPlayerEntity;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.MeteorClient;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(ClientPlayerEntity.class)
public abstract class ClientPlayerEntityMixin {
    @Shadow public Input input;
    @Shadow public abstract boolean isUsingItem();
    @Shadow public abstract boolean isSneaking();
    @Inject(method = "playSoundToPlayer", at = @At("HEAD"), cancellable = true)
    private void mixinPlaySound(SoundEvent sound, SoundCategory category, float volume, float pitch, CallbackInfo ci) {
        Modules modules = Modules.get();
        if (modules == null) return;
        RocketMan rocketMan = modules.get(RocketMan.class);
        if (rocketMan.isActive() && sound == SoundEvents.ITEM_ELYTRA_FLYING) {
            if (rocketMan.shouldMuteElytra()) ci.cancel();
        }
    }
    @Inject(method = "pushOutOfBlocks", at = @At("HEAD"), cancellable = true)
    private void onPushOutOfBlocks(double x, double z, CallbackInfo ci) {
        PushOutOfBlocksEvent event = new PushOutOfBlocksEvent();
        MeteorClient.EVENT_BUS.post(event);
        if (event.isCanceled()) {
            ci.cancel();
        }
    }
    @Inject(method = "tick", at = @At("HEAD"))
    private void onTickStart(CallbackInfo ci) {
        ClientPlayerEntity player = (ClientPlayerEntity) (Object) this;
        if (player == null) return;
        bephax$checkStartEating(player);
    }
    @Inject(method = "tickMovement", at = @At(value = "FIELD", target = "Lnet/minecraft/client/network/ClientPlayerEntity;input:Lnet/minecraft/client/input/Input;", ordinal = 0, shift = At.Shift.AFTER))
    private void bephax$multiplyInputAfterInputTick(CallbackInfo ci) {
        ClientPlayerEntity player = (ClientPlayerEntity) (Object) this;
        NoSlow noSlow = Modules.get().get(NoSlow.class);
        if (!noSlow.isActive()) return;
        if (bephax$isGrimV3Enabled(noSlow)) {
            if (player.isUsingItem() && bephax$checkGrimV3Timing()) {
                float multiplier = bephax$getGrimV3Multiplier();
                InputAccessor inputAccessor = (InputAccessor) input;
                inputAccessor.setMovementForward(inputAccessor.getMovementForward() * multiplier);
                inputAccessor.setMovementSideways(inputAccessor.getMovementSideways() * multiplier);
            }
            return;
        }
        if (bephax$shouldMultiplyInput(noSlow)) {
            float multiplier = bephax$getInputMultiplier();
            InputAccessor inputAccessor = (InputAccessor) input;
            inputAccessor.setMovementForward(inputAccessor.getMovementForward() * multiplier);
            inputAccessor.setMovementSideways(inputAccessor.getMovementSideways() * multiplier);
        }
        if (noSlow.sneaking() && isSneaking()) {
            float sneakMultiplier = 1.0f / 0.3f;
            InputAccessor inputAccessor = (InputAccessor) input;
            inputAccessor.setMovementForward(inputAccessor.getMovementForward() * sneakMultiplier);
            inputAccessor.setMovementSideways(inputAccessor.getMovementSideways() * sneakMultiplier);
        }
    }
    @Inject(method = "tickMovement", at = @At("TAIL"))
    private void bephax$handleManualEatingAtTail(CallbackInfo ci) {
        ClientPlayerEntity player = (ClientPlayerEntity) (Object) this;
        bephax$handleManualEating(player);
    }
    @Unique
    private boolean bephax$shouldMultiplyInput(NoSlow noSlow) {
        ClientPlayerEntity player = (ClientPlayerEntity) (Object) this;
        if (player.hasVehicle() || isSneaking()) return false;
        return isUsingItem() && noSlow.items();
    }
    @Unique
    private boolean bephax$isGrimV3Enabled(NoSlow noSlow) {
        try {
            var field = noSlow.getClass().getDeclaredField("bephax$grimV3Bypass");
            field.setAccessible(true);
            var setting = field.get(noSlow);
            var getMethod = setting.getClass().getMethod("get");
            Object value = getMethod.invoke(setting);
            return value instanceof Boolean && (Boolean) value;
        } catch (Exception e) {
            return false;
        }
    }
    @Unique
    private boolean bephax$checkGrimV3Timing() {
        ClientPlayerEntity player = (ClientPlayerEntity) (Object) this;
        if (player == null) return false;
        return (!player.isSneaking() && !player.hasVehicle() && player.getItemUseTimeLeft() < 5)
            || (player.getItemUseTime() > 1 && player.getItemUseTime() % 2 != 0);
    }
    @Unique
    private float bephax$getGrimV3Multiplier() {
        NoSlow noSlow = Modules.get().get(NoSlow.class);
        try {
            var field = noSlow.getClass().getDeclaredField("bephax$grimV3Multiplier");
            field.setAccessible(true);
            var setting = field.get(noSlow);
            var valueMethod = setting.getClass().getMethod("get");
            Object value = valueMethod.invoke(setting);
            if (value instanceof Number) {
                return ((Number) value).floatValue();
            }
        } catch (Exception e) {
        }
        return 5.0f;
    }
    @Unique
    private float bephax$getInputMultiplier() {
        NoSlow noSlow = Modules.get().get(NoSlow.class);
        try {
            var field = noSlow.getClass().getDeclaredField("bephax$inputMultiplier");
            field.setAccessible(true);
            var setting = field.get(noSlow);
            var valueMethod = setting.getClass().getMethod("get");
            Object value = valueMethod.invoke(setting);
            if (value instanceof Number) {
                return ((Number) value).floatValue();
            }
        } catch (Exception e) {
        }
        return 5.0f;
    }
    @Unique
    private boolean bephax$wasManuallyEating = false;
    @Unique
    private int bephax$lastManualEatingSlot = -1;
    @Unique
    private int bephax$lastItemUseTime = 0;
    @Unique
    private void bephax$checkStartEating(ClientPlayerEntity player) {
        if (player == null) return;
        int currentUseTime = player.getItemUseTime();
        if (currentUseTime == 1 && bephax$lastItemUseTime == 0) {
            ItemStack activeStack = player.getActiveItem();
            if (!activeStack.isEmpty() && activeStack.get(DataComponentTypes.FOOD) != null) {
                InventoryManager invManager = InventoryManager.getInstance();
                int currentSlot = ((PlayerInventoryAccessor) player.getInventory()).getSelectedSlot();
                int serverSlot = invManager.getServerSlot();
                if (serverSlot != currentSlot) {
                    invManager.setSlotForced(currentSlot);
                }
                invManager.setEating(true);
                bephax$wasManuallyEating = true;
                bephax$lastManualEatingSlot = currentSlot;
            }
        }
        bephax$lastItemUseTime = currentUseTime;
    }
    @Unique
    private void bephax$handleManualEating(ClientPlayerEntity player) {
        if (player == null) return;
        boolean isEatingNow = bephax$isManuallyEatingFood(player);
        if (!isEatingNow && bephax$wasManuallyEating) {
            bephax$wasManuallyEating = false;
            InventoryManager.getInstance().setEating(false);
            bephax$lastManualEatingSlot = -1;
        }
    }
    @Unique
    private boolean bephax$isManuallyEatingFood(ClientPlayerEntity player) {
        if (!player.isUsingItem()) return false;
        ItemStack stack = player.getActiveItem();
        if (stack.isEmpty()) return false;
        return stack.get(DataComponentTypes.FOOD) != null;
    }
}