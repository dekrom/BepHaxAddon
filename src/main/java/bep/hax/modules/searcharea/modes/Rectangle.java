package bep.hax.modules.searcharea.modes;
import bep.hax.accessor.InputAccessor;
import bep.hax.modules.searcharea.SearchAreaMode;
import bep.hax.modules.searcharea.SearchAreaModes;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.misc.AutoReconnect;
import meteordevelopment.meteorclient.systems.modules.movement.elytrafly.ElytraFly;
import meteordevelopment.meteorclient.utils.player.Rotations;
import net.minecraft.util.math.Vec3d;
import net.minecraft.network.packet.s2c.common.DisconnectS2CPacket;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import java.io.*;
import static meteordevelopment.meteorclient.utils.player.ChatUtils.info;
import static bep.hax.util.Utils.*;
public class Rectangle extends SearchAreaMode
{
    private PathingDataRectangle pd;
    private boolean goingToStart = true;
    private long startTime;
    public Rectangle() {
        super(SearchAreaModes.Rectangle);
    }
    @Override
    public void onActivate()
    {
        goingToStart = true;
        File file = getJsonFile(super.toString());
        if (file == null || !file.exists())
        {
            pd = new PathingDataRectangle(searchArea.startPos.get(), searchArea.targetPos.get(), searchArea.startPos.get(), 90, true, (int)mc.player.getZ());
        }
        else
        {
            try {
                FileReader reader = new FileReader(file);
                pd = GSON.fromJson(reader, PathingDataRectangle.class);
                reader.close();
            } catch (Exception ignored) {
            }
        }
    }
    @Override
    public void onDeactivate()
    {
        super.onDeactivate();
        super.saveToJson(goingToStart, pd);
    }
    private double getMovementSpeed()
    {
        double speedBPS = 40.0; // Default fallback speed

        // Try to get speed from ElytraFly module if active
        try {
            Module elytraFly = Modules.get().get(ElytraFly.class);
            if (elytraFly != null && elytraFly.isActive()) {
                // Try to get the speed setting from ElytraFly
                Object speedSetting = elytraFly.settings.get("speed");
                if (speedSetting != null) {
                    speedBPS = (double) speedSetting.getClass().getMethod("get").invoke(speedSetting);
                }
            }
        } catch (Exception ignored) {
            // If ElytraFly doesn't have speed setting, try calculating from player velocity
        }

        // If we still have default value, try to calculate from actual player velocity
        if (speedBPS == 40.0 && mc.player != null) {
            Vec3d velocity = mc.player.getVelocity();
            double horizontalSpeed = Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z);

            // Convert to blocks per second (velocity is in blocks per tick, 20 ticks per second)
            double calculatedSpeed = horizontalSpeed * 20.0;

            // Only use calculated speed if player is actually moving (> 1 block/sec)
            if (calculatedSpeed > 1.0) {
                speedBPS = calculatedSpeed;
            }
        }

        return speedBPS;
    }

    private void printRectangleEstimate()
    {
        double speedBPS = getMovementSpeed();
        double rowDistance = Math.abs(pd.initialPos.getX() - pd.targetPos.getX());
        int rowCount = Math.abs(pd.currPos.getZ() - pd.targetPos.getZ()) / 16 / searchArea.rowGap.get();
        double totalBlocks = rowCount * (rowDistance + (searchArea.rowGap.get() * 16));
        long totalSeconds = (long)(totalBlocks / speedBPS);
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        info("Completion will take an estimated %02d hours %02d minutes %02d seconds at a speed of %.2f blocks/sec and a gap of %d chunks between paths.", hours, minutes, seconds, speedBPS, searchArea.rowGap.get());
    }
    @Override
    public void onTick()
    {
        if (System.nanoTime() - startTime > 6e11)
        {
            startTime = System.nanoTime();
            super.saveToJson(goingToStart, pd);
        }
        if (goingToStart)
        {
            if (Math.sqrt(mc.player.getBlockPos().getSquaredDistance(pd.currPos.getX(), mc.player.getY(), pd.currPos.getZ())) < 5)
            {
                goingToStart = false;
                mc.player.setVelocity(0, 0, 0);
                printRectangleEstimate();
            }
            else
            {
                mc.player.setYaw((float) Rotations.getYaw(pd.currPos.toCenterPos()));
                setPressed(mc.options.forwardKey, true);
            }
            return;
        }
        setPressed(mc.options.forwardKey, true);
        mc.player.setYaw(pd.yawDirection);
        if (Math.sqrt(mc.player.getBlockPos().getSquaredDistance(pd.targetPos.getX(), mc.player.getY(), pd.targetPos.getZ())) < 20)
        {
            setPressed(mc.options.forwardKey, false);
            searchArea.toggle();
            if (searchArea.disconnectOnCompletion.get())
            {
                var autoReconnect = Modules.get().get(AutoReconnect.class);
                if (autoReconnect.isActive()) autoReconnect.toggle();
                mc.player.networkHandler.onDisconnect(new DisconnectS2CPacket(Text.literal("[Search Area] Path is complete")));
            }
        }
        else if (pd.mainPath && ((pd.yawDirection == -90.0f && mc.player.getX() >= (Math.max(pd.initialPos.getX(), pd.targetPos.getX())))) ||
            (pd.yawDirection == 90.0f && mc.player.getX() <= (Math.min(pd.initialPos.getX(), pd.targetPos.getX()))))
        {
            pd.yawDirection = (mc.player.getZ() < pd.targetPos.getZ()) ? 0.0f : 180.0f;
            pd.mainPath = false;
            mc.player.setVelocity(0, 0, 0);
        }
        else if (!pd.mainPath && Math.abs(mc.player.getZ() - pd.lastCompleteRowZ) >= (16 * searchArea.rowGap.get()))
        {
            pd.lastCompleteRowZ = (int)mc.player.getZ();
            pd.yawDirection = (pd.initialPos.getX() > mc.player.getX() ? -90.0f : 90.0f);
            pd.mainPath = true;
            mc.player.setVelocity(0, 0, 0);
        }
    }
    public static class PathingDataRectangle extends PathingData
    {
        public BlockPos targetPos;
        public int lastCompleteRowZ;
        public PathingDataRectangle(BlockPos initialPos, BlockPos targetPos, BlockPos currPos, float yawDirection, boolean mainPath, int lastCompleteRowZ)
        {
            this.initialPos = initialPos;
            this.targetPos = targetPos;
            this.currPos = currPos;
            this.yawDirection = yawDirection;
            this.mainPath = mainPath;
            this.lastCompleteRowZ = lastCompleteRowZ;
        }
    }
}