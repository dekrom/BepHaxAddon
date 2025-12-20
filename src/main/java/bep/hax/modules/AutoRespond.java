package bep.hax.modules;
import bep.hax.Bep;
import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import java.util.List;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
public class AutoRespond extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgResponses = settings.createGroup("Responses");
    private final SettingGroup sgTiming = settings.createGroup("Timing");
    private final SettingGroup sgFilters = settings.createGroup("Filters");
    private final Setting<String> trigger = sgGeneral.add(new StringSetting.Builder()
        .name("trigger")
        .description("Word or phrase to detect in messages.")
        .defaultValue("bep")
        .build()
    );
    private final Setting<MatchMode> matchMode = sgGeneral.add(new EnumSetting.Builder<MatchMode>()
        .name("match-mode")
        .description("How to match the trigger in messages.")
        .defaultValue(MatchMode.Contains)
        .build()
    );
    private final Setting<Boolean> caseSensitive = sgGeneral.add(new BoolSetting.Builder()
        .name("case-sensitive")
        .description("Whether trigger detection is case sensitive.")
        .defaultValue(false)
        .build()
    );
    private final Setting<List<String>> responses = sgResponses.add(new StringListSetting.Builder()
        .name("responses")
        .description("List of responses. Use the + button to add more.")
        .defaultValue(List.of("bep"))
        .build()
    );
    private final Setting<ResponseMode> responseMode = sgResponses.add(new EnumSetting.Builder<ResponseMode>()
        .name("response-mode")
        .description("How to select responses from the list.")
        .defaultValue(ResponseMode.Random)
        .build()
    );
    private final Setting<Boolean> usePrivateReply = sgResponses.add(new BoolSetting.Builder()
        .name("private-reply")
        .description("Reply to whispers using /msg command.")
        .defaultValue(true)
        .build()
    );
    private final Setting<Integer> cooldown = sgTiming.add(new IntSetting.Builder()
        .name("cooldown")
        .description("Cooldown between responses in seconds.")
        .defaultValue(60)
        .min(0)
        .max(300)
        .sliderMin(0)
        .sliderMax(300)
        .build()
    );
    private final Setting<Integer> minDelay = sgTiming.add(new IntSetting.Builder()
        .name("min-delay")
        .description("Minimum delay before responding in milliseconds.")
        .defaultValue(500)
        .min(0)
        .max(10000)
        .sliderMin(0)
        .sliderMax(5000)
        .build()
    );
    private final Setting<Integer> maxDelay = sgTiming.add(new IntSetting.Builder()
        .name("max-delay")
        .description("Maximum delay before responding in milliseconds.")
        .defaultValue(2000)
        .min(0)
        .max(10000)
        .sliderMin(0)
        .sliderMax(5000)
        .build()
    );
    private final Setting<Boolean> respondToSelf = sgFilters.add(new BoolSetting.Builder()
        .name("respond-to-self")
        .description("Respond to your own messages.")
        .defaultValue(false)
        .build()
    );
    private final Setting<Boolean> respondToWhispers = sgFilters.add(new BoolSetting.Builder()
        .name("respond-to-whispers")
        .description("Respond to private/whisper messages.")
        .defaultValue(true)
        .build()
    );
    private final Setting<Boolean> ignoreServerMessages = sgFilters.add(new BoolSetting.Builder()
        .name("ignore-server-messages")
        .description("Ignore messages from the server (no player name).")
        .defaultValue(false)
        .build()
    );
    private final Setting<Boolean> logTriggers = sgFilters.add(new BoolSetting.Builder()
        .name("log-triggers")
        .description("Log when trigger is detected (for debugging).")
        .defaultValue(false)
        .build()
    );
    private static final Pattern CHAT_PATTERN_2B2T = Pattern.compile("^<\\d{1,2}:\\d{2}> <([a-zA-Z0-9_]{1,16})> (.+)$");
    private static final Pattern WHISPER_PATTERN_2B2T = Pattern.compile("^<\\d{1,2}:\\d{2}> ([a-zA-Z0-9_]{1,16}) whispers: (.+)$");
    private static final Pattern TO_PATTERN_2B2T = Pattern.compile("^<\\d{1,2}:\\d{2}> to ([a-zA-Z0-9_]{1,16}): (.+)$");
    private final Random random = new Random();
    private long lastResponseTime = 0;
    private String pendingResponse = null;
    private String pendingTargetPlayer = null;
    private long responseScheduledTime = 0;
    private int currentResponseIndex = 0;
    public AutoRespond() {
        super(Bep.CATEGORY, "auto-respond", "Automatically respond when someone says a specific word or phrase.");
    }
    @Override
    public void onActivate() {
        lastResponseTime = 0;
        pendingResponse = null;
        pendingTargetPlayer = null;
        responseScheduledTime = 0;
        currentResponseIndex = 0;
    }
    @EventHandler
    private void onReceiveMessage(ReceiveMessageEvent event) {
        if (mc.player == null || mc.world == null) return;
        String messageText = event.getMessage().getString();
        ChatMessage parsedMessage = parseMessage(messageText);
        if (parsedMessage == null) {
            if (logTriggers.get()) {
                info("Failed to parse message: %s", messageText);
            }
            return;
        }
        if (ignoreServerMessages.get() && parsedMessage.playerName == null) {
            return;
        }
        if (!respondToSelf.get() && parsedMessage.isFromSelf) {
            return;
        }
        if (!respondToWhispers.get() && parsedMessage.isWhisper) {
            return;
        }
        if (!messageContainsTrigger(parsedMessage.content)) {
            return;
        }
        long currentTime = System.currentTimeMillis();
        long cooldownMs = cooldown.get() * 1000L;
        if (currentTime - lastResponseTime < cooldownMs) {
            if (logTriggers.get()) {
                info("Trigger detected but on cooldown (%d seconds remaining)",
                    (cooldownMs - (currentTime - lastResponseTime)) / 1000);
            }
            return;
        }
        String selectedResponse = getNextResponse();
        if (selectedResponse == null || selectedResponse.isEmpty()) {
            if (logTriggers.get()) {
                info("No valid response available");
            }
            return;
        }
        int delayMs = minDelay.get() + random.nextInt(Math.max(1, maxDelay.get() - minDelay.get() + 1));
        responseScheduledTime = currentTime + delayMs;
        pendingResponse = selectedResponse;
        pendingTargetPlayer = (parsedMessage.isWhisper && usePrivateReply.get()) ? parsedMessage.playerName : null;
        lastResponseTime = currentTime;
        if (logTriggers.get()) {
            String targetInfo = pendingTargetPlayer != null ? " to " + pendingTargetPlayer : "";
            info("Trigger detected! Responding%s in %dms", targetInfo, delayMs);
        }
    }
    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;
        if (pendingResponse != null && System.currentTimeMillis() >= responseScheduledTime) {
            sendResponse(pendingResponse, pendingTargetPlayer);
            pendingResponse = null;
            pendingTargetPlayer = null;
            responseScheduledTime = 0;
        }
    }
    private String getNextResponse() {
        List<String> responseList = responses.get();
        if (responseList == null || responseList.isEmpty()) {
            return null;
        }
        return switch (responseMode.get()) {
            case Random -> responseList.get(random.nextInt(responseList.size()));
            case Sequential -> {
                String response = responseList.get(currentResponseIndex % responseList.size());
                currentResponseIndex++;
                yield response;
            }
        };
    }
    private ChatMessage parseMessage(String rawMessage) {
        if (mc.player == null) return null;
        String playerName = mc.player.getName().getString();
        Matcher chatMatcher = CHAT_PATTERN_2B2T.matcher(rawMessage);
        if (chatMatcher.matches()) {
            String sender = chatMatcher.group(1);
            String content = chatMatcher.group(2);
            return new ChatMessage(sender, content, false, sender.equals(playerName));
        }
        Matcher whisperMatcher = WHISPER_PATTERN_2B2T.matcher(rawMessage);
        if (whisperMatcher.matches()) {
            String sender = whisperMatcher.group(1);
            String content = whisperMatcher.group(2);
            return new ChatMessage(sender, content, true, sender.equals(playerName));
        }
        Matcher toMatcher = TO_PATTERN_2B2T.matcher(rawMessage);
        if (toMatcher.matches()) {
            String recipient = toMatcher.group(1);
            String content = toMatcher.group(2);
            return new ChatMessage(recipient, content, true, true);
        }
        if (rawMessage.contains(">")) {
            int startIdx = rawMessage.indexOf('<');
            int endIdx = rawMessage.indexOf('>');
            if (startIdx >= 0 && endIdx > startIdx) {
                String sender = rawMessage.substring(startIdx + 1, endIdx);
                String content = rawMessage.substring(endIdx + 1).trim();
                if (sender.matches("[a-zA-Z0-9_]{1,16}")) {
                    return new ChatMessage(sender, content, false, sender.equals(playerName));
                }
            }
        }
        return new ChatMessage(null, rawMessage, false, false);
    }
    private boolean messageContainsTrigger(String message) {
        String triggerText = trigger.get();
        if (triggerText.isEmpty()) return false;
        String messageToCheck = caseSensitive.get() ? message : message.toLowerCase();
        String triggerToCheck = caseSensitive.get() ? triggerText : triggerText.toLowerCase();
        return switch (matchMode.get()) {
            case Contains -> messageToCheck.contains(triggerToCheck);
            case StartsWith -> messageToCheck.startsWith(triggerToCheck);
            case EndsWith -> messageToCheck.endsWith(triggerToCheck);
            case Exact -> messageToCheck.equals(triggerToCheck);
            case Word -> containsWord(messageToCheck, triggerToCheck);
        };
    }
    private boolean containsWord(String message, String word) {
        String regex = "(?i)\\b" + Pattern.quote(word) + "\\b";
        return Pattern.compile(regex).matcher(message).find();
    }
    private void sendResponse(String message, String targetPlayer) {
        if (mc.player == null) return;
        String finalMessage;
        if (targetPlayer != null && usePrivateReply.get()) {
            finalMessage = "/msg " + targetPlayer + " " + message;
        } else {
            finalMessage = message;
        }
        mc.player.networkHandler.sendChatMessage(finalMessage);
        if (logTriggers.get()) {
            info("Sent response: %s", finalMessage);
        }
    }
    @Override
    public String getInfoString() {
        if (pendingResponse != null) {
            long remainingMs = responseScheduledTime - System.currentTimeMillis();
            if (remainingMs > 0) {
                return String.format("Responding in %.1fs", remainingMs / 1000.0);
            }
        }
        long cooldownRemaining = (cooldown.get() * 1000L) - (System.currentTimeMillis() - lastResponseTime);
        if (cooldownRemaining > 0 && lastResponseTime > 0) {
            return String.format("Cooldown: %ds", cooldownRemaining / 1000);
        }
        return null;
    }
    public enum MatchMode {
        Contains("Contains"),
        StartsWith("Starts With"),
        EndsWith("Ends With"),
        Exact("Exact"),
        Word("Whole Word");
        private final String title;
        MatchMode(String title) {
            this.title = title;
        }
        @Override
        public String toString() {
            return title;
        }
    }
    public enum ResponseMode {
        Random("Random"),
        Sequential("Sequential");
        private final String title;
        ResponseMode(String title) {
            this.title = title;
        }
        @Override
        public String toString() {
            return title;
        }
    }
    private static class ChatMessage {
        public final String playerName;
        public final String content;
        public final boolean isWhisper;
        public final boolean isFromSelf;
        public ChatMessage(String playerName, String content, boolean isWhisper, boolean isFromSelf) {
            this.playerName = playerName;
            this.content = content;
            this.isWhisper = isWhisper;
            this.isFromSelf = isFromSelf;
        }
    }
}