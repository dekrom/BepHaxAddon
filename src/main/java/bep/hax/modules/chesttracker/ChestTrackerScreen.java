package bep.hax.modules.chesttracker;
import bep.hax.modules.ItemSearchBar;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import java.util.*;
import java.util.stream.Collectors;
public class ChestTrackerScreen extends Screen {
    private final ChestTrackerModule module;
    private final ChestTrackerDataV2 data;
    private TextFieldWidget searchField;
    private String searchQuery = "";
    private List<ItemEntry> allItems = new ArrayList<>();
    private List<ItemEntry> filteredItems = new ArrayList<>();
    private int scrollOffset = 0;
    private int maxScroll = 0;
    private static final int ITEM_SIZE = 18;
    private static final int ITEMS_PER_ROW = 16;
    private static final int PADDING = 10;
    private static final int TOP_PADDING = 70;
    private static final int BOTTOM_PADDING = 35;
    private static final int SCROLLBAR_WIDTH = 8;
    private static final int MAX_PANEL_HEIGHT = 600;
    private static final int MIN_VISIBLE_ROWS = 5;
    private ButtonWidget clearSearchButton;
    private ButtonWidget sortButton;
    private SortMode currentSortMode = SortMode.COUNT_DESC;
    private boolean isDraggingScrollbar = false;
    private int scrollbarDragStartY = 0;
    private int scrollbarDragStartOffset = 0;
    private int cachedStartX;
    private int cachedStartY;
    private int cachedMaxY;
    private int cachedTotalRows;
    private int cachedVisibleHeight;
    public ChestTrackerScreen(ChestTrackerModule module) {
        super(Text.literal("Chest Tracker"));
        this.module = module;
        this.data = module.getData();
    }
    @Override
    protected void init() {
        super.init();
        final ItemSearchBar itemSearchBar = Modules.get().get(ItemSearchBar.class);
        String initialSearch = "";
        if (itemSearchBar != null && itemSearchBar.isActive()) {
            initialSearch = itemSearchBar.searchQuery.get();
        }
        searchField = new TextFieldWidget(
            this.textRenderer,
            this.width / 2 - 110,
            20,
            200,
            20,
            Text.literal("Search items...")
        );
        searchField.setMaxLength(50);
        searchField.setPlaceholder(Text.literal("Search items..."));
        searchField.setChangedListener(this::onSearchChanged);
        if (!initialSearch.isEmpty()) {
            searchField.setText(initialSearch);
            this.searchQuery = initialSearch;
        }
        this.addSelectableChild(searchField);
        clearSearchButton = ButtonWidget.builder(
            Text.literal("§cx"),
            button -> {
                searchField.setText("");
                this.searchQuery = "";
                filterItems();
                if (itemSearchBar != null && itemSearchBar.isActive()) {
                    itemSearchBar.updateSearchQuery("");
                }
            }
        )
        .dimensions(this.width / 2 + 95, 20, 20, 20)
        .build();
        this.addDrawableChild(clearSearchButton);
        sortButton = ButtonWidget.builder(
            Text.literal("Sort: " + currentSortMode.getDisplayName()),
            button -> {
                currentSortMode = currentSortMode.next();
                button.setMessage(Text.literal("Sort: " + currentSortMode.getDisplayName()));
                sortItems();
                filterItems();
            }
        )
        .dimensions(this.width / 2 - 220, 20, 100, 20)
        .build();
        this.addDrawableChild(sortButton);
        loadItems();
        filterItems();
    }
    private void loadItems() {
        allItems = new ArrayList<>();
        Map<String, Integer> itemCounts = new HashMap<>();
        String currentDim = getCurrentDimension();
        for (TrackedContainer container : data.getAllContainers(currentDim)) {
            for (Map.Entry<String, Integer> entry : container.getItems().entrySet()) {
                itemCounts.merge(entry.getKey(), entry.getValue(), Integer::sum);
            }
        }
        for (Map.Entry<String, Integer> entry : itemCounts.entrySet()) {
            Identifier id = Identifier.tryParse(entry.getKey());
            if (id != null && Registries.ITEM.containsId(id)) {
                Item item = Registries.ITEM.get(id);
                allItems.add(new ItemEntry(item, entry.getValue()));
            }
        }
        sortItems();
    }
    private void sortItems() {
        switch (currentSortMode) {
            case COUNT_DESC:
                allItems.sort((a, b) -> Integer.compare(b.count, a.count));
                break;
            case COUNT_ASC:
                allItems.sort((a, b) -> Integer.compare(a.count, b.count));
                break;
            case NAME_ASC:
                allItems.sort((a, b) -> a.item.getName().getString().compareToIgnoreCase(b.item.getName().getString()));
                break;
            case NAME_DESC:
                allItems.sort((a, b) -> b.item.getName().getString().compareToIgnoreCase(a.item.getName().getString()));
                break;
        }
    }
    private void filterItems() {
        if (allItems == null) {
            allItems = new ArrayList<>();
        }
        if (searchQuery.isEmpty()) {
            filteredItems = new ArrayList<>(allItems);
        } else {
            String query = searchQuery.toLowerCase();
            filteredItems = allItems.stream()
                .filter(entry -> entry.item.getName().getString().toLowerCase().contains(query))
                .collect(Collectors.toList());
        }
        int rows = (int) Math.ceil(filteredItems.size() / (double) ITEMS_PER_ROW);
        int contentHeight = Math.max(rows * ITEM_SIZE, MIN_VISIBLE_ROWS * ITEM_SIZE);
        int maxPanelHeight = Math.min(MAX_PANEL_HEIGHT, this.height - TOP_PADDING - BOTTOM_PADDING);
        int actualPanelHeight = Math.min(contentHeight, maxPanelHeight);
        int visibleRows = actualPanelHeight / ITEM_SIZE;
        maxScroll = Math.max(0, rows - visibleRows);
        scrollOffset = Math.min(scrollOffset, maxScroll);
    }
    private void onSearchChanged(String query) {
        this.searchQuery = query;
        filterItems();
        ItemSearchBar itemSearchBar = Modules.get().get(ItemSearchBar.class);
        if (itemSearchBar != null && itemSearchBar.isActive()) {
            itemSearchBar.updateSearchQuery(query);
        }
    }
    private void updateCachedBounds() {
        cachedStartX = this.width / 2 - (ITEMS_PER_ROW * ITEM_SIZE) / 2;
        cachedStartY = TOP_PADDING;
        cachedTotalRows = (int) Math.ceil(filteredItems.size() / (double) ITEMS_PER_ROW);
        int contentHeight = Math.max(cachedTotalRows * ITEM_SIZE, MIN_VISIBLE_ROWS * ITEM_SIZE);
        int maxPanelHeight = Math.min(MAX_PANEL_HEIGHT, this.height - TOP_PADDING - BOTTOM_PADDING);
        cachedVisibleHeight = Math.min(contentHeight, maxPanelHeight);
        cachedMaxY = TOP_PADDING + cachedVisibleHeight;
    }
    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        updateCachedBounds();
        context.fill(0, 0, this.width, this.height, 0xF0000000);
        int panelWidth = (ITEMS_PER_ROW * ITEM_SIZE) + 20;
        int panelX = this.width / 2 - panelWidth / 2;
        int panelY = TOP_PADDING - 5;
        int totalRows = (int) Math.ceil(filteredItems.size() / (double) ITEMS_PER_ROW);
        int contentHeight = Math.max(totalRows * ITEM_SIZE, MIN_VISIBLE_ROWS * ITEM_SIZE);
        int maxPanelHeight = Math.min(MAX_PANEL_HEIGHT, this.height - TOP_PADDING - BOTTOM_PADDING);
        int panelContentHeight = Math.min(contentHeight, maxPanelHeight);
        int panelBottom = panelY + panelContentHeight + 15;
        context.fill(panelX, panelY, panelX + panelWidth, panelBottom, 0xFF1A1A1A);
        context.fill(panelX, panelY, panelX + panelWidth, panelY + 2, 0xFF555555);
        context.fill(panelX, panelY, panelX + 2, panelBottom, 0xFF555555);
        context.fill(panelX + panelWidth - 2, panelY, panelX + panelWidth, panelBottom, 0xFF2A2A2A);
        context.fill(panelX, panelBottom - 2, panelX + panelWidth, panelBottom, 0xFF2A2A2A);
        String currentDim = getCurrentDimension();
        String dimName = currentDim.contains("overworld") ? "Overworld" :
                        currentDim.contains("nether") ? "Nether" :
                        currentDim.contains("end") ? "End" : currentDim;
        context.drawCenteredTextWithShadow(
            this.textRenderer,
            "§l§eChest Tracker §r§7- " + dimName,
            this.width / 2,
            8,
            0xFFFFFF
        );
        searchField.render(context, mouseX, mouseY, delta);
        clearSearchButton.visible = !searchQuery.isEmpty();
        clearSearchButton.active = !searchQuery.isEmpty();
        renderItemGrid(context, mouseX, mouseY);
        renderScrollbar(context, mouseX, mouseY);
        super.render(context, mouseX, mouseY, delta);
        renderTooltip(context, mouseX, mouseY);
    }
    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
    }
    private void renderItemGrid(DrawContext context, int mouseX, int mouseY) {
        int index = scrollOffset * ITEMS_PER_ROW;
        int maxIndex = filteredItems.size();
        int panelWidth = (ITEMS_PER_ROW * ITEM_SIZE) + 20;
        int panelX = this.width / 2 - panelWidth / 2;
        context.enableScissor(panelX + 10, TOP_PADDING, panelX + panelWidth - 10, cachedMaxY);
        int visibleRows = (cachedVisibleHeight / ITEM_SIZE) + 2;
        int maxRow = Math.min(visibleRows, cachedTotalRows - scrollOffset);
        for (int row = 0; row < maxRow; row++) {
            for (int col = 0; col < ITEMS_PER_ROW; col++) {
                if (index >= maxIndex) break;
                ItemEntry entry = filteredItems.get(index);
                int x = cachedStartX + col * ITEM_SIZE;
                int y = cachedStartY + row * ITEM_SIZE;
                if (y + ITEM_SIZE <= TOP_PADDING || y >= cachedMaxY) {
                    index++;
                    continue;
                }
                boolean hovered = mouseX >= x && mouseX < x + ITEM_SIZE &&
                                mouseY >= y && mouseY < y + ITEM_SIZE;
                context.fill(x, y, x + ITEM_SIZE, y + ITEM_SIZE, 0xFF3A3A3A);
                if (hovered) {
                    context.fill(x, y, x + ITEM_SIZE, y + 1, 0xFF00FF00);
                    context.fill(x, y, x + 1, y + ITEM_SIZE, 0xFF00FF00);
                    context.fill(x + ITEM_SIZE - 1, y, x + ITEM_SIZE, y + ITEM_SIZE, 0xFF00FF00);
                    context.fill(x, y + ITEM_SIZE - 1, x + ITEM_SIZE, y + ITEM_SIZE, 0xFF00FF00);
                } else {
                    context.fill(x, y, x + ITEM_SIZE, y + 1, 0xFF555555);
                    context.fill(x, y, x + 1, y + ITEM_SIZE, 0xFF555555);
                    context.fill(x + ITEM_SIZE - 1, y, x + ITEM_SIZE, y + ITEM_SIZE, 0xFF2A2A2A);
                    context.fill(x, y + ITEM_SIZE - 1, x + ITEM_SIZE, y + ITEM_SIZE, 0xFF2A2A2A);
                }
                context.drawItem(new ItemStack(entry.item), x + 1, y + 1);
                index++;
            }
            if (index >= maxIndex) break;
        }
        context.disableScissor();
        String itemCountText;
        if (searchQuery.isEmpty()) {
            itemCountText = String.format("§e%d §7unique items tracked", filteredItems.size());
        } else {
            itemCountText = String.format("§e%d §7items found (filtered from §e%d§7 total)", filteredItems.size(), allItems.size());
        }
        int countTextWidth = this.textRenderer.getWidth(itemCountText);
        int countX = this.width / 2 - countTextWidth / 2;
        int countY = 52;
        context.fill(countX - 4, countY - 2, countX + countTextWidth + 4, countY + 10, 0xDD000000);
        context.drawText(
            this.textRenderer,
            itemCountText,
            countX,
            countY,
            0xFFFFAA00,
            false
        );
    }
    private String formatNumber(int number) {
        if (number >= 1000000) {
            return String.format("%.1fM", number / 1000000.0);
        } else if (number >= 1000) {
            return String.format("%.1fK", number / 1000.0);
        }
        return String.valueOf(number);
    }
    private void renderScrollbar(DrawContext context, int mouseX, int mouseY) {
        if (maxScroll <= 0) return;
        int panelWidth = (ITEMS_PER_ROW * ITEM_SIZE) + 20;
        int scrollbarX = this.width / 2 + panelWidth / 2 + 5;
        int scrollbarY = TOP_PADDING;
        int scrollbarHeight = cachedVisibleHeight;
        context.fill(scrollbarX, scrollbarY, scrollbarX + SCROLLBAR_WIDTH, scrollbarY + scrollbarHeight, 0xFF2A2A2A);
        int visibleRows = scrollbarHeight / ITEM_SIZE;
        int thumbHeight = Math.max(20, (int)((double)visibleRows / cachedTotalRows * scrollbarHeight));
        int scrollableHeight = scrollbarHeight - thumbHeight;
        int thumbY = scrollbarY + (maxScroll > 0 ? (int)((double)scrollOffset / maxScroll * scrollableHeight) : 0);
        boolean hovered = mouseX >= scrollbarX && mouseX <= scrollbarX + SCROLLBAR_WIDTH &&
                         mouseY >= thumbY && mouseY <= thumbY + thumbHeight;
        int thumbColor = isDraggingScrollbar ? 0xFF00FF00 : (hovered ? 0xFF00CC00 : 0xFF008800);
        context.fill(scrollbarX + 1, thumbY, scrollbarX + SCROLLBAR_WIDTH - 1, thumbY + thumbHeight, thumbColor);
        context.fill(scrollbarX + 1, thumbY, scrollbarX + SCROLLBAR_WIDTH - 1, thumbY + 1, 0xFF00FF00);
        context.fill(scrollbarX + 1, thumbY + thumbHeight - 1, scrollbarX + SCROLLBAR_WIDTH - 1, thumbY + thumbHeight, 0xFF005500);
    }
    private void renderTooltip(DrawContext context, int mouseX, int mouseY) {
        int index = scrollOffset * ITEMS_PER_ROW;
        int maxIndex = filteredItems.size();
        int visibleRows = (cachedVisibleHeight / ITEM_SIZE) + 2;
        int maxRow = Math.min(visibleRows, cachedTotalRows - scrollOffset);
        for (int row = 0; row < maxRow; row++) {
            for (int col = 0; col < ITEMS_PER_ROW; col++) {
                if (index >= maxIndex) return;
                int x = cachedStartX + col * ITEM_SIZE;
                int y = cachedStartY + row * ITEM_SIZE;
                if (y + ITEM_SIZE <= TOP_PADDING || y >= cachedMaxY) {
                    index++;
                    continue;
                }
                if (mouseX >= x && mouseX < x + ITEM_SIZE &&
                    mouseY >= y && mouseY < y + ITEM_SIZE) {
                    ItemEntry entry = filteredItems.get(index);
                    List<TrackedContainer> containers = data.searchItem(entry.item);
                    int withinRange = 0;
                    double renderDist = module.getRenderDistance();
                    if (client != null && client.player != null) {
                        for (TrackedContainer container : containers) {
                            BlockPos pos = container.getPosition();
                            double distSq = client.player.squaredDistanceTo(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
                            if (distSq <= renderDist * renderDist) {
                                withinRange++;
                            }
                        }
                    }
                    List<Text> tooltip = new ArrayList<>();
                    tooltip.add(Text.literal("§f§l" + entry.item.getName().getString()));
                    tooltip.add(Text.literal(""));
                    tooltip.add(Text.literal("§7Total Amount: §a" + formatCountFull(entry.count)));
                    tooltip.add(Text.literal("§7Found in: §e" + containers.size() + " §7container(s)"));
                    if (withinRange > 0 && withinRange < containers.size()) {
                        tooltip.add(Text.literal("§7Will highlight: §e" + withinRange + " §7nearby"));
                        tooltip.add(Text.literal("§8(Increase render distance for more)"));
                    } else if (withinRange == 0) {
                        tooltip.add(Text.literal("§cAll containers are far away!"));
                        tooltip.add(Text.literal("§8(Increase render distance in settings)"));
                    }
                    tooltip.add(Text.literal(""));
                    tooltip.add(Text.literal("§e§l» Click to Highlight All Within Range «"));
                    ItemSearchBar itemSearchBar = Modules.get().get(ItemSearchBar.class);
                    if (itemSearchBar != null && itemSearchBar.isActive()) {
                        tooltip.add(Text.literal("§7(Also searches in ItemSearchBar)"));
                    }
                    context.drawTooltip(this.textRenderer, tooltip, mouseX, mouseY);
                    return;
                }
                index++;
            }
        }
    }
    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        double mouseX = click.x();
        double mouseY = click.y();
        int button = click.button();
        if (maxScroll > 0 && button == 0) {
            int panelWidth = (ITEMS_PER_ROW * ITEM_SIZE) + 20;
            int scrollbarX = this.width / 2 + panelWidth / 2 + 5;
            int scrollbarY = TOP_PADDING;
            int totalRows = (int) Math.ceil(filteredItems.size() / (double) ITEMS_PER_ROW);
            int contentHeight = Math.max(totalRows * ITEM_SIZE, MIN_VISIBLE_ROWS * ITEM_SIZE);
            int maxPanelHeight = Math.min(MAX_PANEL_HEIGHT, this.height - TOP_PADDING - BOTTOM_PADDING);
            int scrollbarHeight = Math.min(contentHeight, maxPanelHeight);
            if (mouseX >= scrollbarX && mouseX <= scrollbarX + SCROLLBAR_WIDTH &&
                mouseY >= scrollbarY && mouseY <= scrollbarY + scrollbarHeight) {
                int visibleRows = scrollbarHeight / ITEM_SIZE;
                int thumbHeight = Math.max(20, (int)((double)visibleRows / totalRows * scrollbarHeight));
                int scrollableHeight = scrollbarHeight - thumbHeight;
                int thumbY = scrollbarY + (maxScroll > 0 ? (int)((double)scrollOffset / maxScroll * scrollableHeight) : 0);
                if (mouseY >= thumbY && mouseY <= thumbY + thumbHeight) {
                    isDraggingScrollbar = true;
                    scrollbarDragStartY = (int)mouseY;
                    scrollbarDragStartOffset = scrollOffset;
                    return true;
                } else {
                    double clickRatio = (mouseY - scrollbarY) / (double)scrollableHeight;
                    scrollOffset = (int)(clickRatio * maxScroll);
                    scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset));
                    return true;
                }
            }
        }
        int index = scrollOffset * ITEMS_PER_ROW;
        int maxIndex = filteredItems.size();
        int visibleRows = (cachedVisibleHeight / ITEM_SIZE) + 2;
        int maxRow = Math.min(visibleRows, cachedTotalRows - scrollOffset);
        for (int row = 0; row < maxRow; row++) {
            for (int col = 0; col < ITEMS_PER_ROW; col++) {
                if (index >= maxIndex) break;
                int x = cachedStartX + col * ITEM_SIZE;
                int y = cachedStartY + row * ITEM_SIZE;
                if (y + ITEM_SIZE <= TOP_PADDING || y >= cachedMaxY) {
                    index++;
                    continue;
                }
                if (mouseX >= x && mouseX < x + ITEM_SIZE &&
                    mouseY >= y && mouseY < y + ITEM_SIZE) {
                    ItemEntry entry = filteredItems.get(index);
                    onItemClicked(entry);
                    return true;
                }
                index++;
            }
        }
        return super.mouseClicked(click, doubled);
    }
    private void onItemClicked(ItemEntry entry) {
        List<TrackedContainer> results = data.searchItem(entry.item);
        module.searchItem(entry.item);
        ItemSearchBar itemSearchBar = Modules.get().get(ItemSearchBar.class);
        if (itemSearchBar != null && itemSearchBar.isActive()) {
            String itemName = entry.item.getName().getString();
            itemSearchBar.updateSearchQuery(itemName);
            searchField.setText(itemName);
            this.searchQuery = itemName;
            filterItems();
        }
        int withinRange = 0;
        if (client != null && client.player != null) {
            double renderDist = module.getRenderDistance();
            for (TrackedContainer container : results) {
                BlockPos pos = container.getPosition();
                double distSq = client.player.squaredDistanceTo(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
                if (distSq <= renderDist * renderDist) {
                    withinRange++;
                }
            }
        }
        if (client != null && client.player != null) {
            String msg = withinRange < results.size()
                ? String.format("§aLit: §e%d§7/§f%d §7(%d far)", withinRange, results.size(), results.size() - withinRange)
                : String.format("§aLit: §e%d §7boxes", results.size());
            client.player.sendMessage(Text.literal(msg), false);
        }
        this.close();
    }
    @Override
    public boolean mouseDragged(Click click, double deltaX, double deltaY) {
        double mouseX = click.x();
        double mouseY = click.y();
        if (isDraggingScrollbar && maxScroll > 0) {
            int totalRows = (int) Math.ceil(filteredItems.size() / (double) ITEMS_PER_ROW);
            int contentHeight = Math.max(totalRows * ITEM_SIZE, MIN_VISIBLE_ROWS * ITEM_SIZE);
            int maxPanelHeight = Math.min(MAX_PANEL_HEIGHT, this.height - TOP_PADDING - BOTTOM_PADDING);
            int scrollbarHeight = Math.min(contentHeight, maxPanelHeight);
            int visibleRows = scrollbarHeight / ITEM_SIZE;
            int thumbHeight = Math.max(20, (int)((double)visibleRows / totalRows * scrollbarHeight));
            int scrollableHeight = scrollbarHeight - thumbHeight;
            int dragDelta = (int)mouseY - scrollbarDragStartY;
            double scrollRatio = (double)dragDelta / scrollableHeight;
            int newOffset = scrollbarDragStartOffset + (int)(scrollRatio * maxScroll);
            scrollOffset = Math.max(0, Math.min(maxScroll, newOffset));
            return true;
        }
        return super.mouseDragged(click, deltaX, deltaY);
    }
    @Override
    public boolean mouseReleased(Click click) {
        int button = click.button();
        if (isDraggingScrollbar && button == 0) {
            isDraggingScrollbar = false;
            return true;
        }
        return super.mouseReleased(click);
    }
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (verticalAmount > 0) {
            scrollOffset = Math.max(0, scrollOffset - 1);
        } else if (verticalAmount < 0) {
            scrollOffset = Math.min(maxScroll, scrollOffset + 1);
        }
        return true;
    }
    @Override
    public boolean shouldPause() {
        return false;
    }
    private String formatCount(int count) {
        if (count >= 1000000) {
            return String.format("%.1fM", count / 1000000.0);
        } else if (count >= 1000) {
            return String.format("%.1fK", count / 1000.0);
        }
        return String.valueOf(count);
    }
    private String formatCountFull(int count) {
        return String.format("%,d", count);
    }
    private String getCurrentDimension() {
        if (client == null || client.world == null) return "unknown";
        return client.world.getRegistryKey().getValue().toString();
    }
    private static class ItemEntry {
        final Item item;
        final int count;
        ItemEntry(Item item, int count) {
            this.item = item;
            this.count = count;
        }
    }
    private enum SortMode {
        COUNT_DESC("Count ↓"),
        COUNT_ASC("Count ↑"),
        NAME_ASC("Name A-Z"),
        NAME_DESC("Name Z-A");
        private final String displayName;
        SortMode(String displayName) {
            this.displayName = displayName;
        }
        public String getDisplayName() {
            return displayName;
        }
        public SortMode next() {
            SortMode[] values = values();
            return values[(this.ordinal() + 1) % values.length];
        }
    }
}