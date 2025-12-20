package bep.hax.modules.livemessage.util;
import com.mojang.authlib.GameProfile;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
public class LiveProfileCache {
    public static Map<UUID, LiveProfile> cachedProfiles = new ConcurrentHashMap<>();
    public static Map<String, UUID> cachedNames = new ConcurrentHashMap<>();
    public static Set<UUID> badUUIDs = ConcurrentHashMap.newKeySet();
    public static Set<String> badNames = ConcurrentHashMap.newKeySet();
    public static class LiveProfile {
        public UUID uuid;
        public String username;
        public boolean weakCache;
    }
    private static final Pattern usernamePattern = Pattern.compile("^\\w{3,16}$");
    private static LiveProfile banUUID(UUID uuid) {
        badUUIDs.add(uuid);
        return null;
    }
    private static LiveProfile banName(String name) {
        badNames.add(name);
        return null;
    }
    private static UUID getUUIDFromTab(String username) {
        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.getNetworkHandler() == null) return null;
            for (PlayerListEntry entry : mc.getNetworkHandler().getPlayerList()) {
                GameProfile profile = entry.getProfile();
                if (profile.name().equals(username))
                    return profile.id();
            }
        } catch (Exception e) {
            bep.hax.modules.livemessage.LiveMessage.LOG.error("Failed to get UUID from tab list for username: {}", username, e);
        }
        return null;
    }
    private static String getUsernameFromTab(UUID uuid) {
        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.getNetworkHandler() == null) return null;
            PlayerListEntry entry = mc.getNetworkHandler().getPlayerListEntry(uuid);
            if (entry != null) {
                return entry.getProfile().name();
            }
        } catch (Exception e) {
            bep.hax.modules.livemessage.LiveMessage.LOG.error("Failed to get username from tab list for UUID: {}", uuid, e);
        }
        return null;
    }
    public static LiveProfile getLiveprofileFromUUID(UUID uuid, boolean weak) {
        if (cachedProfiles.containsKey(uuid)) {
            return cachedProfiles.get(uuid);
        }
        if (badUUIDs.contains(uuid))
            return null;
        LivemessageUtil.ChatSettings chatSettings = LivemessageUtil.getChatSettings(uuid);
        LiveProfile liveProfile = new LiveProfile();
        liveProfile.uuid = uuid;
        liveProfile.username = getUsernameFromTab(uuid);
        if (liveProfile.username == null && chatSettings.lastName != null) {
            liveProfile.username = chatSettings.lastName;
            liveProfile.weakCache = true;
        }
        if (liveProfile.username == null) {
            return banUUID(uuid);
        }
        UUID offlineUuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + liveProfile.username.toLowerCase(Locale.ROOT)).getBytes());
        if (!uuid.equals(offlineUuid) && cachedProfiles.containsKey(offlineUuid)) {
            bep.hax.modules.livemessage.LiveMessage.LOG.info("Merging offline UUID {} with real UUID {} for player {}",
                offlineUuid, uuid, liveProfile.username);
            mergeMessageHistories(offlineUuid, uuid);
            int offlineIndex = bep.hax.modules.livemessage.gui.LivemessageGui.recentChats.indexOf(offlineUuid);
            if (offlineIndex >= 0) {
                bep.hax.modules.livemessage.gui.LivemessageGui.recentChats.set(offlineIndex, uuid);
            }
            int chatsIndex = bep.hax.modules.livemessage.gui.LivemessageGui.chats.indexOf(offlineUuid);
            if (chatsIndex >= 0) {
                bep.hax.modules.livemessage.gui.LivemessageGui.chats.remove(chatsIndex);
                if (!bep.hax.modules.livemessage.gui.LivemessageGui.chats.contains(uuid)) {
                    bep.hax.modules.livemessage.gui.LivemessageGui.chats.add(uuid);
                    java.util.Collections.sort(bep.hax.modules.livemessage.gui.LivemessageGui.chats);
                }
            }
            Integer offlineUnreads = bep.hax.modules.livemessage.gui.LivemessageGui.unreadMessages.get(offlineUuid);
            if (offlineUnreads != null && offlineUnreads > 0) {
                int currentUnreads = bep.hax.modules.livemessage.gui.LivemessageGui.unreadMessages.getOrDefault(uuid, 0);
                bep.hax.modules.livemessage.gui.LivemessageGui.unreadMessages.put(uuid, currentUnreads + offlineUnreads);
                bep.hax.modules.livemessage.gui.LivemessageGui.unreadMessages.remove(offlineUuid);
            }
            cachedProfiles.remove(offlineUuid);
            cachedNames.remove(liveProfile.username.toLowerCase(Locale.ROOT));
        }
        if (!liveProfile.username.equals(chatSettings.lastName)) {
            chatSettings.lastName = liveProfile.username;
            LivemessageUtil.saveChatSettings(liveProfile.uuid, chatSettings);
        }
        cachedNames.put(liveProfile.username.toLowerCase(Locale.ROOT), uuid);
        cachedProfiles.put(uuid, liveProfile);
        return liveProfile;
    }
    private static void mergeMessageHistories(UUID offlineUuid, UUID realUuid) {
        java.io.File offlineFile = LivemessageUtil.MESSAGES_FOLDER.resolve(offlineUuid.toString() + ".jsonl").toFile();
        java.io.File realFile = LivemessageUtil.MESSAGES_FOLDER.resolve(realUuid.toString() + ".jsonl").toFile();
        if (offlineFile.exists()) {
            try {
                java.util.List<String> offlineMessages = new java.util.ArrayList<>();
                try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.FileReader(offlineFile))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        offlineMessages.add(line);
                    }
                }
                if (!offlineMessages.isEmpty()) {
                    try (java.io.FileWriter writer = new java.io.FileWriter(realFile, true)) {
                        for (String msg : offlineMessages) {
                            writer.write(msg + "\n");
                        }
                    }
                    bep.hax.modules.livemessage.LiveMessage.LOG.info("Merged {} messages from offline UUID to real UUID", offlineMessages.size());
                }
                offlineFile.delete();
                java.io.File offlineSettings = LivemessageUtil.SETTINGS_FOLDER.resolve(offlineUuid.toString() + ".json").toFile();
                if (offlineSettings.exists()) {
                    offlineSettings.delete();
                }
            } catch (Exception e) {
                bep.hax.modules.livemessage.LiveMessage.LOG.error("Failed to merge message histories", e);
            }
        }
    }
    public static LiveProfile getLiveprofileFromName(String username) {
        String originalUsername = username;
        username = username.toLowerCase(Locale.ROOT);
        if (cachedNames.containsKey(username))
            return cachedProfiles.get(cachedNames.get(username));
        if (badNames.contains(username))
            return null;
        if (!username.matches(usernamePattern.pattern()))
            return banName(username);
        UUID uuid = getUUIDFromTab(originalUsername);
        if (uuid == null) {
            java.io.File[] settingsFiles = LivemessageUtil.SETTINGS_FOLDER.toFile().listFiles();
            if (settingsFiles != null) {
                for (java.io.File file : settingsFiles) {
                    if (file.getName().endsWith(".json")) {
                        try {
                            UUID savedUuid = UUID.fromString(file.getName().substring(0, 36));
                            LivemessageUtil.ChatSettings settings = LivemessageUtil.getChatSettings(savedUuid);
                            if (settings.lastName != null && settings.lastName.equalsIgnoreCase(username)) {
                                uuid = savedUuid;
                                bep.hax.modules.livemessage.LiveMessage.LOG.info("Found saved UUID for offline player: {} -> {}", username, uuid);
                                break;
                            }
                        } catch (Exception e) {
                        }
                    }
                }
            }
        }
        if (uuid == null) {
            java.io.File[] messageFiles = LivemessageUtil.MESSAGES_FOLDER.toFile().listFiles();
            if (messageFiles != null) {
                for (java.io.File file : messageFiles) {
                    if (file.getName().endsWith(".jsonl")) {
                        try {
                            UUID savedUuid = UUID.fromString(file.getName().substring(0, 36));
                            LivemessageUtil.ChatSettings settings = LivemessageUtil.getChatSettings(savedUuid);
                            if (settings.lastName != null && settings.lastName.equalsIgnoreCase(username)) {
                                uuid = savedUuid;
                                bep.hax.modules.livemessage.LiveMessage.LOG.info("Found UUID from message history for offline player: {} -> {}", username, uuid);
                                break;
                            }
                        } catch (Exception e) {
                        }
                    }
                }
            }
        }
        if (uuid == null) {
            uuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + username).getBytes());
            bep.hax.modules.livemessage.LiveMessage.LOG.warn("Player {} not found in tab list or saved data - using offline UUID: {}", username, uuid);
            LiveProfile offlineProfile = new LiveProfile();
            offlineProfile.uuid = uuid;
            offlineProfile.username = originalUsername;
            offlineProfile.weakCache = true;
            LivemessageUtil.ChatSettings settings = new LivemessageUtil.ChatSettings();
            settings.lastName = originalUsername;
            LivemessageUtil.saveChatSettings(uuid, settings);
            cachedNames.put(username, uuid);
            cachedProfiles.put(uuid, offlineProfile);
            return offlineProfile;
        }
        return getLiveprofileFromUUID(uuid, false);
    }
}