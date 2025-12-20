package bep.hax.modules;
import bep.hax.Bep;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import bep.hax.util.InventoryManager;
import bep.hax.util.InventoryManager.IPlayerInteractEntityC2SPacket;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.Hand;
import bep.hax.modules.PVPModule;
import bep.hax.util.CacheTimer;
import bep.hax.util.EntityUtil;
import bep.hax.util.MovementUtil;
import bep.hax.util.PlacementUtils;
import static meteordevelopment.meteorclient.MeteorClient.mc;
public class Criticals extends PVPModule {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final Setting<Boolean> multitask = sgGeneral.add(new BoolSetting.Builder()
        .name("multitask")
        .description("Allows crits when other combat modules are enabled")
        .defaultValue(true)
        .build()
    );
    private final Setting<CritMode> mode = sgGeneral.add(new EnumSetting.Builder<CritMode>()
        .name("mode")
        .description("Mode for critical attack modifier")
        .defaultValue(CritMode.PACKET)
        .build()
    );
    private final Setting<Boolean> phaseOnly = sgGeneral.add(new BoolSetting.Builder()
        .name("phase-only")
        .description("Only attempts criticals when phased")
        .defaultValue(false)
        .visible(() -> mode.get() == CritMode.GRIM_V3 || mode.get() == CritMode.GRIM)
        .build()
    );
    private final Setting<Boolean> wallsOnly = sgGeneral.add(new BoolSetting.Builder()
        .name("walls-only")
        .description("Only attempts criticals in walls")
        .defaultValue(false)
        .visible(() -> (mode.get() == CritMode.GRIM_V3 || mode.get() == CritMode.GRIM) && phaseOnly.get())
        .build()
    );
    private final Setting<Boolean> moveFix = sgGeneral.add(new BoolSetting.Builder()
        .name("move-fix")
        .description("Pauses crits when moving")
        .defaultValue(false)
        .visible(() -> mode.get() == CritMode.GRIM_V3 || mode.get() == CritMode.GRIM)
        .build()
    );
    private final CacheTimer attackTimer = new CacheTimer();
    private boolean postUpdateGround;
    private boolean postUpdateSprint;
    public Criticals() {
        super(Bep.CATEGORY, "criticals", "Modifies attacks to always land critical hits");
    }
    @Override
    public void onDeactivate() {
        postUpdateGround = false;
        postUpdateSprint = false;
    }
    @EventHandler
    private void onSendPacket(PacketEvent.Send event) {
        if (mc.player == null || mc.world == null) return;
        if (isOtherCombatActive()) return;
        if (event.packet instanceof PlayerInteractEntityC2SPacket packet) {
            IPlayerInteractEntityC2SPacket accessor = (IPlayerInteractEntityC2SPacket) packet;
            if (!accessor.isAttackPacket()) return;
            Entity target = null;
            if (mc.world != null) {
                int entityId = accessor.getTargetEntityId();
                for (Entity entity : mc.world.getEntities()) {
                    if (entity.getId() == entityId) {
                        target = entity;
                        break;
                    }
                }
            }
            if (!isValidTarget(target)) return;
            if (EntityUtil.isVehicle(target)) {
                handleVehicleAttack(target);
                return;
            }
            postUpdateSprint = mc.player.isSprinting();
            if (postUpdateSprint) {
                mc.getNetworkHandler().sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.STOP_SPRINTING));
            }
            performCriticalAttack(target);
        }
    }
    @EventHandler
    private void onSentPacket(PacketEvent.Sent event) {
        if (mc.player == null) return;
        if (event.packet instanceof PlayerInteractEntityC2SPacket) {
            if (postUpdateGround) {
                mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(
                    mc.player.getX(), mc.player.getY(), mc.player.getZ(), false, false
                ));
                postUpdateGround = false;
            }
            if (postUpdateSprint) {
                mc.getNetworkHandler().sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_SPRINTING));
                postUpdateSprint = false;
            }
        }
    }
    private boolean isOtherCombatActive() {
        if (!multitask.get()) {
            return false;
        }
        return false;
    }
    private boolean isValidTarget(Entity entity) {
        if (entity == null || !entity.isAlive() || !(entity instanceof LivingEntity)) {
            return false;
        }
        return !(mc.player.isRiding() ||
            mc.player.isGliding() ||
            mc.player.isTouchingWater() ||
            mc.player.isInLava() ||
            mc.player.isHoldingOntoLadder() ||
            mc.player.hasStatusEffect(StatusEffects.BLINDNESS) ||
            InventoryManager.isHolding32k());
    }
    private void handleVehicleAttack(Entity target) {
        if (mode.get() == CritMode.PACKET) {
            for (int i = 0; i < 5; i++) {
                mc.getNetworkHandler().sendPacket(PlayerInteractEntityC2SPacket.attack(target, mc.player.isSneaking()));
                mc.getNetworkHandler().sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));
            }
        }
    }
    private void performCriticalAttack(Entity target) {
        double x = mc.player.getX();
        double y = mc.player.getY();
        double z = mc.player.getZ();
        switch (mode.get()) {
            case VANILLA -> {
                if (mc.player.isOnGround() && !mc.options.jumpKey.isPressed()) {
                    double d = 1.0e-7 + 1.0e-7 * (1.0 + RANDOM.nextInt(RANDOM.nextBoolean() ? 34 : 43));
                    mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(
                        x, y + 0.1016f + d * 3.0f, z, false, false));
                    mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(
                        x, y + 0.0202f + d * 2.0f, z, false, false));
                    mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(
                        x, y + 3.239e-4 + d, z, false, false));
                    mc.player.addCritParticles(target);
                }
            }
            case PACKET -> {
                if (mc.player.isOnGround() && !mc.options.jumpKey.isPressed()) {
                    mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(
                        x, y + 0.0625f, z, false, false));
                    mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(
                        x, y, z, false, false));
                    mc.player.addCritParticles(target);
                }
            }
            case PACKET_STRICT -> {
                if (attackTimer.passed(500) && mc.player.isOnGround() && !mc.options.jumpKey.isPressed()) {
                    mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(
                        x, y + 1.1e-7f, z, false, false));
                    mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(
                        x, y + 1.0e-8f, z, false, false));
                    postUpdateGround = true;
                    attackTimer.reset();
                }
            }
            case GRIM -> {
                if (phaseOnly.get() && (wallsOnly.get() ? !PlacementUtils.isDoublePhased() : !PlacementUtils.isPhased())) {
                    return;
                }
                if (moveFix.get() && MovementUtil.isMovingInput()) {
                    return;
                }
                if (attackTimer.passed(250) && mc.player.isOnGround() && !mc.player.isCrawling()) {
                    float yaw = mc.player.getYaw();
                    float pitch = mc.player.getPitch();
                    mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.Full(
                        x, y + 0.0625, z, yaw, pitch, false, false));
                    mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.Full(
                        x, y + 0.0625013579, z, yaw, pitch, false, false));
                    mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.Full(
                        x, y + 1.3579e-6, z, yaw, pitch, false, false));
                    attackTimer.reset();
                }
            }
            case GRIM_V3 -> {
                if (phaseOnly.get() && (wallsOnly.get() ? !PlacementUtils.isDoublePhased() : !PlacementUtils.isPhased())) {
                    return;
                }
                if (moveFix.get() && MovementUtil.isMovingInput()) {
                    return;
                }
                if (mc.player.isOnGround() && !mc.player.isCrawling()) {
                    float yaw = mc.player.getYaw();
                    float pitch = mc.player.getPitch();
                    mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.Full(
                        x, y, z, yaw, pitch, true, false));
                    mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.Full(
                        x, y + 0.0625f, z, yaw, pitch, false, false));
                    mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.Full(
                        x, y + 0.04535f, z, yaw, pitch, false, false));
                }
            }
            case LOW_HOP -> {
                mc.player.setVelocity(mc.player.getVelocity().x, 0.3425, mc.player.getVelocity().z);
            }
        }
    }
    public enum CritMode {
        PACKET("Packet"),
        PACKET_STRICT("Packet Strict"),
        VANILLA("Vanilla"),
        GRIM("Grim"),
        GRIM_V3("Grim V3"),
        LOW_HOP("Low Hop");
        private final String displayName;
        CritMode(String displayName) {
            this.displayName = displayName;
        }
        @Override
        public String toString() {
            return displayName;
        }
    }
}