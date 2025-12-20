package bep.hax.mixin.accessor;
import net.minecraft.network.packet.BundlePacket;
import net.minecraft.network.packet.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(BundlePacket.class)
public interface BundleS2CPacketAccessor {
    @Accessor("packets")
    @Mutable
    void setPackets(Iterable<Packet<?>> iterable);
}