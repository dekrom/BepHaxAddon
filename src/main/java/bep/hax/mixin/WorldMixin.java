package bep.hax.mixin;
import net.minecraft.world.World;
import bep.hax.modules.AutoSmith;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.world.WorldAccess;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import net.minecraft.sound.SoundCategory;
import bep.hax.modules.StashBrander;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import meteordevelopment.meteorclient.systems.modules.Modules;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(World.class)
public abstract class WorldMixin implements WorldAccess, AutoCloseable {
}