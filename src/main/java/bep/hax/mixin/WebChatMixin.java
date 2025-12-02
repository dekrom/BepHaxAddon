package bep.hax.mixin;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import meteordevelopment.meteorclient.systems.modules.Modules;
import bep.hax.modules.WebChat;
import net.minecraft.client.gui.DrawContext;
@Mixin(InGameHud.class)
public abstract class WebChatMixin {
    @Shadow
    private ChatHud chatHud;
    @Inject(method = "renderChat", at = @At("HEAD"), cancellable = true)
    private void onRenderChat(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        Modules modules = Modules.get();
        if (modules != null) {
            WebChat webChat = modules.get(WebChat.class);
            if (webChat != null && webChat.isActive() && webChat.shouldHideInGameChat()) {
                ci.cancel();
            }
        }
    }
}