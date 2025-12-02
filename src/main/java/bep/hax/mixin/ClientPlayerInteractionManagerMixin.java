package bep.hax.mixin;
import bep.hax.modules.BepMine;
import bep.hax.modules.RapidFire;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(ClientPlayerInteractionManager.class)
public abstract class ClientPlayerInteractionManagerMixin {
    @Shadow private float currentBreakingProgress;
    @Inject(method = "stopUsingItem", at = @At("HEAD"), cancellable = true)
    private void preventCrossbowUseReset(CallbackInfo ci) {
        Modules mods = Modules.get();
        if (mods == null) return;
        RapidFire rf = mods.get(RapidFire.class);
        if (!rf.isActive() || !rf.charging) return;
        ci.cancel();
    }
    @Inject(method = "updateBlockBreakingProgress", at = @At("HEAD"), cancellable = true)
    private void onUpdateBlockBreakingProgress(BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> cir) {
        BepMine bepMine = Modules.get().get(BepMine.class);
        if (bepMine != null && bepMine.isActive() && bepMine.getModeConfig().get() == BepMine.SpeedmineMode.DAMAGE) {
            if (this.currentBreakingProgress >= bepMine.getSpeedConfig().get().floatValue()) {
                this.currentBreakingProgress = 1.0f;
                cir.setReturnValue(true);
            }
        }
    }
}