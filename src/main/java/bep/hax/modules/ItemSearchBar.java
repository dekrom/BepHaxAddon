package bep.hax.modules;
import bep.hax.Bep;
import bep.hax.modules.chesttracker.ChestTrackerModule;
import meteordevelopment.meteorclient.events.game.ItemStackTooltipEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.screen.*;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.entity.decoration.GlowItemFrameEntity;
import net.minecraft.util.math.Box;
import org.lwjgl.glfw.GLFW;
import java.util.WeakHashMap;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.utils.render.RenderUtils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.misc.Keybind;
public class ItemSearchBar extends Module {
    private final WeakHashMap<ItemStack, Boolean> highlightCache = new WeakHashMap<>();
    private String cachedQuery = "";
    private String[] cachedSplitQueries = null;
    private final SettingGroup sgGeneral = settings.createGroup("General");
    private final SettingGroup sgGUI = settings.createGroup("GUI Settings");
    private final SettingGroup sgItemFrames = settings.createGroup("Item Frame ESP");
    public final Setting<String> searchQuery = sgGeneral.add(
        new StringSetting.Builder()
            .name("search-query")
            .description("Search query to match item names. Use commas to separate multiple search terms.")
            .defaultValue("")
            .build()
    );
    private final Setting<Boolean> caseSensitive = sgGeneral.add(
        new BoolSetting.Builder()
            .name("case-sensitive")
            .description("Whether the search should be case sensitive.")
            .defaultValue(false)
            .build()
    );
    private final Setting<Boolean> splitQueries = sgGeneral.add(
        new BoolSetting.Builder()
            .name("split-queries")
            .description("Split search queries by commas. Disable to treat commas literally.")
            .defaultValue(true)
            .build()
    );
    private final Setting<Boolean> searchItemName = sgGeneral.add(
        new BoolSetting.Builder()
            .name("search-item-name")
            .description("Search in item display names.")
            .defaultValue(true)
            .build()
    );
    private final Setting<Boolean> searchItemType = sgGeneral.add(
        new BoolSetting.Builder()
            .name("search-item-type")
            .description("Search in item type names.")
            .defaultValue(true)
            .build()
    );
    private final Setting<Boolean> searchLore = sgGeneral.add(
        new BoolSetting.Builder()
            .name("search-lore")
            .description("Search in item lore/tooltip text.")
            .defaultValue(false)
            .build()
    );
    public final Setting<SettingColor> highlightColor = sgGeneral.add(
        new ColorSetting.Builder()
            .name("highlight-color")
            .description("Color to highlight matching items.")
            .defaultValue(new SettingColor(255, 255, 0, 100))
            .build()
    );
    private final Setting<Boolean> ownInventory = sgGeneral.add(
        new BoolSetting.Builder()
            .name("inventory-highlight")
            .description("Highlight items in player inventory.")
            .defaultValue(true)
            .build()
    );
    private final Setting<Boolean> showSearchField = sgGeneral.add(
        new BoolSetting.Builder()
            .name("show-search-field")
            .description("Show search input field on top of container windows.")
            .defaultValue(true)
            .build()
    );
    private final Setting<Boolean> chestTrackerIntegration = sgGeneral.add(
        new BoolSetting.Builder()
            .name("chest-tracker-integration")
            .description("Automatically search in ChestTracker when searching items.")
            .defaultValue(true)
            .build()
    );
    private final Setting<Keybind> clickToSearchKey = sgGeneral.add(
        new KeybindSetting.Builder()
            .name("click-to-search-key")
            .description("Key/button to click an item to search for it.")
            .defaultValue(Keybind.fromButton(GLFW.GLFW_MOUSE_BUTTON_MIDDLE))
            .visible(() -> chestTrackerIntegration.get())
            .build()
    );
    private final Setting<Integer> fieldWidth = sgGUI.add(
        new IntSetting.Builder()
            .name("field-width")
            .description("Width of the search field.")
            .defaultValue(160)
            .min(80)
            .max(300)
            .sliderMin(80)
            .sliderMax(300)
            .build()
    );
    private final Setting<Integer> fieldHeight = sgGUI.add(
        new IntSetting.Builder()
            .name("field-height")
            .description("Height of the search field.")
            .defaultValue(12)
            .min(8)
            .max(20)
            .sliderMin(8)
            .sliderMax(20)
            .build()
    );
    private final Setting<Integer> offsetX = sgGUI.add(
        new IntSetting.Builder()
            .name("offset-x")
            .description("Horizontal offset from container edge.")
            .defaultValue(8)
            .min(-100)
            .max(100)
            .sliderMin(-100)
            .sliderMax(100)
            .build()
    );
    private final Setting<Integer> offsetY = sgGUI.add(
        new IntSetting.Builder()
            .name("offset-y")
            .description("Vertical offset from container top (negative = above container).")
            .defaultValue(-18)
            .min(-50)
            .max(50)
            .sliderMin(-50)
            .sliderMax(50)
            .build()
    );
    private final Setting<Boolean> highlightItemFrames = sgItemFrames.add(
        new BoolSetting.Builder()
            .name("highlight-item-frames")
            .description("Highlight item frames containing matching items in the world.")
            .defaultValue(true)
            .build()
    );
    private final Setting<SettingColor> frameFillColor = sgItemFrames.add(
        new ColorSetting.Builder()
            .name("fill-color")
            .description("Fill color for item frame highlight.")
            .defaultValue(new SettingColor(255, 255, 0, 50))
            .visible(highlightItemFrames::get)
            .build()
    );
    private final Setting<SettingColor> frameOutlineColor = sgItemFrames.add(
        new ColorSetting.Builder()
            .name("outline-color")
            .description("Outline color for item frame highlight.")
            .defaultValue(new SettingColor(255, 255, 0, 255))
            .visible(highlightItemFrames::get)
            .build()
    );
    private final Setting<Boolean> frameRenderFill = sgItemFrames.add(
        new BoolSetting.Builder()
            .name("render-fill")
            .description("Render fill of item frame highlight.")
            .defaultValue(true)
            .visible(highlightItemFrames::get)
            .build()
    );
    private final Setting<Boolean> frameRenderOutline = sgItemFrames.add(
        new BoolSetting.Builder()
            .name("render-outline")
            .description("Render outline of item frame highlight.")
            .defaultValue(true)
            .visible(highlightItemFrames::get)
            .build()
    );
    private final Setting<Boolean> frameRenderTracer = sgItemFrames.add(
        new BoolSetting.Builder()
            .name("tracers")
            .description("Draw tracers to matching item frames.")
            .defaultValue(false)
            .visible(highlightItemFrames::get)
            .build()
    );
    private final Setting<SettingColor> frameTracerColor = sgItemFrames.add(
        new ColorSetting.Builder()
            .name("tracer-color")
            .description("Color of tracers to item frames.")
            .defaultValue(new SettingColor(255, 255, 0, 125))
            .visible(() -> highlightItemFrames.get() && frameRenderTracer.get())
            .build()
    );
    private ItemStack lastHoveredItem = null;
    private boolean middleMousePressed = false;
    private String currentSearchQuery = "";
    public ItemSearchBar() {
        super(Bep.CATEGORY, "ItemSearchBar", "Search and highlight items in inventory and containers.");
    }
    @Override
    public void onActivate() {
        if (!searchQuery.get().isEmpty() && chestTrackerIntegration.get()) {
            updateChestTrackerSearch(searchQuery.get());
        }
    }
    @Override
    public void onDeactivate() {
        if (chestTrackerIntegration.get()) {
            ChestTrackerModule chestTracker = Modules.get().get(ChestTrackerModule.class);
            if (chestTracker != null && chestTracker.isActive()) {
                chestTracker.searchItem(null);
            }
        }
        lastHoveredItem = null;
        middleMousePressed = false;
    }
    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (chestTrackerIntegration.get() && !searchQuery.get().equals(currentSearchQuery)) {
            updateSearchQuery(searchQuery.get());
        }
        if (!chestTrackerIntegration.get() || !clickToSearchKey.get().isSet()) return;
        if (mc.currentScreen == null || !(mc.currentScreen instanceof HandledScreen<?> screen)) {
            lastHoveredItem = null;
            return;
        }
        boolean keyDown = clickToSearchKey.get().isPressed();
        ItemStack hoveredStack = null;
        if (screen.getScreenHandler() != null) {
            double mouseX = mc.mouse.getX() * mc.getWindow().getScaledWidth() / mc.getWindow().getWidth();
            double mouseY = mc.mouse.getY() * mc.getWindow().getScaledHeight() / mc.getWindow().getHeight();
            for (var slot : screen.getScreenHandler().slots) {
                if (isPointInSlot(screen, slot, mouseX, mouseY) && slot.hasStack()) {
                    hoveredStack = slot.getStack();
                    break;
                }
            }
        }
        if (hoveredStack != null) {
            if (keyDown && !middleMousePressed) {
                middleMousePressed = true;
                lastHoveredItem = hoveredStack;
            } else if (!keyDown && middleMousePressed && lastHoveredItem != null) {
                middleMousePressed = false;
                String itemName = lastHoveredItem.getName().getString();
                updateSearchQuery(itemName);
                info("Searching for: " + itemName);
                lastHoveredItem = null;
            }
        }
        if (!keyDown) {
            middleMousePressed = false;
        }
    }
    private boolean isPointInSlot(HandledScreen<?> screen, net.minecraft.screen.slot.Slot slot, double pointX, double pointY) {
        int x = (screen.width - 176) / 2;
        int y = (screen.height - 166) / 2;
        if (screen instanceof GenericContainerScreen) {
        } else if (screen instanceof net.minecraft.client.gui.screen.ingame.InventoryScreen) {
            x = (screen.width - 176) / 2;
            y = (screen.height - 166) / 2;
        }
        int slotX = x + slot.x;
        int slotY = y + slot.y;
        return pointX >= slotX && pointX < slotX + 16 &&
               pointY >= slotY && pointY < slotY + 16;
    }
    private boolean shouldIgnoreCurrentScreenHandler(ClientPlayerEntity player) {
        if (mc.currentScreen == null) return true;
        if (player.currentScreenHandler == null) return true;
        ScreenHandler handler = player.currentScreenHandler;
        if (handler instanceof PlayerScreenHandler) return !ownInventory.get();
        return !(handler instanceof AbstractFurnaceScreenHandler || handler instanceof GenericContainerScreenHandler
            || handler instanceof Generic3x3ContainerScreenHandler || handler instanceof ShulkerBoxScreenHandler
            || handler instanceof HopperScreenHandler || handler instanceof HorseScreenHandler);
    }
    private boolean matchesSearchQuery(String text, String query) {
        if (caseSensitive.get()) {
            return text.contains(query);
        } else {
            return text.toLowerCase().contains(query.toLowerCase());
        }
    }
    public void updateSearchQuery(String query) {
        this.currentSearchQuery = query;
        if (!searchQuery.get().equals(query)) {
            searchQuery.set(query);
        }
        highlightCache.clear();
        cachedQuery = "";
        cachedSplitQueries = null;
        if (chestTrackerIntegration.get()) {
            updateChestTrackerSearch(query);
        }
    }
    private void updateChestTrackerSearch(String query) {
        ChestTrackerModule chestTracker = Modules.get().get(ChestTrackerModule.class);
        if (chestTracker == null || !chestTracker.isActive()) return;
        if (query == null || query.trim().isEmpty()) {
            chestTracker.searchItem(null);
            info("ChestTracker search cleared");
        } else {
            Item searchItem = null;
            String searchQuery = query.trim().toLowerCase();
            if (splitQueries.get() && searchQuery.contains(",")) {
                String[] queries = searchQuery.split(",");
                if (queries.length > 0) {
                    searchQuery = queries[0].trim();
                }
            }
            for (Item item : Registries.ITEM) {
                String itemName = item.getDefaultStack().getName().getString().toLowerCase();
                if (itemName.equals(searchQuery)) {
                    searchItem = item;
                    break;
                }
            }
            if (searchItem == null) {
                for (Item item : Registries.ITEM) {
                    String itemName = item.getDefaultStack().getName().getString().toLowerCase();
                    String translationKey = item.getTranslationKey().toLowerCase();
                    String simplifiedKey = translationKey
                        .replace("item.minecraft.", "")
                        .replace("block.minecraft.", "")
                        .replace("_", " ");
                    if (itemName.contains(searchQuery) ||
                        simplifiedKey.contains(searchQuery) ||
                        translationKey.contains(searchQuery)) {
                        searchItem = item;
                        break;
                    }
                }
            }
            chestTracker.searchItem(searchItem);
            if (searchItem != null) {
                String itemDisplayName = searchItem.getDefaultStack().getName().getString();
                info("ChestTracker: Searching for §e" + itemDisplayName);
                var results = chestTracker.getData().searchItem(searchItem);
                if (!results.isEmpty()) {
                    final Item finalSearchItem = searchItem;
                    int totalCount = results.stream()
                        .mapToInt(c -> c.getItemCount(Registries.ITEM.getId(finalSearchItem).toString()))
                        .sum();
                    info("Found §a" + totalCount + "§r items in §e" + results.size() + "§r containers");
                }
            } else {
                info("ChestTracker: No item found matching \"" + query + "\"");
            }
        }
    }
    public boolean shouldShowSearchField() {
        return showSearchField.get();
    }
    public int getFieldWidth() { return fieldWidth.get(); }
    public int getFieldHeight() { return fieldHeight.get(); }
    public int getOffsetX() { return offsetX.get(); }
    public int getOffsetY() { return offsetY.get(); }
    public boolean shouldHighlightSlot(ItemStack stack) {
        if (mc.player == null) return false;
        if (stack.isEmpty() || shouldIgnoreCurrentScreenHandler(mc.player)) return false;
        String query = !currentSearchQuery.isEmpty() ? currentSearchQuery.trim() : searchQuery.get().trim();
        if (query.isEmpty()) return false;
        Boolean cached = highlightCache.get(stack);
        if (cached != null) return cached;
        if (!query.equals(cachedQuery)) {
            cachedQuery = query;
            if (splitQueries.get() && query.contains(",")) {
                cachedSplitQueries = query.split(",");
            } else {
                cachedSplitQueries = null;
            }
        }
        boolean result = computeHighlight(stack, query);
        highlightCache.put(stack, result);
        return result;
    }
    private boolean computeHighlight(ItemStack stack, String query) {
        if (Utils.hasItems(stack)) {
            ItemStack[] stacks = new ItemStack[27];
            Utils.getItemsInContainerItem(stack, stacks);
            for (ItemStack s : stacks) {
                if (s != null && !s.isEmpty() && matchesItemDirect(s, query)) return true;
            }
        }
        return matchesItemDirect(stack, query);
    }
    private boolean matchesItemDirect(ItemStack stack, String query) {
        if (cachedSplitQueries != null) {
            for (String q : cachedSplitQueries) {
                q = q.trim();
                if (q.isEmpty()) continue;
                if (matchesItem(stack, q)) return true;
            }
            return false;
        } else {
            return matchesItem(stack, query);
        }
    }
    private boolean matchesItem(ItemStack stack, String query) {
        if (searchItemName.get()) {
            String displayName = stack.getName().getString();
            if (matchesSearchQuery(displayName, query)) return true;
        }
        if (searchItemType.get()) {
            String typeName = stack.getItem().getDefaultStack().getName().getString();
            if (matchesSearchQuery(typeName, query)) return true;
        }
        if (searchLore.get()) {
            String tooltip = stack.getComponents().toString();
            if (matchesSearchQuery(tooltip, query)) return true;
        }
        return false;
    }
    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (mc.world == null || mc.player == null) return;
        if (!highlightItemFrames.get()) return;
        String query = !currentSearchQuery.isEmpty() ? currentSearchQuery.trim() : searchQuery.get().trim();
        if (query.isEmpty()) return;
        ShapeMode shapeMode = getShapeMode(frameRenderFill.get(), frameRenderOutline.get());
        if (shapeMode == null) return;
        Color fillColor = new Color(frameFillColor.get());
        Color outlineColor = new Color(frameOutlineColor.get());
        for (Entity entity : mc.world.getEntities()) {
            if (!(entity instanceof ItemFrameEntity frame)) continue;
            ItemStack heldStack = frame.getHeldItemStack();
            if (heldStack.isEmpty()) continue;
            if (!matchesItemForFrame(heldStack, query)) continue;
            Box box = frame.getBoundingBox();
            event.renderer.box(box, fillColor, outlineColor, shapeMode, 0);
            if (frameRenderTracer.get()) {
                event.renderer.line(
                    RenderUtils.center.x,
                    RenderUtils.center.y,
                    RenderUtils.center.z,
                    box.getCenter().x, box.getCenter().y, box.getCenter().z,
                    frameTracerColor.get()
                );
            }
        }
    }
    private ShapeMode getShapeMode(boolean renderFill, boolean renderOutline) {
        if (renderFill && renderOutline) return ShapeMode.Both;
        else if (renderFill) return ShapeMode.Sides;
        else if (renderOutline) return ShapeMode.Lines;
        return null;
    }
    private boolean matchesItemForFrame(ItemStack stack, String query) {
        if (!query.equals(cachedQuery)) {
            cachedQuery = query;
            if (splitQueries.get() && query.contains(",")) {
                cachedSplitQueries = query.split(",");
            } else {
                cachedSplitQueries = null;
            }
        }
        if (cachedSplitQueries != null) {
            for (String q : cachedSplitQueries) {
                q = q.trim();
                if (q.isEmpty()) continue;
                if (matchesItem(stack, q)) return true;
            }
            return false;
        } else {
            return matchesItem(stack, query);
        }
    }
}