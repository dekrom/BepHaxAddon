package bep.hax.modules;
import bep.hax.Bep;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.registry.RegistryKey;
import xaero.hud.minimap.BuiltInHudModules;
import xaero.hud.minimap.module.MinimapSession;
import xaero.hud.minimap.waypoint.set.WaypointSet;
import xaero.hud.minimap.world.MinimapWorld;
import xaero.map.mods.SupportMods;
import xaeroplus.XaeroPlus;
import xaeroplus.event.ChunkDataEvent;
import xaeroplus.module.ModuleManager;
import xaeroplus.module.impl.OldChunks;
import xaeroplus.module.impl.PaletteNewChunks;
import xaero.common.minimap.waypoints.Waypoint;
import java.util.ArrayDeque;
import static bep.hax.util.Utils.*;
public class OldChunkNotifier extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    public enum DimensionMode {
        OVERWORLD,
        NETHER,
        BOTH
    }
    public enum ChunkTypeMode {
        ONLY_112("1.12 Only"),
        ONLY_119("1.19+ Only"),
        BOTH("Both");
        private final String displayName;
        ChunkTypeMode(String displayName) {
            this.displayName = displayName;
        }
        @Override
        public String toString() {
            return displayName;
        }
    }
    private final Setting<Boolean> notifyAnyChunks = sgGeneral.add(new BoolSetting.Builder()
        .name("notify-any-chunks")
        .description("Whether to notify you of any old chunks.")
        .defaultValue(false)
        .build()
    );
    private final Setting<Boolean> notifyOffHighway = sgGeneral.add(new BoolSetting.Builder()
        .name("notify-trails-off-highway")
        .description("Whether to notify you of old chunks off the highway.")
        .defaultValue(false)
        .build()
    );
    private final Setting<Double> directionOfTravel = sgGeneral.add(new DoubleSetting.Builder()
        .name("direction-of-travel")
        .description("The direction of travel (yaw) in degrees.")
        .defaultValue(0)
        .min(-180)
        .max(180)
        .visible(notifyOffHighway::get)
        .build()
    );
    private final Setting<Double> distanceOffAxis = sgGeneral.add(new DoubleSetting.Builder()
        .name("distance-off-axis")
        .description("The distance in chunks off the axis of movement from the player to check for old chunks.")
        .defaultValue(13)
        .sliderRange(0, 15)
        .visible(notifyOffHighway::get)
        .build()
    );
    private final Setting<ChunkTypeMode> chunkTypeMode = sgGeneral.add(new EnumSetting.Builder<ChunkTypeMode>()
        .name("chunk-type")
        .description("Which type of old chunks to detect.")
        .defaultValue(ChunkTypeMode.BOTH)
        .build()
    );
    private final Setting<LogType> logType = sgGeneral.add(new EnumSetting.Builder<LogType>()
        .name("log-type")
        .description("What to do when an old chunk is detected.")
        .defaultValue(LogType.Marker)
        .build()
    );
    private final Setting<String> webhookLink = sgGeneral.add(new StringSetting.Builder()
        .name("webhook-link")
        .description("A discord webhook link. Looks like this: https://discord.com/api/webhooks/webhookUserId/webHookTokenOrSomething")
        .defaultValue("")
        .visible(() -> logType.get() == LogType.Webhook || logType.get() == LogType.Both)
        .build()
    );
    private final Setting<Boolean> ping = sgGeneral.add(new BoolSetting.Builder()
        .name("ping")
        .description("Whether to ping you or not.")
        .defaultValue(false)
        .visible(() -> logType.get() == LogType.Webhook || logType.get() == LogType.Both)
        .build()
    );
    private final Setting<String> discordId = sgGeneral.add(new StringSetting.Builder()
        .name("discord-ID")
        .description("Your discord ID")
        .defaultValue("")
        .visible(() -> ping.get() && (logType.get() == LogType.Webhook || logType.get() == LogType.Both))
        .build()
    );
    public final Setting<DimensionMode> dimensionMode = sgGeneral.add(new EnumSetting.Builder<DimensionMode>()
        .name("dimension-mode")
        .description("Choose where the module will detect old chunks.")
        .defaultValue(DimensionMode.BOTH)
        .build()
    );
    public OldChunkNotifier() {
        super(Bep.STASH, "old-chunk-notifier", "Sends a webhook message and optionally pings you when an old chunk is detected.");
    }
    @Override
    public void onActivate()
    {
        XaeroPlus.EVENT_BUS.register(this);
        oldChunks.clear();
    }
    @Override
    public void onDeactivate()
    {
        XaeroPlus.EVENT_BUS.unregister(this);
    }
    private final ArrayDeque<ChunkPos> oldChunks = new ArrayDeque<>();
    @net.lenni0451.lambdaevents.EventHandler(priority = -1)
    public void onChunkData(ChunkDataEvent event)
    {
        if (event.seenChunk()) return;
        if (mc.player.getAbilities().allowFlying) return;
        if ((dimensionMode.get() == DimensionMode.NETHER && mc.world.getRegistryKey() != World.NETHER) ||
            (dimensionMode.get() == DimensionMode.OVERWORLD && mc.world.getRegistryKey() != World.OVERWORLD)) return;
        if (oldChunks.size() > 1000) {
            oldChunks.removeFirst();
        }
        if (oldChunks.contains(event.chunk().getPos())) return;
        oldChunks.add(event.chunk().getPos());
        boolean is119NewChunk = ModuleManager.getModule(PaletteNewChunks.class)
            .isNewChunk(
                event.chunk().getPos().x,
                event.chunk().getPos().z,
                event.chunk().getWorld().getRegistryKey()
            );
        boolean is112OldChunk = ModuleManager.getModule(OldChunks.class)
            .isOldChunk(
                event.chunk().getPos().x,
                event.chunk().getPos().z,
                event.chunk().getWorld().getRegistryKey()
            );
        ChunkTypeMode typeMode = chunkTypeMode.get();
        boolean shouldNotify = false;
        switch (typeMode) {
            case ONLY_112:
                shouldNotify = is112OldChunk;
                break;
            case ONLY_119:
                shouldNotify = !is119NewChunk && !is112OldChunk;
                break;
            case BOTH:
                shouldNotify = !is119NewChunk || is112OldChunk;
                break;
        }
        if (!shouldNotify) return;
        if (notifyAnyChunks.get())
        {
            if (logType.get() == LogType.Both || logType.get() == LogType.Marker)
            {
                createMapMarker(event.chunk().getPos().x, event.chunk().getPos().z);
            }
            if (logType.get() == LogType.Both || logType.get() == LogType.Webhook)
            {
                String message = "";
                if (is112OldChunk && !is119NewChunk) {
                    message = "1.12 Followed in 1.19+ Old Chunk Detected";
                } else if (is112OldChunk && is119NewChunk) {
                    message = "1.12 Unfollowed in 1.19+ Old Chunk Detected";
                } else {
                    message = "1.19+ Old Chunk Detected";
                }
                String finalMessage = message;
                String discordID = !ping.get() || discordId.get().isBlank() ? null : discordId.get();
                new Thread(() -> sendWebhook(webhookLink.get(), "Old Chunk Detected", finalMessage + " at " + mc.player.getEntityPos().toString(), discordID, mc.player.getGameProfile().name())).start();
            }
        }
        if (notifyOffHighway.get())
        {
            ChunkPos chunkPos = event.chunk().getPos();
            Vec3d direction = yawToDirection(directionOfTravel.get());
            ChunkPos playerChunkPos = mc.player.getChunkPos();
            double distance = distancePointToDirection(new Vec3d(chunkPos.x, 0, chunkPos.z), direction, new Vec3d(playerChunkPos.x, 0, playerChunkPos.z));
            if (distance > distanceOffAxis.get())
            {
                if (logType.get() == LogType.Both || logType.get() == LogType.Marker)
                {
                    createMapMarker(chunkPos.x, chunkPos.z);
                }
                if (logType.get() == LogType.Both || logType.get() == LogType.Webhook)
                {
                    String discordID = !ping.get() || discordId.get().isBlank() ? null : discordId.get();
                    new Thread(() -> sendWebhook(webhookLink.get(), "Old Chunk Detected", "Old chunk detected off the highway at " + chunkPos.x * 16 + " " + chunkPos.z * 16, discordID, mc.player.getGameProfile().name())).start();
                }
            }
        }
    }
    @SuppressWarnings("deprecation")
    private void createMapMarker(int x, int z)
    {
        MinimapSession minimapSession = BuiltInHudModules.MINIMAP.getCurrentSession();
        if (minimapSession == null) return;
        MinimapWorld currentWorld = minimapSession.getWorldManager().getCurrentWorld();
        if (currentWorld == null) return;
        WaypointSet waypointSet = currentWorld.getCurrentWaypointSet();
        if (waypointSet == null) return;
        Waypoint waypoint = new Waypoint(
            x * 16,
            70,
            z * 16,
            "Old Chunk",
            "O",
            5,
            0,
            false);
        waypointSet.add(waypoint);
        SupportMods.xaeroMinimap.requestWaypointsRefresh();
    }
    private enum LogType
    {
        Webhook,
        Marker,
        Both
    }
}