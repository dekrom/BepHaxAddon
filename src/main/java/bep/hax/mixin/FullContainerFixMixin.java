package bep.hax.mixin;
import bep.hax.modules.InvFix;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(ClientPlayerInteractionManager.class)
public class FullContainerFixMixin {
    @Inject(method = "clickSlot", at = @At("HEAD"), cancellable = true)
    private void onClickSlot(int syncId, int slotId, int button, SlotActionType actionType, PlayerEntity player, CallbackInfo ci) {
        InvFix module = Modules.get().get(InvFix.class);
        if (module == null || !module.shouldPreventFullContainerClicks()) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;
        ScreenHandler handler = mc.player.currentScreenHandler;
        if (handler == null || handler.syncId != syncId) return;
        if (actionType != SlotActionType.QUICK_MOVE) return;
        if (slotId < 0 || slotId >= handler.slots.size()) return;
        Slot sourceSlot = handler.getSlot(slotId);
        ItemStack sourceStack = sourceSlot.getStack();
        if (sourceStack.isEmpty()) return;
        boolean isFromPlayerInventory = slotId >= handler.slots.size() - 36;
        if (isFromPlayerInventory) {
            if (isContainerFull(handler, sourceStack)) {
                ci.cancel();
                return;
            }
        } else {
            if (isPlayerInventoryFull(handler, sourceStack)) {
                ci.cancel();
                return;
            }
        }
    }
    private boolean isContainerFull(ScreenHandler handler, ItemStack itemToMove) {
        int containerSlots = handler.slots.size() - 36;
        for (int i = 0; i < containerSlots; i++) {
            Slot slot = handler.getSlot(i);
            if (!slot.canInsert(itemToMove)) continue;
            ItemStack slotStack = slot.getStack();
            if (slotStack.isEmpty()) return false;
            if (ItemStack.areItemsAndComponentsEqual(slotStack, itemToMove) &&
                slotStack.getCount() < slotStack.getMaxCount()) {
                return false;
            }
        }
        return true;
    }
    private boolean isPlayerInventoryFull(ScreenHandler handler, ItemStack itemToMove) {
        int totalSlots = handler.slots.size();
        int playerInvStart = totalSlots - 36;
        for (int i = playerInvStart; i < totalSlots; i++) {
            Slot slot = handler.getSlot(i);
            ItemStack slotStack = slot.getStack();
            if (slotStack.isEmpty()) return false;
            if (ItemStack.areItemsAndComponentsEqual(slotStack, itemToMove) &&
                slotStack.getCount() < slotStack.getMaxCount()) {
                return false;
            }
        }
        return true;
    }
}