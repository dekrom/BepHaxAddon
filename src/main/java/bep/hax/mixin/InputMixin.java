package bep.hax.mixin;
import bep.hax.accessor.InputAccessor;
import net.minecraft.client.input.Input;
import net.minecraft.util.math.Vec2f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
@Mixin(Input.class)
public abstract class InputMixin implements InputAccessor {
    @Shadow
    public abstract Vec2f getMovementInput();
    @Override
    public float getMovementForward() {
        return this.getMovementInput().y;
    }
    @Override
    public void setMovementForward(float value) {
    }
    @Override
    public float getMovementSideways() {
        return this.getMovementInput().x;
    }
    @Override
    public void setMovementSideways(float value) {
    }
}