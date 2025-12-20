package bep.hax.mixin.meteor;
import net.minecraft.text.Text;
import bep.hax.util.LogUtil;
import org.jetbrains.annotations.Nullable;
import bep.hax.util.StardustUtil;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import bep.hax.config.StardustConfig;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import meteordevelopment.orbit.EventHandler;
import org.spongepowered.asm.mixin.injection.At;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.MeteorClient;
import org.spongepowered.asm.mixin.injection.Inject;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.misc.AutoReconnect;
import bep.hax.mixin.accessor.DisconnectS2CPacketAccessor;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.systems.modules.combat.AutoLog;
import meteordevelopment.meteorclient.utils.world.TickRate;
import net.minecraft.item.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.network.packet.s2c.common.DisconnectS2CPacket;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(value = AutoLog.class, remap = false)
public abstract class AutoLogMixin extends Module {
    public AutoLogMixin(Category category, String name, String description) {
        super(category, name, description);
    }
    @Shadow
    @Final
    private SettingGroup sgGeneral;
    @Shadow
    @Final
    private Setting<Boolean> toggleOff;
    @Shadow
    @Final
    private Setting<Boolean> smartToggle;
    @Unique
    private boolean didLog = false;
    @Unique
    private long requestedDcAt = 0L;
    @Unique
    @Nullable
    private Text disconnectReason = null;
    @Unique
    @Nullable
    private Setting<Boolean> forceKick = null;
    @Unique
    @Nullable
    private SettingGroup bephaxGroup = null;
    @Unique
    @Nullable
    private Setting<Boolean> logOnY = null;
    @Unique
    @Nullable
    private Setting<Double> yLevel = null;
    @Unique
    @Nullable
    private Setting<Boolean> logArmor = null;
    @Unique
    @Nullable
    private Setting<Boolean> ignoreElytra = null;
    @Unique
    @Nullable
    private Setting<Double> armorPercent = null;
    @Unique
    @Nullable
    private Setting<Boolean> logPortal = null;
    @Unique
    @Nullable
    private Setting<Integer> portalTicks = null;
    @Unique
    @Nullable
    private Setting<Boolean> logPosition = null;
    @Unique
    @Nullable
    private Setting<BlockPos> position = null;
    @Unique
    @Nullable
    private Setting<Double> distance = null;
    @Unique
    @Nullable
    private Setting<Boolean> serverNotResponding = null;
    @Unique
    @Nullable
    private Setting<Double> serverNotRespondingSecs = null;
    @Unique
    @Nullable
    private Setting<Boolean> reconnectAfterNotResponding = null;
    @Unique
    @Nullable
    private Setting<Double> secondsToReconnect = null;
    @Unique
    private int currPortalTicks = 0;
    @Unique
    private double oldDelay;
    @Unique
    private boolean autoReconnectEnabled;
    @Unique
    private boolean waitingForReconnection = false;
    @SuppressWarnings("unchecked")
    @Override
    public void onActivate() {
        super.onActivate();
        currPortalTicks = 0;
        if (waitingForReconnection) {
            waitingForReconnection = false;
            AutoReconnect autoReconnect = Modules.get().get(AutoReconnect.class);
            if (autoReconnect != null) {
                Setting<Double> delay = (Setting<Double>) autoReconnect.settings.get("delay");
                if (delay != null) delay.set(oldDelay);
                if (!autoReconnectEnabled && autoReconnect.isActive()) {
                    autoReconnect.toggle();
                }
            }
        }
    }
    @Override
    public void onDeactivate() {
        if (toggleOff.get() || smartToggle.get() && didLog) {
            MeteorClient.EVENT_BUS.subscribe(this);
        }
    }
    @Inject(method = "<init>", at = @At(value = "FIELD", target = "Lmeteordevelopment/meteorclient/systems/modules/combat/AutoLog;entities:Lmeteordevelopment/meteorclient/settings/Setting;"))
    private void addIllegalDisconnectSetting(CallbackInfo ci) {
        forceKick = sgGeneral.add(
            new BoolSetting.Builder()
                .name("illegal-disconnect")
                .description("Tip: Change the illegal disconnect method in your Meteor config settings (Stardust category.)")
                .defaultValue(false)
                .build()
        );
        bephaxGroup = settings.createGroup("BepHax Extended");
        logOnY = bephaxGroup.add(new BoolSetting.Builder()
            .name("log-on-y")
            .description("Logs out if you are below a certain Y level.")
            .defaultValue(false)
            .build()
        );
        yLevel = bephaxGroup.add(new DoubleSetting.Builder()
            .name("y-level")
            .description("Auto log out if below this Y level.")
            .defaultValue(256)
            .min(-128)
            .sliderRange(-128, 320)
            .visible(logOnY::get)
            .build()
        );
        logArmor = bephaxGroup.add(new BoolSetting.Builder()
            .name("log-armor")
            .description("Logs out if you have low armor durability.")
            .defaultValue(false)
            .build()
        );
        ignoreElytra = bephaxGroup.add(new BoolSetting.Builder()
            .name("ignore-elytra")
            .description("Ignores the elytra when checking for armor.")
            .defaultValue(false)
            .visible(logArmor::get)
            .build()
        );
        armorPercent = bephaxGroup.add(new DoubleSetting.Builder()
            .name("armor-percent")
            .description("Auto log out if armor durability is below this percent.")
            .defaultValue(5)
            .min(0)
            .sliderRange(0, 100)
            .visible(logArmor::get)
            .build()
        );
        logPortal = bephaxGroup.add(new BoolSetting.Builder()
            .name("log-on-portal")
            .description("Logs out if you are in a portal for too long.")
            .defaultValue(false)
            .build()
        );
        portalTicks = bephaxGroup.add(new IntSetting.Builder()
            .name("portal-ticks")
            .description("The amount of ticks in a portal before you log out (80 ticks to go through a portal).")
            .defaultValue(30)
            .min(1)
            .sliderMax(70)
            .visible(logPortal::get)
            .build()
        );
        logPosition = bephaxGroup.add(new BoolSetting.Builder()
            .name("log-position")
            .description("Logs out if you are within x blocks of this position. Y position is not included.")
            .defaultValue(false)
            .build()
        );
        position = bephaxGroup.add(new BlockPosSetting.Builder()
            .name("position")
            .description("The position to log out at. Y position is ignored.")
            .defaultValue(new BlockPos(0, 0, 0))
            .visible(logPosition::get)
            .build()
        );
        distance = bephaxGroup.add(new DoubleSetting.Builder()
            .name("distance")
            .description("The distance from the position to log out at.")
            .defaultValue(100)
            .sliderRange(0, 1000)
            .visible(logPosition::get)
            .build()
        );
        serverNotResponding = bephaxGroup.add(new BoolSetting.Builder()
            .name("server-not-responding")
            .description("Logs out if the server is not responding.")
            .defaultValue(false)
            .build()
        );
        serverNotRespondingSecs = bephaxGroup.add(new DoubleSetting.Builder()
            .name("not-responding-seconds")
            .description("The amount of seconds the server is not responding before you log out.")
            .defaultValue(10)
            .min(1)
            .sliderMax(60)
            .visible(serverNotResponding::get)
            .build()
        );
        reconnectAfterNotResponding = bephaxGroup.add(new BoolSetting.Builder()
            .name("reconnect-after-not-responding")
            .description("Reconnects after the server is not responding.")
            .defaultValue(false)
            .visible(serverNotResponding::get)
            .build()
        );
        secondsToReconnect = bephaxGroup.add(new DoubleSetting.Builder()
            .name("reconnect-delay")
            .description("The amount of seconds to wait before reconnecting (Will temporarily overwrite Meteor's AutoReconnect).")
            .defaultValue(60)
            .min(10)
            .sliderMax(300)
            .visible(() -> reconnectAfterNotResponding.get() && serverNotResponding.get())
            .build()
        );
    }
    @Inject(method = "onTick",at = @At("HEAD"), cancellable = true)
    private void mixinOnTick(CallbackInfo ci) {
        if (!Utils.canUpdate() || !isActive()) ci.cancel();
        if (didLog && System.currentTimeMillis() - requestedDcAt >= 1337) {
            LogUtil.warn("Detected illegal disconnect failure, falling back on regular disconnect (try adjusting your illegal disconnect method config setting).");
            if (mc.getNetworkHandler() != null) mc.getNetworkHandler().onDisconnect(new DisconnectS2CPacket(disconnectReason));
            disconnectReason = null;
            didLog = false;
            requestedDcAt = 0L;
        }
    }
    @SuppressWarnings("unchecked")
    @EventHandler
    private void onTickBephaxExtended(TickEvent.Post event) {
        if (mc.player == null || mc.player.getAbilities().allowFlying) return;
        if (serverNotResponding != null && serverNotResponding.get() && !waitingForReconnection) {
            if (TickRate.INSTANCE.getTimeSinceLastTick() > serverNotRespondingSecs.get()) {
                if (reconnectAfterNotResponding != null && reconnectAfterNotResponding.get()) {
                    AutoReconnect autoReconnect = Modules.get().get(AutoReconnect.class);
                    if (autoReconnect != null) {
                        autoReconnectEnabled = autoReconnect.isActive();
                        Setting<Double> delay = (Setting<Double>) autoReconnect.settings.get("delay");
                        if (delay != null) {
                            oldDelay = delay.get();
                            delay.set(secondsToReconnect.get());
                        }
                        if (!autoReconnectEnabled) {
                            autoReconnect.toggle();
                        }
                        waitingForReconnection = true;
                    }
                }
                bephaxDisconnect("Server was not responding for " + serverNotRespondingSecs.get() + " seconds.", reconnectAfterNotResponding == null || !reconnectAfterNotResponding.get());
                return;
            }
        }
        if (logPortal != null && logPortal.get() && mc.player.portalManager != null) {
            if (mc.player.portalManager.isInPortal()) {
                currPortalTicks++;
                if (portalTicks != null && currPortalTicks > portalTicks.get()) {
                    bephaxDisconnect("Player was in a portal for " + currPortalTicks + " ticks.", true);
                    return;
                }
            } else {
                currPortalTicks = 0;
            }
        }
        if (logOnY != null && logOnY.get() && yLevel != null && mc.player.getY() < yLevel.get()) {
            bephaxDisconnect("Player was at Y=" + mc.player.getY() + " which is below your limit of Y=" + yLevel.get(), true);
            return;
        }
        if (logArmor != null && logArmor.get() && armorPercent != null) {
            for (EquipmentSlot slot : new EquipmentSlot[]{
                EquipmentSlot.FEET,
                EquipmentSlot.LEGS,
                EquipmentSlot.CHEST,
                EquipmentSlot.HEAD
            }) {
                ItemStack armorPiece = mc.player.getEquippedStack(slot);
                if (ignoreElytra != null && ignoreElytra.get() && armorPiece.getItem() == Items.ELYTRA) continue;
                if (armorPiece.isDamageable()) {
                    int max = armorPiece.getMaxDamage();
                    int current = armorPiece.getDamage();
                    double percentUndamaged = 100 - ((double) current / max) * 100;
                    if (percentUndamaged < armorPercent.get()) {
                        bephaxDisconnect("You had low armor", true);
                        return;
                    }
                }
            }
        }
        if (logPosition != null && logPosition.get() && position != null && distance != null) {
            Vec3d playerPos = new Vec3d(mc.player.getX(), 0, mc.player.getZ());
            Vec3d targetPos = new Vec3d(position.get().getX(), 0, position.get().getZ());
            double distanceToTarget = playerPos.distanceTo(targetPos);
            if (distanceToTarget < distance.get()) {
                bephaxDisconnect("Player was within " + distanceToTarget + " blocks of the target position.", true);
                return;
            }
        }
    }
    @Unique
    private void bephaxDisconnect(String reason, boolean turnOffReconnect) {
        if (mc.player == null) return;
        if (turnOffReconnect) {
            AutoReconnect autoReconnect = Modules.get().get(AutoReconnect.class);
            if (autoReconnect != null && autoReconnect.isActive()) {
                autoReconnect.toggle();
            }
        }
        if (forceKick != null && forceKick.get()) {
            didLog = true;
            requestedDcAt = System.currentTimeMillis();
            disconnectReason = Text.literal("§8[§a§oAutoLog§8] §f" + reason);
            StardustUtil.illegalDisconnect(true, StardustConfig.illegalDisconnectMethodSetting.get());
        } else {
            mc.player.networkHandler.onDisconnect(new DisconnectS2CPacket(Text.literal("[AutoLog] " + reason)));
        }
    }
    @Inject(method = "disconnect(Lnet/minecraft/text/Text;)V", at = @At("HEAD"), cancellable = true, remap = true)
    private void maybeIllegalDisconnect(Text reason, CallbackInfo ci) {
        if (forceKick != null && forceKick.get()) {
            ci.cancel();
            didLog = true;
            requestedDcAt = System.currentTimeMillis();
            disconnectReason = Text.literal("§8[§a§oAutoLog§8] §f" + reason.getString());
            StardustUtil.illegalDisconnect(true, StardustConfig.illegalDisconnectMethodSetting.get());
        }
    }
    @Unique
    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (disconnectReason == null || !(event.packet instanceof DisconnectS2CPacket packet))  return;
        if (didLog) {
            ((DisconnectS2CPacketAccessor)(Object) packet).setReason(disconnectReason);
            if (!isActive()) MeteorClient.EVENT_BUS.unsubscribe(this);
            disconnectReason = null;
            didLog = false;
        }
    }
}