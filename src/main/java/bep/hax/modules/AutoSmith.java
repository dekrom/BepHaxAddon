package bep.hax.modules;
import java.util.List;
import java.util.ArrayDeque;
import net.minecraft.item.*;
import bep.hax.Bep;
import bep.hax.util.MsgUtil;
import bep.hax.util.StardustUtil;
import net.minecraft.sound.SoundEvents;
import org.jetbrains.annotations.Nullable;
import meteordevelopment.orbit.EventHandler;
import java.util.concurrent.ThreadLocalRandom;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.screen.slot.SlotActionType;
import meteordevelopment.meteorclient.settings.*;
import net.minecraft.screen.SmithingScreenHandler;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.screen.sync.ItemStackHash;
import bep.hax.mixin.accessor.PlayerInventoryAccessor;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import net.minecraft.client.gui.screen.ingame.SmithingScreen;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.events.world.TickEvent;
import net.minecraft.network.packet.c2s.play.ClickSlotC2SPacket;
import meteordevelopment.meteorclient.events.game.OpenScreenEvent;
public class AutoSmith extends Module {
    public AutoSmith() {
        super(Bep.STARDUST, "AutoSmith", "Automatically upgrade gear in smithing tables with configurable templates, materials, and equipment.");
    }
    public enum ModuleMode {
        Packet, Interact
    }
    private final SettingGroup sgMode = settings.createGroup("Mode Settings");
    private final SettingGroup sgItems = settings.createGroup("Items");
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final Setting<ModuleMode> moduleMode = sgMode.add(
        new EnumSetting.Builder<ModuleMode>()
            .name("module-mode")
            .description("Packet is significantly faster, but may get you kicked in some scenarios.")
            .defaultValue(ModuleMode.Packet)
            .build()
    );
    private final Setting<Integer> tickRate = sgMode.add(
        new IntSetting.Builder()
            .name("tick-delay")
            .description("Increase this if the server is kicking you.")
            .visible(() -> moduleMode.get().equals(ModuleMode.Interact))
            .range(2, 100)
            .sliderRange(2, 20)
            .defaultValue(4)
            .build()
    );
    private final Setting<Integer> packetLimit = sgMode.add(
        new IntSetting.Builder()
            .name("packet-limit")
            .description("Decrease this if the server is kicking you.")
            .visible(() -> moduleMode.get().equals(ModuleMode.Packet))
            .min(20).sliderMax(100)
            .defaultValue(42)
            .build()
    );
    private final Setting<List<Item>> templates = sgItems.add(
        new ItemListSetting.Builder()
            .name("templates")
            .description("Smithing templates to use for upgrading.")
            .defaultValue(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE)
            .build()
    );
    private final Setting<List<Item>> materials = sgItems.add(
        new ItemListSetting.Builder()
            .name("materials")
            .description("Materials to use for upgrading (ingots, etc).")
            .defaultValue(Items.NETHERITE_INGOT)
            .build()
    );
    private final Setting<List<Item>> equipment = sgItems.add(
        new ItemListSetting.Builder()
            .name("equipment")
            .description("Equipment to upgrade.")
            .defaultValue(
                Items.DIAMOND_HELMET,
                Items.DIAMOND_CHESTPLATE,
                Items.DIAMOND_LEGGINGS,
                Items.DIAMOND_BOOTS,
                Items.DIAMOND_SWORD,
                Items.DIAMOND_PICKAXE,
                Items.DIAMOND_AXE,
                Items.DIAMOND_SHOVEL,
                Items.DIAMOND_HOE
            )
            .build()
    );
    public final Setting<Boolean> muteSmithy = sgGeneral.add(
        new BoolSetting.Builder()
            .name("mute-smithing-table")
            .description("Mute the smithing table sounds.")
            .defaultValue(true)
            .build()
    );
    private final Setting<Boolean> closeOnDone = sgGeneral.add(
        new BoolSetting.Builder()
            .name("close-screen")
            .description("Automatically close the crafting screen when no more gear can be upgraded.")
            .defaultValue(true)
            .build()
    );
    private final Setting<Boolean> disableOnDone = sgGeneral.add(
        new BoolSetting.Builder()
            .name("disable-on-done")
            .description("Automatically disable the module when no more gear can be upgraded.")
            .defaultValue(false)
            .build()
    );
    private final Setting<Boolean> pingOnDone = sgGeneral.add(
        new BoolSetting.Builder()
            .name("sound-ping")
            .description("Play a sound cue when no more gear can be upgraded.")
            .defaultValue(true)
            .build()
    );
    private final Setting<Double> pingVolume = sgGeneral.add(
        new DoubleSetting.Builder()
            .name("ping-volume")
            .visible(pingOnDone::get)
            .sliderMin(0.0)
            .sliderMax(5.0)
            .defaultValue(0.5)
            .build()
    );
    private int timer = 0;
    private boolean notified = false;
    private boolean foundEquip = false;
    private boolean foundMaterial = false;
    private boolean foundTemplate = false;
    private @Nullable ItemStack templateStack = null;
    private @Nullable ItemStack materialStack = null;
    private @Nullable ItemStack equipmentStack = null;
    private final IntArrayList projectedEmpty = new IntArrayList();
    private final IntArrayList processedSlots = new IntArrayList();
    private int getInvSize() {
        return ((PlayerInventoryAccessor) mc.player.getInventory()).getMain().size();
    }
    private boolean isValidEquipment(ItemStack stack) {
        return equipment.get().contains(stack.getItem());
    }
    private boolean isValidTemplate(ItemStack stack) {
        return templates.get().contains(stack.getItem());
    }
    private boolean isValidMaterial(ItemStack stack) {
        return materials.get().contains(stack.getItem());
    }
    @Override
    public void onDeactivate() {
        timer = 0;
        templateStack = null;
        notified = false;
        foundEquip = false;
        foundMaterial = false;
        materialStack = null;
        equipmentStack = null;
        foundTemplate = false;
        processedSlots.clear();
        projectedEmpty.clear();
    }
    @EventHandler
    private void onScreenOpened(OpenScreenEvent event) {
        if (event.screen instanceof SmithingScreen) {
            notified = false;
        }
    }
    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null) return;
        if (mc.getNetworkHandler() == null) return;
        if (mc.currentScreen == null) {
            notified = false;
            return;
        }
        if (!(mc.currentScreen instanceof SmithingScreen)) return;
        if (!(mc.player.currentScreenHandler instanceof SmithingScreenHandler ss)) return;
        switch (moduleMode.get()) {
            case Packet -> {
                if (notified) return;
                ArrayDeque<ClickSlotC2SPacket> packets = new ArrayDeque<>();
                boolean exhausted = false;
                while (!exhausted) {
                    ClickSlotC2SPacket packet = generateSmithingPacket(ss);
                    if (packet == null) {
                        exhausted = true;
                    } else if (packets.size() >= packetLimit.get()) {
                        exhausted = true;
                        packets.addLast(packet);
                        MsgUtil.sendModuleMsg("Packet limit was hit§c..! §7You may need to run the module again§c...", this.name);
                    } else {
                        packets.addLast(packet);
                    }
                }
                while (!packets.isEmpty()) {
                    mc.getNetworkHandler()
                        .getConnection()
                        .send(packets.removeFirst());
                }
                finished();
            }
            case Interact -> {
                if (timer >= tickRate.get()) {
                    timer = 0;
                } else {
                    ++timer;
                    return;
                }
                ItemStack output = ss.getSlot(SmithingScreenHandler.OUTPUT_ID).getStack();
                if (!output.isEmpty()) {
                    InvUtils.shiftClick().slotId(SmithingScreenHandler.OUTPUT_ID);
                    foundEquip = false;
                    int materialsRemaining = ss.getSlot(SmithingScreenHandler.MATERIAL_ID).getStack().getCount();
                    int templatesRemaining = ss.getSlot(SmithingScreenHandler.TEMPLATE_ID).getStack().getCount();
                    if (materialsRemaining == 0) foundMaterial = false;
                    if (templatesRemaining == 0) foundTemplate = false;
                } else if (!foundEquip) {
                    for (int n = 4; n < getInvSize() + 4; n++) {
                        ItemStack stack = ss.getSlot(n).getStack();
                        if (isValidEquipment(stack)) {
                            foundEquip = true;
                            InvUtils.shiftClick().slotId(n);
                            break;
                        }
                    }
                    if (!foundEquip && !notified) {
                        MsgUtil.sendModuleMsg("No gear left to upgrade§c..!", this.name);
                        finished();
                    }
                } else if (!foundMaterial) {
                    for (int n = 4; n < getInvSize() + 4; n++) {
                        ItemStack stack = ss.getSlot(n).getStack();
                        if (isValidMaterial(stack)) {
                            foundMaterial = true;
                            InvUtils.shiftClick().slotId(n);
                            break;
                        }
                    }
                    if (!foundMaterial && !notified) {
                        MsgUtil.sendModuleMsg("No materials left to use§c..!", this.name);
                        finished();
                    }
                } else if (!foundTemplate) {
                    for (int n = 4; n < getInvSize() + 4; n++) {
                        ItemStack stack = ss.getSlot(n).getStack();
                        if (isValidTemplate(stack)) {
                            foundTemplate = true;
                            InvUtils.shiftClick().slotId(n);
                            break;
                        }
                    }
                    if (!foundTemplate && !notified) {
                        MsgUtil.sendModuleMsg("No templates left to use§c..!", this.name);
                        finished();
                    }
                } else {
                    timer = tickRate.get() - 1;
                }
            }
        }
    }
    private void finished() {
        if (mc.player == null) {
            notified = true;
            return;
        }
        if (!notified) {
            MsgUtil.sendModuleMsg("Finished processing items" + StardustUtil.rCC() + "..!", this.name);
            if (pingOnDone.get()) {
                mc.player.playSound(
                    SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP,
                    pingVolume.get().floatValue(),
                    ThreadLocalRandom.current().nextFloat(0.69f, 1.337f)
                );
            }
        }
        notified = true;
        processedSlots.clear();
        projectedEmpty.clear();
        if (closeOnDone.get()) mc.player.closeHandledScreen();
        if (disableOnDone.get()) toggle();
    }
    private @Nullable ClickSlotC2SPacket generateSmithingPacket(SmithingScreenHandler handler) {
        if (mc.player == null) return null;
        Int2ObjectMap<ItemStack> changedSlots = new Int2ObjectOpenHashMap<>();
        if (templateStack != null && materialStack != null && equipmentStack != null) {
            int templateCount = templateStack.getCount();
            int materialCount = materialStack.getCount();
            changedSlots.put(SmithingScreenHandler.OUTPUT_ID, ItemStack.EMPTY);
            changedSlots.put(SmithingScreenHandler.EQUIPMENT_ID, ItemStack.EMPTY);
            if (templateCount - 1 > 0) {
                ItemStack newTemplateStack = templateStack.copyWithCount(templateCount - 1);
                changedSlots.put(SmithingScreenHandler.TEMPLATE_ID, newTemplateStack);
                templateStack = newTemplateStack;
            } else {
                changedSlots.put(SmithingScreenHandler.TEMPLATE_ID, ItemStack.EMPTY);
                templateStack = null;
            }
            if (materialCount - 1 > 0) {
                ItemStack newMaterialStack = materialStack.copyWithCount(materialCount - 1);
                changedSlots.put(SmithingScreenHandler.MATERIAL_ID, newMaterialStack);
                materialStack = newMaterialStack;
            } else {
                changedSlots.put(SmithingScreenHandler.MATERIAL_ID, ItemStack.EMPTY);
                materialStack = null;
            }
            int shiftClickTargetSlot = predictEmptySlot(handler);
            if (shiftClickTargetSlot == -1) {
                MsgUtil.sendModuleMsg("Failed to predict empty target slot§c..!", this.name);
                return null;
            }
            ItemStack output = getUpgradedItem(equipmentStack);
            changedSlots.put(shiftClickTargetSlot, output);
            equipmentStack = null;
            Int2ObjectMap<ItemStackHash> hashMap = new Int2ObjectOpenHashMap<>();
            changedSlots.forEach((slot, stack) -> hashMap.put(slot.intValue(), ItemStackHash.fromItemStack(stack, component -> 0)));
            return new ClickSlotC2SPacket(
                handler.syncId, handler.getRevision(), (short) SmithingScreenHandler.OUTPUT_ID, (byte) 0,
                SlotActionType.QUICK_MOVE, hashMap, ItemStackHash.fromItemStack(ItemStack.EMPTY, component -> 0)
            );
        }
        if (equipmentStack == null) {
            for (int n = 4; n < getInvSize() + 4; n++) {
                if (processedSlots.contains(n)) continue;
                ItemStack stack = handler.getSlot(n).getStack();
                if (isValidEquipment(stack)) {
                    equipmentStack = stack;
                    processedSlots.add(n);
                    projectedEmpty.add(n);
                    processedSlots.add(SmithingScreenHandler.EQUIPMENT_ID);
                    changedSlots.put(SmithingScreenHandler.EQUIPMENT_ID, stack);
                    changedSlots.put(n, ItemStack.EMPTY);
                    if (templateStack != null && materialStack != null) {
                        ItemStack output = getUpgradedItem(equipmentStack);
                        changedSlots.put(SmithingScreenHandler.OUTPUT_ID, output);
                    }
                    Int2ObjectMap<ItemStackHash> hashMap = new Int2ObjectOpenHashMap<>();
                    changedSlots.forEach((slot, stack2) -> hashMap.put(slot.intValue(), ItemStackHash.fromItemStack(stack2, component -> 0)));
                    return new ClickSlotC2SPacket(
                        handler.syncId, handler.getRevision(), (short) n, (byte) 0,
                        SlotActionType.QUICK_MOVE, hashMap, ItemStackHash.fromItemStack(ItemStack.EMPTY, component -> 0)
                    );
                }
            }
            return null;
        }
        if (materialStack == null) {
            for (int n = 4; n < getInvSize() + 4; n++) {
                if (processedSlots.contains(n)) continue;
                ItemStack stack = handler.getSlot(n).getStack();
                if (isValidMaterial(stack)) {
                    materialStack = stack;
                    processedSlots.add(n);
                    projectedEmpty.add(n);
                    processedSlots.add(SmithingScreenHandler.MATERIAL_ID);
                    changedSlots.put(SmithingScreenHandler.MATERIAL_ID, stack);
                    changedSlots.put(n, ItemStack.EMPTY);
                    if (templateStack != null) {
                        ItemStack output = getUpgradedItem(equipmentStack);
                        changedSlots.put(SmithingScreenHandler.OUTPUT_ID, output);
                    }
                    Int2ObjectMap<ItemStackHash> hashMap = new Int2ObjectOpenHashMap<>();
                    changedSlots.forEach((slot, stack2) -> hashMap.put(slot.intValue(), ItemStackHash.fromItemStack(stack2, component -> 0)));
                    return new ClickSlotC2SPacket(
                        handler.syncId, handler.getRevision(), (short) n, (byte) 0,
                        SlotActionType.QUICK_MOVE, hashMap, ItemStackHash.fromItemStack(ItemStack.EMPTY, component -> 0)
                    );
                }
            }
            return null;
        }
        if (templateStack == null) {
            for (int n = 4; n < getInvSize() + 4; n++) {
                if (processedSlots.contains(n)) continue;
                ItemStack stack = handler.getSlot(n).getStack();
                if (isValidTemplate(stack)) {
                    templateStack = stack;
                    processedSlots.add(n);
                    projectedEmpty.add(n);
                    processedSlots.add(SmithingScreenHandler.TEMPLATE_ID);
                    changedSlots.put(SmithingScreenHandler.TEMPLATE_ID, stack);
                    changedSlots.put(n, ItemStack.EMPTY);
                    if (equipmentStack != null && materialStack != null) {
                        ItemStack output = getUpgradedItem(equipmentStack);
                        changedSlots.put(SmithingScreenHandler.OUTPUT_ID, output);
                    }
                    Int2ObjectMap<ItemStackHash> hashMap = new Int2ObjectOpenHashMap<>();
                    changedSlots.forEach((slot, stack2) -> hashMap.put(slot.intValue(), ItemStackHash.fromItemStack(stack2, component -> 0)));
                    return new ClickSlotC2SPacket(
                        handler.syncId, handler.getRevision(), (short) n, (byte) 0,
                        SlotActionType.QUICK_MOVE, hashMap, ItemStackHash.fromItemStack(ItemStack.EMPTY, component -> 0)
                    );
                }
            }
            return null;
        }
        return null;
    }
    @SuppressWarnings("deprecation")
    private ItemStack getUpgradedItem(ItemStack original) {
        if (original.isOf(Items.DIAMOND_HELMET)) {
            return new ItemStack(Items.NETHERITE_HELMET.getRegistryEntry(), original.getCount(), original.getComponentChanges());
        } else if (original.isOf(Items.DIAMOND_CHESTPLATE)) {
            return new ItemStack(Items.NETHERITE_CHESTPLATE.getRegistryEntry(), original.getCount(), original.getComponentChanges());
        } else if (original.isOf(Items.DIAMOND_LEGGINGS)) {
            return new ItemStack(Items.NETHERITE_LEGGINGS.getRegistryEntry(), original.getCount(), original.getComponentChanges());
        } else if (original.isOf(Items.DIAMOND_BOOTS)) {
            return new ItemStack(Items.NETHERITE_BOOTS.getRegistryEntry(), original.getCount(), original.getComponentChanges());
        } else if (original.isOf(Items.DIAMOND_SWORD)) {
            return new ItemStack(Items.NETHERITE_SWORD.getRegistryEntry(), original.getCount(), original.getComponentChanges());
        } else if (original.isOf(Items.DIAMOND_PICKAXE)) {
            return new ItemStack(Items.NETHERITE_PICKAXE.getRegistryEntry(), original.getCount(), original.getComponentChanges());
        } else if (original.isOf(Items.DIAMOND_AXE)) {
            return new ItemStack(Items.NETHERITE_AXE.getRegistryEntry(), original.getCount(), original.getComponentChanges());
        } else if (original.isOf(Items.DIAMOND_SHOVEL)) {
            return new ItemStack(Items.NETHERITE_SHOVEL.getRegistryEntry(), original.getCount(), original.getComponentChanges());
        } else if (original.isOf(Items.DIAMOND_HOE)) {
            return new ItemStack(Items.NETHERITE_HOE.getRegistryEntry(), original.getCount(), original.getComponentChanges());
        } else {
            return original;
        }
    }
    private int predictEmptySlot(SmithingScreenHandler handler) {
        if (mc.player == null) return -1;
        for (int n = getInvSize() + 3; n >= 4; n--) {
            if (processedSlots.contains(n) && !projectedEmpty.contains(n)) continue;
            if (projectedEmpty.contains(n)) {
                projectedEmpty.rem(n);
                return n;
            } else if (handler.getSlot(n).getStack().isEmpty()) {
                processedSlots.add(n);
                return n;
            }
        }
        return -1;
    }
}