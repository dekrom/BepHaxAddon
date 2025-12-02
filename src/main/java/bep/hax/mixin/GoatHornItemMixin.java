package bep.hax.mixin;
import net.minecraft.item.Item;
import bep.hax.modules.Honker;
import net.minecraft.item.GoatHornItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import meteordevelopment.meteorclient.systems.modules.Modules;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(GoatHornItem.class)
public class GoatHornItemMixin extends Item {
    public GoatHornItemMixin(Settings settings) {
        super(settings);
    }
    @Inject(method = "playSound", at = @At("HEAD"), cancellable = true)
    private static void mixinPlaySound(CallbackInfo ci) {
        Modules modules = Modules.get();
        if (modules == null) return;
        Honker honker = modules.get(Honker.class);
        if (honker.shouldMuteHorns()) ci.cancel();
    }
}