package bep.hax.mixin;
import bep.hax.modules.GrimScaffold;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(PlayerEntity.class)
public class GrimScaffoldMixin {
    @Inject(method = "clipAtLedge", at = @At("HEAD"), cancellable = true)
    private void onClipAtLedge(CallbackInfoReturnable<Boolean> cir) {
        GrimScaffold scaffold = Modules.get().get(GrimScaffold.class);
        if (scaffold != null && scaffold.isSafeWalking()) {
            cir.setReturnValue(true);
        }
    }
}