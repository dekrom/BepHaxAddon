package bep.hax.modules;
import bep.hax.Bep;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringSetting;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.text.Text;
import bep.hax.util.ShulkerDataParser;
import java.util.Map;
import java.util.WeakHashMap;
public class ShulkerOverviewModule extends Module {
    private final WeakHashMap<ItemStack, CachedShulkerData> shulkerCache = new WeakHashMap<>();
    private static class CachedShulkerData {
        final Map<Item, Integer> itemCounts;
        final Item mostCommonItem;
        final boolean hasMultiple;
        CachedShulkerData(Map<Item, Integer> itemCounts) {
            this.itemCounts = itemCounts;
            this.hasMultiple = itemCounts.size() > 1;
            this.mostCommonItem = itemCounts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);
        }
    }
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    public final Setting<Integer> iconSize = sgGeneral.add(new IntSetting.Builder()
        .name("icon-size")
        .description("Size of the item icon overlay.")
        .defaultValue(12)
        .min(4)
        .max(16)
        .sliderMin(4)
        .sliderMax(16)
        .build()
    );
    public final Setting<IconPosition> iconPosition = sgGeneral.add(new EnumSetting.Builder<IconPosition>()
        .name("icon-position")
        .description("Position of the item icon overlay.")
        .defaultValue(IconPosition.Center)
        .build()
    );
    public final Setting<String> multipleText = sgGeneral.add(new StringSetting.Builder()
        .name("multiple-indicator")
        .description("Text to show when shulker contains multiple item types.")
        .defaultValue("+")
        .build()
    );
    public final Setting<Integer> multipleSize = sgGeneral.add(new IntSetting.Builder()
        .name("multiple-size")
        .description("Size of the multiple indicator text.")
        .defaultValue(8)
        .min(4)
        .max(16)
        .sliderMin(4)
        .sliderMax(16)
        .build()
    );
    public final Setting<Boolean> debugMode = sgGeneral.add(new BoolSetting.Builder()
        .name("debug-mode")
        .description("Show debug information.")
        .defaultValue(false)
        .build()
    );
    public ShulkerOverviewModule() {
        super(Bep.CATEGORY, "shulker-overview", "Overlays most common item icon on shulker boxes in inventory.");
    }
    public void renderShulkerOverlay(DrawContext context, int x, int y, ItemStack stack) {
        if (stack.isEmpty()) return;
        if (!(stack.getItem() instanceof BlockItem blockItem)) return;
        if (!(blockItem.getBlock() instanceof ShulkerBoxBlock)) return;
        CachedShulkerData cached = shulkerCache.get(stack);
        if (cached == null) {
            Map<Item, Integer> itemCounts = ShulkerDataParser.parseShulkerContents(stack);
            if (itemCounts.isEmpty()) return;
            cached = new CachedShulkerData(itemCounts);
            shulkerCache.put(stack, cached);
        }
        if (cached.mostCommonItem == null) return;
        Item item = cached.mostCommonItem;
        boolean hasMultiple = cached.hasMultiple;
        if (debugMode.get()) {
            MinecraftClient mc = MinecraftClient.getInstance();
            int count = cached.itemCounts.getOrDefault(item, 0);
            String debug = String.format("Items: %d, Most: %s x%d",
                cached.itemCounts.size(),
                item.getName().getString(),
                count);
            context.drawText(mc.textRenderer, debug, x, y - 10, 0xFFFFFF, true);
        }
        int iconSize = this.iconSize.get();
        int iconX, iconY;
        switch (iconPosition.get()) {
            case BottomLeft -> {
                iconX = x;
                iconY = y + 16 - iconSize;
            }
            case TopRight -> {
                iconX = x + 16 - iconSize;
                iconY = y;
            }
            case TopLeft -> {
                iconX = x;
                iconY = y;
            }
            case Center -> {
                iconX = x + (16 - iconSize) / 2;
                iconY = y + (16 - iconSize) / 2;
            }
            default -> {
                iconX = x + 16 - iconSize;
                iconY = y + 16 - iconSize;
            }
        }
        context.getMatrices().pushMatrix();
        if (iconSize == 16) {
            context.drawItem(new ItemStack(item), iconX, iconY);
        } else {
            float scale = iconSize / 16.0f;
            context.getMatrices().translate(iconX, iconY);
            context.getMatrices().scale(scale, scale);
            context.drawItem(new ItemStack(item), 0, 0);
        }
        context.getMatrices().popMatrix();
        if (hasMultiple && !multipleText.get().isEmpty()) {
            renderMultipleIndicator(context, x, y, multipleText.get(), multipleSize.get());
        }
    }
    private void renderMultipleIndicator(DrawContext context, int slotX, int slotY, String text, int size) {
        MinecraftClient mc = MinecraftClient.getInstance();
        int textWidth = mc.textRenderer.getWidth(text);
        int textX = slotX + 16 - textWidth - 1;
        int textY = slotY + 1;
        context.drawText(mc.textRenderer, text, textX, textY, 0xFFFFFF00, true);
    }
    public enum IconPosition {
        BottomRight,
        BottomLeft,
        TopRight,
        TopLeft,
        Center
    }
}