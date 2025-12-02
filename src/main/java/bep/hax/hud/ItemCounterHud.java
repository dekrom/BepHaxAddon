package bep.hax.hud;
import bep.hax.Bep;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import java.util.List;
public class ItemCounterHud extends HudElement {
    public static final HudElementInfo<ItemCounterHud> INFO = new HudElementInfo<>(
        Bep.HUD_GROUP,
        "item-counter",
        "Displays selected items and their inventory counts.",
        ItemCounterHud::new
    );
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgDisplay = settings.createGroup("Display");
    private final Setting<List<Item>> items = sgGeneral.add(new ItemListSetting.Builder()
        .name("items")
        .description("Items to track and display in the HUD.")
        .build()
    );
    private final Setting<Boolean> showTitle = sgGeneral.add(new BoolSetting.Builder()
        .name("show-title")
        .description("Display the HUD title.")
        .defaultValue(false)
        .build()
    );
    private final Setting<String> titleText = sgGeneral.add(new StringSetting.Builder()
        .name("title-text")
        .description("Custom title text.")
        .defaultValue("Item Counter")
        .visible(showTitle::get)
        .build()
    );
    private final Setting<Boolean> showZero = sgGeneral.add(new BoolSetting.Builder()
        .name("show-zero")
        .description("Show items with zero count.")
        .defaultValue(false)
        .build()
    );
    private final Setting<Boolean> showTotal = sgGeneral.add(new BoolSetting.Builder()
        .name("show-total")
        .description("Show total count of all tracked items.")
        .defaultValue(false)
        .build()
    );
    private final Setting<Layout> layout = sgGeneral.add(new EnumSetting.Builder<Layout>()
        .name("layout")
        .description("Layout of the displayed items.")
        .defaultValue(Layout.Vertical)
        .build()
    );
    private final Setting<Double> itemScale = sgDisplay.add(new DoubleSetting.Builder()
        .name("item-scale")
        .description("Scale of the item icons.")
        .defaultValue(1.0)
        .min(0.1)
        .max(3.0)
        .sliderRange(0.1, 3.0)
        .build()
    );
    private final Setting<Double> textScale = sgDisplay.add(new DoubleSetting.Builder()
        .name("text-scale")
        .description("Scale of the count text.")
        .defaultValue(1.0)
        .min(0.1)
        .max(3.0)
        .sliderRange(0.1, 3.0)
        .build()
    );
    private final Setting<SettingColor> textColor = sgDisplay.add(new ColorSetting.Builder()
        .name("text-color")
        .description("Color of the count text.")
        .defaultValue(new SettingColor(255, 255, 255, 255))
        .build()
    );
    private final Setting<SettingColor> zeroColor = sgDisplay.add(new ColorSetting.Builder()
        .name("zero-color")
        .description("Color for items with zero count.")
        .defaultValue(new SettingColor(128, 128, 128, 255))
        .visible(showZero::get)
        .build()
    );
    private final Setting<SettingColor> lowCountColor = sgDisplay.add(new ColorSetting.Builder()
        .name("low-count-color")
        .description("Color for items with low count.")
        .defaultValue(new SettingColor(255, 100, 100, 255))
        .build()
    );
    private final Setting<Integer> lowCountThreshold = sgDisplay.add(new IntSetting.Builder()
        .name("low-count-threshold")
        .description("Threshold for low count warning.")
        .defaultValue(10)
        .min(1)
        .max(100)
        .sliderRange(1, 100)
        .build()
    );
    private final Setting<Boolean> textShadow = sgDisplay.add(new BoolSetting.Builder()
        .name("text-shadow")
        .description("Render shadow behind the text.")
        .defaultValue(true)
        .build()
    );
    private final Setting<Boolean> showStackCount = sgDisplay.add(new BoolSetting.Builder()
        .name("show-stack-count")
        .description("Show count in stacks (e.g., 2.5 stacks).")
        .defaultValue(false)
        .build()
    );
    private final Setting<Boolean> showItemName = sgDisplay.add(new BoolSetting.Builder()
        .name("show-item-name")
        .description("Show item name next to count.")
        .defaultValue(false)
        .build()
    );
    private final Setting<Integer> maxItemsPerRow = sgDisplay.add(new IntSetting.Builder()
        .name("max-items-per-row")
        .description("Maximum items per row in horizontal layout.")
        .defaultValue(8)
        .min(1)
        .max(20)
        .sliderRange(1, 20)
        .visible(() -> layout.get() == Layout.Horizontal)
        .build()
    );
    public enum Layout {
        Vertical,
        Horizontal,
        Grid
    }
    public ItemCounterHud() {
        super(INFO);
    }
    @Override
    public void render(HudRenderer renderer) {
        double curX = this.x;
        double curY = this.y;
        double startX = this.x;
        double width = 0;
        double height = 0;
        if (isInEditor()) {
            String preview = titleText.get();
            renderer.text(preview, curX, curY, textColor.get(), textShadow.get(), textScale.get());
            curY += renderer.textHeight(textShadow.get(), textScale.get()) + 2;
            ItemStack diamond = new ItemStack(Items.DIAMOND);
            renderer.item(diamond, (int) curX, (int) curY, itemScale.get().floatValue(), true);
            renderer.text("64", curX + 16 * itemScale.get() + 2, curY + (8 * itemScale.get() - renderer.textHeight(textShadow.get(), textScale.get()) / 2),
                         textColor.get(), textShadow.get(), textScale.get());
            setSize(
                Math.max(renderer.textWidth(preview, textShadow.get(), textScale.get()),
                        16 * itemScale.get() + renderer.textWidth("64", textShadow.get(), textScale.get()) + 2),
                renderer.textHeight(textShadow.get(), textScale.get()) + 2 + 16 * itemScale.get()
            );
            return;
        }
        if (showTitle.get()) {
            String title = titleText.get();
            renderer.text(title, curX, curY, textColor.get(), textShadow.get(), textScale.get());
            curY += renderer.textHeight(textShadow.get(), textScale.get()) + 2;
            height += renderer.textHeight(textShadow.get(), textScale.get()) + 2;
            width = Math.max(width, renderer.textWidth(title, textShadow.get(), textScale.get()));
        }
        double itemSize = 16 * itemScale.get();
        double textHeight = renderer.textHeight(textShadow.get(), textScale.get());
        double spacing = 2;
        int totalCount = 0;
        int itemsInRow = 0;
        for (Item item : items.get()) {
            ItemStack stack = new ItemStack(item);
            int count = InvUtils.find(item).count();
            totalCount += count;
            if (count == 0 && !showZero.get()) continue;
            SettingColor countColor = textColor.get();
            if (count == 0) {
                countColor = zeroColor.get();
            } else if (count < lowCountThreshold.get()) {
                countColor = lowCountColor.get();
            }
            renderer.item(stack, (int) curX, (int) curY, itemScale.get().floatValue(), true);
            String countText;
            if (showStackCount.get() && stack.getMaxCount() > 1) {
                double stacks = count / (double) stack.getMaxCount();
                countText = String.format("%.1f", stacks);
            } else {
                countText = String.valueOf(count);
            }
            if (showItemName.get()) {
                String itemName = item.getName().getString();
                if (itemName.length() > 10) {
                    itemName = itemName.substring(0, 8) + "..";
                }
                countText = countText + " " + itemName;
            }
            double textX = curX + itemSize + spacing;
            double textY = curY + (itemSize / 2 - textHeight / 2);
            renderer.text(countText, textX, textY, countColor, textShadow.get(), textScale.get());
            double itemWidth = itemSize + spacing + renderer.textWidth(countText, textShadow.get(), textScale.get());
            if (layout.get() == Layout.Horizontal) {
                curX += itemWidth + spacing;
                width += itemWidth + spacing;
                height = Math.max(height, itemSize);
            } else if (layout.get() == Layout.Grid) {
                itemsInRow++;
                if (itemsInRow >= maxItemsPerRow.get()) {
                    curX = startX;
                    curY += itemSize + spacing;
                    height += itemSize + spacing;
                    itemsInRow = 0;
                } else {
                    curX += itemWidth + spacing;
                    width = Math.max(width, curX - startX);
                }
                if (itemsInRow == 0) {
                    height = Math.max(height, itemSize);
                }
            } else {
                curY += itemSize + spacing;
                height += itemSize + spacing;
                width = Math.max(width, itemWidth);
            }
        }
        if (showTotal.get() && totalCount > 0) {
            if (layout.get() != Layout.Vertical) {
                curY += itemSize + spacing;
            }
            String totalText = "Total: " + totalCount;
            renderer.text(totalText, startX, curY, textColor.get(), textShadow.get(), textScale.get());
            height += renderer.textHeight(textShadow.get(), textScale.get()) + spacing;
            width = Math.max(width, renderer.textWidth(totalText, textShadow.get(), textScale.get()));
        }
        setSize(width, height);
    }
}