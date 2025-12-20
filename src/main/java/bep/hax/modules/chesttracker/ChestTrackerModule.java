package bep.hax.modules.chesttracker;
import bep.hax.Bep;
import meteordevelopment.meteorclient.events.packets.InventoryEvent;
import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.utils.world.BlockIterator;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.containers.WTable;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.renderer.text.TextRenderer;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.misc.Keybind;
import meteordevelopment.meteorclient.utils.render.NametagUtils;
import meteordevelopment.meteorclient.utils.render.RenderUtils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import org.joml.Vector3d;
import net.minecraft.block.*;
import net.minecraft.block.enums.ChestType;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;
import java.util.*;
import static meteordevelopment.meteorclient.MeteorClient.mc;
import static meteordevelopment.meteorclient.utils.Utils.*;
public class ChestTrackerModule extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgAutoOpen = settings.createGroup("Auto-Open");
    private final SettingGroup sgRender = settings.createGroup("Render");
    private final SettingGroup sgLabels = settings.createGroup("Labels");
    private final SettingGroup sgFilter = settings.createGroup("Filter");
    private final SettingGroup sgAdvanced = settings.createGroup("Advanced");
    private final Setting<Keybind> browserKey = sgGeneral.add(new KeybindSetting.Builder()
        .name("browser-keybind")
        .description("Open container browser GUI.")
        .defaultValue(Keybind.fromKey(GLFW.GLFW_KEY_Y))
        .action(() -> {
            if (mc.currentScreen == null) {
                mc.setScreen(new ChestTrackerScreen(this));
            }
        })
        .build()
    );
    private final Setting<Boolean> autoOpenEnabled = sgAutoOpen.add(new BoolSetting.Builder()
        .name("auto-open")
        .description("Automatically open nearby containers.")
        .defaultValue(false)
        .build()
    );
    private final Setting<Double> autoOpenRange = sgAutoOpen.add(new DoubleSetting.Builder()
        .name("auto-open-range")
        .description("Range to search for containers to auto-open.")
        .defaultValue(4.0)
        .min(1.0)
        .max(6.0)
        .sliderRange(1.0, 6.0)
        .decimalPlaces(1)
        .visible(autoOpenEnabled::get)
        .build()
    );
    private final Setting<Integer> autoOpenDelay = sgAutoOpen.add(new IntSetting.Builder()
        .name("auto-open-delay")
        .description("Delay in ticks between opening containers.")
        .defaultValue(5)
        .min(1)
        .max(20)
        .sliderRange(1, 20)
        .visible(autoOpenEnabled::get)
        .build()
    );
    private final Setting<Integer> autoOpenCloseDelay = sgAutoOpen.add(new IntSetting.Builder()
        .name("auto-close-delay")
        .description("Delay in ticks before closing container after opening (allows server to send all contents).")
        .defaultValue(5)
        .min(0)
        .max(40)
        .sliderRange(0, 40)
        .visible(autoOpenEnabled::get)
        .build()
    );
    private final Setting<Boolean> renderTracked = sgRender.add(new BoolSetting.Builder()
        .name("render-tracked")
        .description("Render all tracked containers.")
        .defaultValue(true)
        .build()
    );
    private final Setting<Boolean> renderSearchResults = sgRender.add(new BoolSetting.Builder()
        .name("render-search-results")
        .description("Render search results.")
        .defaultValue(true)
        .build()
    );
    private final Setting<Integer> renderDistance = sgRender.add(new IntSetting.Builder()
        .name("render-distance")
        .description("Maximum render distance.")
        .defaultValue(128)
        .min(8)
        .max(2048)
        .sliderRange(8, 2048)
        .build()
    );
    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode")
        .description("Render shape mode.")
        .defaultValue(ShapeMode.Both)
        .build()
    );
    private final Setting<SettingColor> trackedColor = sgRender.add(new ColorSetting.Builder()
        .name("tracked-color")
        .description("Color for tracked containers.")
        .defaultValue(new SettingColor(255, 255, 0, 75))
        .build()
    );
    private final Setting<SettingColor> searchColor = sgRender.add(new ColorSetting.Builder()
        .name("search-color")
        .description("Color for search results.")
        .defaultValue(new SettingColor(0, 255, 0, 100))
        .build()
    );
    private final Setting<SettingColor> searchLineColor = sgRender.add(new ColorSetting.Builder()
        .name("search-line-color")
        .description("Line color for search results.")
        .defaultValue(new SettingColor(0, 255, 0, 255))
        .build()
    );
    private final Setting<Boolean> renderLabels = sgLabels.add(new BoolSetting.Builder()
        .name("render-labels")
        .description("Render item icons on containers.")
        .defaultValue(true)
        .build()
    );
    private final Setting<Double> labelScale = sgLabels.add(new DoubleSetting.Builder()
        .name("icon-scale")
        .description("Item icon scale.")
        .defaultValue(1.0)
        .min(0.5)
        .max(3.0)
        .sliderRange(0.5, 3.0)
        .build()
    );
    private final Setting<Integer> labelMaxDistance = sgLabels.add(new IntSetting.Builder()
        .name("icon-max-distance")
        .description("Max distance for item icons.")
        .defaultValue(64)
        .min(8)
        .max(512)
        .sliderRange(8, 512)
        .build()
    );
    private final Setting<Boolean> trackChests = sgFilter.add(new BoolSetting.Builder()
        .name("track-chests")
        .description("Track chests and trapped chests.")
        .defaultValue(true)
        .build()
    );
    private final Setting<Boolean> trackBarrels = sgFilter.add(new BoolSetting.Builder()
        .name("track-barrels")
        .description("Track barrels.")
        .defaultValue(true)
        .build()
    );
    private final Setting<Boolean> trackShulkers = sgFilter.add(new BoolSetting.Builder()
        .name("track-shulkers")
        .description("Track shulker boxes.")
        .defaultValue(true)
        .build()
    );
    private final Setting<Boolean> trackEnderChests = sgFilter.add(new BoolSetting.Builder()
        .name("track-ender-chests")
        .description("Track ender chests.")
        .defaultValue(false)
        .build()
    );
    private final Setting<Boolean> trackHoppers = sgFilter.add(new BoolSetting.Builder()
        .name("track-hoppers")
        .description("Track hoppers.")
        .defaultValue(true)
        .build()
    );
    private final Setting<Boolean> trackDispensers = sgFilter.add(new BoolSetting.Builder()
        .name("track-dispensers")
        .description("Track dispensers and droppers.")
        .defaultValue(true)
        .build()
    );
    private final Setting<Boolean> trackCopperChests = sgFilter.add(new BoolSetting.Builder()
        .name("track-copper-chests")
        .description("Track copper chests (for modded servers).")
        .defaultValue(true)
        .build()
    );
    private final Setting<Boolean> debugMode = sgAdvanced.add(new BoolSetting.Builder()
        .name("debug")
        .description("Show debug messages in chat.")
        .defaultValue(false)
        .build()
    );
    private final ChestTrackerDataV2 data;
    private Item currentSearchItem;
    private BlockPos lastInteractedBlock = null;
    private boolean awaiting = false;
    private int awaitingTicks = 0;
    private int tickCounter = 0;
    private BlockPos[] currentOpenPositions = new BlockPos[2];
    private List<TrackedContainer> renderCache = new ArrayList<>();
    private long lastRenderCacheUpdate = 0;
    private boolean shouldAutoClose = false;
    private int ticksUntilClose = 0;
    private static final int AWAITING_TIMEOUT = 40;
    private final Map<BlockPos, Integer> blockedContainers = new HashMap<>();
    private static final int BLOCKED_COOLDOWN_TICKS = 100;
    private static final int MAX_FAILED_ATTEMPTS = 3;
    public ChestTrackerModule() {
        super(Bep.CATEGORY, "chest-tracker", "Track items in containers.");
        this.data = new ChestTrackerDataV2();
    }
    @Override
    public void onActivate() {
        data.loadData();
        int count = data.getTotalContainerCount();
        if (count > 0 && debugMode.get()) {
            info("Loaded " + count + " containers");
        }
        resetState();
        setupBlockInteractionTracking();
    }
    @Override
    public void onDeactivate() {
        data.saveData();
    }
    private void resetState() {
        lastInteractedBlock = null;
        currentSearchItem = null;
        awaiting = false;
        awaitingTicks = 0;
        tickCounter = 0;
        currentOpenPositions = new BlockPos[2];
        renderCache.clear();
        shouldAutoClose = false;
        ticksUntilClose = 0;
        blockedContainers.clear();
    }
    private void setupBlockInteractionTracking() {
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (!isActive()) return ActionResult.PASS;
            if (hand != Hand.MAIN_HAND) return ActionResult.PASS;
            if (mc.player != player) return ActionResult.PASS;
            BlockPos pos = hitResult.getBlockPos();
            if (mc.world == null) return ActionResult.PASS;
            Block block = mc.world.getBlockState(pos).getBlock();
            if (isTrackableContainer(block)) {
                lastInteractedBlock = pos.toImmutable();
            }
            return ActionResult.PASS;
        });
    }
    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (!blockedContainers.isEmpty()) {
            Iterator<Map.Entry<BlockPos, Integer>> it = blockedContainers.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<BlockPos, Integer> entry = it.next();
                int ticksRemaining = entry.getValue() - 1;
                if (ticksRemaining <= 0) {
                    it.remove();
                    if (debugMode.get()) info("Removed " + entry.getKey().toShortString() + " from blocked list (cooldown expired)");
                } else {
                    entry.setValue(ticksRemaining);
                }
            }
        }
        if (shouldAutoClose) {
            if (!isInContainerScreen()) {
                shouldAutoClose = false;
                ticksUntilClose = 0;
                if (debugMode.get()) info("Container closed manually, cancelling auto-close");
            } else if (ticksUntilClose > 0) {
                ticksUntilClose--;
                if (ticksUntilClose % 5 == 0 && debugMode.get()) {
                    info("Auto-close countdown: " + ticksUntilClose + " ticks remaining");
                }
                if (ticksUntilClose == 0) {
                    shouldAutoClose = false;
                    if (mc.player != null) {
                        mc.player.closeHandledScreen();
                        if (debugMode.get()) info("Auto-closed container after " + autoOpenCloseDelay.get() + " tick delay");
                    }
                }
            }
        }
        if (awaiting) {
            if (isInContainerScreen()) {
                awaitingTicks++;
                if (awaitingTicks > 5) {
                    if (debugMode.get()) info("InventoryEvent didn't fire - manually processing container");
                    ScreenHandler handler = mc.player.currentScreenHandler;
                    if (handler != null && currentOpenPositions[0] != null) {
                        BlockPos trackPos = currentOpenPositions[0];
                        awaiting = false;
                        awaitingTicks = 0;
                        blockedContainers.remove(trackPos);
                        if (currentOpenPositions[1] != null) {
                            blockedContainers.remove(currentOpenPositions[1]);
                        }
                        List<ItemStack> items = new ArrayList<>();
                        int containerSlots = handler.slots.size() - 36;
                        for (int i = 0; i < containerSlots && i < handler.slots.size(); i++) {
                            Slot slot = handler.slots.get(i);
                            ItemStack stack = slot.getStack();
                            if (!stack.isEmpty()) {
                                items.add(stack.copy());
                                if (hasItems(stack)) {
                                    ItemStack[] nestedItems = new ItemStack[27];
                                    getItemsInContainerItem(stack, nestedItems);
                                    for (ItemStack nestedItem : nestedItems) {
                                        if (nestedItem != null && !nestedItem.isEmpty()) {
                                            items.add(nestedItem.copy());
                                        }
                                    }
                                }
                            }
                        }
                        String currentDim = getCurrentDimension();
                        String containerType = getContainerType(trackPos);
                        data.trackContainer(trackPos, currentDim, containerType, items);
                        if (debugMode.get()) info("Manually tracked " + containerType + " (" + items.size() + " items)");
                        int closeDelay = autoOpenCloseDelay.get();
                        if (closeDelay == 0) {
                            mc.player.closeHandledScreen();
                            if (debugMode.get()) info("Closed immediately (0 tick delay)");
                        } else {
                            shouldAutoClose = true;
                            ticksUntilClose = closeDelay;
                            if (debugMode.get()) info("Set shouldAutoClose=true, ticksUntilClose=" + closeDelay);
                        }
                        currentOpenPositions = new BlockPos[2];
                    } else {
                        awaiting = false;
                        awaitingTicks = 0;
                        currentOpenPositions = new BlockPos[2];
                        if (debugMode.get()) info("Reset awaiting flag (can't process inventory)");
                    }
                }
            } else {
                awaitingTicks++;
                if (awaitingTicks > AWAITING_TIMEOUT) {
                    if (currentOpenPositions[0] != null) {
                        blockedContainers.put(currentOpenPositions[0], BLOCKED_COOLDOWN_TICKS);
                        if (debugMode.get()) info("Container at " + currentOpenPositions[0].toShortString() + " failed to open (timeout), adding to blocked list");
                    }
                    awaiting = false;
                    awaitingTicks = 0;
                    currentOpenPositions = new BlockPos[2];
                    if (debugMode.get()) info("Reset awaiting flag (timeout - container never opened or closed without inventory event)");
                }
            }
        }
        if (isInContainerScreen()) return;
        if (!autoOpenEnabled.get()) return;
        if (awaiting) return;
        if (tickCounter < autoOpenDelay.get()) {
            tickCounter++;
            return;
        }
        tickCounter = 0;
        int range = (int) Math.ceil(autoOpenRange.get());
        BlockPos playerPos = mc.player.getBlockPos();
        String currentDim = getCurrentDimension();
        for (int x = -range; x <= range; x++) {
            for (int y = -range; y <= range; y++) {
                for (int z = -range; z <= range; z++) {
                    BlockPos blockPos = playerPos.add(x, y, z);
                    double distSq = mc.player.squaredDistanceTo(blockPos.getX() + 0.5, blockPos.getY() + 0.5, blockPos.getZ() + 0.5);
                    double maxDistSq = autoOpenRange.get() * autoOpenRange.get();
                    if (distSq > maxDistSq) continue;
                    BlockState blockState = mc.world.getBlockState(blockPos);
                    Block block = blockState.getBlock();
                    if (!isTrackableContainer(block)) continue;
                    boolean isAlreadyTracked = data.getContainer(blockPos, currentDim) != null;
                    if (!isAlreadyTracked && block instanceof ChestBlock) {
                        ChestType chestType = blockState.get(ChestBlock.CHEST_TYPE);
                        if (chestType == ChestType.LEFT || chestType == ChestType.RIGHT) {
                            Direction facing = blockState.get(ChestBlock.FACING);
                            BlockPos otherHalf = blockPos.offset(chestType == ChestType.LEFT ? facing.rotateYClockwise() : facing.rotateYCounterclockwise());
                            if (data.getContainer(otherHalf, currentDim) != null) {
                                isAlreadyTracked = true;
                            }
                        }
                    }
                    if (blockedContainers.containsKey(blockPos)) {
                        continue;
                    }
                    if (!isAlreadyTracked) {
                        Vec3d vec = new Vec3d(blockPos.getX() + 0.5, blockPos.getY() + 0.5, blockPos.getZ() + 0.5);
                        BlockHitResult hitResult = new BlockHitResult(vec, Direction.UP, blockPos, false);
                        ActionResult result = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hitResult);
                        if (result == ActionResult.SUCCESS || result == ActionResult.CONSUME) {
                            blockedContainers.remove(blockPos);
                            awaiting = true;
                            awaitingTicks = 0;
                            currentOpenPositions[0] = blockPos.toImmutable();
                            currentOpenPositions[1] = null;
                            if (block instanceof ChestBlock) {
                                ChestType chestType = blockState.get(ChestBlock.CHEST_TYPE);
                                if (chestType == ChestType.LEFT || chestType == ChestType.RIGHT) {
                                    Direction facing = blockState.get(ChestBlock.FACING);
                                    BlockPos otherPos = blockPos.offset(chestType == ChestType.LEFT ? facing.rotateYClockwise() : facing.rotateYCounterclockwise());
                                    currentOpenPositions[1] = otherPos;
                                }
                            }
                            mc.player.swingHand(Hand.MAIN_HAND);
                            if (debugMode.get()) {
                                info("Auto-opening container at " + blockPos.toShortString());
                            }
                            return;
                        } else if (result == ActionResult.FAIL) {
                            blockedContainers.put(blockPos.toImmutable(), BLOCKED_COOLDOWN_TICKS);
                            if (debugMode.get()) {
                                info("Container at " + blockPos.toShortString() + " is blocked, adding to cooldown list");
                            }
                            continue;
                        }
                    }
                }
            }
        }
    }
    @EventHandler
    private void onInventory(InventoryEvent event) {
        if (!isActive()) return;
        ScreenHandler handler = mc.player.currentScreenHandler;
        if (handler == null) return;
        BlockPos trackPos = currentOpenPositions[0];
        if (trackPos == null) {
            trackPos = lastInteractedBlock;
        }
        if (trackPos == null) {
            awaiting = false;
            awaitingTicks = 0;
            return;
        }
        boolean wasAutoOpened = awaiting;
        awaiting = false;
        awaitingTicks = 0;
        blockedContainers.remove(trackPos);
        if (currentOpenPositions[1] != null) {
            blockedContainers.remove(currentOpenPositions[1]);
        }
        if (debugMode.get()) info("Inventory event fired - wasAutoOpened: " + wasAutoOpened);
        List<ItemStack> items = new ArrayList<>();
        int containerSlots = handler.slots.size() - 36;
        for (int i = 0; i < containerSlots && i < handler.slots.size(); i++) {
            Slot slot = handler.slots.get(i);
            ItemStack stack = slot.getStack();
            if (!stack.isEmpty()) {
                items.add(stack.copy());
                if (hasItems(stack)) {
                    ItemStack[] nestedItems = new ItemStack[27];
                    getItemsInContainerItem(stack, nestedItems);
                    for (ItemStack nestedItem : nestedItems) {
                        if (nestedItem != null && !nestedItem.isEmpty()) {
                            items.add(nestedItem.copy());
                        }
                    }
                }
            }
        }
        String currentDim = getCurrentDimension();
        String containerType = getContainerType(trackPos);
        data.trackContainer(trackPos, currentDim, containerType, items);
        if (debugMode.get()) info("Tracked " + containerType + " (" + items.size() + " items)");
        if (wasAutoOpened) {
            int closeDelay = autoOpenCloseDelay.get();
            if (debugMode.get()) info("Scheduling auto-close with delay: " + closeDelay + " ticks");
            if (closeDelay == 0) {
                mc.player.closeHandledScreen();
                if (debugMode.get()) info("Closed immediately (0 tick delay)");
            } else {
                shouldAutoClose = true;
                ticksUntilClose = closeDelay;
                if (debugMode.get()) info("Set shouldAutoClose=true, ticksUntilClose=" + closeDelay);
            }
        }
        lastInteractedBlock = null;
        currentOpenPositions = new BlockPos[2];
    }
    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc.player == null || mc.world == null) return;
        if (!renderTracked.get() && !renderSearchResults.get()) return;
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastRenderCacheUpdate > 1000) {
            String currentDim = getCurrentDimension();
            renderCache = new ArrayList<>(data.getAllContainers(currentDim));
            lastRenderCacheUpdate = currentTime;
        }
        double maxDist = renderDistance.get();
        double maxDistSq = maxDist * maxDist;
        for (TrackedContainer container : renderCache) {
            BlockPos pos = container.getPosition();
            double distSq = mc.player.squaredDistanceTo(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
            if (distSq > maxDistSq) continue;
            boolean isSearchResult = currentSearchItem != null &&
                                    container.containsItem(currentSearchItem);
            boolean shouldRender = false;
            SettingColor sideCol = null;
            SettingColor lineCol = null;
            if (isSearchResult && renderSearchResults.get()) {
                shouldRender = true;
                sideCol = searchColor.get();
                lineCol = searchLineColor.get();
            } else if (renderTracked.get()) {
                shouldRender = true;
                sideCol = trackedColor.get();
                lineCol = trackedColor.get();
            }
            if (!shouldRender) continue;
            event.renderer.box(pos, sideCol, lineCol, shapeMode.get(), 0);
            BlockPos otherHalf = findDoubleChestOtherHalf(pos);
            if (otherHalf != null) {
                event.renderer.box(otherHalf, sideCol, lineCol, shapeMode.get(), 0);
            }
        }
    }
    @EventHandler
    private void onRender2D(Render2DEvent event) {
        if (mc.player == null || mc.world == null) return;
        if (!renderLabels.get()) return;
        if (currentSearchItem == null) return;
        DrawContext context = event.drawContext;
        double maxDist = labelMaxDistance.get();
        double maxDistSq = maxDist * maxDist;
        Vector3d tempVec = new Vector3d();
        for (TrackedContainer container : renderCache) {
            if (!container.containsItem(currentSearchItem)) continue;
            BlockPos pos = container.getPosition();
            double distSq = mc.player.squaredDistanceTo(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
            if (distSq > maxDistSq) continue;
            tempVec.set(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
            if (!NametagUtils.to2D(tempVec, 1.0)) continue;
            int screenX = (int) tempVec.x;
            int screenY = (int) tempVec.y;
            int itemSize = (int) (16 * labelScale.get());
            int renderX = screenX - itemSize / 2;
            int renderY = screenY - itemSize / 2;
            var matrices = context.getMatrices();
            matrices.pushMatrix();
            if (itemSize != 16) {
                float scale = itemSize / 16.0f;
                matrices.translate(renderX + itemSize / 2.0f, renderY + itemSize / 2.0f);
                matrices.scale(scale, scale);
                matrices.translate(-8.0f, -8.0f);
                context.drawItem(new ItemStack(currentSearchItem), 0, 0);
            } else {
                context.drawItem(new ItemStack(currentSearchItem), renderX, renderY);
            }
            matrices.popMatrix();
        }
    }
    private BlockPos findDoubleChestOtherHalf(BlockPos pos) {
        if (mc.world == null) return null;
        BlockState state = mc.world.getBlockState(pos);
        Block block = state.getBlock();
        if (!(block instanceof ChestBlock || block instanceof TrappedChestBlock)) return null;
        try {
            if (state.contains(ChestBlock.CHEST_TYPE)) {
                ChestType chestType = state.get(ChestBlock.CHEST_TYPE);
                if (chestType == ChestType.SINGLE) return null;
                if (state.contains(ChestBlock.FACING)) {
                    Direction facing = state.get(ChestBlock.FACING);
                    BlockPos otherPos = chestType == ChestType.LEFT ?
                        pos.offset(facing.rotateYClockwise()) :
                        pos.offset(facing.rotateYCounterclockwise());
                    BlockState otherState = mc.world.getBlockState(otherPos);
                    if (otherState.getBlock().getClass() == block.getClass()) {
                        return otherPos;
                    }
                }
            }
        } catch (Exception e) {
        }
        return null;
    }
    @Override
    public WWidget getWidget(GuiTheme theme) {
        WTable table = theme.table();
        WButton openBrowser = table.add(theme.button("Open Browser (" + browserKey.get() + ")")).expandX().widget();
        openBrowser.action = () -> mc.setScreen(new ChestTrackerScreen(this));
        table.row();
        WButton searchHeld = table.add(theme.button("Search Held Item")).expandX().widget();
        searchHeld.action = this::searchHeldItem;
        table.row();
        WButton clearSearch = table.add(theme.button("Clear Search")).expandX().widget();
        clearSearch.action = () -> {
            currentSearchItem = null;
            if (debugMode.get()) info("Search cleared");
        };
        table.row();
        WButton saveData = table.add(theme.button("Save Data")).expandX().widget();
        saveData.action = () -> data.saveData();
        table.row();
        WButton clearAll = table.add(theme.button("Clear All Data")).expandX().widget();
        clearAll.action = () -> {
            data.clearAll();
            if (debugMode.get()) info("All data cleared");
        };
        return table;
    }
    private void searchHeldItem() {
        if (mc.player == null) return;
        ItemStack held = mc.player.getMainHandStack();
        if (held.isEmpty()) {
            if (debugMode.get()) warning("No item in hand");
            return;
        }
        currentSearchItem = held.getItem();
        List<TrackedContainer> results = data.searchItem(currentSearchItem);
        if (debugMode.get()) info("Found " + results.size() + " containers with " + currentSearchItem.getName().getString());
    }
    private boolean isTrackableContainer(Block block) {
        if (block instanceof ChestBlock || block instanceof TrappedChestBlock) {
            if (block == Blocks.COPPER_CHEST ||
                block == Blocks.EXPOSED_COPPER_CHEST ||
                block == Blocks.WEATHERED_COPPER_CHEST ||
                block == Blocks.OXIDIZED_COPPER_CHEST ||
                block == Blocks.WAXED_COPPER_CHEST ||
                block == Blocks.WAXED_EXPOSED_COPPER_CHEST ||
                block == Blocks.WAXED_WEATHERED_COPPER_CHEST ||
                block == Blocks.WAXED_OXIDIZED_COPPER_CHEST) {
                return trackCopperChests.get();
            }
            return trackChests.get();
        }
        if (block instanceof BarrelBlock) return trackBarrels.get();
        if (block instanceof ShulkerBoxBlock) return trackShulkers.get();
        if (block instanceof EnderChestBlock) return trackEnderChests.get();
        if (block instanceof HopperBlock) return trackHoppers.get();
        if (block instanceof DispenserBlock || block instanceof DropperBlock) return trackDispensers.get();
        return false;
    }
    private String getContainerType(BlockPos pos) {
        if (mc.world == null) return "container";
        Block block = mc.world.getBlockState(pos).getBlock();
        if (block == Blocks.COPPER_CHEST ||
            block == Blocks.EXPOSED_COPPER_CHEST ||
            block == Blocks.WEATHERED_COPPER_CHEST ||
            block == Blocks.OXIDIZED_COPPER_CHEST ||
            block == Blocks.WAXED_COPPER_CHEST ||
            block == Blocks.WAXED_EXPOSED_COPPER_CHEST ||
            block == Blocks.WAXED_WEATHERED_COPPER_CHEST ||
            block == Blocks.WAXED_OXIDIZED_COPPER_CHEST) {
            return "copper_chest";
        }
        if (block instanceof ChestBlock || block instanceof TrappedChestBlock) return "chest";
        if (block instanceof BarrelBlock) return "barrel";
        if (block instanceof ShulkerBoxBlock) return "shulker_box";
        if (block instanceof EnderChestBlock) return "ender_chest";
        if (block instanceof HopperBlock) return "hopper";
        if (block instanceof DispenserBlock) return "dispenser";
        if (block instanceof DropperBlock) return "dropper";
        return "container";
    }
    private boolean isInContainerScreen() {
        if (mc.currentScreen == null) return false;
        if (mc.player == null) return false;
        return mc.player.currentScreenHandler != mc.player.playerScreenHandler;
    }
    private String getCurrentDimension() {
        if (mc.world == null) return "unknown";
        return mc.world.getRegistryKey().getValue().toString();
    }
    public Item getCurrentSearchItem() {
        return currentSearchItem;
    }
    public void setCurrentSearchItem(Item item) {
        this.currentSearchItem = item;
    }
    public ChestTrackerDataV2 getData() {
        return data;
    }
    public void searchItem(Item item) {
        currentSearchItem = item;
    }
    public double getRenderDistance() {
        return renderDistance.get();
    }
}