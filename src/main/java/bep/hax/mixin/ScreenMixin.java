package bep.hax.mixin;
import java.util.Arrays;
import net.minecraft.text.*;
import bep.hax.util.LogUtil;
import bep.hax.modules.AntiToS;
import bep.hax.modules.ChatSigns;
import net.minecraft.util.math.BlockPos;
import net.minecraft.client.gui.Drawable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Mutable;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.injection.At;
import bep.hax.mixin.accessor.StyleAccessor;
import org.spongepowered.asm.mixin.injection.Inject;
import net.minecraft.client.gui.AbstractParentElement;
import meteordevelopment.meteorclient.systems.modules.Modules;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(Screen.class)
public abstract class ScreenMixin extends AbstractParentElement implements Drawable {
    @Shadow
    @Final
    @Mutable
    protected Text title;
    @Inject(method = "render", at = @At("HEAD"))
    private void censorScreenTitles(CallbackInfo ci) {
        Modules mods = Modules.get();
        if (mods == null) return;
        AntiToS tos = mods.get(AntiToS.class);
        if (!tos.isActive() || !tos.containsBlacklistedText(this.title.getString())) return;
        MutableText txt = Text.literal(tos.censorText(this.title.getString()));
        this.title = txt.setStyle(this.title.getStyle());
    }
    @Inject(method = "handleTextClick", at = @At("HEAD"), cancellable = true)
    private void handleClickESP(@Nullable Style style, CallbackInfoReturnable<Boolean> cir) {
    }
}