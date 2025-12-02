package bep.hax.hud;
import bep.hax.Bep;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.entity.*;
import net.minecraft.block.enums.ChestType;
import net.minecraft.util.DyeColor;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.WorldChunk;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
public class DubCounterHud extends HudElement {
    public static final HudElementInfo<DubCounterHud> INFO = new HudElementInfo<>(
        Bep.HUD_GROUP,
        "DubCounter",
        "Displays count of all containers in render distance for 2b2t looting.",
        DubCounterHud::new
    );
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgDisplay = settings.createGroup("Display");
    private final SettingGroup sgContainers = settings.createGroup("Containers");
    private final Setting<String> titleText = sgGeneral.add(new StringSetting.Builder()
        .name("title-text")
        .description("Custom title text for the HUD.")
        .defaultValue("Dub Counter")
        .build()
    );
    private final Setting<Boolean> showTitle = sgGeneral.add(new BoolSetting.Builder()
        .name("show-title")
        .description("Display the HUD title.")
        .defaultValue(true)
        .build()
    );
    private final Setting<Boolean> showChestBreakdown = sgGeneral.add(new BoolSetting.Builder()
        .name("show-chest-breakdown")
        .description("Show single and double chest counts (S:X D:X).")
        .defaultValue(false)
        .build()
    );
    private final Setting<Boolean> showShulkerBreakdown = sgGeneral.add(new BoolSetting.Builder()
        .name("show-shulker-breakdown")
        .description("Display shulker boxes broken down by color.")
        .defaultValue(false)
        .build()
    );
    private final Setting<Boolean> sortShulkersByCount = sgGeneral.add(new BoolSetting.Builder()
        .name("sort-shulkers-by-count")
        .description("Sort shulker colors by count (highest first).")
        .defaultValue(true)
        .visible(showShulkerBreakdown::get)
        .build()
    );
    private final Setting<Boolean> showTotalValue = sgGeneral.add(new BoolSetting.Builder()
        .name("show-total-value")
        .description("Show estimated total storage slots.")
        .defaultValue(true)
        .build()
    );
    private final Setting<Boolean> compactMode = sgGeneral.add(new BoolSetting.Builder()
        .name("compact-mode")
        .description("Show counts in a more compact format.")
        .defaultValue(false)
        .build()
    );
    private final Setting<Boolean> showZeroCounts = sgGeneral.add(new BoolSetting.Builder()
        .name("show-zero-counts")
        .description("Always show toggled containers even when count is 0.")
        .defaultValue(false)
        .build()
    );
    private final Setting<Boolean> countChests = sgContainers.add(new BoolSetting.Builder()
        .name("chests")
        .description("Count regular and trapped chests.")
        .defaultValue(true)
        .build()
    );
    private final Setting<Boolean> countBarrels = sgContainers.add(new BoolSetting.Builder()
        .name("barrels")
        .description("Count barrels.")
        .defaultValue(true)
        .build()
    );
    private final Setting<Boolean> countShulkers = sgContainers.add(new BoolSetting.Builder()
        .name("shulker-boxes")
        .description("Count shulker boxes.")
        .defaultValue(true)
        .build()
    );
    private final Setting<Boolean> countEnderChests = sgContainers.add(new BoolSetting.Builder()
        .name("ender-chests")
        .description("Count ender chests.")
        .defaultValue(true)
        .build()
    );
    private final Setting<Boolean> countHoppers = sgContainers.add(new BoolSetting.Builder()
        .name("hoppers")
        .description("Count hoppers (valuable for farms).")
        .defaultValue(true)
        .build()
    );
    private final Setting<Boolean> countDroppers = sgContainers.add(new BoolSetting.Builder()
        .name("droppers")
        .description("Count droppers.")
        .defaultValue(false)
        .build()
    );
    private final Setting<Boolean> countDispensers = sgContainers.add(new BoolSetting.Builder()
        .name("dispensers")
        .description("Count dispensers.")
        .defaultValue(false)
        .build()
    );
    private final Setting<Boolean> countFurnaces = sgContainers.add(new BoolSetting.Builder()
        .name("furnaces")
        .description("Count furnaces (includes blast furnaces and smokers).")
        .defaultValue(false)
        .build()
    );
    private final Setting<Boolean> countBrewingStands = sgContainers.add(new BoolSetting.Builder()
        .name("brewing-stands")
        .description("Count brewing stands.")
        .defaultValue(false)
        .build()
    );
    private final Setting<Boolean> countLecterns = sgContainers.add(new BoolSetting.Builder()
        .name("lecterns")
        .description("Count lecterns (book holders).")
        .defaultValue(false)
        .build()
    );
    private final Setting<Boolean> countCrafters = sgContainers.add(new BoolSetting.Builder()
        .name("crafters")
        .description("Count crafters (auto-crafting blocks).")
        .defaultValue(false)
        .build()
    );
    private final Setting<Boolean> countDecoratedPots = sgContainers.add(new BoolSetting.Builder()
        .name("decorated-pots")
        .description("Count decorated pots.")
        .defaultValue(false)
        .build()
    );
    private final Setting<Double> textScale = sgDisplay.add(new DoubleSetting.Builder()
        .name("text-scale")
        .description("Scale of the text.")
        .defaultValue(1.0)
        .min(0.5)
        .max(3.0)
        .sliderRange(0.5, 3.0)
        .build()
    );
    private final Setting<SettingColor> titleColor = sgDisplay.add(new ColorSetting.Builder()
        .name("title-color")
        .description("Color of the title text.")
        .defaultValue(new SettingColor(255, 255, 255, 255))
        .build()
    );
    private final Setting<SettingColor> textColor = sgDisplay.add(new ColorSetting.Builder()
        .name("text-color")
        .description("Color of the count text.")
        .defaultValue(new SettingColor(200, 200, 200, 255))
        .build()
    );
    private final Setting<SettingColor> shulkerTextColor = sgDisplay.add(new ColorSetting.Builder()
        .name("shulker-text-color")
        .description("Color of the shulker count text.")
        .defaultValue(new SettingColor(255, 200, 100, 255))
        .build()
    );
    private final Setting<SettingColor> valueTextColor = sgDisplay.add(new ColorSetting.Builder()
        .name("value-text-color")
        .description("Color of the value text.")
        .defaultValue(new SettingColor(100, 255, 100, 255))
        .build()
    );
    private final Setting<Boolean> textShadow = sgDisplay.add(new BoolSetting.Builder()
        .name("text-shadow")
        .description("Render shadow behind the text.")
        .defaultValue(true)
        .build()
    );
    private final Setting<Boolean> rainbowTitle = sgDisplay.add(new BoolSetting.Builder()
        .name("rainbow-title")
        .description("Rainbow colored title.")
        .defaultValue(false)
        .build()
    );
    public DubCounterHud() {
        super(INFO);
    }
    @Override
    public void render(HudRenderer renderer) {
        if (MeteorClient.mc.world == null || MeteorClient.mc.player == null) {
            if (isInEditor()) {
                renderPlaceholder(renderer);
            }
            return;
        }
        ContainerCounts counts = countContainers();
        renderCounts(renderer, counts);
    }
    private void renderPlaceholder(HudRenderer renderer) {
        double y = this.y;
        if (showTitle.get()) {
            renderer.text(titleText.get(), x, y, titleColor.get(), textShadow.get(), textScale.get());
            y += renderer.textHeight(textShadow.get(), textScale.get()) + 2;
        }
        renderer.text("Dubs: 12.5", x, y, textColor.get(), textShadow.get(), textScale.get());
        y += renderer.textHeight(textShadow.get(), textScale.get());
        renderer.text("Barrels: 24", x, y, textColor.get(), textShadow.get(), textScale.get());
        y += renderer.textHeight(textShadow.get(), textScale.get());
        renderer.text("Shulkers: 37", x, y, shulkerTextColor.get(), textShadow.get(), textScale.get());
        y += renderer.textHeight(textShadow.get(), textScale.get());
        if (showTotalValue.get()) {
            renderer.text("Storage: ~2145 slots", x, y, valueTextColor.get(), textShadow.get(), textScale.get());
        }
        setSize(150, y - this.y);
    }
    private void renderCounts(HudRenderer renderer, ContainerCounts counts) {
        double y = this.y;
        double maxWidth = 0;
        if (showTitle.get()) {
            SettingColor color = rainbowTitle.get() ? getRainbowColor() : titleColor.get();
            renderer.text(titleText.get(), x, y, color, textShadow.get(), textScale.get());
            y += renderer.textHeight(textShadow.get(), textScale.get()) + 2;
            maxWidth = Math.max(maxWidth, renderer.textWidth(titleText.get(), textShadow.get(), textScale.get()));
        }
        if (countChests.get() && (counts.totalDubs > 0 || showZeroCounts.get())) {
            String dubText;
            if (compactMode.get()) {
                dubText = String.format("D: %.1f", counts.totalDubs);
            } else if (showChestBreakdown.get()) {
                dubText = String.format("Dubs: %.1f (S:%d D:%d)", counts.totalDubs, counts.singleChests, counts.doubleChests);
            } else {
                dubText = String.format("Dubs: %.1f", counts.totalDubs);
            }
            renderer.text(dubText, x, y, textColor.get(), textShadow.get(), textScale.get());
            y += renderer.textHeight(textShadow.get(), textScale.get());
            maxWidth = Math.max(maxWidth, renderer.textWidth(dubText, textShadow.get(), textScale.get()));
        }
        if (countBarrels.get() && (counts.barrelCount > 0 || showZeroCounts.get())) {
            String barrelText = compactMode.get()
                ? String.format("B: %d", counts.barrelCount)
                : String.format("Barrels: %d", counts.barrelCount);
            renderer.text(barrelText, x, y, textColor.get(), textShadow.get(), textScale.get());
            y += renderer.textHeight(textShadow.get(), textScale.get());
            maxWidth = Math.max(maxWidth, renderer.textWidth(barrelText, textShadow.get(), textScale.get()));
        }
        if (countShulkers.get() && (counts.totalShulkers > 0 || showZeroCounts.get())) {
            String shulkerTitle = compactMode.get()
                ? String.format("S: %d", counts.totalShulkers)
                : String.format("Shulkers: %d", counts.totalShulkers);
            renderer.text(shulkerTitle, x, y, shulkerTextColor.get(), textShadow.get(), textScale.get());
            y += renderer.textHeight(textShadow.get(), textScale.get());
            maxWidth = Math.max(maxWidth, renderer.textWidth(shulkerTitle, textShadow.get(), textScale.get()));
            if (showShulkerBreakdown.get()) {
                Map<String, Integer> shulkersToShow = sortShulkersByCount.get()
                    ? sortByValue(counts.shulkersByColor)
                    : counts.shulkersByColor;
                for (Map.Entry<String, Integer> entry : shulkersToShow.entrySet()) {
                    if (entry.getValue() > 0 || showZeroCounts.get()) {
                        String colorText = compactMode.get()
                            ? String.format("  %s: %d", getShortColorName(entry.getKey()), entry.getValue())
                            : String.format("  %s: %d", entry.getKey(), entry.getValue());
                        SettingColor color = getShulkerColor(entry.getKey());
                        renderer.text(colorText, x, y, color, textShadow.get(), textScale.get() * 0.9);
                        y += renderer.textHeight(textShadow.get(), textScale.get() * 0.9);
                        maxWidth = Math.max(maxWidth, renderer.textWidth(colorText, textShadow.get(), textScale.get() * 0.9));
                    }
                }
            }
        }
        if (countEnderChests.get() && (counts.enderChestCount > 0 || showZeroCounts.get())) {
            String enderText = compactMode.get()
                ? String.format("E: %d", counts.enderChestCount)
                : String.format("Ender Chests: %d", counts.enderChestCount);
            renderer.text(enderText, x, y, textColor.get(), textShadow.get(), textScale.get());
            y += renderer.textHeight(textShadow.get(), textScale.get());
            maxWidth = Math.max(maxWidth, renderer.textWidth(enderText, textShadow.get(), textScale.get()));
        }
        if (countHoppers.get() && (counts.hopperCount > 0 || showZeroCounts.get())) {
            String hopperText = compactMode.get()
                ? String.format("H: %d", counts.hopperCount)
                : String.format("Hoppers: %d", counts.hopperCount);
            renderer.text(hopperText, x, y, textColor.get(), textShadow.get(), textScale.get());
            y += renderer.textHeight(textShadow.get(), textScale.get());
            maxWidth = Math.max(maxWidth, renderer.textWidth(hopperText, textShadow.get(), textScale.get()));
        }
        if (countDroppers.get() && (counts.dropperCount > 0 || showZeroCounts.get())) {
            String dropperText = compactMode.get()
                ? String.format("Dr: %d", counts.dropperCount)
                : String.format("Droppers: %d", counts.dropperCount);
            renderer.text(dropperText, x, y, textColor.get(), textShadow.get(), textScale.get());
            y += renderer.textHeight(textShadow.get(), textScale.get());
            maxWidth = Math.max(maxWidth, renderer.textWidth(dropperText, textShadow.get(), textScale.get()));
        }
        if (countDispensers.get() && (counts.dispenserCount > 0 || showZeroCounts.get())) {
            String dispenserText = compactMode.get()
                ? String.format("Di: %d", counts.dispenserCount)
                : String.format("Dispensers: %d", counts.dispenserCount);
            renderer.text(dispenserText, x, y, textColor.get(), textShadow.get(), textScale.get());
            y += renderer.textHeight(textShadow.get(), textScale.get());
            maxWidth = Math.max(maxWidth, renderer.textWidth(dispenserText, textShadow.get(), textScale.get()));
        }
        if (countFurnaces.get() && (counts.furnaceCount > 0 || showZeroCounts.get())) {
            String furnaceText = compactMode.get()
                ? String.format("F: %d", counts.furnaceCount)
                : String.format("Furnaces: %d", counts.furnaceCount);
            renderer.text(furnaceText, x, y, textColor.get(), textShadow.get(), textScale.get());
            y += renderer.textHeight(textShadow.get(), textScale.get());
            maxWidth = Math.max(maxWidth, renderer.textWidth(furnaceText, textShadow.get(), textScale.get()));
        }
        if (countBrewingStands.get() && (counts.brewingStandCount > 0 || showZeroCounts.get())) {
            String brewText = compactMode.get()
                ? String.format("BS: %d", counts.brewingStandCount)
                : String.format("Brewing Stands: %d", counts.brewingStandCount);
            renderer.text(brewText, x, y, textColor.get(), textShadow.get(), textScale.get());
            y += renderer.textHeight(textShadow.get(), textScale.get());
            maxWidth = Math.max(maxWidth, renderer.textWidth(brewText, textShadow.get(), textScale.get()));
        }
        if (countLecterns.get() && (counts.lecternCount > 0 || showZeroCounts.get())) {
            String lecternText = compactMode.get()
                ? String.format("L: %d", counts.lecternCount)
                : String.format("Lecterns: %d", counts.lecternCount);
            renderer.text(lecternText, x, y, textColor.get(), textShadow.get(), textScale.get());
            y += renderer.textHeight(textShadow.get(), textScale.get());
            maxWidth = Math.max(maxWidth, renderer.textWidth(lecternText, textShadow.get(), textScale.get()));
        }
        if (countCrafters.get() && (counts.crafterCount > 0 || showZeroCounts.get())) {
            String crafterText = compactMode.get()
                ? String.format("Cr: %d", counts.crafterCount)
                : String.format("Crafters: %d", counts.crafterCount);
            renderer.text(crafterText, x, y, textColor.get(), textShadow.get(), textScale.get());
            y += renderer.textHeight(textShadow.get(), textScale.get());
            maxWidth = Math.max(maxWidth, renderer.textWidth(crafterText, textShadow.get(), textScale.get()));
        }
        if (countDecoratedPots.get() && (counts.decoratedPotCount > 0 || showZeroCounts.get())) {
            String potText = compactMode.get()
                ? String.format("DP: %d", counts.decoratedPotCount)
                : String.format("Decorated Pots: %d", counts.decoratedPotCount);
            renderer.text(potText, x, y, textColor.get(), textShadow.get(), textScale.get());
            y += renderer.textHeight(textShadow.get(), textScale.get());
            maxWidth = Math.max(maxWidth, renderer.textWidth(potText, textShadow.get(), textScale.get()));
        }
        if (showTotalValue.get() && counts.getTotalSlots() > 0) {
            y += 2;
            String valueText = compactMode.get()
                ? String.format("Slots: %d", counts.getTotalSlots())
                : String.format("Storage: ~%d slots", counts.getTotalSlots());
            renderer.text(valueText, x, y, valueTextColor.get(), textShadow.get(), textScale.get());
            y += renderer.textHeight(textShadow.get(), textScale.get());
            maxWidth = Math.max(maxWidth, renderer.textWidth(valueText, textShadow.get(), textScale.get()));
        }
        setSize(maxWidth, y - this.y);
    }
    private Map<String, Integer> sortByValue(Map<String, Integer> map) {
        Map<String, Integer> sorted = new TreeMap<>((a, b) -> {
            int comp = map.get(b).compareTo(map.get(a));
            return comp != 0 ? comp : a.compareTo(b);
        });
        sorted.putAll(map);
        return sorted;
    }
    private SettingColor getRainbowColor() {
        double time = System.currentTimeMillis() / 1000.0;
        int r = (int) ((Math.sin(time) + 1) * 127.5);
        int g = (int) ((Math.sin(time + 2.094) + 1) * 127.5);
        int b = (int) ((Math.sin(time + 4.189) + 1) * 127.5);
        return new SettingColor(r, g, b, 255);
    }
    private SettingColor getShulkerColor(String colorName) {
        return switch (colorName) {
            case "White" -> new SettingColor(255, 255, 255, 255);
            case "Orange" -> new SettingColor(255, 165, 0, 255);
            case "Magenta" -> new SettingColor(255, 0, 255, 255);
            case "Light Blue" -> new SettingColor(173, 216, 230, 255);
            case "Yellow" -> new SettingColor(255, 255, 0, 255);
            case "Lime" -> new SettingColor(50, 205, 50, 255);
            case "Pink" -> new SettingColor(255, 192, 203, 255);
            case "Gray" -> new SettingColor(128, 128, 128, 255);
            case "Light Gray" -> new SettingColor(211, 211, 211, 255);
            case "Cyan" -> new SettingColor(0, 255, 255, 255);
            case "Purple" -> new SettingColor(128, 0, 128, 255);
            case "Blue" -> new SettingColor(0, 0, 255, 255);
            case "Brown" -> new SettingColor(139, 69, 19, 255);
            case "Green" -> new SettingColor(0, 128, 0, 255);
            case "Red" -> new SettingColor(255, 0, 0, 255);
            case "Black" -> new SettingColor(50, 50, 50, 255);
            case "Undyed" -> new SettingColor(150, 100, 75, 255);
            default -> textColor.get();
        };
    }
    private String getShortColorName(String fullName) {
        return switch (fullName) {
            case "White" -> "W";
            case "Orange" -> "O";
            case "Magenta" -> "M";
            case "Light Blue" -> "LB";
            case "Yellow" -> "Y";
            case "Lime" -> "Li";
            case "Pink" -> "Pi";
            case "Gray" -> "Gr";
            case "Light Gray" -> "LG";
            case "Cyan" -> "C";
            case "Purple" -> "Pu";
            case "Blue" -> "B";
            case "Brown" -> "Br";
            case "Green" -> "Gn";
            case "Red" -> "R";
            case "Black" -> "Bl";
            case "Undyed" -> "U";
            default -> fullName.substring(0, Math.min(3, fullName.length()));
        };
    }
    private ContainerCounts countContainers() {
        ContainerCounts counts = new ContainerCounts();
        if (MeteorClient.mc.world == null || MeteorClient.mc.player == null) return counts;
        int renderDistance = MeteorClient.mc.options.getViewDistance().getValue();
        int playerChunkX = MeteorClient.mc.player.getChunkPos().x;
        int playerChunkZ = MeteorClient.mc.player.getChunkPos().z;
        for (int cx = playerChunkX - renderDistance; cx <= playerChunkX + renderDistance; cx++) {
            for (int cz = playerChunkZ - renderDistance; cz <= playerChunkZ + renderDistance; cz++) {
                WorldChunk chunk = MeteorClient.mc.world.getChunk(cx, cz);
                if (chunk == null) continue;
                for (BlockPos pos : chunk.getBlockEntityPositions()) {
                    BlockEntity blockEntity = chunk.getBlockEntity(pos);
                    if (blockEntity == null) continue;
                    if (countChests.get()) {
                        if (blockEntity instanceof ChestBlockEntity || blockEntity instanceof TrappedChestBlockEntity) {
                            var blockState = blockEntity.getCachedState();
                            if (blockState.getBlock() instanceof ChestBlock && blockState.contains(ChestBlock.CHEST_TYPE)) {
                                ChestType type = blockState.get(ChestBlock.CHEST_TYPE);
                                if (type == ChestType.SINGLE) {
                                    counts.singleChests++;
                                } else if (type == ChestType.LEFT) {
                                    counts.doubleChests++;
                                }
                            }
                        }
                    }
                    if (countBarrels.get() && blockEntity instanceof BarrelBlockEntity) {
                        counts.barrelCount++;
                    }
                    if (countShulkers.get() && blockEntity instanceof ShulkerBoxBlockEntity shulker) {
                        counts.totalShulkers++;
                        DyeColor color = shulker.getColor();
                        String colorName = color != null ? getColorName(color) : "Undyed";
                        counts.shulkersByColor.merge(colorName, 1, Integer::sum);
                    }
                    if (countEnderChests.get() && blockEntity instanceof EnderChestBlockEntity) {
                        counts.enderChestCount++;
                    }
                    if (countHoppers.get() && blockEntity instanceof HopperBlockEntity) {
                        counts.hopperCount++;
                    }
                    if (countDroppers.get() && blockEntity instanceof DropperBlockEntity) {
                        counts.dropperCount++;
                    }
                    if (countDispensers.get() && blockEntity instanceof DispenserBlockEntity && !(blockEntity instanceof DropperBlockEntity)) {
                        counts.dispenserCount++;
                    }
                    if (countFurnaces.get()) {
                        if (blockEntity instanceof FurnaceBlockEntity ||
                            blockEntity instanceof BlastFurnaceBlockEntity ||
                            blockEntity instanceof SmokerBlockEntity) {
                            counts.furnaceCount++;
                        }
                    }
                    if (countBrewingStands.get() && blockEntity instanceof BrewingStandBlockEntity) {
                        counts.brewingStandCount++;
                    }
                    if (countLecterns.get() && blockEntity instanceof LecternBlockEntity) {
                        counts.lecternCount++;
                    }
                    if (countCrafters.get()) {
                        String className = blockEntity.getClass().getSimpleName();
                        if (className.equals("CrafterBlockEntity")) {
                            counts.crafterCount++;
                        }
                    }
                    if (countDecoratedPots.get() && blockEntity instanceof DecoratedPotBlockEntity) {
                        counts.decoratedPotCount++;
                    }
                }
            }
        }
        counts.totalDubs = counts.doubleChests + (counts.singleChests * 0.5);
        return counts;
    }
    private String getColorName(DyeColor color) {
        return switch (color) {
            case WHITE -> "White";
            case ORANGE -> "Orange";
            case MAGENTA -> "Magenta";
            case LIGHT_BLUE -> "Light Blue";
            case YELLOW -> "Yellow";
            case LIME -> "Lime";
            case PINK -> "Pink";
            case GRAY -> "Gray";
            case LIGHT_GRAY -> "Light Gray";
            case CYAN -> "Cyan";
            case PURPLE -> "Purple";
            case BLUE -> "Blue";
            case BROWN -> "Brown";
            case GREEN -> "Green";
            case RED -> "Red";
            case BLACK -> "Black";
        };
    }
    private static class ContainerCounts {
        int singleChests = 0;
        int doubleChests = 0;
        double totalDubs = 0;
        int barrelCount = 0;
        int hopperCount = 0;
        int dropperCount = 0;
        int dispenserCount = 0;
        int enderChestCount = 0;
        int furnaceCount = 0;
        int brewingStandCount = 0;
        int lecternCount = 0;
        int crafterCount = 0;
        int decoratedPotCount = 0;
        int totalShulkers = 0;
        Map<String, Integer> shulkersByColor = new HashMap<>();
        int getTotalSlots() {
            int slots = 0;
            slots += singleChests * 27;
            slots += doubleChests * 54;
            slots += barrelCount * 27;
            slots += hopperCount * 5;
            slots += dropperCount * 9;
            slots += dispenserCount * 9;
            slots += totalShulkers * 27;
            slots += enderChestCount * 27;
            slots += furnaceCount * 3;
            slots += brewingStandCount * 5;
            slots += lecternCount * 1;
            slots += crafterCount * 9;
            slots += decoratedPotCount * 1;
            return slots;
        }
    }
}