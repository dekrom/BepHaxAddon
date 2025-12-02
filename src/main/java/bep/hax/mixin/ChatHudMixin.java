package bep.hax.mixin;
import net.minecraft.text.Text;
import bep.hax.modules.AntiToS;
import bep.hax.modules.livemessage.LiveMessage;
import bep.hax.modules.livemessage.gui.LivemessageGui;
import bep.hax.modules.livemessage.util.LivemessageUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.MessageIndicator;
import net.minecraft.network.message.MessageSignatureData;
import net.minecraft.text.MutableText;
import org.spongepowered.asm.mixin.Mixin;
import net.minecraft.client.gui.hud.ChatHud;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import meteordevelopment.meteorclient.systems.modules.Modules;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.regex.Matcher;
@Mixin(ChatHud.class)
public class ChatHudMixin {
    @Inject(method = "addMessage(Lnet/minecraft/text/Text;Lnet/minecraft/network/message/MessageSignatureData;Lnet/minecraft/client/gui/hud/MessageIndicator;)V", at = @At("HEAD"), cancellable = true)
    private void onLivemessageAddMessage(Text message, MessageSignatureData signature, MessageIndicator indicator, CallbackInfo ci) {
        if (!LiveMessage.INSTANCE.isActive()) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;
        String messageText = message.getString();
        for (java.util.regex.Pattern pattern : LivemessageUtil.FROM_PATTERNS) {
            Matcher matcher = pattern.matcher(messageText);
            if (matcher.find()) {
                String username = matcher.group(1);
                String msg = matcher.group(2);
                boolean shouldHide = LivemessageGui.newMessage(username, msg, false);
                if (shouldHide) {
                    ci.cancel();
                }
                return;
            }
        }
        for (java.util.regex.Pattern pattern : LivemessageUtil.TO_PATTERNS) {
            Matcher matcher = pattern.matcher(messageText);
            if (matcher.find()) {
                String username = matcher.group(1);
                String msg = matcher.group(2);
                boolean shouldHide = LivemessageGui.newMessage(username, msg, true);
                if (shouldHide) {
                    ci.cancel();
                }
                return;
            }
        }
    }
    @ModifyVariable(
        method = "addMessage(Lnet/minecraft/text/Text;Lnet/minecraft/network/message/MessageSignatureData;Lnet/minecraft/client/gui/hud/MessageIndicator;)V",
        at = @At("HEAD"),
        argsOnly = true
    )
    private Text censorChatMessage(Text message) {
        Modules modules = Modules.get();
        if (modules == null) return message;
        AntiToS antiToS = modules.get(AntiToS.class);
        if (!antiToS.isActive()) return message;
        MutableText mText = Text.literal(antiToS.censorText(message.getString()));
        return (antiToS.containsBlacklistedText(message.getString()) ? mText.setStyle(message.getStyle()) : message);
    }
    @Inject(method = "addMessage(Lnet/minecraft/text/Text;)V", at = @At("HEAD"), cancellable = true)
    private void maybeCancelAddMessage(Text message, CallbackInfo ci) {
        Modules modules = Modules.get();
        if (modules == null) return;
        AntiToS antiToS = modules.get(AntiToS.class);
        if (!antiToS.isActive()) return;
        if (antiToS.chatMode.get() == AntiToS.ChatMode.Remove && antiToS.containsBlacklistedText(message.getString())) ci.cancel();
    }
}