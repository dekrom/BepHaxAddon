package bep.hax.hud;
import bep.hax.Bep;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import net.minecraft.entity.player.PlayerEntity;
public class SpeedKMH extends HudElement {
    public static final HudElementInfo<SpeedKMH> INFO = new HudElementInfo<>(Bep.HUD_GROUP, "SpeedKMH", "Displays movement speed in KM/H.", SpeedKMH::new);
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final Setting<Boolean> showTitle = sgGeneral.add(new BoolSetting.Builder()
        .name("show-title")
        .description("Display the HUD title.")
        .defaultValue(true)
        .build()
    );
    private final Setting<Boolean> showHorizontalOnly = sgGeneral.add(new BoolSetting.Builder()
        .name("horizontal-only")
        .description("Only calculate horizontal speed (ignore Y movement).")
        .defaultValue(true)
        .build()
    );
    private final Setting<Integer> decimalPlaces = sgGeneral.add(new IntSetting.Builder()
        .name("decimal-places")
        .description("Number of decimal places to show.")
        .defaultValue(1)
        .min(0)
        .max(3)
        .sliderRange(0, 3)
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
    private final Setting<SettingColor> speedColor = sgGeneral.add(new ColorSetting.Builder()
        .name("speed-color")
        .description("Color for the speed text.")
        .defaultValue(new SettingColor(0, 255, 255, 255))
        .build()
    );
    private double currentSpeed = 0.0;
    public SpeedKMH() {
        super(INFO);
    }
    @Override
    public void render(HudRenderer renderer) {
        if (MeteorClient.mc.world == null || MeteorClient.mc.player == null) {
            if (isInEditor()) {
                String demoText = showTitle.get() ? "Speed\n25.3 KM/H" : "25.3 KM/H";
                renderer.text(demoText, x, y, speedColor.get(), textShadow.get(), textScale.get());
                setSize(renderer.textWidth(demoText, textShadow.get(), textScale.get()), renderer.textHeight(textShadow.get(), textScale.get()));
            }
            return;
        }
        updateSpeed();
        double curX = x;
        double curY = y;
        double maxWidth = 0;
        double height = 0;
        double textHeight = renderer.textHeight(textShadow.get(), textScale.get());
        double spacing = 2;
        if (showTitle.get()) {
            String title = "Speed";
            double titleWidth = renderer.textWidth(title, textShadow.get(), textScale.get());
            renderer.text(title, curX, curY, titleColor.get(), textShadow.get(), textScale.get());
            curY += textHeight + spacing;
            height += textHeight + spacing;
            maxWidth = Math.max(maxWidth, titleWidth);
        }
        String speedText = String.format("%." + decimalPlaces.get() + "f KM/H", currentSpeed);
        double speedWidth = renderer.textWidth(speedText, textShadow.get(), textScale.get());
        renderer.text(speedText, curX, curY, speedColor.get(), textShadow.get(), textScale.get());
        height += textHeight;
        maxWidth = Math.max(maxWidth, speedWidth);
        setSize(maxWidth, height);
    }
    private void updateSpeed() {
        PlayerEntity player = MeteorClient.mc.player;
        if (player == null) return;
        double velX = player.getVelocity().x;
        double velZ = player.getVelocity().z;
        double velY = player.getVelocity().y;
        double speed;
        if (showHorizontalOnly.get()) {
            speed = Math.sqrt(velX * velX + velZ * velZ);
        } else {
            speed = Math.sqrt(velX * velX + velZ * velZ + velY * velY);
        }
        currentSpeed = speed * 20 * 3.6;
    }
}