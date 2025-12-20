package bep.hax.mixin;
import bep.hax.util.InventoryManager.IPlayerInteractEntityC2SPacket;
import net.minecraft.entity.Entity;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(PlayerInteractEntityC2SPacket.class)
public class PlayerInteractEntityC2SPacketMixin implements IPlayerInteractEntityC2SPacket {
    @Shadow @Final private int entityId;
    @Unique
    private boolean bepHax$isAttackPacket = false;
    @Inject(method = "attack", at = @At("RETURN"))
    private static void onAttack(Entity entity, boolean sneaking, CallbackInfoReturnable<PlayerInteractEntityC2SPacket> cir) {
        PlayerInteractEntityC2SPacket packet = cir.getReturnValue();
        if (packet != null) {
            ((PlayerInteractEntityC2SPacketMixin)(Object)packet).bepHax$isAttackPacket = true;
        }
    }
    @Override
    public boolean isAttackPacket() {
        return bepHax$isAttackPacket;
    }
    @Override
    public int getTargetEntityId() {
        return entityId;
    }
}