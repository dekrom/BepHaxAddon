package bep.hax.modules;
import bep.hax.Bep;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.item.*;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.network.packet.c2s.play.ClickSlotC2SPacket;
import net.minecraft.screen.sync.ItemStackHash;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
public class Replenish extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgItems = settings.createGroup("Items");
    private final SettingGroup sgAdvanced = settings.createGroup("Advanced");
    private final Setting<Integer> threshold = sgGeneral.add(new IntSetting.Builder()
        .name("threshold")
        .description("Refill when stack reaches this amount.")
        .defaultValue(8)
        .min(1)
        .max(63)
        .sliderMin(1)
        .sliderMax(63)
        .build());
    private final Setting<Integer> tickDelay = sgGeneral.add(new IntSetting.Builder()
        .name("tick-delay")
        .description("Delay in ticks between refill operations.")
        .defaultValue(1)
        .min(0)
        .max(10)
        .sliderMin(0)
        .sliderMax(10)
        .build());
    private final Setting<Boolean> pauseOnUse = sgGeneral.add(new BoolSetting.Builder()
        .name("pause-on-use")
        .description("Pause refilling while using items.")
        .defaultValue(true)
        .build());
    private final Setting<Boolean> smartRefill = sgGeneral.add(new BoolSetting.Builder()
        .name("smart-refill")
        .description("Only refill when you're about to run out completely.")
        .defaultValue(false)
        .build());
    private final Setting<StackPreference> stackPreference = sgGeneral.add(new EnumSetting.Builder<StackPreference>()
        .name("stack-preference")
        .description("Which stacks to prioritize when refilling.")
        .defaultValue(StackPreference.FullStacks)
        .build());
    private final Setting<Boolean> refillBlocks = sgItems.add(new BoolSetting.Builder()
        .name("blocks")
        .description("Refill building blocks.")
        .defaultValue(true)
        .build());
    private final Setting<Boolean> refillFood = sgItems.add(new BoolSetting.Builder()
        .name("food")
        .description("Refill food items.")
        .defaultValue(true)
        .build());
    private final Setting<Boolean> refillTools = sgItems.add(new BoolSetting.Builder()
        .name("tools")
        .description("Refill tools (pickaxe, axe, shovel, etc).")
        .defaultValue(false)
        .build());
    private final Setting<Boolean> refillWeapons = sgItems.add(new BoolSetting.Builder()
        .name("weapons")
        .description("Refill weapons (sword, bow, crossbow).")
        .defaultValue(false)
        .build());
    private final Setting<Boolean> refillProjectiles = sgItems.add(new BoolSetting.Builder()
        .name("projectiles")
        .description("Refill projectiles (arrows, fireworks).")
        .defaultValue(true)
        .build());
    private final Setting<Boolean> refillPearls = sgItems.add(new BoolSetting.Builder()
        .name("ender-pearls")
        .description("Refill ender pearls.")
        .defaultValue(true)
        .build());
    private final Setting<Boolean> refillPotions = sgItems.add(new BoolSetting.Builder()
        .name("potions")
        .description("Refill potions.")
        .defaultValue(true)
        .build());
    private final Setting<Boolean> refillTotems = sgItems.add(new BoolSetting.Builder()
        .name("totems")
        .description("Refill totems of undying.")
        .defaultValue(true)
        .build());
    private final Setting<Boolean> refillGaps = sgItems.add(new BoolSetting.Builder()
        .name("golden-apples")
        .description("Refill golden apples.")
        .defaultValue(true)
        .build());
    private final Setting<Boolean> refillFireworks = sgItems.add(new BoolSetting.Builder()
        .name("fireworks")
        .description("Refill firework rockets.")
        .defaultValue(true)
        .build());
    private final Setting<Boolean> useShiftClick = sgAdvanced.add(new BoolSetting.Builder()
        .name("use-shift-click")
        .description("Use shift-click packets for faster refilling.")
        .defaultValue(true)
        .build());
    private final Setting<Boolean> silentRefill = sgAdvanced.add(new BoolSetting.Builder()
        .name("silent-refill")
        .description("Refill without opening inventory (packet-based).")
        .defaultValue(true)
        .build());
    private final Setting<Integer> maxRefillsPerTick = sgAdvanced.add(new IntSetting.Builder()
        .name("max-refills-per-tick")
        .description("Maximum refill operations per tick.")
        .defaultValue(1)
        .min(1)
        .max(5)
        .sliderMin(1)
        .sliderMax(5)
        .build());
    private final Setting<Boolean> maintainTool = sgAdvanced.add(new BoolSetting.Builder()
        .name("maintain-tool-type")
        .description("Only replace tools with the same type and material.")
        .defaultValue(true)
        .build());
    private final Setting<Boolean> respectCustomNames = sgAdvanced.add(new BoolSetting.Builder()
        .name("respect-custom-names")
        .description("Wait until custom-named items are completely empty before replacing with differently-named items.")
        .defaultValue(true)
        .build());
    private final Setting<Boolean> refillAllStackable = sgAdvanced.add(new BoolSetting.Builder()
        .name("refill-all-stackable")
        .description("Refill ALL stackable items, not just specific categories.")
        .defaultValue(true)
        .build());
    private int delayTicks = 0;
    private final Map<Integer, Integer> lastStackSizes = new HashMap<>();
    private final List<RefillOperation> pendingRefills = new ArrayList<>();
    private final Map<Integer, String> hotbarItemNames = new HashMap<>();
    public Replenish() {
        super(Bep.CATEGORY, "replenish", "Advanced auto replenish using shift-click packets.");
    }
    @Override
    public void onActivate() {
        delayTicks = 0;
        lastStackSizes.clear();
        pendingRefills.clear();
        hotbarItemNames.clear();
    }
    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;
        if (delayTicks > 0) {
            delayTicks--;
            return;
        }
        if (pauseOnUse.get() && mc.player.isUsingItem()) {
            return;
        }
        if (!pendingRefills.isEmpty()) {
            processPendingRefills();
            return;
        }
        checkHotbar();
    }
    private void checkHotbar() {
        int refillsThisTick = 0;
        for (int hotbarSlot = 0; hotbarSlot < 9; hotbarSlot++) {
            if (refillsThisTick >= maxRefillsPerTick.get()) break;
            ItemStack hotbarStack = mc.player.getInventory().getStack(hotbarSlot);
            if (hotbarStack.isEmpty()) {
                hotbarItemNames.remove(hotbarSlot);
                continue;
            }
            if (!shouldRefillItem(hotbarStack)) continue;
            int currentSize = hotbarStack.getCount();
            int maxSize = hotbarStack.getMaxCount();
            if (smartRefill.get()) {
                Integer lastSize = lastStackSizes.get(hotbarSlot);
                if (lastSize != null && lastSize > currentSize && currentSize <= 1) {
                    if (attemptRefill(hotbarSlot, hotbarStack)) {
                        refillsThisTick++;
                    }
                }
                lastStackSizes.put(hotbarSlot, currentSize);
            } else {
                if (currentSize <= threshold.get() && currentSize < maxSize) {
                    if (attemptRefill(hotbarSlot, hotbarStack)) {
                        refillsThisTick++;
                    }
                }
            }
        }
        if (refillsThisTick > 0) {
            delayTicks = tickDelay.get();
        }
    }
    private boolean shouldRefillItem(ItemStack stack) {
        Item item = stack.getItem();
        if (refillAllStackable.get() && stack.getMaxCount() > 1) {
            return true;
        }
        if (refillTotems.get() && item == Items.TOTEM_OF_UNDYING) return true;
        if (refillPearls.get() && item == Items.ENDER_PEARL) return true;
        if (refillGaps.get() && (item == Items.GOLDEN_APPLE || item == Items.ENCHANTED_GOLDEN_APPLE)) return true;
        if (refillFireworks.get() && item == Items.FIREWORK_ROCKET) return true;
        if (refillBlocks.get() && item instanceof BlockItem) return true;
        if (refillFood.get() && item.getComponents().contains(net.minecraft.component.DataComponentTypes.FOOD)) return true;
        if (refillTools.get() && (item instanceof ShovelItem || item instanceof AxeItem || item instanceof HoeItem || item.toString().toLowerCase().contains("pickaxe"))) return true;
        if (refillWeapons.get() && (item.toString().toLowerCase().contains("sword") || item instanceof BowItem || item instanceof CrossbowItem)) return true;
        if (refillProjectiles.get() && (item instanceof ArrowItem || item == Items.FIREWORK_ROCKET)) return true;
        if (refillPotions.get() && item instanceof PotionItem) return true;
        return false;
    }
    private boolean attemptRefill(int hotbarSlot, ItemStack hotbarStack) {
        if (respectCustomNames.get() && hotbarStack.getMaxCount() > 1) {
            String currentName = getItemName(hotbarStack);
            String trackedName = hotbarItemNames.get(hotbarSlot);
            if (trackedName == null) {
                hotbarItemNames.put(hotbarSlot, currentName);
                trackedName = currentName;
            }
            int sourceSlot = findSourceSlot(hotbarStack);
            if (sourceSlot == -1) {
                hotbarItemNames.remove(hotbarSlot);
                return false;
            }
            ItemStack sourceStack = mc.player.getInventory().getStack(sourceSlot);
            String sourceName = getItemName(sourceStack);
            if (!trackedName.equals(sourceName)) {
                if (hotbarStack.getCount() > 1) {
                    return false;
                }
                hotbarItemNames.put(hotbarSlot, sourceName);
            }
        } else {
            int sourceSlot = findSourceSlot(hotbarStack);
            if (sourceSlot == -1) return false;
        }
        int sourceSlot = findSourceSlot(hotbarStack);
        if (sourceSlot == -1) return false;
        RefillOperation operation = new RefillOperation(sourceSlot, hotbarSlot + 36, hotbarStack.getItem());
        if (useShiftClick.get()) {
            performShiftClickRefill(operation);
        } else {
            performNormalRefill(operation);
        }
        return true;
    }
    private String getItemName(ItemStack stack) {
        if (stack.contains(net.minecraft.component.DataComponentTypes.CUSTOM_NAME)) {
            net.minecraft.text.Text customName = stack.get(net.minecraft.component.DataComponentTypes.CUSTOM_NAME);
            if (customName != null) {
                return customName.getString();
            }
        }
        return stack.getItem().getName().getString();
    }
    private int findSourceSlot(ItemStack targetStack) {
        int bestSlot = -1;
        int bestCount = 0;
        boolean searchingForMax = stackPreference.get() == StackPreference.FullStacks;
        boolean searchingForMin = stackPreference.get() == StackPreference.SmallStacks;
        if (searchingForMin) {
            bestCount = Integer.MAX_VALUE;
        }
        for (int i = 9; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.isEmpty()) continue;
            if (!canStack(targetStack, stack)) continue;
            if (maintainTool.get() && (targetStack.getItem() instanceof ShovelItem || targetStack.getItem() instanceof AxeItem || targetStack.getItem() instanceof HoeItem || targetStack.getItem().toString().toLowerCase().contains("pickaxe"))) {
                if (stack.getItem().getClass() != targetStack.getItem().getClass()) continue;
            }
            if (stackPreference.get() == StackPreference.FirstMatch) {
                return i;
            } else if (searchingForMax) {
                if (stack.getCount() > bestCount) {
                    bestCount = stack.getCount();
                    bestSlot = i;
                }
            } else if (searchingForMin) {
                if (stack.getCount() < bestCount) {
                    bestCount = stack.getCount();
                    bestSlot = i;
                }
            }
        }
        return bestSlot;
    }
    private boolean canStack(ItemStack stack1, ItemStack stack2) {
        if (stack1.getItem() != stack2.getItem()) return false;
        if (stack1.getMaxCount() == 1) {
            return true;
        }
        if (respectCustomNames.get()) {
            return ItemStack.areItemsEqual(stack1, stack2);
        } else {
            return ItemStack.areItemsAndComponentsEqual(stack1, stack2);
        }
    }
    private void performShiftClickRefill(RefillOperation operation) {
        if (silentRefill.get()) {
            sendShiftClickPacket(operation.sourceSlot);
        } else {
            pendingRefills.add(operation);
        }
    }
    private void performNormalRefill(RefillOperation operation) {
        InvUtils.move().from(operation.sourceSlot).to(operation.targetSlot - 36);
    }
    private void sendShiftClickPacket(int slot) {
        int syncId = mc.player.currentScreenHandler.syncId;
        mc.interactionManager.clickSlot(
            syncId,
            slot,
            0,
            SlotActionType.QUICK_MOVE,
            mc.player
        );
    }
    private void processPendingRefills() {
        if (mc.player.currentScreenHandler != mc.player.playerScreenHandler) {
            return;
        }
        int processed = 0;
        while (!pendingRefills.isEmpty() && processed < maxRefillsPerTick.get()) {
            RefillOperation operation = pendingRefills.remove(0);
            sendShiftClickPacket(operation.sourceSlot);
            processed++;
        }
        if (processed > 0) {
            delayTicks = tickDelay.get();
        }
    }
    private static class RefillOperation {
        final int sourceSlot;
        final int targetSlot;
        final Item item;
        RefillOperation(int sourceSlot, int targetSlot, Item item) {
            this.sourceSlot = sourceSlot;
            this.targetSlot = targetSlot;
            this.item = item;
        }
    }
    @Override
    public String getInfoString() {
        if (!pendingRefills.isEmpty()) {
            return "Refilling (" + pendingRefills.size() + ")";
        }
        return null;
    }
    public enum StackPreference {
        FirstMatch("First Match"),
        FullStacks("Full Stacks"),
        SmallStacks("Small Stacks");
        private final String title;
        StackPreference(String title) {
            this.title = title;
        }
        @Override
        public String toString() {
            return title;
        }
    }
}