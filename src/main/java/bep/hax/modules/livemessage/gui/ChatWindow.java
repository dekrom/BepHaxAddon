package bep.hax.modules.livemessage.gui;
import bep.hax.modules.livemessage.util.LiveProfileCache;
import bep.hax.modules.livemessage.util.LiveProfileCache.LiveProfile;
import bep.hax.modules.livemessage.util.LiveSkinUtil;
import bep.hax.modules.livemessage.util.LivemessageUtil;
import com.google.gson.Gson;
import meteordevelopment.meteorclient.systems.friends.Friends;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import static bep.hax.modules.livemessage.gui.GuiUtil.*;
public class ChatWindow extends LiveWindow {
    boolean valid;
    LiveProfile liveProfile;
    String msgString;
    final int scrollBarWidth = 10;
    int scrollBarHeight = 50;
    int chatScrollPosition = 0;
    boolean scrolling = false;
    public boolean chatScrolledToBottom = true;
    public TextFieldWidget inputField;
    public LivemessageUtil.ChatSettings chatSettings;
    final int chatBoxY = titlebarHeight + 44;
    final int chatBoxX = 5;
    final int chatBoxSize = 13;
    List<ChatMessage> chatHistory = new ArrayList<>();
    List<ClickableLink> clickableLinks = new ArrayList<>();
    LiveSkinUtil liveSkinUtil;
    QuintAnimation hatFade = new QuintAnimation(300, 1f);
    QuintAnimation fullSkinAnim = new QuintAnimation(600, 0f);
    public static class ChatMessage {
        public String message;
        public boolean sentByMe;
        public long timestamp;
        public UUID myUUID;
        ChatMessage(String message, boolean sentByMe, long timestamp) {
            this.message = message;
            this.sentByMe = sentByMe;
            this.timestamp = timestamp;
        }
        ChatMessage(String message, boolean sentByMe, long timestamp, UUID myUUID) {
            this(message, sentByMe, timestamp);
            this.myUUID = myUUID;
        }
    }
    public static class ClickableLink {
        public String url;
        public int x, y, width, height;
        ClickableLink(String url, int x, int y, int width, int height) {
            this.url = url;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }
        public boolean contains(int mouseX, int mouseY) {
            return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
        }
    }
    private static final Pattern URL_PATTERN = Pattern.compile(
        "(https?://[^\\s]+|www\\.[^\\s]+)",
        Pattern.CASE_INSENSITIVE
    );
    ChatWindow(UUID uuid) {
        this(LiveProfileCache.getLiveprofileFromUUID(uuid, false));
    }
    ChatWindow(String username) {
        this(LiveProfileCache.getLiveprofileFromName(username));
    }
    public ChatWindow(LiveProfile liveProfile) {
        if (liveProfile == null) {
            bep.hax.modules.livemessage.LiveMessage.LOG.warn("Tried to open an invalid chat window - offline mode?");
            valid = false;
            return;
        }
        valid = true;
        minw = 280;
        this.w = bep.hax.modules.livemessage.LiveMessage.INSTANCE.defaultChatWidth.get();
        this.h = bep.hax.modules.livemessage.LiveMessage.INSTANCE.defaultChatHeight.get();
        x = Math.min(x, Math.max(0, LivemessageGui.screenWidth - w));
        y = Math.min(y, Math.max(0, LivemessageGui.screenHeight - h));
        this.liveProfile = liveProfile;
        chatSettings = LivemessageUtil.getChatSettings(liveProfile.uuid);
        loadWindowColor();
        loadChatHistory();
        initButtons();
        liveSkinUtil = LiveSkinUtil.get(liveProfile.uuid);
        msgString = "/msg " + liveProfile.username + " ";
        this.inputField = new TextFieldWidget(mc.textRenderer, 9, this.h - 16, this.w - 18, 12, Text.literal(""));
        this.inputField.setMaxLength(256 - msgString.length());
        this.inputField.setDrawsBackground(false);
        this.inputField.setFocused(true);
        this.inputField.setText("");
        this.inputField.setEditableColor(0xFFFFFFFF);
        this.inputField.setUneditableColor(0xFF808080);
        chatScrollPosition = 0;
        chatScrolledToBottom = true;
        animateInStart = System.currentTimeMillis();
    }
    public void initButtons() {
        liveButtons.add(new LiveButton(0, 14, titlebarHeight + 3, 11, 11, true, 0, "Toggle friend/enemy", () -> {
            toggleFriend();
            updateButtonStates();
        }));
        liveButtons.add(new LiveButton(2, 14, titlebarHeight + 3 + 13, 11, 11, true, 2, "Custom color", () -> {
            toggleColor();
            updateButtonStates();
        }));
        liveButtons.add(new LiveButton(3, 14, titlebarHeight + 3 + 26, 11, 11, true, 1, "Ignore player", () -> {
            ignorePlayer();
        }));
        updateButtonStates();
    }
    private void updateButtonStates() {
        Friends friends = Friends.get();
        bep.hax.util.EnemyManager enemyManager = bep.hax.util.EnemyManager.getInstance();
        boolean isFriend = friends.get(liveProfile.username) != null;
        boolean isEnemy = enemyManager.isEnemy(liveProfile.uuid) || enemyManager.isEnemy(liveProfile.username);
        for (LiveButton btn : liveButtons) {
            if (btn.id == 0) {
                btn.iconActive = isFriend || isEnemy;
                if (isFriend) {
                    btn.iconColor = GuiUtil.getRGB(85, 255, 85);
                } else if (isEnemy) {
                    btn.iconColor = GuiUtil.getRGB(255, 85, 85);
                } else {
                    btn.iconColor = -1;
                }
            } else if (btn.id == 2) {
                btn.iconActive = chatSettings.customColor > 0;
            }
        }
    }
    private void ignorePlayer() {
        if (mc.player != null) {
            mc.player.networkHandler.sendChatCommand("ignorehard " + liveProfile.username);
        }
        LivemessageGui.liveWindows.remove(this);
        if (!LivemessageGui.liveWindows.isEmpty()) {
            LivemessageGui.liveWindows.get(LivemessageGui.liveWindows.size() - 1).activateWindow();
        }
    }
    public void toggleFriend() {
        Friends friends = Friends.get();
        if (friends.get(liveProfile.username) != null) {
            friends.remove(friends.get(liveProfile.username));
        } else {
            friends.add(new meteordevelopment.meteorclient.systems.friends.Friend(liveProfile.username));
        }
    }
    private static final int[] PRESET_COLORS = {
        0,
        getRGB(255, 100, 100),
        getRGB(100, 255, 100),
        getRGB(100, 100, 255),
        getRGB(255, 255, 100),
        getRGB(255, 100, 255),
        getRGB(100, 255, 255),
        getRGB(255, 150, 100),
        getRGB(200, 100, 255),
        getRGB(100, 255, 150),
        getRGB(255, 200, 150)
    };
    public void toggleColor() {
        int currentIndex = 0;
        for (int i = 0; i < PRESET_COLORS.length; i++) {
            if (chatSettings.customColor == PRESET_COLORS[i]) {
                currentIndex = i;
                break;
            }
        }
        currentIndex = (currentIndex + 1) % PRESET_COLORS.length;
        chatSettings.customColor = PRESET_COLORS[currentIndex];
        if (chatSettings.customColor > 0) {
            primaryColor = chatSettings.customColor;
        } else {
            primaryColor = GuiUtil.getWindowColor(liveProfile.uuid);
        }
        LivemessageUtil.saveChatSettings(liveProfile.uuid, chatSettings);
        bep.hax.modules.livemessage.LiveMessage.LOG.info("Changed window color for {} to {}", liveProfile.username, chatSettings.customColor);
    }
    public void loadWindowColor() {
        primaryColor = GuiUtil.getWindowColor(liveProfile.uuid);
        bep.hax.modules.livemessage.LiveMessage.LOG.info("Loaded window color for {} = {}", liveProfile.username, primaryColor);
    }
    public void loadChatHistory() {
        Gson gson = new Gson();
        try (BufferedReader reader = new BufferedReader(new FileReader(
            LivemessageUtil.MESSAGES_FOLDER.resolve(liveProfile.uuid.toString() + ".jsonl").toFile()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                try {
                    chatHistory.add(gson.fromJson(line, ChatMessage.class));
                } catch (Exception e) {
                    bep.hax.modules.livemessage.LiveMessage.LOG.error("Failed to parse chat message from history file for UUID: {}", liveProfile.uuid, e);
                }
            }
        } catch (IOException e) {
        }
    }
    public void saveChatMessage(ChatMessage message) {
        Gson gson = new Gson();
        try (FileWriter writer = new FileWriter(
            LivemessageUtil.MESSAGES_FOLDER.resolve(liveProfile.uuid.toString() + ".jsonl").toFile(), true)) {
            writer.write(gson.toJson(message) + "\n");
        } catch (IOException e) {
            bep.hax.modules.livemessage.LiveMessage.LOG.error("Failed to save chat message to history file for UUID: {}", liveProfile.uuid, e);
        }
    }
    public void addMessage(String message, boolean sentByMe) {
        ChatMessage chatMessage = new ChatMessage(message, sentByMe, System.currentTimeMillis(), mc.player.getUuid());
        chatHistory.add(chatMessage);
        saveChatMessage(chatMessage);
        if (!sentByMe && !active) {
            int unreads = LivemessageGui.unreadMessages.getOrDefault(liveProfile.uuid, 0);
            LivemessageGui.unreadMessages.put(liveProfile.uuid, unreads + 1);
        }
    }
    @Override
    public void keyTyped(char typedChar, int keyCode) {
        markAsRead();
        if (keyCode != GLFW.GLFW_KEY_ENTER && keyCode != GLFW.GLFW_KEY_KP_ENTER) {
            if (keyCode == GLFW.GLFW_KEY_PAGE_UP) {
                chatScrollPosition = MathHelper.clamp(chatScrollPosition - 10, 0, Math.max(chatHistory.size() - 1, 0));
            } else if (keyCode == GLFW.GLFW_KEY_PAGE_DOWN) {
                chatScrollPosition = MathHelper.clamp(chatScrollPosition + 10, 0, Math.max(chatHistory.size() - 1, 0));
            } else {
                if (keyCode != 0 && lastKeyInput != null) {
                    this.inputField.keyPressed(lastKeyInput);
                }
                if (typedChar != 0 && lastCharInput != null) {
                    this.inputField.charTyped(lastCharInput);
                }
            }
        } else {
            String s = this.inputField.getText().trim();
            if (!s.isEmpty()) {
                mc.player.networkHandler.sendChatCommand("msg " + liveProfile.username + " " + s);
                this.inputField.setText("");
            }
        }
        super.keyTyped(typedChar, keyCode);
    }
    @Override
    public void mouseWheel(int mWheelState) {
        markAsRead();
        boolean shift = GLFW.glfwGetKey(mc.getWindow().getHandle(), GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS;
        int scrollAmount = (shift ? 10 : 1);
        if (mWheelState < 0) {
            chatScrollPosition = Math.min(chatScrollPosition + scrollAmount, Math.max(chatHistory.size() - 1, 0));
        } else {
            chatScrollPosition = Math.max(chatScrollPosition - scrollAmount, 0);
        }
        super.mouseWheel(mWheelState);
    }
    @Override
    public void mouseReleased(int mouseX, int mouseY, int state) {
        scrolling = false;
        super.mouseReleased(mouseX, mouseY, state);
    }
    @Override
    public void handleMouseDrag(double mouseX, double mouseY) {
        if (scrolling && chatHistory.size() > 1) {
            int totalPixels = (h - (chatBoxY + 10 + chatBoxSize + scrollBarHeight));
            int maxScroll = chatHistory.size() - 1;
            int relativeMouseY = (int)mouseY - (dragY + chatBoxY + this.y);
            chatScrollPosition = (int) MathHelper.clamp((relativeMouseY * maxScroll) / (float) totalPixels, 0, maxScroll);
        } else {
            super.handleMouseDrag(mouseX, mouseY);
        }
    }
    @Override
    public void mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
        if (scrolling && chatHistory.size() > 1) {
            int totalPixels = (h - (chatBoxY + 10 + chatBoxSize + scrollBarHeight));
            int maxScroll = chatHistory.size() - 1;
            int relativeMouseY = mouseY - (dragY + chatBoxY + this.y);
            chatScrollPosition = (int) MathHelper.clamp((relativeMouseY * maxScroll) / (float) totalPixels, 0, maxScroll);
        }
        super.mouseClickMove(mouseX, mouseY, clickedMouseButton, timeSinceLastClick);
    }
    @Override
    public void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        lastMouseX = mouseX;
        lastMouseY = mouseY;
        if (mouseInRect(0, 0, w, h, mouseX, mouseY))
            markAsRead();
        if (mouseButton == 0) {
            for (ClickableLink link : clickableLinks) {
                if (link.contains(mouseX - x, mouseY - y)) {
                    openUrl(link.url);
                    return;
                }
            }
        }
        boolean buttonClicked = false;
        for (LiveButton btn : liveButtons) {
            if (btn.isMouseOver()) {
                bep.hax.modules.livemessage.LiveMessage.LOG.info("Button {} clicked at ({}, {}) - btn pos: ({}, {}) window pos: ({}, {})",
                    btn.id, mouseX, mouseY, btn.gx(), btn.by, x, y);
                try {
                    btn.action.run();
                    buttonClicked = true;
                } catch (Exception e) {
                    bep.hax.modules.livemessage.LiveMessage.LOG.error("Error executing button action for button {}", btn.id, e);
                }
                break;
            }
        }
        if (buttonClicked) {
            return;
        }
        int inputFieldY = h - chatBoxSize - 2;
        if (mouseInRect(chatBoxX, inputFieldY, w - 10, chatBoxSize, mouseX, mouseY)) {
            inputField.setFocused(true);
        } else {
            inputField.setFocused(false);
        }
        if (chatHistory.size() > 1 && mouseInRect(chatBoxX + w - 10 - scrollBarWidth, chatBoxY, scrollBarWidth, h - (chatBoxY + 10 + chatBoxSize), mouseX, mouseY)) {
            scrolling = true;
            int maxScroll = Math.max(0, chatHistory.size() - 1);
            if (maxScroll > 0) {
                int availableScrollArea = h - (chatBoxY + 10 + chatBoxSize) - scrollBarHeight;
                int scrollY = chatBoxY + (availableScrollArea * chatScrollPosition / maxScroll);
                dragY = mouseY - (this.y + scrollY);
            }
        }
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }
    private void openUrl(String url) {
        try {
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                url = "https://" + url;
            }
            net.minecraft.util.Util.getOperatingSystem().open(url);
            bep.hax.modules.livemessage.LiveMessage.LOG.info("Opening URL: {}", url);
        } catch (Exception e) {
            bep.hax.modules.livemessage.LiveMessage.LOG.error("Failed to open URL: {}", url, e);
        }
    }
    @Override
    public void activateWindow() {
        markAsRead();
        super.activateWindow();
    }
    public void markAsRead() {
        LivemessageGui.unreadMessages.put(liveProfile.uuid, 0);
    }
    private void drawChatHistory(DrawContext context, int chatBoxX, int chatBoxY, int chatColorMe, int chatColorOther) {
        clickableLinks.clear();
        if (chatHistory.size() == 0) {
            context.drawText(fontRenderer, "You're chatting with " + liveProfile.username, chatBoxX + 4, chatBoxY + 5, getSingleRGB(96), false);
            chatScrolledToBottom = false;
            return;
        }
        int drawHeight = 0;
        chatScrolledToBottom = true;
        DateFormat dateFormat = new SimpleDateFormat("MMMM dd, yyyy");
        DateFormat timeFormat = new SimpleDateFormat("<HH:mm> ");
        String lastDay = dateFormat.format(new Date(System.currentTimeMillis()));
        for (int i = chatScrollPosition; i < chatHistory.size(); i++) {
            ChatMessage chatMessage = chatHistory.get(i);
            boolean isTrimmed = false;
            String message = chatMessage.message;
            Date timestamp = new Date(chatMessage.timestamp);
            while (true) {
                if (chatBoxY + 5 + 12 * drawHeight > h - 34) {
                    chatScrolledToBottom = false;
                    break;
                }
                String thisDay = dateFormat.format(timestamp);
                if (!thisDay.equals(lastDay)) {
                    lastDay = thisDay;
                    context.drawText(fontRenderer, lastDay, chatBoxX + 4, chatBoxY + 5 + 12 * drawHeight, getSingleRGB(64), false);
                    drawHeight++;
                    continue;
                }
                if (!isTrimmed)
                    message = timeFormat.format(timestamp) + message;
                int maxWidth = w - (chatBoxX * 2 + 8 + (isTrimmed ? fontRenderer.getWidth("<00:00> ") : 0) + scrollBarWidth - 5);
                String trimmed = fontRenderer.trimToWidth(message, maxWidth);
                int baseX = chatBoxX + 4 + (isTrimmed ? fontRenderer.getWidth("<00:00> ") : 0);
                int baseY = chatBoxY + 5 + 12 * drawHeight;
                int baseColor = chatMessage.sentByMe ? chatColorMe : chatColorOther;
                drawTextWithUrls(context, trimmed, baseX, baseY, baseColor);
                drawHeight++;
                if (message.equals(trimmed))
                    break;
                message = message.substring(trimmed.length());
                isTrimmed = true;
            }
            if (!chatScrolledToBottom)
                break;
        }
        if (chatScrolledToBottom && chatBoxY + 5 + 12 * (drawHeight + 2) <= h - 34)
            chatScrolledToBottom = false;
    }
    private void drawTextWithUrls(DrawContext context, String text, int x, int y, int baseColor) {
        Matcher matcher = URL_PATTERN.matcher(text);
        int lastEnd = 0;
        int currentX = x;
        while (matcher.find()) {
            if (matcher.start() > lastEnd) {
                String beforeUrl = text.substring(lastEnd, matcher.start());
                context.drawText(fontRenderer, beforeUrl, currentX, y, baseColor, false);
                currentX += fontRenderer.getWidth(beforeUrl);
            }
            String url = matcher.group();
            int urlWidth = fontRenderer.getWidth(url);
            boolean hovering = lastMouseX >= (x + currentX - x) && lastMouseX <= (x + currentX - x + urlWidth) &&
                               lastMouseY >= y && lastMouseY <= y + fontRenderer.fontHeight;
            int urlColor = hovering ? getRGB(100, 200, 255) : getRGB(85, 170, 255);
            context.drawText(fontRenderer, url, currentX, y, urlColor, true);
            drawRect(context, currentX - x, y + fontRenderer.fontHeight - 1 - (chatBoxY + 5), urlWidth, 1, urlColor);
            clickableLinks.add(new ClickableLink(url, currentX, y, urlWidth, fontRenderer.fontHeight));
            currentX += urlWidth;
            lastEnd = matcher.end();
        }
        if (lastEnd < text.length()) {
            String afterUrl = text.substring(lastEnd);
            context.drawText(fontRenderer, afterUrl, currentX, y, baseColor, false);
        }
        if (lastEnd == 0) {
            context.drawText(fontRenderer, text, x, y, baseColor, false);
        }
    }
    public boolean shouldDrawBlur() {
        boolean removeHat = (lastMouseX > this.x + 5 && lastMouseX < this.x + 37 && lastMouseY > this.y + titlebarHeight + 5 && lastMouseY < this.y + titlebarHeight + 37);
        float progress = fullSkinAnim.animate(removeHat && clicked && !dragging && !resizing && !scrolling ? 1F : 0F);
        return progress > 0;
    }
    public int getBlurAlpha() {
        boolean removeHat = (lastMouseX > this.x + 5 && lastMouseX < this.x + 37 && lastMouseY > this.y + titlebarHeight + 5 && lastMouseY < this.y + titlebarHeight + 37);
        float progress = fullSkinAnim.animate(removeHat && clicked && !dragging && !resizing && !scrolling ? 1F : 0F);
        return (int) (progress * 128f);
    }
    private void drawProfilePic(DrawContext context, int x, int y) {
        boolean removeHat = (lastMouseX > this.x + x && lastMouseX < this.x + x + 32 && lastMouseY > this.y + y && lastMouseY < this.y + y + 32);
        float progress = fullSkinAnim.animate(removeHat && clicked && !dragging && !resizing && !scrolling ? 1F : 0F);
        int displaySize = Math.round(32 + (progress * 224));
        int displayX = Math.round(x - (progress * 32));
        int displayY = Math.round(y - (progress * 32));
        net.minecraft.client.network.PlayerListEntry entry = mc.getNetworkHandler().getPlayerListEntry(liveProfile.uuid);
        if (entry != null) {
            net.minecraft.client.gui.PlayerSkinDrawer.draw(context, entry.getSkinTextures(), displayX, displayY, displaySize);
        }
    }
    @Override
    public void drawWindow(DrawContext context, int bgColor, int fgColor) {
        boolean online = LivemessageUtil.checkOnlineStatus(liveProfile.uuid);
        title = "[DM] " + liveProfile.username;
        int unreads = LivemessageGui.unreadMessages.getOrDefault(liveProfile.uuid, 0);
        if (unreads > 0)
            title += " §l(" + unreads + ")";
        scrollBarHeight = (chatHistory.size() < 2) ? 0 : (int) MathHelper.clamp(
            Math.floor((h - (chatBoxY + 10 + chatBoxSize)) / Math.max((chatHistory.size() - 1) / 10, 1)),
            10, (h - (chatBoxY + 10 + chatBoxSize)) / 2);
        super.drawWindow(context, bgColor, fgColor);
        drawRect(context, 3, titlebarHeight + 3, 36, 36, (online) ? getRGB(60, 148, 100) : getSingleRGB(128));
        if (lastMouseX > x + 40 && lastMouseX < x + 40 + fontRenderer.getWidth(liveProfile.username) + 4 &&
            lastMouseY > y + titlebarHeight + 3 && lastMouseY < y + titlebarHeight + 4 + 12)
            drawRect(context, 40, titlebarHeight + 3, fontRenderer.getWidth(liveProfile.username) + 4, 12, getSingleRGB(64));
        String displayUsername = liveProfile.username;
        int usernameColor = getSingleRGB(255);
        boolean isFriend = Friends.get().get(liveProfile.username) != null;
        bep.hax.util.EnemyManager enemyManager = bep.hax.util.EnemyManager.getInstance();
        boolean isEnemy = enemyManager.isEnemy(liveProfile.uuid) || enemyManager.isEnemy(liveProfile.username);
        if (isFriend) {
            displayUsername += " (friend)";
            usernameColor = getRGB(85, 255, 85);
        } else if (isEnemy) {
            displayUsername += " (enemy)";
            usernameColor = getRGB(255, 85, 85);
        }
        context.drawText(fontRenderer, displayUsername, 42, titlebarHeight + 5, usernameColor, false);
        context.drawText(fontRenderer, liveProfile.uuid.toString(), 42, titlebarHeight + 5 + 11, getSingleRGB(128), false);
        context.drawText(fontRenderer, (online) ? "online" : "offline", 42, titlebarHeight + 5 + 21, getSingleRGB(128), false);
        liveButtons.forEach(btn -> btn.draw(context));
        int chatbg = 36;
        int textbg = 24;
        drawRect(context, chatBoxX - 1, chatBoxY - 1, w - 10 + 2, h - (chatBoxY + 10 + chatBoxSize) + 2, getSingleRGB(64));
        drawRect(context, chatBoxX, chatBoxY, w - 10, h - (chatBoxY + 10 + chatBoxSize), getSingleRGB(chatbg));
        int inputBorderColor = online ? getSingleRGB(64) : getRGB(200, 50, 50);
        int inputBgColor = online ? getSingleRGB(textbg) : getRGB(40, 20, 20);
        drawRect(context, chatBoxX - 1, chatBoxY - 1 + h - (chatBoxY + 5 + chatBoxSize), w - 10 + 2, chatBoxSize + 2, inputBorderColor);
        drawRect(context, chatBoxX, chatBoxY + h - (chatBoxY + 5 + chatBoxSize), w - 10, chatBoxSize, inputBgColor);
        if (!online) {
            String warningIcon = "§l!";
            int iconX = chatBoxX + w - 10 - fontRenderer.getWidth(warningIcon) - 3;
            int iconY = chatBoxY + h - (chatBoxY + 5 + chatBoxSize) + 2;
            context.drawText(fontRenderer, warningIcon, iconX + 1, iconY, getRGB(100, 20, 20), false);
            context.drawText(fontRenderer, warningIcon, iconX, iconY, getRGB(255, 85, 85), false);
        }
        if (chatHistory.size() > 1) {
            int maxScroll = Math.max(0, chatHistory.size() - 1);
            int availableScrollArea = h - (chatBoxY + 10 + chatBoxSize) - scrollBarHeight;
            int scrollY = chatBoxY + (maxScroll > 0 ? (availableScrollArea * chatScrollPosition / maxScroll) : 0);
            drawRect(context, chatBoxX + w - 10 - scrollBarWidth,
                scrollY,
                scrollBarWidth, scrollBarHeight,
                (scrolling) ? getSingleRGB(128) :
                    (mouseInRect(chatBoxX + w - 10 - scrollBarWidth, chatBoxY, scrollBarWidth, h - (chatBoxY + 10 + chatBoxSize), lastMouseX, lastMouseY)) ?
                        getSingleRGB(96) : getSingleRGB(64));
        }
        int otherPlayerColor;
        if (isFriend) {
            otherPlayerColor = getRGB(85, 255, 85);
        } else if (isEnemy) {
            otherPlayerColor = getRGB(255, 85, 85);
        } else {
            otherPlayerColor = fgColor;
        }
        drawChatHistory(context, chatBoxX, chatBoxY, getSingleRGB(255), otherPlayerColor);
        drawProfilePic(context, 5, titlebarHeight + 5);
        liveButtons.forEach(btn -> btn.drawTooltips(context));
    }
    @Override
    public void drawTextFields(DrawContext context) {
        context.getMatrices().translate((float)x, (float)y);
        this.inputField.setEditableColor(active ? 0xFFFFFFFF : 0xFF808080);
        this.inputField.setX(8);
        this.inputField.setY(this.h - chatBoxSize - 2);
        this.inputField.setWidth(this.w - 18);
        this.inputField.render(context, lastMouseX - x, lastMouseY - y, 0);
        context.getMatrices().translate((float)-x, (float)-y);
    }
}