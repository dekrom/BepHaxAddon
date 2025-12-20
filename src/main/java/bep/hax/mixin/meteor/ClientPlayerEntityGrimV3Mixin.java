package bep.hax.mixin.meteor;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.movement.NoSlow;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
@Mixin(value = ClientPlayerEntity.class, priority = 1100)
public class ClientPlayerEntityGrimV3Mixin {
    @ModifyExpressionValue(
        method = "tickMovement",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;isUsingItem()Z")
    )
    private boolean bephax$allowSlowdownForGrimV3(boolean original) {
        NoSlow noSlow = Modules.get().get(NoSlow.class);
        if (noSlow.isActive() && noSlow.items() && bephax$isGrimV3Enabled(noSlow)) {
            return original;
        }
        if (noSlow.isActive() && noSlow.items()) {
            return false;
        }
        return original;
    }
    @Unique
    private boolean bephax$isGrimV3Enabled(NoSlow noSlow) {
        try {
            var field = noSlow.getClass().getDeclaredField("bephax$grimV3Bypass");
            field.setAccessible(true);
            var setting = field.get(noSlow);
            var getMethod = setting.getClass().getMethod("get");
            Object value = getMethod.invoke(setting);
            return value instanceof Boolean && (Boolean) value;
        } catch (Exception e) {
            return false;
        }
    }
}