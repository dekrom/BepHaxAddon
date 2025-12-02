package bep.hax.mixin;
import bep.hax.modules.InvFix;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.tooltip.BundleTooltipSubmenuHandler;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.BundleContentsComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.BundleItemSelectedC2SPacket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(BundleTooltipSubmenuHandler.class)
public class BundleIssue2b2tHotfixMixin {
    @Unique private static final Logger LOGGER = LoggerFactory.getLogger("BepHax.BundleIssue2b2tHotfixMixin");
    @Shadow @Final private MinecraftClient client;
    @Unique private Integer packetSelectedItemIndex = null;
    @Inject(method = "sendPacket", at = @At("HEAD"))
    public void sendPacketHead(ItemStack item, int slotId, int selectedItemIndex, CallbackInfo info) {
        packetSelectedItemIndex = null;
        InvFix module = Modules.get().get(InvFix.class);
        if(module == null || !module.shouldFixBundles()) return;
        ClientPlayNetworkHandler networkHandler = client.getNetworkHandler();
        if(networkHandler == null || networkHandler.getServerInfo() == null) return;
        String address = networkHandler.getServerInfo().address;
        if(address == null) return;
        if(!address.equalsIgnoreCase("2b2t.org") && !address.toLowerCase().endsWith(".2b2t.org")) return;
        if(!item.contains(DataComponentTypes.BUNDLE_CONTENTS)) return;
        if(selectedItemIndex == -1) return;
        BundleContentsComponent bundleContents = item.get(DataComponentTypes.BUNDLE_CONTENTS);
        if(bundleContents.isEmpty()) return;
        packetSelectedItemIndex = (bundleContents.size()-1) - selectedItemIndex;
    }
    @ModifyArg(method = "sendPacket", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayNetworkHandler;sendPacket(Lnet/minecraft/network/packet/Packet;)V", ordinal = 0))
    public Packet<?> sendPacketAtSetSelectedItem(Packet<?> packet) {
        if(packet instanceof BundleItemSelectedC2SPacket itemSelPacket && packetSelectedItemIndex != null) {
            LOGGER.info("Changed selected bundle index " + itemSelPacket.selectedItemIndex() + " to " + packetSelectedItemIndex);
            return new BundleItemSelectedC2SPacket(itemSelPacket.slotId(), packetSelectedItemIndex);
        } else {
            return packet;
        }
    }
}