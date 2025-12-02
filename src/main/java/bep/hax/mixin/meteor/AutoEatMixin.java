package bep.hax.mixin.meteor;
import bep.hax.mixin.accessor.PlayerInventoryAccessor;
import bep.hax.util.InventoryManager;
import meteordevelopment.meteorclient.pathing.PathManagers;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.systems.modules.player.AutoEat;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import static meteordevelopment.meteorclient.MeteorClient.mc;
@Mixin(value = AutoEat.class, remap = false)
public abstract class AutoEatMixin {
    @Shadow public boolean eating;
    @Shadow private int slot;
    @Shadow private int prevSlot;
    @Shadow @Final private Setting<Boolean> pauseBaritone;
    @Shadow protected abstract void stopEating();
    @Unique private boolean bephax$wasBaritone = false;
    @Inject(method = "eat", at = @At("HEAD"), cancellable = true)
    private void onEat(CallbackInfo ci) {
        ci.cancel();
        if (!isSlotValid()) {
            stopEating();
            return;
        }
        if (!eating && pauseBaritone.get() && PathManagers.get().isPathing() && !bephax$wasBaritone) {
            bephax$wasBaritone = true;
            PathManagers.get().pause();
        }
        InventoryManager invManager = InventoryManager.getInstance();
        int serverSlot = invManager.getServerSlot();
        if (serverSlot != slot && slot != 40) {
            if (((PlayerInventoryAccessor) mc.player.getInventory()).getSelectedSlot() != slot) {
                ((PlayerInventoryAccessor) mc.player.getInventory()).setSelectedSlot(slot);
            }
            invManager.setSlotForced(slot);
        } else if (slot != 40) {
            bephax$changeSlot(slot);
        }
        invManager.setEating(true);
        boolean shouldPressKey = mc.currentScreen == null;
        if (shouldPressKey) {
            mc.options.useKey.setPressed(true);
        }
        if (!mc.player.isUsingItem()) Utils.rightClick();
        eating = true;
    }
    @Inject(method = "onTick", at = @At("HEAD"))
    private void onTickValidate(CallbackInfo ci) {
        if (eating && !isSlotValid()) {
            stopEating();
            return;
        }
        if (eating) {
            boolean shouldPressKey = mc.currentScreen == null;
            if (mc.options != null) {
                if (shouldPressKey && !mc.options.useKey.isPressed()) {
                    mc.options.useKey.setPressed(true);
                } else if (!shouldPressKey && mc.options.useKey.isPressed()) {
                    mc.options.useKey.setPressed(false);
                }
            }
        }
    }
    @Inject(method = "stopEating", at = @At("HEAD"))
    private void onStopEating(CallbackInfo ci) {
        InventoryManager.getInstance().setEating(false);
        bephax$changeSlot(prevSlot);
        if (mc.options != null) {
            mc.options.useKey.setPressed(false);
        }
        if (pauseBaritone.get() && bephax$wasBaritone) {
            bephax$wasBaritone = false;
            PathManagers.get().resume();
        }
    }
    @Inject(method = "onDeactivate", at = @At("HEAD"))
    private void onDeactivateCleanup(CallbackInfo ci) {
        InventoryManager.getInstance().setEating(false);
        if (mc.options != null) {
            mc.options.useKey.setPressed(false);
        }
        if (pauseBaritone.get() && bephax$wasBaritone) {
            bephax$wasBaritone = false;
            PathManagers.get().resume();
        }
        eating = false;
    }
    @Unique
    private void bephax$changeSlot(int slot) {
        InvUtils.swap(slot, false);
        this.slot = slot;
    }
    @Unique
    private boolean isSlotValid() {
        if (mc.player == null) return false;
        ItemStack stack;
        if (slot == 40) {
            stack = mc.player.getOffHandStack();
        } else if (slot >= 0 && slot < 9) {
            stack = mc.player.getInventory().getStack(slot);
        } else {
            return false;
        }
        return !stack.isEmpty() && stack.get(DataComponentTypes.FOOD) != null;
    }
}