package bep.hax.modules;
import bep.hax.Bep;
import bep.hax.util.MsgUtil;
import bep.hax.util.MapUtil;
import bep.hax.util.StardustUtil;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.ChunkDataEvent;
import meteordevelopment.meteorclient.events.entity.player.InteractBlockEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.RenderUtils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.block.*;
import net.minecraft.block.entity.BannerBlockEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.entity.decoration.GlowItemFrameEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.property.Property;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.chunk.WorldChunk;
import java.util.*;
public class VanityESP extends Module {
    private final SettingGroup sgFeatures = settings.getDefaultGroup();
    private final SettingGroup sgMapFrames = settings.createGroup("Map Frames");
    private final SettingGroup sgBanners = settings.createGroup("Banners");
    private final SettingGroup sgShulkerFrames = settings.createGroup("Shulker Frames");
    private final SettingGroup sgOminousVaults = settings.createGroup("Ominous Vaults");
    private final SettingGroup sgTreasure = settings.createGroup("Buried Treasure");
    private final Setting<Boolean> highlightMapFrames = sgFeatures.add(new BoolSetting.Builder()
        .name("map-frames")
        .description("Highlights item frames containing maps.")
        .defaultValue(true)
        .build()
    );
    private final Setting<Boolean> highlightBanners = sgFeatures.add(new BoolSetting.Builder()
        .name("banners")
        .description("Highlights banners.")
        .defaultValue(true)
        .build()
    );
    private final Setting<Boolean> highlightShulkerFrames = sgFeatures.add(new BoolSetting.Builder()
        .name("shulker-frames")
        .description("Highlights item frames containing shulker boxes.")
        .defaultValue(true)
        .build()
    );
    private final Setting<Boolean> highlightOminousVaults = sgFeatures.add(new BoolSetting.Builder()
        .name("ominous-vaults")
        .description("Highlights ominous vaults.")
        .defaultValue(true)
        .build()
    );
    private final Setting<Boolean> highlightTreasure = sgFeatures.add(new BoolSetting.Builder()
        .name("buried-treasure")
        .description("Highlights buried treasure chests.")
        .defaultValue(true)
        .build()
    );
    private final Setting<SettingColor> mapFillColor = sgMapFrames.add(new ColorSetting.Builder()
        .name("side-color")
        .description("Fill color for map frames.")
        .defaultValue(new SettingColor(255, 255, 0, 50))
        .visible(highlightMapFrames::get)
        .build()
    );
    private final Setting<SettingColor> mapOutlineColor = sgMapFrames.add(new ColorSetting.Builder()
        .name("line-color")
        .description("Outline color for map frames.")
        .defaultValue(new SettingColor(255, 255, 0, 255))
        .visible(highlightMapFrames::get)
        .build()
    );
    private final Setting<Boolean> mapRenderFill = sgMapFrames.add(new BoolSetting.Builder()
        .name("render-sides")
        .description("Render sides of map frames.")
        .defaultValue(true)
        .visible(highlightMapFrames::get)
        .build()
    );
    private final Setting<Boolean> mapRenderOutline = sgMapFrames.add(new BoolSetting.Builder()
        .name("render-lines")
        .description("Render lines of map frames.")
        .defaultValue(true)
        .visible(highlightMapFrames::get)
        .build()
    );
    private final Setting<SettingColor> bannerFillColor = sgBanners.add(new ColorSetting.Builder()
        .name("side-color")
        .description("Fill color for banners.")
        .defaultValue(new SettingColor(255, 0, 0, 50))
        .visible(highlightBanners::get)
        .build()
    );
    private final Setting<SettingColor> bannerOutlineColor = sgBanners.add(new ColorSetting.Builder()
        .name("line-color")
        .description("Outline color for banners.")
        .defaultValue(new SettingColor(255, 0, 0, 255))
        .visible(highlightBanners::get)
        .build()
    );
    private final Setting<Boolean> bannerRenderFill = sgBanners.add(new BoolSetting.Builder()
        .name("render-sides")
        .description("Render sides of banners.")
        .defaultValue(true)
        .visible(highlightBanners::get)
        .build()
    );
    private final Setting<Boolean> bannerRenderOutline = sgBanners.add(new BoolSetting.Builder()
        .name("render-lines")
        .description("Render lines of banners.")
        .defaultValue(true)
        .visible(highlightBanners::get)
        .build()
    );
    private final Setting<SettingColor> shulkerFillColor = sgShulkerFrames.add(new ColorSetting.Builder()
        .name("side-color")
        .description("Fill color for shulker frames.")
        .defaultValue(new SettingColor(152, 98, 43, 50))
        .visible(highlightShulkerFrames::get)
        .build()
    );
    private final Setting<SettingColor> shulkerOutlineColor = sgShulkerFrames.add(new ColorSetting.Builder()
        .name("line-color")
        .description("Outline color for shulker frames.")
        .defaultValue(new SettingColor(85, 43, 19, 255))
        .visible(highlightShulkerFrames::get)
        .build()
    );
    private final Setting<SettingColor> shulkerTracerColor = sgShulkerFrames.add(new ColorSetting.Builder()
        .name("tracer-color")
        .description("Tracer color for shulker frames.")
        .defaultValue(new SettingColor(166, 150, 101, 255))
        .visible(highlightShulkerFrames::get)
        .build()
    );
    private final Setting<Boolean> shulkerRenderFill = sgShulkerFrames.add(new BoolSetting.Builder()
        .name("render-sides")
        .description("Render sides of shulker frames.")
        .defaultValue(true)
        .visible(highlightShulkerFrames::get)
        .build()
    );
    private final Setting<Boolean> shulkerRenderOutline = sgShulkerFrames.add(new BoolSetting.Builder()
        .name("render-lines")
        .description("Render lines of shulker frames.")
        .defaultValue(true)
        .visible(highlightShulkerFrames::get)
        .build()
    );
    private final Setting<Boolean> shulkerRenderTracer = sgShulkerFrames.add(new BoolSetting.Builder()
        .name("tracers")
        .description("Add tracers to shulker frames.")
        .defaultValue(true)
        .visible(highlightShulkerFrames::get)
        .build()
    );
    private final Setting<SettingColor> vaultFillColor = sgOminousVaults.add(new ColorSetting.Builder()
        .name("side-color")
        .description("Fill color for ominous vaults.")
        .defaultValue(new SettingColor(0, 120, 120, 50))
        .visible(highlightOminousVaults::get)
        .build()
    );
    private final Setting<SettingColor> vaultOutlineColor = sgOminousVaults.add(new ColorSetting.Builder()
        .name("line-color")
        .description("Outline color for ominous vaults.")
        .defaultValue(new SettingColor(31, 161, 159, 255))
        .visible(highlightOminousVaults::get)
        .build()
    );
    private final Setting<SettingColor> vaultTracerColor = sgOminousVaults.add(new ColorSetting.Builder()
        .name("tracer-color")
        .description("Tracer color for ominous vaults.")
        .defaultValue(new SettingColor(40, 200, 195, 255))
        .visible(highlightOminousVaults::get)
        .build()
    );
    private final Setting<Boolean> vaultRenderFill = sgOminousVaults.add(new BoolSetting.Builder()
        .name("render-sides")
        .description("Render sides of ominous vaults.")
        .defaultValue(true)
        .visible(highlightOminousVaults::get)
        .build()
    );
    private final Setting<Boolean> vaultRenderOutline = sgOminousVaults.add(new BoolSetting.Builder()
        .name("render-lines")
        .description("Render lines of ominous vaults.")
        .defaultValue(true)
        .visible(highlightOminousVaults::get)
        .build()
    );
    private final Setting<Boolean> vaultRenderTracer = sgOminousVaults.add(new BoolSetting.Builder()
        .name("tracers")
        .description("Add tracers to ominous vaults.")
        .defaultValue(true)
        .visible(highlightOminousVaults::get)
        .build()
    );
    private final Setting<Boolean> treasureChat = sgTreasure.add(new BoolSetting.Builder()
        .name("chat-notification")
        .description("Notify with a chat message.")
        .defaultValue(true)
        .visible(highlightTreasure::get)
        .build()
    );
    private final Setting<Boolean> treasureCoords = sgTreasure.add(new BoolSetting.Builder()
        .name("show-coords")
        .description("Display chest coordinates in chat notifications.")
        .defaultValue(false)
        .visible(() -> highlightTreasure.get() && treasureChat.get())
        .build()
    );
    private final Setting<Boolean> treasureWaypoints = sgTreasure.add(new BoolSetting.Builder()
        .name("add-waypoints")
        .description("Adds waypoints to your Xaeros map for treasure chests.")
        .defaultValue(false)
        .visible(() -> highlightTreasure.get() && StardustUtil.XAERO_AVAILABLE)
        .build()
    );
    private final Setting<Boolean> treasureTempWaypoints = sgTreasure.add(new BoolSetting.Builder()
        .name("temporary-waypoints")
        .description("Temporary waypoints are removed when you disconnect.")
        .defaultValue(true)
        .visible(() -> highlightTreasure.get() && StardustUtil.XAERO_AVAILABLE && treasureWaypoints.get())
        .build()
    );
    private final Setting<Boolean> treasureSound = sgTreasure.add(new BoolSetting.Builder()
        .name("sound-notification")
        .description("Notify with sound.")
        .defaultValue(true)
        .visible(highlightTreasure::get)
        .build()
    );
    private final Setting<Double> treasureVolume = sgTreasure.add(new DoubleSetting.Builder()
        .name("volume")
        .min(0.0)
        .max(10.0)
        .sliderMin(0.0)
        .sliderMax(5.0)
        .defaultValue(1.0)
        .visible(() -> highlightTreasure.get() && treasureSound.get())
        .build()
    );
    private final Setting<SettingColor> treasureFillColor = sgTreasure.add(new ColorSetting.Builder()
        .name("side-color")
        .description("Fill color for treasure chests.")
        .defaultValue(new SettingColor(147, 233, 190, 25))
        .visible(highlightTreasure::get)
        .build()
    );
    private final Setting<SettingColor> treasureOutlineColor = sgTreasure.add(new ColorSetting.Builder()
        .name("line-color")
        .description("Outline color for treasure chests.")
        .defaultValue(new SettingColor(147, 233, 190, 255))
        .visible(highlightTreasure::get)
        .build()
    );
    private final Setting<SettingColor> treasureTracerColor = sgTreasure.add(new ColorSetting.Builder()
        .name("tracer-color")
        .description("Tracer color for treasure chests.")
        .defaultValue(new SettingColor(147, 233, 190, 125))
        .visible(highlightTreasure::get)
        .build()
    );
    private final Setting<Boolean> treasureRenderFill = sgTreasure.add(new BoolSetting.Builder()
        .name("render-sides")
        .description("Render sides of treasure chests.")
        .defaultValue(true)
        .visible(highlightTreasure::get)
        .build()
    );
    private final Setting<Boolean> treasureRenderOutline = sgTreasure.add(new BoolSetting.Builder()
        .name("render-lines")
        .description("Render lines of treasure chests.")
        .defaultValue(true)
        .visible(highlightTreasure::get)
        .build()
    );
    private final Setting<Boolean> treasureRenderTracer = sgTreasure.add(new BoolSetting.Builder()
        .name("tracers")
        .description("Add tracers to treasure chests.")
        .defaultValue(true)
        .visible(highlightTreasure::get)
        .build()
    );
    private final Set<BlockPos> ominousVaults = Collections.synchronizedSet(new HashSet<>());
    private final Map<ChunkPos, Set<BlockPos>> chunkVaults = new HashMap<>();
    private Set<ChunkPos> lastLoadedChunks = new HashSet<>();
    private long lastRecheckTime = 0;
    private final int recheckIntervalMs = 4000;
    private final Map<ChunkPos, Integer> pendingChunks = new HashMap<>();
    private final Deque<ChunkPos> initialScanQueue = new ArrayDeque<>();
    private boolean initialScanActive = false;
    private int initialScanChunksPerTick = 0;
    private long lastFullRescan = 0;
    private final int fullRescanIntervalMs = 3000;
    private final Set<BlockPos> lootedTreasure = new HashSet<>();
    private final List<BlockPos> notifiedTreasure = new ArrayList<>();
    public VanityESP() {
        super(Bep.STASH, "VanityESP", "Unified ESP for decorative items and special blocks.");
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            if (mc.world == null || mc.player == null) return;
            initialScanQueue.clear();
            int chunkRadius = mc.options.getViewDistance().getValue();
            int totalChunks = (2 * chunkRadius + 1) * (2 * chunkRadius + 1);
            int durationTicks = 70;
            initialScanChunksPerTick = Math.max(1, (int) Math.ceil(totalChunks / (double) durationTicks));
            for (int cx = mc.player.getChunkPos().x - chunkRadius; cx <= mc.player.getChunkPos().x + chunkRadius; cx++) {
                for (int cz = mc.player.getChunkPos().z - chunkRadius; cz <= mc.player.getChunkPos().z + chunkRadius; cz++) {
                    initialScanQueue.addLast(new ChunkPos(cx, cz));
                }
            }
            initialScanActive = true;
        });
    }
    @Override
    public void onActivate() {
        if (mc.player == null || mc.world == null) return;
        if (!highlightTreasure.get()) return;
        BlockPos pos = mc.player.getBlockPos();
        int viewDistance = mc.options.getViewDistance().getValue();
        int startChunkX = (pos.getX() - (viewDistance * 16)) >> 4;
        int endChunkX = (pos.getX() + (viewDistance * 16)) >> 4;
        int startChunkZ = (pos.getZ() - (viewDistance * 16)) >> 4;
        int endChunkZ = (pos.getZ() + (viewDistance * 16)) >> 4;
        for (int x = startChunkX; x < endChunkX; x++) {
            for (int z = startChunkZ; z < endChunkZ; z++) {
                if (mc.world.isChunkLoaded(x, z)) {
                    WorldChunk chunk = mc.world.getChunk(x, z);
                    scanChunkForTreasure(chunk);
                }
            }
        }
    }
    @Override
    public void onDeactivate() {
        notifiedTreasure.clear();
    }
    @EventHandler
    private void onTick(meteordevelopment.meteorclient.events.world.TickEvent.Pre event) {
        if (mc.world == null || mc.player == null) return;
        if (!highlightOminousVaults.get()) return;
        if (initialScanActive) {
            int processed = 0;
            int minY0 = mc.world.getBottomY();
            int maxY0 = mc.world.getHeight();
            while (processed < initialScanChunksPerTick && !initialScanQueue.isEmpty()) {
                ChunkPos cp = initialScanQueue.pollFirst();
                var chunk = mc.world.getChunk(cp.x, cp.z);
                if (chunk instanceof WorldChunk) {
                    scanChunkForVaults(cp, minY0, maxY0);
                }
                processed++;
            }
            if (initialScanQueue.isEmpty()) initialScanActive = false;
        }
        Set<ChunkPos> currentChunks = new HashSet<>();
        BlockPos playerPos = mc.player.getBlockPos();
        int chunkRadius = mc.options.getViewDistance().getValue();
        int minY = mc.world.getBottomY();
        int maxY = mc.world.getHeight();
        for (int cx = (playerPos.getX() >> 4) - chunkRadius; cx <= (playerPos.getX() >> 4) + chunkRadius; cx++) {
            for (int cz = (playerPos.getZ() >> 4) - chunkRadius; cz <= (playerPos.getZ() >> 4) + chunkRadius; cz++) {
                currentChunks.add(new ChunkPos(cx, cz));
            }
        }
        Set<ChunkPos> newChunks = new HashSet<>(currentChunks);
        newChunks.removeAll(lastLoadedChunks);
        Set<ChunkPos> unloadedChunks = new HashSet<>(lastLoadedChunks);
        unloadedChunks.removeAll(currentChunks);
        for (ChunkPos chunkPos : unloadedChunks) {
            Set<BlockPos> removed = chunkVaults.remove(chunkPos);
            if (removed != null) ominousVaults.removeAll(removed);
            pendingChunks.remove(chunkPos);
        }
        for (ChunkPos chunkPos : newChunks) {
            pendingChunks.put(chunkPos, 10);
        }
        Set<ChunkPos> toScan = new HashSet<>();
        for (Map.Entry<ChunkPos, Integer> entry : new HashMap<>(pendingChunks).entrySet()) {
            int ticksLeft = entry.getValue() - 1;
            if (ticksLeft <= 0) toScan.add(entry.getKey());
            else pendingChunks.put(entry.getKey(), ticksLeft);
        }
        for (ChunkPos chunkPos : toScan) {
            scanChunkForVaults(chunkPos, minY, maxY);
            pendingChunks.remove(chunkPos);
        }
        long now = System.currentTimeMillis();
        if (now - lastRecheckTime >= recheckIntervalMs) {
            lastRecheckTime = now;
            Set<BlockPos> toRemoveVaults = new HashSet<>();
            for (BlockPos vaultPos : ominousVaults) {
                BlockState state = mc.world.getBlockState(vaultPos);
                Property<?> ominousProperty = null;
                for (Property<?> prop : state.getProperties()) {
                    if (prop.getName().equals("ominous")) {
                        ominousProperty = prop;
                        break;
                    }
                }
                if (ominousProperty == null || !Boolean.TRUE.equals(state.get(ominousProperty))) {
                    toRemoveVaults.add(vaultPos);
                }
            }
            ominousVaults.removeAll(toRemoveVaults);
            for (Set<BlockPos> set : chunkVaults.values()) set.removeAll(toRemoveVaults);
        }
        if (now - lastFullRescan >= fullRescanIntervalMs) {
            lastFullRescan = now;
            for (ChunkPos chunkPos : lastLoadedChunks) {
                scanChunkForVaults(chunkPos, minY, maxY);
            }
        }
        lastLoadedChunks = currentChunks;
    }
    @EventHandler
    private void onChunkData(ChunkDataEvent event) {
        if (!highlightTreasure.get()) return;
        if (mc.world == null || mc.player == null) return;
        scanChunkForTreasure(event.chunk());
    }
    @EventHandler
    private void onInteractBlock(InteractBlockEvent event) {
        if (!highlightTreasure.get()) return;
        if (mc.player == null || mc.world == null) return;
        if (notifiedTreasure.contains(event.result.getBlockPos())) {
            if (event.result.getType() == HitResult.Type.BLOCK && mc.world.getBlockState(event.result.getBlockPos()).getBlock() instanceof ChestBlock) {
                lootedTreasure.add(event.result.getBlockPos());
                if (StardustUtil.XAERO_AVAILABLE && treasureWaypoints.get()) {
                    BlockPos wpPos = event.result.getBlockPos();
                    MapUtil.removeWaypoints(
                        "VanityESP",
                        pos -> pos.getX() == wpPos.getX() && pos.getY() == wpPos.getY() && pos.getZ() == wpPos.getZ(),
                        Optional.empty()
                    );
                }
            }
        }
    }
    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc.world == null || mc.player == null) return;
        if (highlightMapFrames.get()) {
            ShapeMode mapMode = getShapeMode(mapRenderFill.get(), mapRenderOutline.get());
            if (mapMode != null) {
                for (ItemFrameEntity frame : mc.world.getEntitiesByClass(ItemFrameEntity.class, mc.player.getBoundingBox().expand(64),
                    e -> e.getHeldItemStack().getItem().getTranslationKey().equals("item.minecraft.filled_map"))) {
                    Box box;
                    float pitch = frame.getPitch();
                    if (pitch == 90 || pitch == -90) {
                        box = frame.getBoundingBox().expand(0.12, 0.01, 0.12);
                    } else {
                        box = frame.getBoundingBox().expand(0.12, 0.12, 0.01);
                    }
                    Color fill = new Color(mapFillColor.get());
                    Color outline = new Color(mapOutlineColor.get());
                    event.renderer.box(box, fill, outline, mapMode, 0);
                }
            }
        }
        if (highlightBanners.get()) {
            ShapeMode bannerMode = getShapeMode(bannerRenderFill.get(), bannerRenderOutline.get());
            if (bannerMode != null) {
                renderBanners(event, bannerMode);
            }
        }
        if (highlightShulkerFrames.get()) {
            ShapeMode shulkerMode = getShapeMode(shulkerRenderFill.get(), shulkerRenderOutline.get());
            if (shulkerMode != null) {
                renderShulkerFrames(event, shulkerMode);
            }
        }
        if (highlightOminousVaults.get()) {
            ShapeMode vaultMode = getShapeMode(vaultRenderFill.get(), vaultRenderOutline.get());
            if (vaultMode != null) {
                renderOminousVaults(event, vaultMode);
            }
        }
        if (highlightTreasure.get()) {
            ShapeMode treasureMode = getShapeMode(treasureRenderFill.get(), treasureRenderOutline.get());
            if (treasureMode != null) {
                renderTreasure(event, treasureMode);
            }
        }
    }
    private ShapeMode getShapeMode(boolean renderFill, boolean renderOutline) {
        if (renderFill && renderOutline) return ShapeMode.Both;
        else if (renderFill) return ShapeMode.Sides;
        else if (renderOutline) return ShapeMode.Lines;
        return null;
    }
    private void renderBanners(Render3DEvent event, ShapeMode shapeMode) {
        int radius = 8;
        BlockPos playerPos = mc.player.getBlockPos();
        Color fill = new Color(bannerFillColor.get());
        Color outline = new Color(bannerOutlineColor.get());
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                WorldChunk chunk = mc.world.getChunk(playerPos.getX() / 16 + dx, playerPos.getZ() / 16 + dz);
                if (chunk == null) continue;
                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    if (!(be instanceof BannerBlockEntity banner)) continue;
                    BlockPos pos = banner.getPos();
                    BlockState state = mc.world.getBlockState(pos);
                    Box box;
                    if (state.contains(WallBannerBlock.FACING)) {
                        Direction facing = state.get(WallBannerBlock.FACING);
                        double centerX = pos.getX() + 0.5;
                        double centerZ = pos.getZ() + 0.5;
                        double offset = 0.1;
                        double depth = 0.03;
                        double width = 0.45;
                        double y1 = pos.getY() - 0.95;
                        double y2 = pos.getY() + 0.85;
                        switch (facing) {
                            case NORTH:
                                box = new Box(centerX - width, y1, pos.getZ() + 1 - offset - depth, centerX + width, y2, pos.getZ() + 1 - offset);
                                break;
                            case SOUTH:
                                box = new Box(centerX - width, y1, pos.getZ() + offset, centerX + width, y2, pos.getZ() + offset + depth);
                                break;
                            case WEST:
                                box = new Box(pos.getX() + 1 - offset - depth, y1, centerZ - width, pos.getX() + 1 - offset, y2, centerZ + width);
                                break;
                            case EAST:
                                box = new Box(pos.getX() + offset, y1, centerZ - width, pos.getX() + offset + depth, y2, centerZ + width);
                                break;
                            default:
                                continue;
                        }
                        event.renderer.box(box, fill, outline, shapeMode, 0);
                    } else if (state.contains(BannerBlock.ROTATION)) {
                        int rotation = state.get(BannerBlock.ROTATION);
                        double centerX = pos.getX() + 0.5;
                        double centerZ = pos.getZ() + 0.5;
                        double y1 = pos.getY();
                        double y2 = pos.getY() + 1.85;
                        if (rotation == 0 || rotation == 8) {
                            double width = 0.45;
                            double depth = 0.03;
                            box = new Box(centerX - width, y1, centerZ - depth, centerX + width, y2, centerZ + depth);
                        } else if (rotation == 4 || rotation == 12) {
                            double width = 0.03;
                            double depth = 0.45;
                            box = new Box(centerX - width, y1, centerZ - depth, centerX + width, y2, centerZ + depth);
                        } else {
                            double size = 0.3;
                            box = new Box(centerX - size, y1, centerZ - size, centerX + size, y2, centerZ + size);
                        }
                        event.renderer.box(box, fill, outline, shapeMode, 0);
                    }
                }
            }
        }
    }
    private void renderShulkerFrames(Render3DEvent event, ShapeMode shapeMode) {
        List<Entity> frames = getShulkerFrames();
        if (frames.isEmpty()) return;
        for (Entity frame : frames) {
            Box box = frame.getBoundingBox();
            event.renderer.box(
                box,
                shulkerFillColor.get(),
                shulkerOutlineColor.get(),
                shapeMode,
                0
            );
            if (shulkerRenderTracer.get()) {
                event.renderer.line(
                    RenderUtils.center.x,
                    RenderUtils.center.y,
                    RenderUtils.center.z,
                    box.getCenter().x, box.getCenter().y, box.getCenter().z,
                    shulkerTracerColor.get()
                );
            }
        }
    }
    private void renderOminousVaults(Render3DEvent event, ShapeMode shapeMode) {
        for (BlockPos pos : ominousVaults) {
            event.renderer.box(
                pos,
                vaultFillColor.get(),
                vaultOutlineColor.get(),
                shapeMode,
                0
            );
            if (vaultRenderTracer.get()) {
                event.renderer.line(
                    RenderUtils.center.x,
                    RenderUtils.center.y,
                    RenderUtils.center.z,
                    pos.toCenterPos().x, pos.toCenterPos().y, pos.toCenterPos().z,
                    vaultTracerColor.get()
                );
            }
        }
    }
    private void renderTreasure(Render3DEvent event, ShapeMode shapeMode) {
        List<BlockPos> inRange = notifiedTreasure
            .stream()
            .filter(pos -> pos.isWithinDistance(mc.player.getBlockPos(), mc.options.getViewDistance().getValue() * 16 + 32))
            .toList();
        for (BlockPos pos : inRange) {
            if (lootedTreasure.contains(pos)) continue;
            event.renderer.box(
                pos.getX(), pos.getY(), pos.getZ(),
                pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1,
                treasureFillColor.get(), treasureOutlineColor.get(), shapeMode, 0
            );
            if (treasureRenderTracer.get()) {
                event.renderer.line(
                    RenderUtils.center.x,
                    RenderUtils.center.y,
                    RenderUtils.center.z,
                    pos.getX() + .5,
                    pos.getY() + .5,
                    pos.getZ() + .5,
                    treasureTracerColor.get()
                );
            }
        }
    }
    private List<Entity> getShulkerFrames() {
        List<Entity> result = new ArrayList<>();
        if (mc.world == null) return result;
        for (Entity entity : mc.world.getEntities()) {
            if (entity instanceof ItemFrameEntity || entity instanceof GlowItemFrameEntity) {
                ItemStack stack = ((ItemFrameEntity) entity).getHeldItemStack();
                if (isShulkerBox(stack)) result.add(entity);
            }
        }
        return result;
    }
    private boolean isShulkerBox(ItemStack stack) {
        if (stack == null) return false;
        return stack.getItem() == Items.SHULKER_BOX
            || stack.getItem() == Items.WHITE_SHULKER_BOX
            || stack.getItem() == Items.ORANGE_SHULKER_BOX
            || stack.getItem() == Items.MAGENTA_SHULKER_BOX
            || stack.getItem() == Items.LIGHT_BLUE_SHULKER_BOX
            || stack.getItem() == Items.YELLOW_SHULKER_BOX
            || stack.getItem() == Items.LIME_SHULKER_BOX
            || stack.getItem() == Items.PINK_SHULKER_BOX
            || stack.getItem() == Items.GRAY_SHULKER_BOX
            || stack.getItem() == Items.LIGHT_GRAY_SHULKER_BOX
            || stack.getItem() == Items.CYAN_SHULKER_BOX
            || stack.getItem() == Items.PURPLE_SHULKER_BOX
            || stack.getItem() == Items.BLUE_SHULKER_BOX
            || stack.getItem() == Items.BROWN_SHULKER_BOX
            || stack.getItem() == Items.GREEN_SHULKER_BOX
            || stack.getItem() == Items.RED_SHULKER_BOX
            || stack.getItem() == Items.BLACK_SHULKER_BOX;
    }
    private boolean scanChunkForVaults(ChunkPos chunkPos, int minY, int maxY) {
        net.minecraft.world.chunk.Chunk chunk;
        try {
            chunk = mc.world.getChunk(chunkPos.x, chunkPos.z);
        } catch (Exception e) {
            return false;
        }
        if (chunk instanceof WorldChunk) {
            Set<BlockPos> foundVaults = new HashSet<>();
            for (BlockEntity blockEntity : ((WorldChunk) chunk).getBlockEntities().values()) {
                if (Registries.BLOCK_ENTITY_TYPE.getId(blockEntity.getType()) != null
                    && Registries.BLOCK_ENTITY_TYPE.getId(blockEntity.getType()).getPath().equals("vault")) {
                    BlockPos pos = blockEntity.getPos();
                    BlockState state = mc.world.getBlockState(pos);
                    Property<?> ominousProperty = null;
                    for (Property<?> prop : state.getProperties()) {
                        if (prop.getName().equals("ominous")) {
                            ominousProperty = prop;
                            break;
                        }
                    }
                    if (ominousProperty != null && Boolean.TRUE.equals(state.get(ominousProperty))) {
                        foundVaults.add(pos);
                    }
                }
            }
            if (!foundVaults.isEmpty()) {
                chunkVaults.put(chunkPos, foundVaults);
                ominousVaults.addAll(foundVaults);
                return true;
            }
        }
        return false;
    }
    private void scanChunkForTreasure(WorldChunk chunk) {
        Map<BlockPos, BlockEntity> blockEntities = chunk.getBlockEntities();
        for (BlockPos pos : blockEntities.keySet()) {
            if (notifiedTreasure.contains(pos)) continue;
            if (blockEntities.get(pos) instanceof ChestBlockEntity) {
                int localX = ChunkSectionPos.getLocalCoord(pos.getX());
                int localZ = ChunkSectionPos.getLocalCoord(pos.getZ());
                if (localX == 9 && localZ == 9 && isBuriedNaturally(pos)) {
                    if (StardustUtil.XAERO_AVAILABLE && treasureWaypoints.get()) {
                        MapUtil.addWaypoint(
                            pos, "VanityESP - Buried Treasure", "❌",
                            MapUtil.Purpose.Normal, MapUtil.WpColor.Dark_Red, treasureTempWaypoints.get()
                        );
                    }
                    if (treasureSound.get()) {
                        mc.player.playSound(SoundEvents.BLOCK_AMETHYST_BLOCK_RESONATE, treasureVolume.get().floatValue(), 1f);
                    }
                    if (treasureChat.get()) {
                        String notification;
                        if (treasureCoords.get()) {
                            notification = "§3§oFound buried treasure at §8[§7§o"
                                + pos.getX() + "§8, §7§o" + pos.getY() + "§8, §7§o" + pos.getZ() + "§8]";
                        } else {
                            notification = "§3§oFound buried treasure§7§o!";
                        }
                        MsgUtil.sendModuleMsg(notification, this.name);
                    }
                    notifiedTreasure.add(pos);
                }
            }
        }
    }
    private boolean isBuriedNaturally(BlockPos pos) {
        if (mc.world == null) return false;
        Block block = mc.world.getBlockState(pos.up()).getBlock();
        return block == Blocks.SAND || block == Blocks.DIRT || block == Blocks.GRAVEL
            || block == Blocks.STONE || block == Blocks.DIORITE || block == Blocks.GRANITE
            || block == Blocks.ANDESITE || block == Blocks.SANDSTONE || block == Blocks.COAL_ORE;
    }
}