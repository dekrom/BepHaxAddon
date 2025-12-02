package bep.hax.hud;
import bep.hax.Bep;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import java.util.*;
public class MobInfo extends HudElement {
    public static final HudElementInfo<MobInfo> INFO = new HudElementInfo<>(
        Bep.HUD_GROUP,
        "MobInfo",
        "Track mob spawns and density.",
        MobInfo::new
    );
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgFilter = settings.createGroup("Filter");
    private final SettingGroup sgDisplay = settings.createGroup("Display");
    private final SettingGroup sgGraph = settings.createGroup("Graph");
    private final SettingGroup sgColors = settings.createGroup("Colors");
    private final Setting<Boolean> trackSpawnRate = sgGeneral.add(new BoolSetting.Builder()
        .name("track-spawn-rate")
        .description("Calculate spawns per hour.")
        .defaultValue(true)
        .build()
    );
    private final Setting<Integer> rateUpdateInterval = sgGeneral.add(new IntSetting.Builder()
        .name("rate-update")
        .description("Ticks between rate updates.")
        .defaultValue(100)
        .min(20)
        .max(600)
        .sliderRange(20, 600)
        .visible(trackSpawnRate::get)
        .build()
    );
    private final Setting<Boolean> trackDensity = sgGeneral.add(new BoolSetting.Builder()
        .name("track-density")
        .description("Track mob density in area.")
        .defaultValue(true)
        .build()
    );
    private final Setting<Integer> scanRadius = sgGeneral.add(new IntSetting.Builder()
        .name("scan-radius")
        .description("Radius to scan in chunks.")
        .defaultValue(8)
        .min(1)
        .max(32)
        .sliderRange(1, 32)
        .visible(trackDensity::get)
        .build()
    );
    private final Setting<Integer> densityAlert = sgGeneral.add(new IntSetting.Builder()
        .name("density-alert")
        .description("Highlight when mob count exceeds this.")
        .defaultValue(30)
        .min(10)
        .max(100)
        .sliderRange(10, 100)
        .visible(trackDensity::get)
        .build()
    );
    private final Setting<Boolean> resetOnDimension = sgGeneral.add(new BoolSetting.Builder()
        .name("reset-on-dimension")
        .description("Reset when changing dimensions.")
        .defaultValue(true)
        .build()
    );
    private final Setting<Set<EntityType<?>>> entities = sgFilter.add(new EntityTypeListSetting.Builder()
        .name("entities")
        .description("Entities to track.")
        .defaultValue(getDefaults())
        .build()
    );
    private final Setting<Boolean> showTitle = sgDisplay.add(new BoolSetting.Builder()
        .name("title")
        .defaultValue(true)
        .build()
    );
    private final Setting<Boolean> showRate = sgDisplay.add(new BoolSetting.Builder()
        .name("spawn-rate")
        .defaultValue(true)
        .visible(trackSpawnRate::get)
        .build()
    );
    private final Setting<Boolean> showNearby = sgDisplay.add(new BoolSetting.Builder()
        .name("nearby-count")
        .defaultValue(true)
        .build()
    );
    private final Setting<Boolean> showTotal = sgDisplay.add(new BoolSetting.Builder()
        .name("total-spawned")
        .defaultValue(true)
        .visible(trackSpawnRate::get)
        .build()
    );
    private final Setting<Boolean> showDensityValue = sgDisplay.add(new BoolSetting.Builder()
        .name("density-value")
        .defaultValue(true)
        .visible(trackDensity::get)
        .build()
    );
    private final Setting<Boolean> showTime = sgDisplay.add(new BoolSetting.Builder()
        .name("session-time")
        .defaultValue(true)
        .build()
    );
    private final Setting<Boolean> showRateGraph = sgGraph.add(new BoolSetting.Builder()
        .name("spawn-rate-graph")
        .description("Show spawn rate graph.")
        .defaultValue(true)
        .visible(trackSpawnRate::get)
        .build()
    );
    private final Setting<Boolean> showCountGraph = sgGraph.add(new BoolSetting.Builder()
        .name("mob-count-graph")
        .description("Show mob count graph.")
        .defaultValue(true)
        .build()
    );
    private final Setting<Integer> graphWidth = sgGraph.add(new IntSetting.Builder()
        .name("width")
        .description("Graph width.")
        .defaultValue(200)
        .min(100)
        .max(400)
        .sliderRange(100, 400)
        .build()
    );
    private final Setting<Integer> graphHeight = sgGraph.add(new IntSetting.Builder()
        .name("height")
        .description("Graph height.")
        .defaultValue(60)
        .min(30)
        .max(150)
        .sliderRange(30, 150)
        .build()
    );
    private final Setting<Integer> graphPoints = sgGraph.add(new IntSetting.Builder()
        .name("data-points")
        .description("Number of data points.")
        .defaultValue(30)
        .min(10)
        .max(60)
        .sliderRange(10, 60)
        .build()
    );
    private final Setting<Integer> graphUpdate = sgGraph.add(new IntSetting.Builder()
        .name("update-rate")
        .description("Ticks between graph updates.")
        .defaultValue(20)
        .min(5)
        .max(100)
        .sliderRange(5, 100)
        .build()
    );
    private final Setting<Boolean> showGrid = sgGraph.add(new BoolSetting.Builder()
        .name("grid")
        .description("Show grid lines.")
        .defaultValue(true)
        .build()
    );
    private final Setting<SettingColor> titleColor = sgColors.add(new ColorSetting.Builder()
        .name("title-color")
        .description("Title text color.")
        .defaultValue(new SettingColor(255, 255, 255))
        .build()
    );
    private final Setting<SettingColor> titleAlertColor = sgColors.add(new ColorSetting.Builder()
        .name("title-alert-color")
        .description("Title color when density alert triggered.")
        .defaultValue(new SettingColor(255, 100, 100))
        .build()
    );
    private final Setting<SettingColor> rateHighColor = sgColors.add(new ColorSetting.Builder()
        .name("rate-high-color")
        .description("Spawn rate color (>3000/hr).")
        .defaultValue(new SettingColor(100, 200, 255))
        .build()
    );
    private final Setting<SettingColor> rateGoodColor = sgColors.add(new ColorSetting.Builder()
        .name("rate-good-color")
        .description("Spawn rate color (>1000/hr).")
        .defaultValue(new SettingColor(100, 255, 100))
        .build()
    );
    private final Setting<SettingColor> rateMedColor = sgColors.add(new ColorSetting.Builder()
        .name("rate-medium-color")
        .description("Spawn rate color (>300/hr).")
        .defaultValue(new SettingColor(255, 255, 100))
        .build()
    );
    private final Setting<SettingColor> rateLowColor = sgColors.add(new ColorSetting.Builder()
        .name("rate-low-color")
        .description("Spawn rate color (<300/hr).")
        .defaultValue(new SettingColor(255, 100, 100))
        .build()
    );
    private final Setting<SettingColor> nearbyHighColor = sgColors.add(new ColorSetting.Builder()
        .name("nearby-high-color")
        .description("Nearby count color (>=alert).")
        .defaultValue(new SettingColor(255, 100, 100))
        .build()
    );
    private final Setting<SettingColor> nearbyMedColor = sgColors.add(new ColorSetting.Builder()
        .name("nearby-medium-color")
        .description("Nearby count color (>=alert/2).")
        .defaultValue(new SettingColor(255, 255, 100))
        .build()
    );
    private final Setting<SettingColor> nearbyLowColor = sgColors.add(new ColorSetting.Builder()
        .name("nearby-low-color")
        .description("Nearby count color (<alert/2).")
        .defaultValue(new SettingColor(100, 200, 255))
        .build()
    );
    private final Setting<SettingColor> totalColor = sgColors.add(new ColorSetting.Builder()
        .name("total-color")
        .description("Total spawned color.")
        .defaultValue(new SettingColor(255, 255, 255))
        .build()
    );
    private final Setting<SettingColor> densityColor = sgColors.add(new ColorSetting.Builder()
        .name("density-color")
        .description("Density value color.")
        .defaultValue(new SettingColor(100, 255, 100))
        .build()
    );
    private final Setting<SettingColor> timeColor = sgColors.add(new ColorSetting.Builder()
        .name("time-color")
        .description("Session time color.")
        .defaultValue(new SettingColor(200, 200, 200))
        .build()
    );
    private final Setting<SettingColor> graphBgColor = sgColors.add(new ColorSetting.Builder()
        .name("graph-background-color")
        .description("Graph background color.")
        .defaultValue(new SettingColor(20, 20, 20, 180))
        .build()
    );
    private final Setting<SettingColor> graphGridColor = sgColors.add(new ColorSetting.Builder()
        .name("graph-grid-color")
        .description("Graph grid line color.")
        .defaultValue(new SettingColor(60, 60, 60, 100))
        .build()
    );
    private final Setting<SettingColor> graphLabelColor = sgColors.add(new ColorSetting.Builder()
        .name("graph-label-color")
        .description("Graph label color.")
        .defaultValue(new SettingColor(100, 255, 100))
        .build()
    );
    private final Setting<SettingColor> graphPeakColor = sgColors.add(new ColorSetting.Builder()
        .name("graph-peak-color")
        .description("Peak value label color.")
        .defaultValue(new SettingColor(255, 100, 100))
        .build()
    );
    private final Set<UUID> tracked = Collections.synchronizedSet(new HashSet<>());
    private final Set<UUID> nearby = Collections.synchronizedSet(new HashSet<>());
    private final LinkedList<Double> rateHistory = new LinkedList<>();
    private final LinkedList<Integer> countHistory = new LinkedList<>();
    private long sessionStart = System.currentTimeMillis();
    private int totalSpawned = 0;
    private double spawnRate = 0;
    private double density = 0;
    private int ticks = 0;
    private int lastTotal = 0;
    private String lastDim = "";
    private int graphTicks = 0;
    private double peakRate = 0;
    private int peakCount = 0;
    private static final double TICKS_PER_HOUR = 72000.0;
    public MobInfo() {
        super(INFO);
        MeteorClient.EVENT_BUS.subscribe(this);
    }
    private static Set<EntityType<?>> getDefaults() {
        Set<EntityType<?>> set = new HashSet<>();
        set.add(EntityType.ZOMBIE);
        set.add(EntityType.SKELETON);
        set.add(EntityType.CREEPER);
        set.add(EntityType.SPIDER);
        set.add(EntityType.ENDERMAN);
        return set;
    }
    @Override
    public void render(HudRenderer renderer) {
        if (MeteorClient.mc.world == null || MeteorClient.mc.player == null) {
            String text = "Mob Info";
            renderer.text(text, x, y, titleColor.get(), false);
            setSize(renderer.textWidth(text, false), renderer.textHeight(false));
            return;
        }
        double posY = y;
        double width = 0;
        double h = renderer.textHeight(false);
        if (showTitle.get()) {
            String title = "Mob Info";
            SettingColor col = titleColor.get();
            if (trackDensity.get() && nearby.size() >= densityAlert.get()) {
                col = titleAlertColor.get();
            }
            width = Math.max(width, renderer.textWidth(title, false));
            renderer.text(title, x, posY, col, false);
            posY += h + 2;
        }
        if (showRate.get() && trackSpawnRate.get()) {
            String text = format(spawnRate) + "/hr";
            width = Math.max(width, renderer.textWidth(text, false));
            renderer.text(text, x, posY, getColorForRate(spawnRate), false);
            posY += h + 2;
        }
        if (showNearby.get()) {
            String text = "Nearby: " + nearby.size();
            SettingColor col = titleColor.get();
            if (trackDensity.get()) {
                int alert = densityAlert.get();
                if (nearby.size() >= alert) col = nearbyHighColor.get();
                else if (nearby.size() >= alert / 2) col = nearbyMedColor.get();
                else col = nearbyLowColor.get();
            }
            width = Math.max(width, renderer.textWidth(text, false));
            renderer.text(text, x, posY, col, false);
            posY += h + 2;
        }
        if (showTotal.get() && trackSpawnRate.get()) {
            String text = "Total: " + totalSpawned;
            width = Math.max(width, renderer.textWidth(text, false));
            renderer.text(text, x, posY, totalColor.get(), false);
            posY += h + 2;
        }
        if (showDensityValue.get() && trackDensity.get()) {
            String text = String.format("Density: %.2f/chunk", density);
            width = Math.max(width, renderer.textWidth(text, false));
            renderer.text(text, x, posY, densityColor.get(), false);
            posY += h + 2;
        }
        if (showTime.get()) {
            long time = System.currentTimeMillis() - sessionStart;
            long sec = time / 1000;
            long min = sec / 60;
            long hrs = min / 60;
            String text;
            if (hrs > 0) text = hrs + "h " + (min % 60) + "m";
            else if (min > 0) text = min + "m " + (sec % 60) + "s";
            else text = sec + "s";
            width = Math.max(width, renderer.textWidth(text, false));
            renderer.text(text, x, posY, timeColor.get(), false);
            posY += h + 2;
        }
        if (showRateGraph.get() && trackSpawnRate.get() && rateHistory.size() >= 2) {
            posY += 4;
            posY = renderRateGraph(renderer, posY);
            width = Math.max(width, graphWidth.get());
        }
        if (showCountGraph.get() && countHistory.size() >= 2) {
            posY += 4;
            posY = renderCountGraph(renderer, posY);
            width = Math.max(width, graphWidth.get());
        }
        setSize(Math.max(width, 80), posY - y);
    }
    private double renderRateGraph(HudRenderer renderer, double startY) {
        double gx = x;
        double gy = startY;
        double w = graphWidth.get();
        double h = graphHeight.get();
        String title = "Spawn Rate";
        renderer.text(title, gx, gy, titleColor.get(), false);
        gy += renderer.textHeight(false) + 2;
        renderer.quad(gx, gy, w, h, graphBgColor.get());
        double max = Math.max(1, rateHistory.stream().max(Double::compare).orElse(1.0));
        if (showGrid.get()) {
            for (int i = 1; i <= 4; i++) {
                double gridY = gy + h - (h * i / 4.0);
                renderer.quad(gx, gridY, w, 1, graphGridColor.get());
            }
        }
        for (int i = 0; i < rateHistory.size() - 1; i++) {
            double v1 = rateHistory.get(i);
            double v2 = rateHistory.get(i + 1);
            double x1 = gx + (i / (double)(rateHistory.size() - 1)) * w;
            double x2 = gx + ((i + 1) / (double)(rateHistory.size() - 1)) * w;
            double y1 = gy + h - (v1 / max) * h;
            double y2 = gy + h - (v2 / max) * h;
            drawLine(renderer, x1, y1, x2, y2, getColorForRate(v2));
        }
        String label = format(spawnRate) + "/hr";
        double lw = renderer.textWidth(label, false);
        renderer.text(label, gx + w - lw, gy - renderer.textHeight(false) - 2, graphLabelColor.get(), false);
        return gy + h + 4;
    }
    private double renderCountGraph(HudRenderer renderer, double startY) {
        double gx = x;
        double gy = startY;
        double w = graphWidth.get();
        double h = graphHeight.get();
        String title = "Mob Count";
        renderer.text(title, gx, gy, titleColor.get(), false);
        gy += renderer.textHeight(false) + 2;
        renderer.quad(gx, gy, w, h, graphBgColor.get());
        int max = Math.max(1, countHistory.stream().max(Integer::compare).orElse(1));
        if (showGrid.get()) {
            for (int i = 1; i <= 4; i++) {
                double gridY = gy + h - (h * i / 4.0);
                renderer.quad(gx, gridY, w, 1, graphGridColor.get());
            }
        }
        for (int i = 0; i < countHistory.size() - 1; i++) {
            int c1 = countHistory.get(i);
            int c2 = countHistory.get(i + 1);
            double x1 = gx + (i / (double)(countHistory.size() - 1)) * w;
            double x2 = gx + ((i + 1) / (double)(countHistory.size() - 1)) * w;
            double y1 = gy + h - (c1 / (double)max) * h;
            double y2 = gy + h - (c2 / (double)max) * h;
            SettingColor fill = getColorForCount(c2);
            drawFill(renderer, x1, y1, x2, y2, gy + h, fill);
            drawLine(renderer, x1, y1, x2, y2, fill);
        }
        String label = "Now: " + nearby.size();
        double lw = renderer.textWidth(label, false);
        renderer.text(label, gx + w - lw, gy - renderer.textHeight(false) - 2, graphLabelColor.get(), false);
        String peak = "Peak: " + peakCount;
        renderer.text(peak, gx + 2, gy + 2, graphPeakColor.get(), false);
        return gy + h + 4;
    }
    private void drawLine(HudRenderer renderer, double x1, double y1, double x2, double y2, SettingColor color) {
        double dx = x2 - x1;
        double dy = y2 - y1;
        double len = Math.sqrt(dx * dx + dy * dy);
        if (len == 0) return;
        int steps = (int)Math.ceil(len);
        for (int i = 0; i <= steps; i++) {
            double t = i / (double)steps;
            double px = x1 + dx * t;
            double py = y1 + dy * t;
            renderer.quad(px - 1, py - 1, 2, 2, color);
        }
    }
    private void drawFill(HudRenderer renderer, double x1, double y1, double x2, double y2, double bottom, SettingColor color) {
        SettingColor fillColor = new SettingColor(color.r, color.g, color.b, 60);
        int steps = Math.max(1, (int)(x2 - x1));
        for (int i = 0; i <= steps; i++) {
            double t = i / (double)steps;
            double px = x1 + (x2 - x1) * t;
            double py = y1 + (y2 - y1) * t;
            double ph = bottom - py;
            renderer.quad(px, py, 1, ph, fillColor);
        }
    }
    private SettingColor getColorForCount(int count) {
        if (trackDensity.get()) {
            int alert = densityAlert.get();
            if (count >= alert) return nearbyHighColor.get();
            if (count >= alert / 2) return nearbyMedColor.get();
            return nearbyLowColor.get();
        }
        return graphLabelColor.get();
    }
    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (MeteorClient.mc.world == null || MeteorClient.mc.player == null) return;
        String dim = MeteorClient.mc.world.getRegistryKey().getValue().toString();
        if (resetOnDimension.get() && !dim.equals(lastDim)) {
            reset();
            lastDim = dim;
            return;
        }
        nearby.clear();
        for (Entity e : MeteorClient.mc.world.getEntities()) {
            if (!(e instanceof LivingEntity)) continue;
            if (!entities.get().contains(e.getType())) continue;
            UUID id = e.getUuid();
            if (trackSpawnRate.get() && !tracked.contains(id)) {
                tracked.add(id);
                totalSpawned++;
            }
            if (e.isAlive()) {
                double dist = MeteorClient.mc.player.distanceTo(e);
                double scanRadiusBlocks = scanRadius.get() * 16.0;
                if (trackDensity.get() && dist <= scanRadiusBlocks) {
                    nearby.add(id);
                } else if (!trackDensity.get()) {
                    nearby.add(id);
                }
            }
        }
        if (trackDensity.get()) {
            double r = scanRadius.get();
            double area = Math.PI * r * r;
            density = area > 0 ? nearby.size() / area : 0;
        }
        if (trackSpawnRate.get()) {
            ticks++;
            if (ticks >= rateUpdateInterval.get()) {
                int spawned = totalSpawned - lastTotal;
                lastTotal = totalSpawned;
                spawnRate = (spawned / (double)rateUpdateInterval.get()) * TICKS_PER_HOUR;
                if (spawnRate > peakRate) peakRate = spawnRate;
                ticks = 0;
            }
        }
        if (nearby.size() > peakCount) {
            peakCount = nearby.size();
        }
        graphTicks++;
        if (graphTicks >= graphUpdate.get()) {
            if (trackSpawnRate.get()) {
                rateHistory.add(spawnRate);
                while (rateHistory.size() > graphPoints.get()) rateHistory.removeFirst();
            }
            countHistory.add(nearby.size());
            while (countHistory.size() > graphPoints.get()) countHistory.removeFirst();
            graphTicks = 0;
        }
        if (tracked.size() > 10000) {
            tracked.clear();
        }
    }
    private void reset() {
        tracked.clear();
        nearby.clear();
        rateHistory.clear();
        countHistory.clear();
        totalSpawned = 0;
        lastTotal = 0;
        spawnRate = 0;
        density = 0;
        ticks = 0;
        graphTicks = 0;
        peakRate = 0;
        peakCount = 0;
        sessionStart = System.currentTimeMillis();
    }
    private String format(double num) {
        if (num < 1000) return String.format("%.0f", num);
        if (num < 1000000) return String.format("%.1fk", num / 1000);
        return String.format("%.1fm", num / 1000000);
    }
    private SettingColor getColorForRate(double rate) {
        if (rate > 3000) return rateHighColor.get();
        if (rate > 1000) return rateGoodColor.get();
        if (rate > 300) return rateMedColor.get();
        return rateLowColor.get();
    }
}