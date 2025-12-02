package bep.hax.mixin.meteor;
import java.util.UUID;
import net.minecraft.text.Text;
import net.minecraft.text.Style;
import net.minecraft.text.MutableText;
import net.minecraft.util.Formatting;
import bep.hax.util.StardustUtil;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.jetbrains.annotations.Nullable;
import java.util.concurrent.ThreadLocalRandom;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import net.minecraft.util.collection.ArrayListDeque;
import net.minecraft.client.network.PlayerListEntry;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Category;
import net.minecraft.network.packet.s2c.play.PlayerListS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerRemoveS2CPacket;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import meteordevelopment.meteorclient.systems.modules.misc.Notifier;
import net.minecraft.sound.SoundEvents;
import static meteordevelopment.meteorclient.MeteorClient.mc;
@Mixin(value = Notifier.class, remap = false)
public abstract class NotifierMixin extends Module {
    public NotifierMixin(Category category, String name, String desc) {
        super(category, name, desc);
    }
    @Shadow
    @Final
    private SettingGroup sgJoinsLeaves;
    @Shadow
    @Final
    private ArrayListDeque<Text> messageQueue;
    @Unique
    @Nullable
    private Setting<Boolean> greeterNotifications = null;
    @Unique
    @Nullable
    private Setting<StardustUtil.TextFormat> notificationFormatting = null;
    @Unique
    @Nullable
    private Setting<Boolean> highlightMentions = null;
    @Unique
    @Nullable
    private Setting<Boolean> mentionSound = null;
    @Unique
    @Nullable
    private Setting<Double> mentionVolume = null;
    @Unique
    private final String[] prefixGreetings = {
        "Hello", "Hi", "Welcome", "Howdy", "Yo", "Sup", "Greetings", "Hey", "Hola", "Hiya"
    };
    @Unique
    private final String[] suffixGreetings = {
        "joined", "arrived", "showed up", "connected", "appeared", "is here", "came through", "made it", "logged in",
        "logged on", "pulled up"
    };
    @Unique
    private final String[] prefixFarewells = {
        "Goodbye", "Bye", "Sayonara", "Later", "Cya", "Farewell", "Adios", "Catch you later", "So long",
        "Take care", "Toodles", "See ya", "See you later"
    };
    @Unique
    private final String[] suffixFarewells = {
        "left", "disconnected", "peaced out", "logged out", "departed", "signed off", "split", "logged off"
    };
    @Inject(method = "<init>", at = @At(value = "FIELD", target = "Lmeteordevelopment/meteorclient/systems/modules/misc/Notifier;simpleNotifications:Lmeteordevelopment/meteorclient/settings/Setting;", shift = At.Shift.AFTER))
    private void addGreeterSettings(CallbackInfo ci) {
        greeterNotifications = sgJoinsLeaves.add(
            new BoolSetting.Builder()
                .name("greeter-style-notifications")
                .description("Use greeter-style messages for join/leave notifications.")
                .defaultValue(false)
                .build()
        );
        notificationFormatting = sgJoinsLeaves.add(
            new EnumSetting.Builder<StardustUtil.TextFormat>()
                .name("greeter-notification-formatting")
                .description("Which text formatting to apply to greeter-style notifications.")
                .defaultValue(StardustUtil.TextFormat.Italic)
                .visible(() -> greeterNotifications != null && greeterNotifications.get())
                .build()
        );
        highlightMentions = sgJoinsLeaves.add(
            new BoolSetting.Builder()
                .name("highlight-mentions")
                .description("Highlights messages containing your name in bold.")
                .defaultValue(true)
                .build()
        );
        mentionSound = sgJoinsLeaves.add(
            new BoolSetting.Builder()
                .name("mention-sound")
                .description("Play a sound when your name is mentioned.")
                .defaultValue(false)
                .visible(() -> highlightMentions != null && highlightMentions.get())
                .build()
        );
        mentionVolume = sgJoinsLeaves.add(
            new DoubleSetting.Builder()
                .name("mention-volume")
                .description("Volume of the mention sound.")
                .defaultValue(1.0)
                .min(0.0)
                .max(2.0)
                .sliderMin(0.0)
                .sliderMax(2.0)
                .visible(() -> highlightMentions != null && highlightMentions.get() && mentionSound != null && mentionSound.get())
                .build()
        );
    }
    @Inject(method = "createJoinNotifications", at = @At("HEAD"), cancellable = true)
    private void greetPlayers(PlayerListS2CPacket packet, CallbackInfo ci) {
        if (greeterNotifications == null || !greeterNotifications.get()) return;
        ci.cancel();
        for (PlayerListS2CPacket.Entry entry : packet.getPlayerAdditionEntries()) {
            if (entry.profile() == null) continue;
            String name = entry.profile().name();
            String format = notificationFormatting == null ? "§o" : notificationFormatting.get().label;
            int luckyInt = ThreadLocalRandom.current().nextInt(3);
            if (luckyInt == 0) {
                String greeting = suffixGreetings[ThreadLocalRandom.current().nextInt(suffixGreetings.length)];
                messageQueue.addLast(Text.of("§8<"+ StardustUtil.rCC()+"§o✨§r§8> §a" + format + name + " §7" + format + greeting + "§a" + format + "."));
            } else {
                String greeting = prefixGreetings[ThreadLocalRandom.current().nextInt(prefixGreetings.length)];
                messageQueue.addLast(Text.of("§8<"+ StardustUtil.rCC()+"§o✨§r§8> §7" + format + greeting + ", §a" + format + name + "§7" + format + "."));
            }
        }
    }
    @Inject(method = "createLeaveNotification", at = @At("HEAD"), cancellable = true)
    private void bidFarewell(PlayerRemoveS2CPacket packet, CallbackInfo ci) {
        if (mc.getNetworkHandler() == null || greeterNotifications == null || !greeterNotifications.get()) return;
        ci.cancel();
        for (UUID id : packet.profileIds()) {
            PlayerListEntry player = mc.getNetworkHandler().getPlayerListEntry(id);
            if (player == null) continue;
            String name = player.getProfile().name();
            String format = notificationFormatting == null ? "§o" : notificationFormatting.get().label;
            int luckyInt = ThreadLocalRandom.current().nextInt(3);
            if (luckyInt == 0) {
                String farewell = suffixFarewells[ThreadLocalRandom.current().nextInt(suffixFarewells.length)];
                messageQueue.addLast(Text.of("§8<"+ StardustUtil.rCC()+"§o✨§r§8> §c" + format + name + " §7" + format + farewell + "§c" + format + "."));
            } else {
                String farewell = prefixFarewells[ThreadLocalRandom.current().nextInt(prefixFarewells.length)];
                messageQueue.addLast(Text.of("§8<"+ StardustUtil.rCC()+"§o✨§r§8> §7" + format + farewell + ", §c" + format + name + "§7" + format + "."));
            }
        }
    }
}