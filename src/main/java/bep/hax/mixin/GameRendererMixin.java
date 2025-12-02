package bep.hax.mixin;
import bep.hax.modules.NoHurtCam;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.RotationAxis;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @Inject(method = "tiltViewWhenHurt", at = @At("HEAD"), cancellable = true)
    private void onTiltViewWhenHurt(MatrixStack matrices, float tickDelta, CallbackInfo ci) {
        Modules modules = Modules.get();
        if (modules == null) return;
        NoHurtCam noHurtCam = modules.get(NoHurtCam.class);
        if (noHurtCam != null && noHurtCam.shouldDisableHurtCam()) {
            ci.cancel();
        }
    }
}