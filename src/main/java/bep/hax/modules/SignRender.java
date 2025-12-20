package bep.hax.modules;
import bep.hax.Bep;
import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.renderer.Renderer2D;
import meteordevelopment.meteorclient.renderer.text.TextRenderer;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.NametagUtils;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.block.entity.HangingSignBlockEntity;
import net.minecraft.block.entity.SignText;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.MathHelper;
import org.joml.Vector3d;
import java.util.*;
public class SignRender extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("Render");
    private final SettingGroup sgClustering = settings.createGroup("Clustering");
    private final SettingGroup sgOptimization = settings.createGroup("Optimization");
    private final Setting<Double> maxDistance = sgGeneral.add(new DoubleSetting.Builder()
        .name("max-distance")
        .description("Maximum distance to render signs (blocks).")
        .defaultValue(512.0)
        .min(16.0)
        .max(1024.0)
        .sliderRange(16.0, 1024.0)
        .build()
    );
    private final Setting<Integer> maxSigns = sgGeneral.add(new IntSetting.Builder()
        .name("max-signs")
        .description("Maximum number of signs to render.")
        .defaultValue(200)
        .min(5)
        .max(500)
        .sliderRange(5, 1000)
        .build()
    );
    private final Setting<Boolean> filterEmpty = sgGeneral.add(new BoolSetting.Builder()
        .name("filter-empty")
        .description("Hide empty signs.")
        .defaultValue(true)
        .build()
    );
    private final Setting<Boolean> multilineDisplay = sgGeneral.add(new BoolSetting.Builder()
        .name("multiline-display")
        .description("Display sign text as multiple lines as they appear on the sign.")
        .defaultValue(true)
        .build()
    );
    private final Setting<SettingColor> textColor = sgRender.add(new ColorSetting.Builder()
        .name("text-color")
        .description("Text color.")
        .defaultValue(new SettingColor(255, 255, 255, 255))
        .build()
    );
    private final Setting<SettingColor> backgroundColor = sgRender.add(new ColorSetting.Builder()
        .name("background-color")
        .description("Background color.")
        .defaultValue(new SettingColor(0, 0, 0, 120))
        .build()
    );
    private final Setting<Boolean> showBackground = sgRender.add(new BoolSetting.Builder()
        .name("show-background")
        .description("Show background behind text.")
        .defaultValue(true)
        .build()
    );
    private final Setting<Boolean> enableClustering = sgClustering.add(new BoolSetting.Builder()
        .name("enable-clustering")
        .description("Group nearby signs to prevent overlap.")
        .defaultValue(true)
        .build()
    );
    private final Setting<Double> clusterRadius = sgClustering.add(new DoubleSetting.Builder()
        .name("cluster-radius")
        .description("Screen distance in pixels to group signs.")
        .defaultValue(100.0)
        .min(20.0)
        .max(500.0)
        .sliderRange(20.0, 200.0)
        .visible(enableClustering::get)
        .build()
    );
    private final Setting<ClusterMode> clusterMode = sgClustering.add(new EnumSetting.Builder<ClusterMode>()
        .name("cluster-mode")
        .description("How to display clustered signs.")
        .defaultValue(ClusterMode.Count)
        .visible(enableClustering::get)
        .build()
    );
    private final Setting<Integer> cycleTime = sgClustering.add(new IntSetting.Builder()
        .name("cycle-time")
        .description("Time in milliseconds between cycling signs.")
        .defaultValue(2000)
        .min(500)
        .max(10000)
        .sliderRange(500, 5000)
        .visible(() -> enableClustering.get() && clusterMode.get() == ClusterMode.Cycle)
        .build()
    );
    private final Setting<Integer> maxClusterDisplay = sgClustering.add(new IntSetting.Builder()
        .name("max-cluster-display")
        .description("Maximum signs to show in a cluster.")
        .defaultValue(5)
        .min(1)
        .max(10)
        .sliderRange(1, 10)
        .visible(() -> enableClustering.get() && clusterMode.get() != ClusterMode.Count)
        .build()
    );
    private final Setting<Boolean> showClusterCount = sgClustering.add(new BoolSetting.Builder()
        .name("show-cluster-count")
        .description("Show number of signs in cluster.")
        .defaultValue(true)
        .visible(enableClustering::get)
        .build()
    );
    private final Setting<SettingColor> clusterCountColor = sgClustering.add(new ColorSetting.Builder()
        .name("cluster-count-color")
        .description("Color for cluster count indicator.")
        .defaultValue(new SettingColor(255, 200, 100, 255))
        .visible(() -> enableClustering.get() && showClusterCount.get())
        .build()
    );
    private final Setting<Double> stackSpacing = sgClustering.add(new DoubleSetting.Builder()
        .name("stack-spacing")
        .description("Vertical spacing between stacked signs.")
        .defaultValue(5.0)
        .min(0.0)
        .max(20.0)
        .sliderRange(0.0, 20.0)
        .visible(() -> enableClustering.get() && clusterMode.get() == ClusterMode.Stack)
        .build()
    );
    private final Setting<Boolean> cullOffScreen = sgOptimization.add(new BoolSetting.Builder()
        .name("cull-off-screen")
        .description("Don't process signs that are off-screen.")
        .defaultValue(true)
        .build()
    );
    private final Setting<Boolean> prioritizeClosest = sgOptimization.add(new BoolSetting.Builder()
        .name("prioritize-closest")
        .description("Always show closest signs first.")
        .defaultValue(true)
        .build()
    );
    private final Setting<Boolean> cacheSignText = sgOptimization.add(new BoolSetting.Builder()
        .name("cache-sign-text")
        .description("Cache sign text for better performance.")
        .defaultValue(true)
        .build()
    );
    private final Setting<Integer> updateInterval = sgOptimization.add(new IntSetting.Builder()
        .name("update-interval")
        .description("Ticks between full sign updates.")
        .defaultValue(20)
        .min(1)
        .max(100)
        .sliderRange(1, 100)
        .visible(cacheSignText::get)
        .build()
    );
    public enum ClusterMode {
        Stack("Stack vertically"),
        Cycle("Cycle through signs"),
        Count("Show count only"),
        Smart("Smart layout");
        private final String description;
        ClusterMode(String description) {
            this.description = description;
        }
        @Override
        public String toString() {
            return description;
        }
    }
    private static class SignRenderData {
        final BlockPos pos;
        final List<String> lines;
        final String fullText;
        final Vec3d worldPos;
        double distance;
        double screenX, screenY;
        boolean onScreen = false;
        double renderWidth;
        double renderHeight;
        double scale;
        Color color;
        SignRenderData(BlockPos pos, List<String> lines, Vec3d worldPos) {
            this.pos = pos;
            this.lines = new ArrayList<>(lines);
            this.fullText = String.join(" ", lines).trim();
            this.worldPos = worldPos;
        }
        void updateScreenPosition(Vector3d tempVec) {
            tempVec.set(worldPos.x, worldPos.y + 0.5, worldPos.z);
            if (NametagUtils.to2D(tempVec, 1.0)) {
                screenX = tempVec.x;
                screenY = tempVec.y;
                onScreen = true;
            } else {
                onScreen = false;
            }
        }
    }
    private static class SignCluster {
        final List<SignRenderData> signs = new ArrayList<>();
        double centerX, centerY;
        SignRenderData primarySign;
        int cycleIndex = 0;
        long lastCycleTime = 0;
        void addSign(SignRenderData sign) {
            signs.add(sign);
            sign.onScreen = true;
        }
        void calculateCenter() {
            if (signs.isEmpty()) return;
            signs.sort(Comparator.comparingDouble(s -> s.distance));
            primarySign = signs.get(0);
            centerX = primarySign.screenX;
            centerY = primarySign.screenY;
        }
        SignRenderData getCurrentSign(long currentTime, int cycleTimeMs) {
            if (signs.isEmpty()) return null;
            if (signs.size() == 1) return signs.get(0);
            if (lastCycleTime == 0) {
                lastCycleTime = currentTime;
            }
            if (currentTime - lastCycleTime >= cycleTimeMs) {
                cycleIndex = (cycleIndex + 1) % signs.size();
                lastCycleTime = currentTime;
            }
            return signs.get(cycleIndex);
        }
    }
    private final Vector3d tempVec = new Vector3d();
    private final List<SignRenderData> allSigns = new ArrayList<>();
    private final List<SignCluster> clusters = new ArrayList<>();
    private final Map<BlockPos, SignRenderData> signCache = new HashMap<>();
    private int updateTicker = 0;
    private int globalCycleIndex = 0;
    private long lastGlobalCycleTime = 0;
    public SignRender() {
        super(Bep.CATEGORY, "sign-render", "Renders sign text through walls with advanced clustering.");
    }
    @EventHandler
    private void onRender2D(Render2DEvent event) {
        if (mc.world == null || mc.player == null) return;
        updateTicker++;
        boolean fullUpdate = !cacheSignText.get() || updateTicker >= updateInterval.get();
        if (fullUpdate) {
            updateTicker = 0;
            collectSigns();
        } else {
            updateSignPositions();
        }
        if (enableClustering.get() && !allSigns.isEmpty()) {
            createClusters();
        }
        renderSigns();
    }
    private void collectSigns() {
        allSigns.clear();
        signCache.clear();
        Vec3d playerPos = mc.player.getEntityPos();
        double maxDist = maxDistance.get();
        List<SignRenderData> tempSignList = new ArrayList<>();
        for (BlockEntity blockEntity : Utils.blockEntities()) {
            try {
                if (!(blockEntity instanceof SignBlockEntity) &&
                    !(blockEntity instanceof HangingSignBlockEntity)) {
                    continue;
                }
                BlockPos signPos = blockEntity.getPos();
                Vec3d signVec = Vec3d.ofCenter(signPos);
                double distance = playerPos.distanceTo(signVec);
                if (distance > maxDist) continue;
                List<String> lines = extractSignLines(blockEntity);
                if (lines.isEmpty() && filterEmpty.get()) continue;
                SignRenderData signData = new SignRenderData(signPos, lines, signVec);
                signData.distance = distance;
                signData.updateScreenPosition(tempVec);
                if (!signData.onScreen && cullOffScreen.get()) continue;
                signData.scale = 1.0;
                signData.color = new Color(textColor.get());
                tempSignList.add(signData);
                signCache.put(signPos, signData);
            } catch (Exception ignored) {}
        }
        if (prioritizeClosest.get()) {
            tempSignList.sort(Comparator.comparingDouble(s -> s.distance));
        }
        int limit = Math.min(tempSignList.size(), maxSigns.get());
        for (int i = 0; i < limit; i++) {
            allSigns.add(tempSignList.get(i));
        }
        if (globalCycleIndex >= allSigns.size() && !allSigns.isEmpty()) {
            globalCycleIndex = 0;
        }
    }
    private void updateSignPositions() {
        if (mc.player == null) return;
        Vec3d playerPos = mc.player.getEntityPos();
        Iterator<SignRenderData> iterator = allSigns.iterator();
        while (iterator.hasNext()) {
            SignRenderData sign = iterator.next();
            sign.distance = playerPos.distanceTo(sign.worldPos);
            sign.updateScreenPosition(tempVec);
            sign.scale = 1.0;
            sign.color = new Color(textColor.get());
            if (!sign.onScreen && cullOffScreen.get()) {
                iterator.remove();
            }
        }
    }
    private void createClusters() {
        clusters.clear();
        for (SignRenderData sign : allSigns) {
        }
        List<SignRenderData> toCluster = new ArrayList<>(allSigns);
        Set<SignRenderData> clustered = new HashSet<>();
        double radiusSq = clusterRadius.get() * clusterRadius.get();
        while (!toCluster.isEmpty()) {
            SignRenderData seed = toCluster.remove(0);
            if (clustered.contains(seed) || !seed.onScreen) continue;
            SignCluster cluster = new SignCluster();
            cluster.addSign(seed);
            clustered.add(seed);
            Iterator<SignRenderData> iter = toCluster.iterator();
            while (iter.hasNext()) {
                SignRenderData other = iter.next();
                if (!other.onScreen || clustered.contains(other)) continue;
                double dx = seed.screenX - other.screenX;
                double dy = seed.screenY - other.screenY;
                double distSq = dx * dx + dy * dy;
                if (distSq <= radiusSq) {
                    cluster.addSign(other);
                    clustered.add(other);
                }
            }
            cluster.calculateCenter();
            if (cluster.signs.size() > 1) {
                clusters.add(cluster);
            }
        }
    }
    private void renderSigns() {
        if (allSigns.isEmpty()) return;
        TextRenderer textRenderer = TextRenderer.get();
        if (enableClustering.get() && !clusters.isEmpty()) {
            renderWithClusters(textRenderer);
        } else {
            renderAllSigns(textRenderer);
        }
    }
    private void renderWithClusters(TextRenderer textRenderer) {
        long currentTime = System.currentTimeMillis();
        Set<SignRenderData> rendered = new HashSet<>();
        for (SignCluster cluster : clusters) {
            switch (clusterMode.get()) {
                case Stack -> renderStackedCluster(cluster, textRenderer, rendered);
                case Cycle -> renderCyclingCluster(cluster, textRenderer, currentTime, rendered);
                case Count -> renderCountCluster(cluster, textRenderer, rendered);
                case Smart -> renderSmartCluster(cluster, textRenderer, rendered);
            }
        }
        for (SignRenderData sign : allSigns) {
            if (!rendered.contains(sign) && sign.onScreen) {
                renderSignAtPosition(sign, textRenderer, sign.screenX, sign.screenY);
            }
        }
    }
    private void renderAllSigns(TextRenderer textRenderer) {
        if (enableClustering.get() && clusterMode.get() == ClusterMode.Cycle && !allSigns.isEmpty()) {
            long currentTime = System.currentTimeMillis();
            if (lastGlobalCycleTime == 0) {
                lastGlobalCycleTime = currentTime;
            }
            if (currentTime - lastGlobalCycleTime >= cycleTime.get()) {
                globalCycleIndex = (globalCycleIndex + 1) % allSigns.size();
                lastGlobalCycleTime = currentTime;
            }
            if (globalCycleIndex >= allSigns.size()) {
                globalCycleIndex = 0;
            }
            SignRenderData currentSign = allSigns.get(globalCycleIndex);
            if (currentSign.onScreen) {
                renderSignAtPosition(currentSign, textRenderer, currentSign.screenX, currentSign.screenY);
                if (showClusterCount.get() && allSigns.size() > 1) {
                    textRenderer.begin(currentSign.scale, false, true);
                    double lineHeight = textRenderer.getHeight();
                    textRenderer.end();
                    double signHeight = (multilineDisplay.get() && !currentSign.lines.isEmpty()) ?
                        (currentSign.lines.size() * lineHeight + 8) : (lineHeight + 8);
                    String indicator = String.format("[%d/%d]",
                        globalCycleIndex + 1, allSigns.size());
                    renderTextAtScreenPos(
                        indicator,
                        currentSign.screenX,
                        currentSign.screenY + signHeight / 2 + 15,
                        0.7,
                        clusterCountColor.get(),
                        textRenderer
                    );
                }
            }
        } else {
            for (SignRenderData sign : allSigns) {
                if (sign.onScreen) {
                    renderSignAtPosition(sign, textRenderer, sign.screenX, sign.screenY);
                }
            }
        }
    }
    private void renderStackedCluster(SignCluster cluster, TextRenderer textRenderer, Set<SignRenderData> rendered) {
        double baseX = cluster.centerX;
        double baseY = cluster.centerY;
        double offsetY = 0;
        int count = 0;
        for (SignRenderData sign : cluster.signs) {
            if (count >= maxClusterDisplay.get()) break;
            renderSignAtPosition(sign, textRenderer, baseX, baseY + offsetY);
            rendered.add(sign);
            textRenderer.begin(sign.scale, false, true);
            double lineHeight = textRenderer.getHeight();
            textRenderer.end();
            double signHeight = (multilineDisplay.get() && !sign.lines.isEmpty()) ?
                (sign.lines.size() * lineHeight + 8) : (lineHeight + 8);
            offsetY += signHeight + stackSpacing.get();
            count++;
        }
        if (showClusterCount.get() && cluster.signs.size() > maxClusterDisplay.get()) {
            String countText = "+" + (cluster.signs.size() - maxClusterDisplay.get()) + " more";
            renderTextAtScreenPos(
                countText,
                baseX,
                baseY + offsetY,
                0.8,
                clusterCountColor.get(),
                textRenderer
            );
        }
    }
    private void renderCyclingCluster(SignCluster cluster, TextRenderer textRenderer, long currentTime, Set<SignRenderData> rendered) {
        SignRenderData currentSign = cluster.getCurrentSign(currentTime, cycleTime.get());
        if (currentSign != null) {
            renderSignAtPosition(currentSign, textRenderer, cluster.centerX, cluster.centerY);
            rendered.addAll(cluster.signs);
            if (showClusterCount.get() && cluster.signs.size() > 1) {
                textRenderer.begin(currentSign.scale, false, true);
                double lineHeight = textRenderer.getHeight();
                textRenderer.end();
                double signHeight = (multilineDisplay.get() && !currentSign.lines.isEmpty()) ?
                    (currentSign.lines.size() * lineHeight + 8) : (lineHeight + 8);
                String indicator = String.format("[%d/%d]",
                    cluster.cycleIndex + 1, cluster.signs.size());
                renderTextAtScreenPos(
                    indicator,
                    cluster.centerX,
                    cluster.centerY + signHeight / 2 + 15,
                    0.7,
                    clusterCountColor.get(),
                    textRenderer
                );
            }
        }
    }
    private void renderCountCluster(SignCluster cluster, TextRenderer textRenderer, Set<SignRenderData> rendered) {
        SignRenderData primary = cluster.primarySign;
        renderSignAtPosition(primary, textRenderer, cluster.centerX, cluster.centerY);
        rendered.addAll(cluster.signs);
        if (cluster.signs.size() > 1) {
            textRenderer.begin(primary.scale, false, true);
            double lineHeight = textRenderer.getHeight();
            textRenderer.end();
            double signHeight = (multilineDisplay.get() && !primary.lines.isEmpty()) ?
                (primary.lines.size() * lineHeight + 8) : (lineHeight + 8);
            String countText = "(" + cluster.signs.size() + " signs)";
            renderTextAtScreenPos(
                countText,
                cluster.centerX,
                cluster.centerY + signHeight / 2 + 10,
                0.8,
                clusterCountColor.get(),
                textRenderer
            );
        }
    }
    private void renderSmartCluster(SignCluster cluster, TextRenderer textRenderer, Set<SignRenderData> rendered) {
        int displayCount = Math.min(cluster.signs.size(), maxClusterDisplay.get());
        if (displayCount == 1) {
            renderSignAtPosition(cluster.signs.get(0), textRenderer, cluster.centerX, cluster.centerY);
            rendered.add(cluster.signs.get(0));
        } else {
            double radius = 30.0 + (displayCount * 5.0);
            double angleStep = 2 * Math.PI / displayCount;
            for (int i = 0; i < displayCount; i++) {
                SignRenderData sign = cluster.signs.get(i);
                double angle = i * angleStep - Math.PI / 2;
                double offsetX = Math.cos(angle) * radius;
                double offsetY = Math.sin(angle) * radius;
                renderSignAtPosition(sign, textRenderer,
                    cluster.centerX + offsetX,
                    cluster.centerY + offsetY);
                rendered.add(sign);
            }
            if (showClusterCount.get() && cluster.signs.size() > displayCount) {
                String countText = "+" + (cluster.signs.size() - displayCount);
                renderTextAtScreenPos(
                    countText,
                    cluster.centerX,
                    cluster.centerY,
                    0.9,
                    clusterCountColor.get(),
                    textRenderer
                );
            }
        }
    }
    private void renderSignAtPosition(SignRenderData sign, TextRenderer textRenderer, double centerX, double centerY) {
        if (multilineDisplay.get() && !sign.lines.isEmpty()) {
            renderMultilineSign(sign, textRenderer, centerX, centerY);
        } else if (!sign.fullText.isEmpty()) {
            renderSingleLineSign(sign, textRenderer, centerX, centerY);
        }
    }
    private void renderMultilineSign(SignRenderData sign, TextRenderer textRenderer, double centerX, double centerY) {
        textRenderer.begin(sign.scale, false, true);
        double lineHeight = textRenderer.getHeight();
        List<Double> lineWidths = new ArrayList<>();
        double maxWidth = 0;
        for (String line : sign.lines) {
            double width = line.isEmpty() ? 0 : textRenderer.getWidth(line);
            lineWidths.add(width);
            maxWidth = Math.max(maxWidth, width);
        }
        double totalHeight = sign.lines.size() * lineHeight;
        double bgPadding = 4;
        double bgWidth = maxWidth + bgPadding * 2;
        double bgHeight = totalHeight + bgPadding * 2;
        double bgLeft = centerX - bgWidth / 2;
        double bgTop = centerY - bgHeight / 2;
        textRenderer.end();
        if (showBackground.get()) {
            Color bgColor = new Color(
                backgroundColor.get().r,
                backgroundColor.get().g,
                backgroundColor.get().b,
                (int)(backgroundColor.get().a * (sign.color.a / 255.0))
            );
            Renderer2D.COLOR.begin();
            Renderer2D.COLOR.quad(bgLeft, bgTop, bgWidth, bgHeight, bgColor);
            Renderer2D.COLOR.render();
        }
        textRenderer.begin(sign.scale, false, true);
        for (int i = 0; i < sign.lines.size(); i++) {
            String line = sign.lines.get(i);
            if (!line.isEmpty()) {
                double lineWidth = lineWidths.get(i);
                double textX = centerX - lineWidth / 2;
                double textY = bgTop + bgPadding + i * lineHeight;
                textRenderer.render(line, textX, textY, sign.color);
            }
        }
        textRenderer.end();
    }
    private void renderSingleLineSign(SignRenderData sign, TextRenderer textRenderer, double centerX, double centerY) {
        renderTextAtScreenPos(sign.fullText, centerX, centerY, sign.scale, sign.color, textRenderer);
    }
    private void renderTextAtScreenPos(String text, double screenX, double screenY, double scale, Color color, TextRenderer textRenderer) {
        textRenderer.begin(scale, false, true);
        double textWidth = textRenderer.getWidth(text);
        double textHeight = textRenderer.getHeight();
        double bgPadding = 4;
        double elementWidth = textWidth + bgPadding * 2;
        double elementHeight = textHeight + bgPadding * 2;
        double elementLeft = screenX - elementWidth / 2;
        double elementTop = screenY - elementHeight / 2;
        textRenderer.end();
        if (showBackground.get()) {
            Color bgColor = new Color(
                backgroundColor.get().r,
                backgroundColor.get().g,
                backgroundColor.get().b,
                (int)(backgroundColor.get().a * (color.a / 255.0))
            );
            Renderer2D.COLOR.begin();
            Renderer2D.COLOR.quad(elementLeft, elementTop, elementWidth, elementHeight, bgColor);
            Renderer2D.COLOR.render();
        }
        textRenderer.begin(scale, false, true);
        textRenderer.render(text, elementLeft + bgPadding, elementTop + bgPadding, color);
        textRenderer.end();
    }
    private List<String> extractSignLines(BlockEntity blockEntity) {
        List<String> lines = new ArrayList<>();
        try {
            SignText frontText = null;
            SignText backText = null;
            if (blockEntity instanceof SignBlockEntity sign) {
                frontText = sign.getFrontText();
                backText = sign.getBackText();
            } else if (blockEntity instanceof HangingSignBlockEntity sign) {
                frontText = sign.getFrontText();
                backText = sign.getBackText();
            }
            if (frontText != null) {
                List<String> frontLines = extractTextLines(frontText);
                if (!frontLines.isEmpty()) {
                    lines.addAll(frontLines);
                }
            }
            if (backText != null && lines.isEmpty()) {
                List<String> backLines = extractTextLines(backText);
                if (!backLines.isEmpty()) {
                    lines.addAll(backLines);
                }
            }
        } catch (Exception ignored) {}
        return lines;
    }
    private List<String> extractTextLines(SignText signText) {
        List<String> lines = new ArrayList<>();
        try {
            Text[] messages = signText.getMessages(false);
            if (messages != null) {
                for (Text message : messages) {
                    if (message == null) continue;
                    String line = safeExtractString(message);
                    if (!line.isEmpty()) {
                        lines.add(line);
                    }
                }
            }
        } catch (Exception ignored) {}
        return lines;
    }
    private String safeExtractString(Text text) {
        if (text == null) return "";
        try {
            String result = text.getString();
            if (result == null) return "";
            return cleanSignText(result);
        } catch (Exception e) {
            try {
                String literal = text.getLiteralString();
                if (literal != null) {
                    return cleanSignText(literal);
                }
            } catch (Exception ignored) {}
            return "";
        }
    }
    private String cleanSignText(String text) {
        if (text == null || text.isEmpty()) return "";
        text = text.replaceAll("§.", "");
        text = text.replaceAll("&[0-9a-fklmnor]", "");
        if (text.contains("{\"") || text.contains("[\"")) {
            text = text.replaceAll("\\{\".*?\":\"(.*?)\".*?\\}", "$1");
            text = text.replaceAll("\\[\"(.*?)\"\\]", "$1");
        }
        text = text.replaceAll("\\{[^\\s].*?\\}", "");
        text = text.replaceAll("[\\p{C}&&[^\\s]]", "");
        text = text.replaceAll("[\\u0000-\\u001F\\u007F-\\u009F]", "");
        text = text.replaceAll("[\\[\\]{}\"']", "");
        text = text.replaceAll("\\s+", " ").trim();
        if (text.length() > 100) {
            text = text.substring(0, 97) + "...";
        }
        return text;
    }
}