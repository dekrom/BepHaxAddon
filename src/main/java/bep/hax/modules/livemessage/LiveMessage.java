package bep.hax.modules.livemessage;
import bep.hax.Bep;
import bep.hax.modules.livemessage.gui.LivemessageGui;
import meteordevelopment.meteorclient.events.game.OpenScreenEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.misc.Keybind;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screen.ChatScreen;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
public class LiveMessage extends Module {
    public static final Logger LOG = LoggerFactory.getLogger("Livemessage");
    public static LiveMessage INSTANCE;
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgNotifications = settings.createGroup("Notifications");
    private final SettingGroup sgPatterns = settings.createGroup("Patterns");
    public final Setting<Keybind> openGuiKey = sgGeneral.add(new KeybindSetting.Builder()
        .name("open-gui-key")
        .description("Keybind to open Livemessage GUI.")
        .defaultValue(Keybind.fromKey(GLFW.GLFW_KEY_Y))
        .action(this::openGui)
        .build()
    );
    public final Setting<Boolean> hideMessages = sgGeneral.add(new BoolSetting.Builder()
        .name("hide-messages")
        .description("Hide DMs from main chat.")
        .defaultValue(true)
        .build()
    );
    public final Setting<Boolean> readOnReply = sgGeneral.add(new BoolSetting.Builder()
        .name("read-on-reply")
        .description("Mark messages as read when you reply.")
        .defaultValue(true)
        .build()
    );
    public final Setting<Integer> guiScale = sgGeneral.add(new IntSetting.Builder()
        .name("gui-scale")
        .description("GUI scale for Livemessage windows.")
        .defaultValue(2)
        .min(1)
        .max(8)
        .sliderMin(1)
        .sliderMax(8)
        .build()
    );
    public final Setting<Boolean> enableBlur = sgGeneral.add(new BoolSetting.Builder()
        .name("enable-blur")
        .description("Enable blur overlay when profile picture is enlarged.")
        .defaultValue(false)
        .build()
    );
    public final Setting<Integer> defaultChatWidth = sgGeneral.add(new IntSetting.Builder()
        .name("default-chat-width")
        .description("Default width of chat windows.")
        .defaultValue(400)
        .min(200)
        .max(1000)
        .sliderMin(200)
        .sliderMax(800)
        .build()
    );
    public final Setting<Integer> defaultChatHeight = sgGeneral.add(new IntSetting.Builder()
        .name("default-chat-height")
        .description("Default height of chat windows.")
        .defaultValue(300)
        .min(150)
        .max(800)
        .sliderMin(150)
        .sliderMax(600)
        .build()
    );
    public final Setting<Boolean> openOnChatKey = sgGeneral.add(new BoolSetting.Builder()
        .name("open-on-chat-key")
        .description("Open Livemessage GUI when pressing the chat key.")
        .defaultValue(true)
        .build()
    );
    public final Setting<Boolean> toastsEnabled = sgNotifications.add(new BoolSetting.Builder()
        .name("toasts")
        .description("Show toast notifications for new DMs.")
        .defaultValue(true)
        .build()
    );
    public final Setting<Boolean> soundsEnabled = sgNotifications.add(new BoolSetting.Builder()
        .name("sounds")
        .description("Play notification sounds for new DMs.")
        .defaultValue(true)
        .build()
    );
    public final Setting<String> fromPattern1 = sgPatterns.add(new StringSetting.Builder()
        .name("from-pattern-1")
        .description("Regex pattern for incoming DMs (group 1: username, group 2: message).")
        .defaultValue("^(?:<[^>]+> )?From (\\\\w{3,16}): (.*)")
        .build()
    );
    public final Setting<String> fromPattern2 = sgPatterns.add(new StringSetting.Builder()
        .name("from-pattern-2")
        .description("Regex pattern for incoming DMs (group 1: username, group 2: message).")
        .defaultValue("^(?:<[^>]+> )?from (\\\\w{3,16}): (.*)")
        .build()
    );
    public final Setting<String> fromPattern3 = sgPatterns.add(new StringSetting.Builder()
        .name("from-pattern-3")
        .description("Regex pattern for incoming DMs (group 1: username, group 2: message).")
        .defaultValue("^(?:<[^>]+> )?(\\\\w{3,16}) whispers: (.*)")
        .build()
    );
    public final Setting<String> fromPattern4 = sgPatterns.add(new StringSetting.Builder()
        .name("from-pattern-4")
        .description("Regex pattern for incoming DMs (group 1: username, group 2: message).")
        .defaultValue("^(?:<[^>]+> )?\\\\[(\\\\w{3,16}) -> me\\\\] (.*)")
        .build()
    );
    public final Setting<String> toPattern1 = sgPatterns.add(new StringSetting.Builder()
        .name("to-pattern-1")
        .description("Regex pattern for outgoing DMs (group 1: username, group 2: message).")
        .defaultValue("^(?:<[^>]+> )?To (\\\\w{3,16}): (.*)")
        .build()
    );
    public final Setting<String> toPattern2 = sgPatterns.add(new StringSetting.Builder()
        .name("to-pattern-2")
        .description("Regex pattern for outgoing DMs (group 1: username, group 2: message).")
        .defaultValue("^(?:<[^>]+> )?to (\\\\w{3,16}): (.*)")
        .build()
    );
    public final Setting<String> toPattern3 = sgPatterns.add(new StringSetting.Builder()
        .name("to-pattern-3")
        .description("Regex pattern for outgoing DMs (group 1: username, group 2: message).")
        .defaultValue("^(?:<[^>]+> )?\\\\[me -> (\\\\w{3,16})\\\\] (.*)")
        .build()
    );
    public LiveMessage() {
        super(Bep.CATEGORY, "livemessage", "Advanced DM management system with GUI.");
        INSTANCE = this;
    }
    @Override
    public void onActivate() {
        bep.hax.modules.livemessage.util.LivemessageUtil.initFolders();
    }
    @EventHandler
    private void onOpenScreen(OpenScreenEvent event) {
        if (!openOnChatKey.get()) return;
        if (event.screen instanceof ChatScreen) {
            event.cancel();
            mc.setScreen(new LivemessageGui());
        }
    }
    public void openGui() {
        if (mc.currentScreen == null) {
            mc.setScreen(new LivemessageGui());
        } else if (mc.currentScreen instanceof LivemessageGui) {
            LivemessageGui gui = (LivemessageGui) mc.currentScreen;
            boolean anyFieldFocused = false;
            for (bep.hax.modules.livemessage.gui.LiveWindow window : gui.liveWindows) {
                if (window instanceof bep.hax.modules.livemessage.gui.ChatWindow) {
                    bep.hax.modules.livemessage.gui.ChatWindow chatWindow = (bep.hax.modules.livemessage.gui.ChatWindow) window;
                    if (chatWindow.inputField != null && chatWindow.inputField.isFocused()) {
                        anyFieldFocused = true;
                        break;
                    }
                } else if (window instanceof bep.hax.modules.livemessage.gui.ManeWindow) {
                    bep.hax.modules.livemessage.gui.ManeWindow maneWindow = (bep.hax.modules.livemessage.gui.ManeWindow) window;
                    if (maneWindow.searchField != null && maneWindow.searchField.isFocused()) {
                        anyFieldFocused = true;
                        break;
                    }
                }
            }
            if (!anyFieldFocused) {
                mc.setScreen(null);
            }
        }
    }
}