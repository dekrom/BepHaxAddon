package bep.hax.modules.livemessage.gui;
import bep.hax.modules.livemessage.LiveMessage;
import bep.hax.modules.livemessage.util.LiveProfileCache;
import bep.hax.modules.livemessage.util.LivemessageUtil;
import com.google.gson.Gson;
import meteordevelopment.meteorclient.systems.friends.Friends;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.toast.SystemToast;
import net.minecraft.client.toast.ToastManager;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
public class LivemessageGui extends Screen {
    public LivemessageGui() {
        super(Text.literal("Livemessage"));
        if (client != null) {
            setScl();
        }
    }
    public static boolean buddiesLoaded = false;
    public static List<UUID> chats = new CopyOnWriteArrayList<>();
    public static List<UUID> recentChats = new CopyOnWriteArrayList<>();
    public static Map<UUID, Integer> unreadMessages = new ConcurrentHashMap<>();
    public static List<LiveWindow> liveWindows = new CopyOnWriteArrayList<>();
    public static double sclOrig = 1;
    public static double scl = 1;
    public static int screenHeight = 600;
    public static int screenWidth = 800;
    private LiveWindow activeWindow = null;
    private boolean initialized = false;
    public static void handleBtn(int action) {
        switch (action) {
            case 0:
                liveWindows.get(liveWindows.size() - 1).deactivateWindow();
                liveWindows.add(new ManeWindow());
                break;
        }
    }
    public static void loadBuddies() {
        chats.clear();
        buddiesLoaded = true;
        File folder = LivemessageUtil.MESSAGES_FOLDER.toFile();
        File[] listOfFiles = folder.listFiles();
        if (listOfFiles != null) {
            for (File file : listOfFiles) {
                if (file.isFile()) {
                    UUID uuid = UUID.fromString(file.getName().substring(0, 36));
                    chats.add(uuid);
                }
            }
        }
        Collections.sort(chats);
    }
    public static void markAllAsRead() {
        unreadMessages.clear();
    }
    public void setScl() {
        int guiScale = LiveMessage.INSTANCE.guiScale.get();
        scl = 1.0 / guiScale;
        screenHeight = (int) (client.getWindow().getScaledHeight() / guiScale);
        screenWidth = (int) (client.getWindow().getScaledWidth() / guiScale);
    }
    @Override
    protected void init() {
        setScl();
        loadBuddies();
        if (liveWindows.isEmpty())
            liveWindows.add(new ManeWindow());
    }
    @Override
    public void removed() {
        activeWindow = null;
        for (LiveWindow window : liveWindows) {
            if (window instanceof ChatWindow) {
                ChatWindow chatWindow = (ChatWindow) window;
                if (chatWindow.inputField != null) {
                    chatWindow.inputField.setFocused(false);
                }
            } else if (window instanceof ManeWindow) {
                if (ManeWindow.searchField != null) {
                    ManeWindow.searchField.setFocused(false);
                }
            }
        }
        super.removed();
    }
    public static void openChatWindow(final UUID uuid) {
        if (uuid == null)
            return;
        liveWindows.get(liveWindows.size() - 1).deactivateWindow();
        for (LiveWindow liveWindow : liveWindows) {
            if (!(liveWindow instanceof ChatWindow)) {
                continue;
            }
            final ChatWindow chatWindow = (ChatWindow) liveWindow;
            if (chatWindow.liveProfile.uuid.equals(uuid)) {
                chatWindow.activateWindow();
                liveWindows.removeIf(it -> it == chatWindow);
                liveWindows.add(chatWindow);
                return;
            }
        }
        addChatWindow(new ChatWindow(uuid));
    }
    private static void addChatWindow(ChatWindow chatWindow) {
        if (chatWindow.valid) {
            liveWindows.add(chatWindow);
        } else {
            liveWindows.get(liveWindows.size() - 1).activateWindow();
        }
    }
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        liveWindows.get(liveWindows.size() - 1).mouseWheel((int) verticalAmount);
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }
    @Override
    public boolean mouseDragged(Click click, double deltaX, double deltaY) {
        if (activeWindow != null) {
            int virtualX = (int) (click.x() / LiveMessage.INSTANCE.guiScale.get());
            int virtualY = (int) (click.y() / LiveMessage.INSTANCE.guiScale.get());
            activeWindow.handleMouseDrag(virtualX, virtualY);
        }
        return super.mouseDragged(click, deltaX, deltaY);
    }
    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        if (!liveWindows.isEmpty()) {
            int virtualX = (int) (mouseX / LiveMessage.INSTANCE.guiScale.get());
            int virtualY = (int) (mouseY / LiveMessage.INSTANCE.guiScale.get());
            liveWindows.get(liveWindows.size() - 1).mouseMove(virtualX, virtualY);
        }
        super.mouseMoved(mouseX, mouseY);
    }
    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (!liveWindows.isEmpty()) {
            int guiScale = LiveMessage.INSTANCE.guiScale.get();
            int virtualX = (int) (click.x() / guiScale);
            int virtualY = (int) (click.y() / guiScale);
            int button = click.button();
            LiveWindow clickedWindow = null;
            for (int i = liveWindows.size() - 1; i >= 0; i--) {
                LiveWindow liveWindow = liveWindows.get(i);
                if (liveWindow.mouseInWindow(virtualX, virtualY)) {
                    clickedWindow = liveWindow;
                    if (i != liveWindows.size() - 1) {
                        liveWindows.get(liveWindows.size() - 1).deactivateWindow();
                        liveWindow.activateWindow();
                        liveWindows.remove(i);
                        liveWindows.add(liveWindow);
                    }
                    break;
                }
            }
            if (clickedWindow != null) {
                activeWindow = clickedWindow;
                activeWindow.mouseClicked(virtualX, virtualY, button);
            }
        }
        return super.mouseClicked(click, doubled);
    }
    @Override
    public boolean mouseReleased(Click click) {
        if (activeWindow != null) {
            int virtualX = (int) (click.x() / LiveMessage.INSTANCE.guiScale.get());
            int virtualY = (int) (click.y() / LiveMessage.INSTANCE.guiScale.get());
            activeWindow.mouseReleased(virtualX, virtualY, click.button());
            activeWindow = null;
        }
        return super.mouseReleased(click);
    }
    @Override
    public boolean keyPressed(KeyInput input) {
        if (!liveWindows.isEmpty()) {
            LiveWindow activeWindow = liveWindows.get(liveWindows.size() - 1);
            activeWindow.handleKeyInput(input);
            activeWindow.keyTyped((char) 0, input.key());
        }
        return super.keyPressed(input);
    }
    @Override
    public boolean charTyped(CharInput input) {
        if (!liveWindows.isEmpty()) {
            LiveWindow activeWindow = liveWindows.get(liveWindows.size() - 1);
            activeWindow.handleCharInput(input);
            activeWindow.keyTyped((char) input.codepoint(), 0);
        }
        return super.charTyped(input);
    }
    public static boolean newMessage(String username, String message, boolean sentByMe) {
        LiveProfileCache.LiveProfile profile = LiveProfileCache.getLiveprofileFromName(username);
        if (profile == null) return false;
        final UUID uuid = profile.uuid;
        boolean doHide = false;
        if (uuid != null) {
            try {
                Gson gson = new Gson();
                FileWriter fw = new FileWriter(LivemessageUtil.MESSAGES_FOLDER.resolve(uuid.toString() + ".jsonl").toFile(), true);
                BufferedWriter bw = new BufferedWriter(fw);
                bw.write(gson.toJson(new ChatWindow.ChatMessage(message, sentByMe, System.currentTimeMillis(), MinecraftClient.getInstance().player.getUuid())));
                bw.newLine();
                bw.close();
            } catch (Exception e) {
                bep.hax.modules.livemessage.LiveMessage.LOG.error("Failed to write message to history file for UUID: {}", uuid, e);
            }
            if (!chats.contains(uuid)) {
                chats.add(uuid);
                Collections.sort(chats);
            }
            recentChats.remove(uuid);
            recentChats.add(0, uuid);
            if (recentChats.size() > 10) {
                recentChats.remove(recentChats.size() - 1);
            }
            if (!sentByMe) {
                unreadMessages.put(uuid, unreadMessages.getOrDefault(uuid, 0) + 1);
                if (LiveMessage.INSTANCE.toastsEnabled.get()) {
                    MinecraftClient mc = MinecraftClient.getInstance();
                    ToastManager toastManager = mc.getToastManager();
                    toastManager.add(new SystemToast(
                        SystemToast.Type.NARRATOR_TOGGLE,
                        Text.literal("DM from " + username),
                        Text.literal(message)
                    ));
                }
                if (LiveMessage.INSTANCE.soundsEnabled.get()) {
                    MinecraftClient.getInstance().player.playSound(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
                }
            } else {
                if (LiveMessage.INSTANCE.readOnReply.get())
                    unreadMessages.put(uuid, 0);
            }
            if (LiveMessage.INSTANCE.hideMessages.get())
                doHide = true;
        }
        for (LiveWindow liveWindow : liveWindows) {
            if (!(liveWindow instanceof ChatWindow))
                continue;
            ChatWindow chatWindow = (ChatWindow) liveWindow;
            if (username.equals(chatWindow.liveProfile.username)) {
                chatWindow.chatHistory.add(new ChatWindow.ChatMessage(message, sentByMe, System.currentTimeMillis()));
                break;
            }
        }
        return doHide;
    }
    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        if (LiveMessage.INSTANCE != null && LiveMessage.INSTANCE.enableBlur.get()) {
            super.renderBackground(context, mouseX, mouseY, delta);
        }
    }
    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        float reverseGuiScale = (float) (1f / scl);
        if (LiveMessage.INSTANCE != null && LiveMessage.INSTANCE.enableBlur.get()) {
            boolean shouldDrawBlur = false;
            int blurAlpha = 0;
            for (LiveWindow liveWindow : liveWindows) {
                if (liveWindow instanceof ChatWindow) {
                    ChatWindow chatWindow = (ChatWindow) liveWindow;
                    if (chatWindow.shouldDrawBlur()) {
                        shouldDrawBlur = true;
                        blurAlpha = chatWindow.getBlurAlpha();
                        break;
                    }
                } else if (liveWindow instanceof ManeWindow) {
                    ManeWindow maneWindow = (ManeWindow) liveWindow;
                    if (maneWindow.shouldDrawBlur()) {
                        shouldDrawBlur = true;
                        blurAlpha = maneWindow.getBlurAlpha();
                        break;
                    }
                }
            }
            if (shouldDrawBlur) {
                context.fill(0, 0, screenWidth, screenHeight, GuiUtil.getRGBA(0, 0, 0, blurAlpha));
            }
        }
        context.getMatrices().scale(reverseGuiScale, reverseGuiScale);
        for (LiveWindow liveWindow : liveWindows) {
            liveWindow.preDrawWindow(context);
        }
        for (LiveWindow liveWindow : liveWindows) {
            liveWindow.drawTextFields(context);
        }
        context.getMatrices().scale((float) scl, (float) scl);
    }
    @Override
    public boolean shouldPause() {
        return false;
    }
}