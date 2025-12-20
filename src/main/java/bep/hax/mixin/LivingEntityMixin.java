package bep.hax.mixin;
import bep.hax.modules.ElytraFlyPlusPlus;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.brain.Brain;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import static meteordevelopment.meteorclient.MeteorClient.mc;
import static meteordevelopment.meteorclient.utils.player.ChatUtils.info;
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin
{
    @Shadow
    private int jumpingCooldown;
    @Shadow
    public abstract Brain<?> getBrain();
    private Module noJumpDelay;
    private ElytraFlyPlusPlus efly;
    private Module getNoJumpDelay() {
        if (noJumpDelay == null) noJumpDelay = Modules.get().get(bep.hax.modules.NoJumpDelay.class);
        return noJumpDelay;
    }
    private ElytraFlyPlusPlus getEfly() {
        if (efly == null) efly = Modules.get().get(ElytraFlyPlusPlus.class);
        return efly;
    }
    @Inject(at = @At("HEAD"), method = "Lnet/minecraft/entity/LivingEntity;tickMovement()V")
    private void tickMovement(CallbackInfo ci)
    {
        ElytraFlyPlusPlus eflyModule = getEfly();
        Module noJumpDelayModule = getNoJumpDelay();
        if (mc.player != null && mc.player.getBrain().equals(this.getBrain()) && eflyModule != null && eflyModule.enabled() || noJumpDelayModule != null && noJumpDelayModule.isActive())
        {
            this.jumpingCooldown = 0;
        }
    }
    @Inject(at = @At("HEAD"), method = "Lnet/minecraft/entity/LivingEntity;isGliding()Z", cancellable = true)
    private void isGliding(CallbackInfoReturnable<Boolean> cir)
    {
        ElytraFlyPlusPlus eflyModule = getEfly();
        if (mc.player != null && mc.player.getBrain().equals(this.getBrain()) && eflyModule != null && eflyModule.enabled() && !eflyModule.isFakeFlyEnabled())
        {
            cir.setReturnValue(true);
        }
    }
}