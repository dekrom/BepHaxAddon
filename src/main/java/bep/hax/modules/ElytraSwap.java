package bep.hax.modules;
import bep.hax.Bep;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
public class ElytraSwap extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final Setting<Integer> durabilityThreshold = sgGeneral.add(new IntSetting.Builder()
        .name("Durability Threshold")
        .description("Swap elytra when durability drops below this value.")
        .defaultValue(10)
        .min(1)
        .max(100)
        .sliderRange(1, 50)
        .build()
    );
    private final Setting<Boolean> onlyWhileFlying = sgGeneral.add(new BoolSetting.Builder()
        .name("Only While Flying")
        .description("Only swap elytras while actively flying.")
        .defaultValue(false)
        .build()
    );
    private final Setting<Boolean> pauseInInventory = sgGeneral.add(new BoolSetting.Builder()
        .name("Pause In Inventory")
        .description("Don't swap while inventory is open to prevent desync.")
        .defaultValue(true)
        .build()
    );
    private final Setting<Integer> swapCooldown = sgGeneral.add(new IntSetting.Builder()
        .name("Swap Cooldown")
        .description("Ticks to wait after swapping before checking again.")
        .defaultValue(100)
        .min(20)
        .max(200)
        .sliderRange(20, 200)
        .build()
    );
    private final Setting<Boolean> notifySwap = sgGeneral.add(new BoolSetting.Builder()
        .name("Notify Swap")
        .description("Send a chat message when swapping elytras.")
        .defaultValue(true)
        .build()
    );
    private final SettingGroup sgCombat = settings.createGroup("Combat Protection");
    private final Setting<Boolean> swapOnHit = sgCombat.add(new BoolSetting.Builder()
        .name("Swap On Hit")
        .description("Automatically swap elytra to chestplate when hit by an entity.")
        .defaultValue(false)
        .build()
    );
    private final Setting<Integer> hitProtectionDuration = sgCombat.add(new IntSetting.Builder()
        .name("Protection Duration")
        .description("Ticks to keep chestplate equipped after being hit.")
        .defaultValue(60)
        .min(20)
        .max(200)
        .sliderRange(20, 200)
        .visible(swapOnHit::get)
        .build()
    );
    private final Setting<Boolean> autoSwapBack = sgCombat.add(new BoolSetting.Builder()
        .name("Auto Swap Back")
        .description("Automatically swap back to elytra after protection duration.")
        .defaultValue(true)
        .visible(swapOnHit::get)
        .build()
    );
    private final Setting<Boolean> prioritizeNetherite = sgCombat.add(new BoolSetting.Builder()
        .name("Prioritize Netherite")
        .description("Prioritize netherite chestplates over diamond.")
        .defaultValue(true)
        .visible(swapOnHit::get)
        .build()
    );
    private int cooldownTimer = 0;
    private boolean needsSwap = false;
    private int swapStage = 0;
    private int stageTimer = 0;
    private int targetSlot = -1;
    private int newElytraOriginalSlot = -1;
    private int hotbarSlotUsed = -1;
    private ItemStack hotbarOriginalItem = ItemStack.EMPTY;
    private boolean protectionActive = false;
    private int protectionTimer = 0;
    private int lastHurtTime = 0;
    private boolean needsChestplateSwap = false;
    private int chestplateSwapStage = 0;
    private int chestplateSlot = -1;
    private ItemStack storedElytra = ItemStack.EMPTY;
    public ElytraSwap() {
        super(
            Bep.CATEGORY,
            "ElytraSwap",
            "Automatically swaps elytras when they reach low durability."
        );
    }
    @Override
    public void onActivate() {
        resetSwapState();
    }
    @Override
    public void onDeactivate() {
        resetSwapState();
    }
    private void resetSwapState() {
        cooldownTimer = 0;
        needsSwap = false;
        swapStage = 0;
        stageTimer = 0;
        targetSlot = -1;
        newElytraOriginalSlot = -1;
        hotbarSlotUsed = -1;
        hotbarOriginalItem = ItemStack.EMPTY;
        protectionActive = false;
        protectionTimer = 0;
        lastHurtTime = 0;
        needsChestplateSwap = false;
        chestplateSwapStage = 0;
        chestplateSlot = -1;
        storedElytra = ItemStack.EMPTY;
    }
    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;
        if (swapOnHit.get()) {
            handleCombatProtection();
        }
        if (cooldownTimer > 0) {
            cooldownTimer--;
            return;
        }
        if (pauseInInventory.get() && mc.player.currentScreenHandler != mc.player.playerScreenHandler) {
            resetSwapState();
            return;
        }
        ItemStack chestItem = mc.player.getEquippedStack(EquipmentSlot.CHEST);
        if (protectionActive) {
            return;
        }
        if (!chestItem.getItem().equals(Items.ELYTRA)) {
            return;
        }
        if (onlyWhileFlying.get() && !mc.player.isGliding()) {
            return;
        }
        if (needsSwap) {
            processSwapStages();
            return;
        }
        int currentDurability = chestItem.getMaxDamage() - chestItem.getDamage();
        if (currentDurability <= durabilityThreshold.get()) {
            initiateSwap();
        }
    }
    private void initiateSwap() {
        int bestSlot = -1;
        int bestDurability = durabilityThreshold.get();
        for (int i = 0; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.getItem().equals(Items.ELYTRA)) {
                int durability = stack.getMaxDamage() - stack.getDamage();
                if (durability > bestDurability) {
                    bestDurability = durability;
                    bestSlot = i;
                }
            }
        }
        if (bestSlot == -1) {
            return;
        }
        targetSlot = bestSlot;
        needsSwap = true;
        swapStage = 1;
        stageTimer = 0;
    }
    private void processSwapStages() {
        stageTimer++;
        if (stageTimer < 5) return;
        switch (swapStage) {
            case 1 -> {
                newElytraOriginalSlot = targetSlot;
                if (targetSlot >= 9) {
                    int hotbarSlot = -1;
                    if (hotbarSlotUsed != -1 && hotbarSlotUsed < 9) {
                        hotbarSlot = hotbarSlotUsed;
                    } else {
                        for (int i = 0; i < 9; i++) {
                            ItemStack stack = mc.player.getInventory().getStack(i);
                            if (stack.isEmpty() || !isEssentialItem(stack)) {
                                hotbarSlot = i;
                                break;
                            }
                        }
                        if (hotbarSlot == -1) {
                            hotbarSlot = 0;
                        }
                    }
                    hotbarOriginalItem = mc.player.getInventory().getStack(hotbarSlot).copy();
                    hotbarSlotUsed = hotbarSlot;
                    InvUtils.move().from(targetSlot).toHotbar(hotbarSlot);
                    targetSlot = hotbarSlot;
                    swapStage = 2;
                    stageTimer = 0;
                } else {
                    hotbarSlotUsed = targetSlot;
                    hotbarOriginalItem = ItemStack.EMPTY;
                    swapStage = 2;
                    stageTimer = 0;
                }
            }
            case 2 -> {
                ItemStack toEquip = mc.player.getInventory().getStack(targetSlot);
                if (!toEquip.getItem().equals(Items.ELYTRA)) {
                    resetSwapState();
                    return;
                }
                if (toEquip.getItem().equals(Items.LEATHER_LEGGINGS) ||
                    toEquip.getItem().equals(Items.CHAINMAIL_LEGGINGS) ||
                    toEquip.getItem().equals(Items.IRON_LEGGINGS) ||
                    toEquip.getItem().equals(Items.GOLDEN_LEGGINGS) ||
                    toEquip.getItem().equals(Items.DIAMOND_LEGGINGS) ||
                    toEquip.getItem().equals(Items.NETHERITE_LEGGINGS)) {
                    resetSwapState();
                    return;
                }
                InvUtils.swap(targetSlot, false);
                mc.interactionManager.interactItem(mc.player, net.minecraft.util.Hand.MAIN_HAND);
                InvUtils.swapBack();
                swapStage = 3;
                stageTimer = 0;
            }
            case 3 -> {
                if (newElytraOriginalSlot >= 9) {
                    InvUtils.move().fromHotbar(targetSlot).to(newElytraOriginalSlot);
                    if (!hotbarOriginalItem.isEmpty()) {
                        swapStage = 4;
                        stageTimer = 0;
                        return;
                    }
                }
                if (notifySwap.get()) {
                    ItemStack newChest = mc.player.getEquippedStack(EquipmentSlot.CHEST);
                    if (newChest.getItem().equals(Items.ELYTRA)) {
                        int newDurability = newChest.getMaxDamage() - newChest.getDamage();
                        info("Swapped to elytra with " + newDurability + " durability");
                    }
                }
                needsSwap = false;
                swapStage = 0;
                stageTimer = 0;
                targetSlot = -1;
                cooldownTimer = swapCooldown.get();
            }
            case 4 -> {
                if (stageTimer < 3) {
                    stageTimer++;
                    return;
                }
                for (int i = 9; i < 36; i++) {
                    ItemStack stack = mc.player.getInventory().getStack(i);
                    if (ItemStack.areItemsEqual(stack, hotbarOriginalItem)) {
                        InvUtils.move().from(i).toHotbar(hotbarSlotUsed);
                        break;
                    }
                }
                needsSwap = false;
                swapStage = 0;
                stageTimer = 0;
                targetSlot = -1;
                cooldownTimer = swapCooldown.get();
            }
        }
    }
    private boolean isEssentialItem(ItemStack stack) {
        return stack.getItem().equals(Items.TOTEM_OF_UNDYING) ||
               stack.getItem().equals(Items.GOLDEN_APPLE) ||
               stack.getItem().equals(Items.ENCHANTED_GOLDEN_APPLE) ||
               stack.getItem().equals(Items.ENDER_PEARL) ||
               stack.getItem().equals(Items.CHORUS_FRUIT);
    }
    private void handleCombatProtection() {
        if (mc.player == null) return;
        if (mc.player.hurtTime > 0 && mc.player.hurtTime > lastHurtTime) {
            lastHurtTime = mc.player.hurtTime;
            ItemStack chestItem = mc.player.getEquippedStack(EquipmentSlot.CHEST);
            if (chestItem.getItem().equals(Items.ELYTRA) && !protectionActive) {
                int bestChestplate = findBestChestplate();
                if (bestChestplate != -1) {
                    storedElytra = chestItem.copy();
                    chestplateSlot = bestChestplate;
                    needsChestplateSwap = true;
                    chestplateSwapStage = 1;
                    stageTimer = 0;
                    protectionActive = true;
                    protectionTimer = hitProtectionDuration.get();
                    if (notifySwap.get()) {
                        info("Swapping to chestplate for protection!");
                    }
                }
            } else if (protectionActive) {
                protectionTimer = hitProtectionDuration.get();
            }
        }
        if (mc.player.hurtTime < lastHurtTime) {
            lastHurtTime = mc.player.hurtTime;
        }
        if (needsChestplateSwap) {
            processChestplateSwap();
            return;
        }
        if (protectionActive && !needsChestplateSwap) {
            protectionTimer--;
            if (protectionTimer <= 0 && autoSwapBack.get()) {
                ItemStack chestItem = mc.player.getEquippedStack(EquipmentSlot.CHEST);
                if (!chestItem.getItem().equals(Items.ELYTRA) && !storedElytra.isEmpty()) {
                    int elytraSlot = findStoredElytra();
                    if (elytraSlot != -1) {
                        chestplateSlot = elytraSlot;
                        needsChestplateSwap = true;
                        chestplateSwapStage = 1;
                        stageTimer = 0;
                        if (notifySwap.get()) {
                            info("Protection period ended, swapping back to elytra.");
                        }
                    } else {
                        protectionActive = false;
                        storedElytra = ItemStack.EMPTY;
                    }
                } else {
                    protectionActive = false;
                    storedElytra = ItemStack.EMPTY;
                }
            }
        }
    }
    private void processChestplateSwap() {
        stageTimer++;
        if (stageTimer < 3) return;
        switch (chestplateSwapStage) {
            case 1 -> {
                if (chestplateSlot >= 9) {
                    int hotbarSlot = 0;
                    for (int i = 0; i < 9; i++) {
                        ItemStack stack = mc.player.getInventory().getStack(i);
                        if (stack.isEmpty() || !isEssentialItem(stack)) {
                            hotbarSlot = i;
                            break;
                        }
                    }
                    InvUtils.move().from(chestplateSlot).toHotbar(hotbarSlot);
                    chestplateSlot = hotbarSlot;
                }
                chestplateSwapStage = 2;
                stageTimer = 0;
            }
            case 2 -> {
                ItemStack toEquip = mc.player.getInventory().getStack(chestplateSlot);
                if (!isChestplateItem(toEquip)) {
                    needsChestplateSwap = false;
                    chestplateSwapStage = 0;
                    return;
                }
                if (toEquip.getItem().equals(Items.LEATHER_LEGGINGS) ||
                    toEquip.getItem().equals(Items.CHAINMAIL_LEGGINGS) ||
                    toEquip.getItem().equals(Items.IRON_LEGGINGS) ||
                    toEquip.getItem().equals(Items.GOLDEN_LEGGINGS) ||
                    toEquip.getItem().equals(Items.DIAMOND_LEGGINGS) ||
                    toEquip.getItem().equals(Items.NETHERITE_LEGGINGS)) {
                    needsChestplateSwap = false;
                    chestplateSwapStage = 0;
                    return;
                }
                InvUtils.swap(chestplateSlot, false);
                mc.interactionManager.interactItem(mc.player, net.minecraft.util.Hand.MAIN_HAND);
                InvUtils.swapBack();
                chestplateSwapStage = 3;
                stageTimer = 0;
            }
            case 3 -> {
                needsChestplateSwap = false;
                chestplateSwapStage = 0;
                stageTimer = 0;
                chestplateSlot = -1;
                ItemStack chestItem = mc.player.getEquippedStack(EquipmentSlot.CHEST);
                if (chestItem.getItem().equals(Items.ELYTRA)) {
                    protectionActive = false;
                    storedElytra = ItemStack.EMPTY;
                }
            }
        }
    }
    private int findBestChestplate() {
        int bestSlot = -1;
        int bestValue = 0;
        for (int i = 0; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            int value = getChestplateValue(stack);
            if (value > bestValue) {
                bestValue = value;
                bestSlot = i;
            }
        }
        return bestSlot;
    }
    private int getChestplateValue(ItemStack stack) {
        if (stack.isEmpty()) return 0;
        if (stack.getItem().equals(Items.NETHERITE_CHESTPLATE)) {
            if (prioritizeNetherite.get()) {
                return 1000 + (stack.getMaxDamage() - stack.getDamage());
            }
            return 400 + (stack.getMaxDamage() - stack.getDamage());
        } else if (stack.getItem().equals(Items.DIAMOND_CHESTPLATE)) {
            return 300 + (stack.getMaxDamage() - stack.getDamage());
        } else if (stack.getItem().equals(Items.IRON_CHESTPLATE)) {
            return 200 + (stack.getMaxDamage() - stack.getDamage());
        } else if (stack.getItem().equals(Items.GOLDEN_CHESTPLATE)) {
            return 100 + (stack.getMaxDamage() - stack.getDamage());
        } else if (stack.getItem().equals(Items.CHAINMAIL_CHESTPLATE)) {
            return 150 + (stack.getMaxDamage() - stack.getDamage());
        } else if (stack.getItem().equals(Items.LEATHER_CHESTPLATE)) {
            return 50 + (stack.getMaxDamage() - stack.getDamage());
        }
        return 0;
    }
    private boolean isChestplateItem(ItemStack stack) {
        if (stack.isEmpty()) return false;
        return stack.getItem().equals(Items.ELYTRA) ||
               stack.getItem().equals(Items.NETHERITE_CHESTPLATE) ||
               stack.getItem().equals(Items.DIAMOND_CHESTPLATE) ||
               stack.getItem().equals(Items.IRON_CHESTPLATE) ||
               stack.getItem().equals(Items.GOLDEN_CHESTPLATE) ||
               stack.getItem().equals(Items.CHAINMAIL_CHESTPLATE) ||
               stack.getItem().equals(Items.LEATHER_CHESTPLATE);
    }
    private int findStoredElytra() {
        for (int i = 0; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.getItem().equals(Items.ELYTRA)) {
                if (Math.abs(stack.getDamage() - storedElytra.getDamage()) <= 5) {
                    return i;
                }
            }
        }
        for (int i = 0; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.getItem().equals(Items.ELYTRA)) {
                return i;
            }
        }
        return -1;
    }
}