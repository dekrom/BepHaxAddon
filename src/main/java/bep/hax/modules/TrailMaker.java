package bep.hax.modules;
import bep.hax.util.Utils;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.containers.WTable;
import meteordevelopment.meteorclient.gui.widgets.containers.WVerticalList;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import bep.hax.Bep;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import xaeroplus.module.ModuleManager;
import xaeroplus.module.impl.Drawing;
import java.util.LinkedList;
import java.util.Queue;
public class TrailMaker extends Module
{
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final Setting<Boolean> recording = sgGeneral.add(new BoolSetting.Builder()
        .name("toggle-recording")
        .description("Enabled = recording, Disabled = not recording.")
        .defaultValue(false)
        .build()
    );
    public final Setting<Double> rotationScaling = sgGeneral.add(new DoubleSetting.Builder()
        .name("rotation-scaling")
        .description("Scaling of how fast the yaw changes. 1 = instant, 0 = doesn't change")
        .defaultValue(0.1)
        .sliderRange(0.0, 1.0)
        .build()
    );
    public TrailMaker()
    {
        super(Bep.STASH, "trail-maker", "Allows you to plot xaero chunk highlights on the map and then follow them in order.");
    }
    public RegistryKey<World> dimension;
    public final Queue<ChunkPos> points = new LinkedList<>();
    private boolean following = false;
    @Override
    public void onActivate()
    {
        if (points.isEmpty())
        {
            info("You haven't added any points, toggle the recording setting and add chunk highlights on the xaero map.");
        }
    }
    @Override
    public void onDeactivate()
    {
        following = false;
        recording.set(false);
    }
    @Override
    public WWidget getWidget(GuiTheme theme)
    {
        WVerticalList list = theme.verticalList();
        WTable buttonTable = list.add(theme.table()).widget();
        WButton startFollowing = buttonTable.add(theme.button(following ? "Stop Following" : "Start Following")).widget();
        startFollowing.action = () -> {
            following = !following;
            recording.set(false);
        };
        WButton clearPoints = buttonTable.add(theme.button("Clear Points")).widget();
        clearPoints.action = () -> {
            points.clear();
            dimension = null;
        };
        return list;
    }
    public boolean isRecording()
    {
        return this.isActive() && recording.get();
    }
    @EventHandler
    private void onTick(TickEvent.Pre event)
    {
        if (mc.player == null) return;
        if (following)
        {
            if (points.isEmpty())
            {
                info("Finished following the points! Disabling.");
                this.toggle();
                return;
            }
            ChunkPos goal = points.peek();
            Vec3d centerBlockPos = goal.getCenterAtY((int) mc.player.getY()).toCenterPos();
            if (dimension.equals(World.NETHER) && !mc.world.getRegistryKey().equals(World.NETHER))
            {
                centerBlockPos = centerBlockPos.multiply(8.0, 1.0, 8.0);
            }
            else if (mc.world.getRegistryKey().equals(World.NETHER) && !dimension.equals(World.NETHER))
            {
                centerBlockPos = centerBlockPos.multiply(1.0 / 8.0, 1.0, 1.0 / 8.0);
            }
            float targetYaw = (float) Rotations.getYaw(centerBlockPos);
            mc.player.setYaw(Utils.smoothRotation(mc.player.getYaw(), targetYaw, rotationScaling.get()));
            if (mc.player.getEntityPos().squaredDistanceTo(centerBlockPos) < 16 * 16)
            {
                ChunkPos point = points.poll();
                ModuleManager.getModule(Drawing.class).drawingCache.removeHighlight(point.x, point.z, dimension);
            }
        }
    }
}