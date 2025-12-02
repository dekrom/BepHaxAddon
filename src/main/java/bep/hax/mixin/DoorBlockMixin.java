package bep.hax.mixin;
import net.minecraft.block.Block;
import net.minecraft.block.DoorBlock;
import bep.hax.modules.AutoDoors;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import meteordevelopment.meteorclient.systems.modules.Modules;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(DoorBlock.class)
public class DoorBlockMixin extends Block {
    public DoorBlockMixin(Settings settings) {
        super(settings);
    }
    @Inject(method = "playOpenCloseSound", at = @At("HEAD"), cancellable = true)
    private void mixinPlayOpenCloseSound(CallbackInfo ci) {
        Modules modules = Modules.get();
        if (modules == null) return;
        AutoDoors autoDoors = modules.get(AutoDoors.class);
        if (autoDoors == null) return;
        if (autoDoors.shouldMute()) ci.cancel();
    }
}