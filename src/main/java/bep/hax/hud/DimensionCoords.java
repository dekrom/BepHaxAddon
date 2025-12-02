package bep.hax.hud;
import bep.hax.Bep;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
public class DimensionCoords extends HudElement {
    public static final HudElementInfo<DimensionCoords> INFO = new HudElementInfo<>(Bep.HUD_GROUP, "DimensionCoords", "Displays coordinates for both overworld and nether dimensions.", DimensionCoords::new);
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final Setting<Boolean> showTitle = sgGeneral.add(new BoolSetting.Builder()
        .name("show-title")
        .description("Display the HUD title.")
        .defaultValue(false)
        .build()
    );
    private final Setting<Boolean> showCurrentDim = sgGeneral.add(new BoolSetting.Builder()
        .name("show-current-dimension")
        .description("Show current dimension name.")
        .defaultValue(false)
        .build()
    );
    private final Setting<Double> textScale = sgGeneral.add(new DoubleSetting.Builder()
        .name("text-scale")
        .description("Scale of the text.")
        .defaultValue(1.0)
        .min(0.1)
        .sliderRange(0.1, 3.0)
        .build()
    );
    private final Setting<Boolean> textShadow = sgGeneral.add(new BoolSetting.Builder()
        .name("text-shadow")
        .description("Render shadow behind the text.")
        .defaultValue(true)
        .build()
    );
    private final Setting<SettingColor> titleColor = sgGeneral.add(new ColorSetting.Builder()
        .name("title-color")
        .description("Color for the title text.")
        .defaultValue(new SettingColor(255, 255, 255, 255))
        .build()
    );
    private final Setting<SettingColor> overworldColor = sgGeneral.add(new ColorSetting.Builder()
        .name("overworld-color")
        .description("Color for overworld coordinates.")
        .defaultValue(new SettingColor(0, 255, 0, 255))
        .build()
    );
    private final Setting<SettingColor> netherColor = sgGeneral.add(new ColorSetting.Builder()
        .name("nether-color")
        .description("Color for nether coordinates.")
        .defaultValue(new SettingColor(255, 0, 0, 255))
        .build()
    );
    private final Setting<SettingColor> endColor = sgGeneral.add(new ColorSetting.Builder()
        .name("end-color")
        .description("Color for end coordinates.")
        .defaultValue(new SettingColor(255, 0, 255, 255))
        .build()
    );
    private final Setting<Boolean> showLabels = sgGeneral.add(new BoolSetting.Builder()
        .name("show-labels")
        .description("Show dimension labels (e.g. 'Overworld:', 'Nether:').")
        .defaultValue(true)
        .build()
    );
    private final Setting<Boolean> removeCommas = sgGeneral.add(new BoolSetting.Builder()
        .name("remove-commas")
        .description("Remove commas from coordinates.")
        .defaultValue(false)
        .build()
    );
    private final Setting<Boolean> horizontalLayout = sgGeneral.add(new BoolSetting.Builder()
        .name("horizontal-layout")
        .description("Display coordinates horizontally next to each other.")
        .defaultValue(false)
        .build()
    );
    public DimensionCoords() {
        super(INFO);
    }
    @Override
    public void render(HudRenderer renderer) {
        if (MeteorClient.mc.world == null || MeteorClient.mc.player == null) {
            if (isInEditor()) {
                renderer.text("Dimension Coords", x, y, titleColor.get(), textShadow.get(), textScale.get());
                setSize(renderer.textWidth("Dimension Coords", textShadow.get(), textScale.get()), renderer.textHeight(textShadow.get(), textScale.get()));
            }
            return;
        }
        BlockPos playerPos = MeteorClient.mc.player.getBlockPos();
        Identifier dimensionId = MeteorClient.mc.world.getDimension().effects();
        double curX = x;
        double curY = y;
        double maxWidth = 0;
        double height = 0;
        double textHeight = renderer.textHeight(textShadow.get(), textScale.get());
        double spacing = 2;
        if (showTitle.get()) {
            String title = "Dimension Coords";
            double titleWidth = renderer.textWidth(title, textShadow.get(), textScale.get());
            renderer.text(title, curX, curY, titleColor.get(), textShadow.get(), textScale.get());
            curY += textHeight + spacing;
            height += textHeight + spacing;
            maxWidth = Math.max(maxWidth, titleWidth);
        }
        if (showCurrentDim.get()) {
            String dimName = getDimensionName(dimensionId);
            String dimText = "Current: " + dimName;
            double dimWidth = renderer.textWidth(dimText, textShadow.get(), textScale.get());
            renderer.text(dimText, curX, curY, titleColor.get(), textShadow.get(), textScale.get());
            curY += textHeight + spacing;
            height += textHeight + spacing;
            maxWidth = Math.max(maxWidth, dimWidth);
        }
        String coordFormat = removeCommas.get() ? "%d %d %d" : "%d, %d, %d";
        if (isOverworld(dimensionId)) {
            String overworldLabel = showLabels.get() ? "Overworld: " : "";
            String netherLabel = showLabels.get() ? "Nether: " : "";
            String overworldText = overworldLabel + String.format(coordFormat, playerPos.getX(), playerPos.getY(), playerPos.getZ());
            String netherText = netherLabel + String.format(coordFormat, playerPos.getX() / 8, playerPos.getY(), playerPos.getZ() / 8);
            if (horizontalLayout.get()) {
                double overworldWidth = renderer.textWidth(overworldText, textShadow.get(), textScale.get());
                renderer.text(overworldText, curX, curY, overworldColor.get(), textShadow.get(), textScale.get());
                curX += overworldWidth + spacing * 3;
                renderer.text(netherText, curX, curY, netherColor.get(), textShadow.get(), textScale.get());
                double netherWidth = renderer.textWidth(netherText, textShadow.get(), textScale.get());
                maxWidth = Math.max(maxWidth, overworldWidth + netherWidth + spacing * 3);
                height += textHeight;
            } else {
                double overworldWidth = renderer.textWidth(overworldText, textShadow.get(), textScale.get());
                double netherWidth = renderer.textWidth(netherText, textShadow.get(), textScale.get());
                renderer.text(overworldText, curX, curY, overworldColor.get(), textShadow.get(), textScale.get());
                curY += textHeight + spacing;
                height += textHeight + spacing;
                maxWidth = Math.max(maxWidth, overworldWidth);
                renderer.text(netherText, curX, curY, netherColor.get(), textShadow.get(), textScale.get());
                curY += textHeight + spacing;
                height += textHeight + spacing;
                maxWidth = Math.max(maxWidth, netherWidth);
            }
        } else if (isNether(dimensionId)) {
            String netherLabel = showLabels.get() ? "Nether: " : "";
            String overworldLabel = showLabels.get() ? "Overworld: " : "";
            String netherText = netherLabel + String.format(coordFormat, playerPos.getX(), playerPos.getY(), playerPos.getZ());
            String overworldText = overworldLabel + String.format(coordFormat, playerPos.getX() * 8, playerPos.getY(), playerPos.getZ() * 8);
            if (horizontalLayout.get()) {
                double netherWidth = renderer.textWidth(netherText, textShadow.get(), textScale.get());
                renderer.text(netherText, curX, curY, netherColor.get(), textShadow.get(), textScale.get());
                curX += netherWidth + spacing * 3;
                renderer.text(overworldText, curX, curY, overworldColor.get(), textShadow.get(), textScale.get());
                double overworldWidth = renderer.textWidth(overworldText, textShadow.get(), textScale.get());
                maxWidth = Math.max(maxWidth, netherWidth + overworldWidth + spacing * 3);
                height += textHeight;
            } else {
                double netherWidth = renderer.textWidth(netherText, textShadow.get(), textScale.get());
                double overworldWidth = renderer.textWidth(overworldText, textShadow.get(), textScale.get());
                renderer.text(netherText, curX, curY, netherColor.get(), textShadow.get(), textScale.get());
                curY += textHeight + spacing;
                height += textHeight + spacing;
                maxWidth = Math.max(maxWidth, netherWidth);
                renderer.text(overworldText, curX, curY, overworldColor.get(), textShadow.get(), textScale.get());
                curY += textHeight + spacing;
                height += textHeight + spacing;
                maxWidth = Math.max(maxWidth, overworldWidth);
            }
        } else if (isEnd(dimensionId)) {
            String endLabel = showLabels.get() ? "The End: " : "";
            String endText = endLabel + String.format(coordFormat, playerPos.getX(), playerPos.getY(), playerPos.getZ());
            double endWidth = renderer.textWidth(endText, textShadow.get(), textScale.get());
            renderer.text(endText, curX, curY, endColor.get(), textShadow.get(), textScale.get());
            curY += textHeight + spacing;
            height += textHeight + spacing;
            maxWidth = Math.max(maxWidth, endWidth);
        }
        setSize(maxWidth, height > 0 ? height - spacing : 0);
    }
    private boolean isOverworld(Identifier dimensionId) {
        return dimensionId.equals(Identifier.of("minecraft:overworld"));
    }
    private boolean isNether(Identifier dimensionId) {
        return dimensionId.equals(Identifier.of("minecraft:the_nether"));
    }
    private boolean isEnd(Identifier dimensionId) {
        return dimensionId.equals(Identifier.of("minecraft:the_end"));
    }
    private String getDimensionName(Identifier dimensionId) {
        if (isOverworld(dimensionId)) return "Overworld";
        if (isNether(dimensionId)) return "Nether";
        if (isEnd(dimensionId)) return "The End";
        return dimensionId.getPath();
    }
}