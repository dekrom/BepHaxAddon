package bep.hax.modules;
import bep.hax.Bep;
import meteordevelopment.meteorclient.events.game.GameJoinedEvent;
import meteordevelopment.meteorclient.events.game.OpenScreenEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screen.DeathScreen;
import net.minecraft.network.packet.s2c.play.HealthUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.DeathMessageS2CPacket;
public class GhostMode extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final Setting<Boolean> fullFood = sgGeneral.add(new BoolSetting.Builder()
        .name("full-food")
        .description("Sets the food level client-side to max.")
        .defaultValue(true)
        .build()
    );
    private final Setting<Boolean> maintainHealth = sgGeneral.add(new BoolSetting.Builder()
        .name("maintain-health")
        .description("Maintains health at a specific value to prevent issues.")
        .defaultValue(true)
        .build()
    );
    private final Setting<Double> healthValue = sgGeneral.add(new DoubleSetting.Builder()
        .name("health-value")
        .description("Health value to maintain while in ghost mode.")
        .defaultValue(20)
        .min(1)
        .max(20)
        .sliderMin(1)
        .sliderMax(20)
        .visible(maintainHealth::get)
        .build()
    );
    private final Setting<Boolean> blockDeathPackets = sgGeneral.add(new BoolSetting.Builder()
        .name("block-death-packets")
        .description("Blocks death-related packets from the server.")
        .defaultValue(false)
        .build()
    );
    public GhostMode() {
        super(Bep.CATEGORY, "ghost-mode", "Allows you to keep playing after you die. Works on Forge, Fabric and Vanilla servers.");
    }
    private boolean active = false;
    @Override
    public void onDeactivate() {
        super.onDeactivate();
        active = false;
        warning("You are no longer in a ghost mode!");
        if (mc.player != null && mc.player.networkHandler != null) {
            mc.player.requestRespawn();
            info("Respawn request has been sent to the server.");
        }
    }
    @EventHandler
    private void onGameJoin(GameJoinedEvent event) {
        active = false;
    }
    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (!active) return;
        if (maintainHealth.get()) {
            float targetHealth = healthValue.get().floatValue();
            if (mc.player.getHealth() <= 0f || mc.player.getHealth() != targetHealth) {
                mc.player.setHealth(targetHealth);
            }
        } else if (mc.player.getHealth() <= 0f) {
            mc.player.setHealth(1f);
        }
        if (fullFood.get() && mc.player.getHungerManager().getFoodLevel() < 20) {
            mc.player.getHungerManager().setFoodLevel(20);
        }
        if (mc.player.getAbilities().flying && !mc.player.getAbilities().allowFlying) {
            mc.player.getAbilities().flying = false;
        }
        if (mc.player.isDead()) {
            mc.player.setHealth(maintainHealth.get() ? healthValue.get().floatValue() : 1f);
        }
    }
    @EventHandler
    private void onOpenScreen(OpenScreenEvent event) {
        if (event.screen instanceof DeathScreen) {
            event.cancel();
            if (!active) {
                active = true;
                info("You are now in ghost mode. Toggle off to respawn.");
            }
        }
    }
    @EventHandler
    private void onReceivePacket(PacketEvent.Receive event) {
        if (!active) return;
        if (blockDeathPackets.get() && event.packet instanceof HealthUpdateS2CPacket packet) {
            try {
                var healthField = packet.getClass().getDeclaredField("health");
                healthField.setAccessible(true);
                float health = healthField.getFloat(packet);
                if (health <= 0) {
                    event.cancel();
                    if (mc.player != null) {
                        mc.player.setHealth(maintainHealth.get() ? healthValue.get().floatValue() : 1f);
                    }
                }
            } catch (Exception e) {
            }
        }
        if (blockDeathPackets.get() && event.packet instanceof DeathMessageS2CPacket) {
            event.cancel();
        }
    }
}