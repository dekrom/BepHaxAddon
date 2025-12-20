package bep.hax.mixin.accessor;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
@Mixin(LivingEntity.class)
public interface LivingEntityAccessor {
    @Invoker("getJumpVelocity")
    float invokeGetJumpVelocity();
}