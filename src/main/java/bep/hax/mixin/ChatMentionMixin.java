package bep.hax.mixin;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.misc.Notifier;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.MessageIndicator;
import net.minecraft.network.message.MessageSignatureData;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
@Mixin(ChatHud.class)
public class ChatMentionMixin {
    @Shadow
    @Final
    private MinecraftClient client;
    @ModifyVariable(
        method = "addMessage(Lnet/minecraft/text/Text;Lnet/minecraft/network/message/MessageSignatureData;Lnet/minecraft/client/gui/hud/MessageIndicator;)V",
        at = @At("HEAD"),
        argsOnly = true,
        ordinal = 0
    )
    private Text modifyMessage(Text message) {
        if (client.player == null) return message;
        Notifier notifier = Modules.get().get(Notifier.class);
        if (notifier == null || !notifier.isActive()) return message;
        var highlightSetting = notifier.settings.get("highlight-mentions");
        if (highlightSetting == null || !(boolean) highlightSetting.get()) return message;
        String chatMessage = message.getString();
        String playerName = client.player.getName().getString();
        if (chatMessage.toLowerCase().contains(playerName.toLowerCase())) {
            MutableText highlightedMessage = message.copy();
            highlightedMessage.setStyle(message.getStyle().withBold(true));
            var soundSetting = notifier.settings.get("mention-sound");
            if (soundSetting != null && (boolean) soundSetting.get()) {
                var volumeSetting = notifier.settings.get("mention-volume");
                float volume = volumeSetting != null ? ((Double) volumeSetting.get()).floatValue() : 1.0f;
                client.player.playSound(SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(), volume, 1.0f);
            }
            return highlightedMessage;
        }
        return message;
    }
}