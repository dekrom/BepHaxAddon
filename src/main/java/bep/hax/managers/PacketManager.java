package bep.hax.managers;
import net.minecraft.item.ItemStack;
import bep.hax.config.StardustConfig;
import net.minecraft.screen.ScreenHandler;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.EventPriority;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.MeteorClient;
import static meteordevelopment.meteorclient.MeteorClient.mc;
import net.minecraft.network.packet.c2s.play.ClickSlotC2SPacket;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import net.minecraft.network.packet.s2c.play.OverlayMessageS2CPacket;
public class PacketManager {
    public PacketManager() {
        MeteorClient.EVENT_BUS.subscribe(this);
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    private void onReceivePacket(PacketEvent.Receive event) {
        if (!Utils.canUpdate()) return;
        if (!StardustConfig.ignoreOverlayMessages.get()) return;
        if (!(event.packet instanceof OverlayMessageS2CPacket packet)) return;
        if (StardustConfig.overlayMessageFilter.get().isEmpty()
            || StardustConfig.overlayMessageFilter.get().stream().allMatch(String::isBlank)) return;
        for (String filter : StardustConfig.overlayMessageFilter.get()) {
            if (filter.isBlank()) continue;
            if (packet.text().getString().equalsIgnoreCase(filter)) {
                event.cancel();
            }
        }
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    private void onSendPacket(PacketEvent.Send event) {
        if (mc.player == null) return;
        if (!StardustConfig.antiInventoryPacketKick.get()) return;
        if (!(event.packet instanceof ClickSlotC2SPacket packet)) return;
    }
}