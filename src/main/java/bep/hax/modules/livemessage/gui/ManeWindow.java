package bep.hax.modules.livemessage.gui;
import bep.hax.modules.livemessage.util.LiveProfileCache;
import bep.hax.modules.livemessage.util.LiveProfileCache.LiveProfile;
import bep.hax.modules.livemessage.util.LiveSkinUtil;
import bep.hax.modules.livemessage.util.LivemessageUtil;
import com.mojang.authlib.GameProfile;
import meteordevelopment.meteorclient.systems.friends.Friends;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;
import java.util.*;
import static bep.hax.modules.livemessage.gui.GuiUtil.*;
public class ManeWindow extends LiveWindow {
    LiveProfile liveProfile;
    LiveSkinUtil liveSkinUtil;
    QuintAnimation hatFade = new QuintAnimation(300, 1f);
    QuintAnimation fullSkinAnim = new QuintAnimation(600, 0f);
    final int scrollBarWidth = 10;
    int scrollBarHeight = 50;
    static int listScrollPosition = 0;
    boolean scrolling = false;
    public static TextFieldWidget searchField;
    public static List<BuddyListEntry> buddyListEntries = new ArrayList<>();
    final int buddyListX = 5;
    final int buddyListY = titlebarHeight + 44;
    final int footer = 13;
    private static int mainWindowColor = 0;
    ManeWindow() {
        liveProfile = new LiveProfile();
        liveProfile.username = mc.player.getName().getString();
        liveProfile.uuid = mc.player.getUuid();
        liveSkinUtil = LiveSkinUtil.get(liveProfile.uuid);
        closeButton = false;
        loadMainWindowColor();
        this.searchField = new TextFieldWidget(mc.textRenderer, 9, this.h - 16, this.w - 18, 12, Text.literal(""));
        this.searchField.setMaxLength(16);
        this.searchField.setDrawsBackground(false);
        this.searchField.setFocused(true);
        this.searchField.setText("");
        this.searchField.setEditableColor(0xFFFFFFFF);
        this.searchField.setUneditableColor(0xFF808080);
        initButtons();
    }
    private void loadMainWindowColor() {
        try {
            java.io.File settingsFile = LivemessageUtil.LIVEMESSAGE_FOLDER.resolve("mainwindow.json").toFile();
            if (settingsFile.exists()) {
                com.google.gson.Gson gson = new com.google.gson.Gson();
                com.google.gson.JsonObject json = gson.fromJson(new java.io.FileReader(settingsFile), com.google.gson.JsonObject.class);
                if (json.has("customColor")) {
                    mainWindowColor = json.get("customColor").getAsInt();
                    primaryColor = mainWindowColor > 0 ? mainWindowColor : GuiUtil.getWindowColor(mc.player.getUuid());
                }
            }
        } catch (Exception e) {
        }
    }
    private void saveMainWindowColor() {
        try {
            java.io.File settingsFile = LivemessageUtil.LIVEMESSAGE_FOLDER.resolve("mainwindow.json").toFile();
            com.google.gson.Gson gson = new com.google.gson.GsonBuilder().setPrettyPrinting().create();
            com.google.gson.JsonObject json = new com.google.gson.JsonObject();
            json.addProperty("customColor", mainWindowColor);
            java.io.FileWriter writer = new java.io.FileWriter(settingsFile);
            gson.toJson(json, writer);
            writer.close();
        } catch (Exception e) {
            bep.hax.modules.livemessage.LiveMessage.LOG.error("Failed to save main window color", e);
        }
    }
    private static final int[] PRESET_COLORS = {
        0,
        GuiUtil.getRGB(255, 100, 100),
        GuiUtil.getRGB(100, 255, 100),
        GuiUtil.getRGB(100, 100, 255),
        GuiUtil.getRGB(255, 255, 100),
        GuiUtil.getRGB(255, 100, 255),
        GuiUtil.getRGB(100, 255, 255),
        GuiUtil.getRGB(255, 150, 100),
        GuiUtil.getRGB(200, 100, 255),
        GuiUtil.getRGB(100, 255, 150),
        GuiUtil.getRGB(255, 200, 150)
    };
    public void toggleMainWindowColor() {
        int currentIndex = 0;
        for (int i = 0; i < PRESET_COLORS.length; i++) {
            if (mainWindowColor == PRESET_COLORS[i]) {
                currentIndex = i;
                break;
            }
        }
        currentIndex = (currentIndex + 1) % PRESET_COLORS.length;
        mainWindowColor = PRESET_COLORS[currentIndex];
        primaryColor = mainWindowColor > 0 ? mainWindowColor : GuiUtil.getWindowColor(mc.player.getUuid());
        saveMainWindowColor();
        updateButtonStates();
    }
    public void initButtons() {
        liveButtons.add(new LiveButton(0, 14, titlebarHeight + 3 + 13, 11, 11, true, 2, "Custom color", () -> {
            toggleMainWindowColor();
        }));
    }
    private void updateButtonStates() {
        for (LiveButton btn : liveButtons) {
            if (btn.id == 0) {
                btn.iconActive = mainWindowColor > 0;
            }
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
    private void drawProfilePic(DrawContext context, int x, int y, java.util.UUID uuid) {
        boolean removeHat = (lastMouseX > this.x + x && lastMouseX < this.x + x + 32 && lastMouseY > this.y + y && lastMouseY < this.y + y + 32);
        float progress = fullSkinAnim.animate(removeHat && clicked && !dragging && !resizing && !scrolling ? 1F : 0F);
        int displaySize = Math.round(32 + (progress * 224));
        int displayX = Math.round(x - (progress * 32));
        int displayY = Math.round(y - (progress * 32));
        net.minecraft.client.network.PlayerListEntry entry = mc.getNetworkHandler().getPlayerListEntry(uuid);
        if (entry != null) {
            net.minecraft.client.gui.PlayerSkinDrawer.draw(context, entry.getSkinTextures(), displayX, displayY, displaySize);
        }
    }
    @Override
    public void keyTyped(char typedChar, int keyCode) {
        int maxVisibleLines = (h - (buddyListY + footer + 15)) / 12;
        int maxScroll = Math.max(0, buddyListEntries.size() - maxVisibleLines);
        if (keyCode == GLFW.GLFW_KEY_PAGE_UP) {
            listScrollPosition = Math.max(0, listScrollPosition - 10);
        } else if (keyCode == GLFW.GLFW_KEY_PAGE_DOWN) {
            listScrollPosition = Math.min(maxScroll, listScrollPosition + 10);
        } else {
            if (keyCode != 0 && lastKeyInput != null) {
                this.searchField.keyPressed(lastKeyInput);
            }
            if (typedChar != 0 && lastCharInput != null) {
                this.searchField.charTyped(lastCharInput);
            }
        }
        super.keyTyped(typedChar, keyCode);
    }
    @Override
    public void mouseReleased(int mouseX, int mouseY, int state) {
        scrolling = false;
        super.mouseReleased(mouseX, mouseY, state);
    }
    @Override
    public void mouseWheel(int mWheelState) {
        int maxVisibleLines = (h - (buddyListY + footer + 15)) / 12;
        int maxScroll = Math.max(0, buddyListEntries.size() - maxVisibleLines);
        boolean shift = GLFW.glfwGetKey(mc.getWindow().getHandle(), GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS;
        int scrollAmount = (shift ? 5 : 1);
        if (mWheelState < 0) {
            listScrollPosition = Math.min(maxScroll, listScrollPosition + scrollAmount);
        } else {
            listScrollPosition = Math.max(0, listScrollPosition - scrollAmount);
        }
        super.mouseWheel(mWheelState);
    }
    @Override
    public void handleMouseDrag(double mouseX, double mouseY) {
        if (scrolling && buddyListEntries.size() > 1) {
            int maxVisibleLines = (h - (buddyListY + footer + 15)) / 12;
            int maxScroll = Math.max(0, buddyListEntries.size() - maxVisibleLines);
            int availableScrollArea = h - (buddyListY + 10 + footer) - scrollBarHeight;
            int relativeMouseY = (int)mouseY - (dragY + buddyListY + this.y);
            listScrollPosition = (int) MathHelper.clamp((relativeMouseY * maxScroll) / (float) availableScrollArea, 0, maxScroll);
        } else {
            super.handleMouseDrag(mouseX, mouseY);
        }
    }
    public void mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
        if (scrolling && buddyListEntries.size() > 1) {
            int maxVisibleLines = (h - (buddyListY + footer + 15)) / 12;
            int maxScroll = Math.max(0, buddyListEntries.size() - maxVisibleLines);
            int availableScrollArea = h - (buddyListY + 10 + footer) - scrollBarHeight;
            int relativeMouseY = mouseY - (dragY + buddyListY + this.y);
            listScrollPosition = (int) MathHelper.clamp((relativeMouseY * maxScroll) / (float) availableScrollArea, 0, maxScroll);
        }
        super.mouseClickMove(mouseX, mouseY, clickedMouseButton, timeSinceLastClick);
    }
    @Override
    public void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        lastMouseX = mouseX;
        lastMouseY = mouseY;
        boolean buttonClicked = false;
        for (LiveButton btn : liveButtons) {
            if (btn.isMouseOver()) {
                bep.hax.modules.livemessage.LiveMessage.LOG.info("ManeWindow button {} clicked at ({}, {}) - btn pos: ({}, {})",
                    btn.id, mouseX, mouseY, btn.gx(), btn.by);
                btn.action.run();
                buttonClicked = true;
                break;
            }
        }
        if (buttonClicked) {
            return;
        }
        int searchFieldY = h - footer - 5;
        if (mouseInRect(5, searchFieldY, w - 10, footer, mouseX, mouseY)) {
            searchField.setFocused(true);
        } else {
            searchField.setFocused(false);
        }
        int maxVisibleLines = (h - (buddyListY + footer + 15)) / 12;
        int totalEntries = buddyListEntries.size();
        int maxScroll = Math.max(0, totalEntries - maxVisibleLines);
        int availableScrollArea = h - (buddyListY + 10 + footer) - scrollBarHeight;
        boolean needsScrollbar = totalEntries > maxVisibleLines;
        int listClickWidth = needsScrollbar ? (w - 10 - scrollBarWidth) : (w - 10);
        if (needsScrollbar && maxScroll > 0) {
            int scrollY = buddyListY + (availableScrollArea * listScrollPosition / maxScroll);
            int scrollBarX = buddyListX + w - 10 - scrollBarWidth;
            if (mouseInRect(scrollBarX, scrollY, scrollBarWidth, scrollBarHeight, mouseX, mouseY)) {
                scrolling = true;
                dragY = mouseY - (this.y + scrollY);
            }
        }
        if (!scrolling && mouseInRect(buddyListX, buddyListY, listClickWidth, h - (buddyListY + 10 + footer), mouseX, mouseY)) {
            int i = (int) Math.floor((mouseY - buddyListY - this.y - 3) / 12f) + listScrollPosition;
            if (i < buddyListEntries.size() && i >= 0) {
                BuddyListEntry buddyListEntry = buddyListEntries.get(i);
                if (buddyListEntry.uuid != null) {
                    LivemessageGui.openChatWindow(buddyListEntry.uuid);
                }
            }
        }
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }
    public static class BuddyListEntry {
        UUID uuid = null;
        String username;
        boolean online;
        BuddyListEntry(UUID uuid, String username, boolean online) {
            this.uuid = uuid;
            this.username = username;
            this.online = online;
        }
        BuddyListEntry(String username) {
            this.username = username;
            this.online = true;
        }
    }
    private static boolean rightMode(int mode, UUID uuid) {
        switch (mode) {
            case 0:
                if (LivemessageGui.unreadMessages.getOrDefault(uuid, 0) == 0)
                    return false;
                break;
            case 1:
                if (LivemessageGui.unreadMessages.getOrDefault(uuid, 0) > 0 || !LivemessageUtil.checkOnlineStatus(uuid))
                    return false;
                break;
            case 2:
                if (LivemessageGui.unreadMessages.getOrDefault(uuid, 0) > 0 || LivemessageUtil.checkOnlineStatus(uuid))
                    return false;
                break;
        }
        return true;
    }
    private static boolean searchFilter(String username) {
        try {
            String searchText = searchField.getText().trim().toLowerCase(Locale.ROOT);
            if (searchText.isEmpty()) {
                return false;
            }
            return !username.toLowerCase(Locale.ROOT).contains(searchText);
        } catch (Exception e) {
            return false;
        }
    }
    private static int lastBuddyListSize = 0;
    public static void generateBuddylist() {
        buddyListEntries.clear();
        Friends friends = Friends.get();
        bep.hax.util.EnemyManager enemyManager = bep.hax.util.EnemyManager.getInstance();
        Map<UUID, String> onlinePlayers = new HashMap<>();
        if (MinecraftClient.getInstance().getNetworkHandler() != null) {
            for (PlayerListEntry entry : MinecraftClient.getInstance().getNetworkHandler().getPlayerList()) {
                GameProfile gameProfile = entry.getProfile();
                UUID uuid = gameProfile.id();
                if (!uuid.equals(MinecraftClient.getInstance().player.getUuid())) {
                    onlinePlayers.put(uuid, gameProfile.name());
                }
            }
        }
        Set<UUID> nearbyPlayerUUIDs = new HashSet<>();
        if (MinecraftClient.getInstance().world != null) {
            for (PlayerEntity player : MinecraftClient.getInstance().world.getPlayers()) {
                if (player != MinecraftClient.getInstance().player) {
                    nearbyPlayerUUIDs.add(player.getUuid());
                }
            }
        }
        Map<UUID, String> allPlayers = new HashMap<>(onlinePlayers);
        for (UUID uuid : LivemessageGui.chats) {
            if (!allPlayers.containsKey(uuid)) {
                LiveProfile profile = LiveProfileCache.getLiveprofileFromUUID(uuid, true);
                if (profile != null) {
                    allPlayers.put(uuid, profile.username);
                }
            }
        }
        Set<UUID> shownUUIDs = new HashSet<>();
        List<BuddyListEntry> recentList = new ArrayList<>();
        for (UUID uuid : LivemessageGui.recentChats) {
            LiveProfile profile = LiveProfileCache.getLiveprofileFromUUID(uuid, true);
            if (profile != null) {
                if (searchFilter(profile.username)) continue;
                boolean online = onlinePlayers.containsKey(uuid);
                recentList.add(new BuddyListEntry(uuid, profile.username, online));
                shownUUIDs.add(uuid);
            }
        }
        if (!recentList.isEmpty()) {
            buddyListEntries.add(new BuddyListEntry("Recent"));
            buddyListEntries.addAll(recentList);
        }
        List<BuddyListEntry> nearbyList = new ArrayList<>();
        for (UUID uuid : nearbyPlayerUUIDs) {
            String username = onlinePlayers.get(uuid);
            if (username != null) {
                if (searchFilter(username)) continue;
                if (shownUUIDs.contains(uuid)) continue;
                nearbyList.add(new BuddyListEntry(uuid, username, true));
                shownUUIDs.add(uuid);
            }
        }
        nearbyList.sort(Comparator.comparing(entry -> entry.username.toLowerCase(Locale.ROOT)));
        if (!nearbyList.isEmpty()) {
            buddyListEntries.add(new BuddyListEntry("Nearby Players"));
            buddyListEntries.addAll(nearbyList);
        }
        List<BuddyListEntry> friendsList = new ArrayList<>();
        List<BuddyListEntry> enemiesList = new ArrayList<>();
        List<BuddyListEntry> neutralsList = new ArrayList<>();
        List<BuddyListEntry> offlineList = new ArrayList<>();
        for (Map.Entry<UUID, String> entry : allPlayers.entrySet()) {
            UUID uuid = entry.getKey();
            String username = entry.getValue();
            if (searchFilter(username)) continue;
            if (shownUUIDs.contains(uuid)) continue;
            boolean online = onlinePlayers.containsKey(uuid);
            boolean isFriend = friends.get(username) != null;
            boolean isEnemy = enemyManager.isEnemy(uuid) || enemyManager.isEnemy(username);
            if (!online) {
                offlineList.add(new BuddyListEntry(uuid, username, false));
            } else {
                if (isFriend) {
                    friendsList.add(new BuddyListEntry(uuid, username, true));
                } else if (isEnemy) {
                    enemiesList.add(new BuddyListEntry(uuid, username, true));
                } else {
                    neutralsList.add(new BuddyListEntry(uuid, username, true));
                }
            }
        }
        Comparator<BuddyListEntry> alphabeticalComparator = Comparator.comparing(entry -> entry.username.toLowerCase(Locale.ROOT));
        friendsList.sort(alphabeticalComparator);
        neutralsList.sort(alphabeticalComparator);
        enemiesList.sort(alphabeticalComparator);
        offlineList.sort(alphabeticalComparator);
        if (!friendsList.isEmpty()) {
            buddyListEntries.add(new BuddyListEntry("Friends"));
            buddyListEntries.addAll(friendsList);
        }
        if (!neutralsList.isEmpty()) {
            buddyListEntries.add(new BuddyListEntry("All Players"));
            buddyListEntries.addAll(neutralsList);
        }
        if (!enemiesList.isEmpty()) {
            buddyListEntries.add(new BuddyListEntry("Enemies"));
            buddyListEntries.addAll(enemiesList);
        }
        if (!offlineList.isEmpty()) {
            buddyListEntries.add(new BuddyListEntry("All Offline"));
            buddyListEntries.addAll(offlineList);
        }
        if (buddyListEntries.isEmpty()) {
            buddyListEntries.add(new BuddyListEntry("No players found"));
        }
        if (Math.abs(buddyListEntries.size() - lastBuddyListSize) > 3) {
            listScrollPosition = 0;
        }
        lastBuddyListSize = buddyListEntries.size();
    }
    public void drawBuddylist(DrawContext context, int availableWidth) {
        int lineHeight = 0;
        Friends friends = Friends.get();
        bep.hax.util.EnemyManager enemyManager = bep.hax.util.EnemyManager.getInstance();
        int maxVisibleLines = (h - (buddyListY + footer + 15)) / 12;
        int maxScroll = Math.max(0, buddyListEntries.size() - maxVisibleLines);
        listScrollPosition = MathHelper.clamp(listScrollPosition, 0, maxScroll);
        for (int i = listScrollPosition; i < buddyListEntries.size(); ++i) {
            if (lineHeight >= maxVisibleLines)
                break;
            BuddyListEntry buddyListEntry = buddyListEntries.get(i);
            int yPos = buddyListY + 5 + 12 * lineHeight;
            if (buddyListEntry.uuid != null) {
                PlayerListEntry tabEntry = mc.getNetworkHandler() != null ?
                    mc.getNetworkHandler().getPlayerListEntry(buddyListEntry.uuid) : null;
                if (tabEntry != null) {
                    net.minecraft.client.gui.PlayerSkinDrawer.draw(context, tabEntry.getSkinTextures(),
                        buddyListX + 5, yPos - 1, 10);
                }
            }
            String buddyText = (buddyListEntry.uuid == null ? "§l" : "     ") + buddyListEntry.username;
            int textColor;
            if (buddyListEntry.uuid != null) {
                boolean isFriend = friends.get(buddyListEntry.username) != null;
                boolean isEnemy = enemyManager.isEnemy(buddyListEntry.uuid) || enemyManager.isEnemy(buddyListEntry.username);
                if (isFriend) {
                    textColor = buddyListEntry.online ? getRGB(85, 255, 85) : getRGB(42, 128, 42);
                } else if (isEnemy) {
                    textColor = buddyListEntry.online ? getRGB(255, 85, 85) : getRGB(128, 42, 42);
                } else {
                    textColor = getSingleRGB(buddyListEntry.online ? 255 : 128);
                }
            } else {
                textColor = getSingleRGB(255);
            }
            int maxTextWidth = availableWidth - 10;
            String clippedText = fontRenderer.trimToWidth(buddyText, maxTextWidth);
            context.drawText(fontRenderer, clippedText, buddyListX + 5, yPos, textColor, false);
            if (buddyListEntry.uuid != null) {
                int unreads = LivemessageGui.unreadMessages.getOrDefault(buddyListEntry.uuid, 0);
                if (unreads > 0) {
                    String unreadString = "(" + unreads + ")";
                    int unreadX = buddyListX + 5 + fontRenderer.getWidth(clippedText + " ");
                    if (unreadX + fontRenderer.getWidth(unreadString) < buddyListX + availableWidth - 5) {
                        context.drawText(fontRenderer, unreadString, unreadX, yPos, getRGB(255, 255, 0), false);
                    }
                }
            }
            lineHeight++;
        }
    }
    @Override
    public void drawWindow(DrawContext context, int bgColor, int fgColor) {
        w = 150;
        title = "Livemessage";
        super.drawWindow(context, bgColor, fgColor);
        drawRect(context, buddyListX - 1, buddyListY - 1, w - 10 + 2, h - (buddyListY + 10 + footer) + 2, getRGB(64, 64, 64));
        drawRect(context, buddyListX, buddyListY, w - buddyListX * 2, h - (buddyListY + 10 + footer), getRGB(36, 36, 36));
        liveButtons.forEach(btn -> btn.draw(context));
        generateBuddylist();
        int maxVisibleLines = (h - (buddyListY + footer + 15)) / 12;
        int totalEntries = buddyListEntries.size();
        boolean needsScrollbar = totalEntries > maxVisibleLines;
        if (needsScrollbar) {
            int availableHeight = h - (buddyListY + 10 + footer);
            scrollBarHeight = Math.max(20, (int) ((float) maxVisibleLines / totalEntries * availableHeight));
        } else {
            scrollBarHeight = 0;
        }
        int listWidth = needsScrollbar ? (w - 10 - scrollBarWidth) : (w - 10);
        if (active && mouseInRect(buddyListX, buddyListY, listWidth, h - (buddyListY + 10 + footer), lastMouseX, lastMouseY)) {
            int i = (int) Math.floor((lastMouseY - buddyListY - this.y - 3) / 12f) + listScrollPosition;
            if (i < buddyListEntries.size() && i >= 0 && (i - listScrollPosition) < maxVisibleLines) {
                BuddyListEntry buddyListEntry = buddyListEntries.get(i);
                if (buddyListEntry.uuid != null)
                    drawRect(context, buddyListX, buddyListY + (i - listScrollPosition) * 12 + 3, listWidth, 12, getRGB(64, 64, 64));
            }
        }
        drawBuddylist(context, listWidth);
        if (needsScrollbar && totalEntries > 1) {
            int availableScrollArea = h - (buddyListY + 10 + footer) - scrollBarHeight;
            int maxScroll = totalEntries - maxVisibleLines;
            int scrollY = buddyListY + (availableScrollArea * listScrollPosition / Math.max(1, maxScroll));
            drawRect(context, buddyListX + w - 10 - scrollBarWidth,
                scrollY,
                scrollBarWidth, scrollBarHeight,
                (scrolling) ? getSingleRGB(128) :
                (mouseInRect(buddyListX + w - 10 - scrollBarWidth, buddyListY, scrollBarWidth, h - (buddyListY + 10 + footer), lastMouseX, lastMouseY)) ?
                getSingleRGB(96) : getSingleRGB(64));
        }
        context.drawText(fontRenderer, liveProfile.username, 42, titlebarHeight + 5, getSingleRGB(255), false);
        context.drawText(fontRenderer, "online", 42, titlebarHeight + 5 + 11, getSingleRGB(128), false);
        drawRect(context, 3, titlebarHeight + 3, 36, 36, getRGB(60, 148, 100));
        drawProfilePic(context, 5, titlebarHeight + 5, liveProfile.uuid);
        drawRect(context, 5 - 1, this.h - footer - 5 - 1, this.w - 10 + 2, footer + 2, getSingleRGB(64));
        drawRect(context, 5, this.h - footer - 5, this.w - 10, footer, getSingleRGB(24));
        if (this.searchField.getText().trim().length() == 0)
            context.drawText(fontRenderer, "Search...", 8, this.h - footer - 2, getSingleRGB(64), false);
        liveButtons.forEach(btn -> btn.drawTooltips(context));
    }
    @Override
    public void drawTextFields(DrawContext context) {
        context.getMatrices().translate((float)x, (float)y);
        this.searchField.setEditableColor(active ? 0xFFFFFFFF : 0xFF808080);
        this.searchField.setX(8);
        this.searchField.setY(this.h - footer - 2);
        this.searchField.setWidth(this.w - 18);
        this.searchField.render(context, lastMouseX - x, lastMouseY - y, 0);
        context.getMatrices().translate((float)-x, (float)-y);
    }
}