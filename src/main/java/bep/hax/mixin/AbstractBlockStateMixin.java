package bep.hax.mixin;
import bep.hax.modules.BepMine;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.block.AbstractBlock;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(AbstractBlock.AbstractBlockState.class)
public abstract class AbstractBlockStateMixin {
    @Inject(method = "calcBlockBreakingDelta", at = @At("RETURN"), cancellable = true)
    private void onCalcBlockBreakingDelta(PlayerEntity player, BlockView world, BlockPos pos, CallbackInfoReturnable<Float> info) {
        BepMine bepMine = Modules.get().get(BepMine.class);
        if (bepMine != null && bepMine.isActive()) {
            if (bepMine.getModeConfig().get() == BepMine.SpeedmineMode.DAMAGE) {
                float originalDelta = info.getReturnValueF();
                float speedMultiplier = 1.0f / bepMine.getSpeedConfig().get().floatValue();
                info.setReturnValue(originalDelta * speedMultiplier);
            }
        }
    }
}