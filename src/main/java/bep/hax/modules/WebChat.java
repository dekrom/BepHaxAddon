package bep.hax.modules;
import bep.hax.Bep;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
import meteordevelopment.meteorclient.events.game.SendMessageEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.settings.StringSetting;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.EventPriority;
import net.minecraft.text.Text;
import net.minecraft.text.TextContent;
import net.minecraft.text.TranslatableTextContent;
import net.minecraft.text.Style;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.dimension.DimensionType;
import java.awt.Desktop;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.ArrayList;
import java.util.List;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
public class WebChat extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgServer = settings.createGroup("Server");
    private final SettingGroup sgFilters = settings.createGroup("Filters");
    private final Setting<Integer> port = sgServer.add(new IntSetting.Builder()
        .name("port")
        .description("Port for the web server.")
        .defaultValue(8765)
        .range(1024, 65535)
        .sliderRange(8000, 9000)
        .build()
    );
    private final Setting<Boolean> openBrowserButton = sgServer.add(new BoolSetting.Builder()
        .name("open-in-browser")
        .description("Toggle this to open the web chat in your browser.")
        .defaultValue(false)
        .build()
    );
    private final Setting<Boolean> showTimestamps = sgGeneral.add(new BoolSetting.Builder()
        .name("timestamps")
        .description("Show timestamps for messages.")
        .defaultValue(true)
        .build()
    );
    private final Setting<Boolean> showCoordinates = sgGeneral.add(new BoolSetting.Builder()
        .name("show-coordinates")
        .description("Show current coordinates with dimension conversion.")
        .defaultValue(false)
        .build()
    );
    private final Setting<Integer> maxMessages = sgGeneral.add(new IntSetting.Builder()
        .name("max-messages")
        .description("Maximum number of messages to keep in history.")
        .defaultValue(1000)
        .range(50, 5000)
        .sliderRange(100, 2000)
        .build()
    );
    private final Setting<Boolean> showPlayerMessages = sgFilters.add(new BoolSetting.Builder()
        .name("show-player-messages")
        .description("Show player chat messages.")
        .defaultValue(true)
        .build()
    );
    private final Setting<Boolean> showSystemMessages = sgFilters.add(new BoolSetting.Builder()
        .name("show-system-messages")
        .description("Show system messages.")
        .defaultValue(true)
        .build()
    );
    private final Setting<Boolean> showDeathMessages = sgFilters.add(new BoolSetting.Builder()
        .name("show-death-messages")
        .description("Show death messages.")
        .defaultValue(true)
        .build()
    );
    private final Setting<Boolean> hideChatInGame = sgGeneral.add(new BoolSetting.Builder()
        .name("hide-in-game-chat")
        .description("Hide the in-game chat HUD when web chat is active.")
        .defaultValue(true)
        .build()
    );
    private final Setting<String> pageTitle = sgGeneral.add(new StringSetting.Builder()
        .name("page-title")
        .description("Title shown in browser tab (uses server address if empty).")
        .defaultValue("")
        .build()
    );
    private final Setting<Boolean> persistEnabled = sgGeneral.add(new BoolSetting.Builder()
        .name("persist-enabled")
        .description("Keep module enabled between game sessions (WARNING: May cause server hosting issues).")
        .defaultValue(false)
        .build()
    );
    private HttpServer server;
    private ExecutorService executor;
    private final List<ChatMessage> messageHistory = new ArrayList<>();
    private final ConcurrentLinkedQueue<String> commandQueue = new ConcurrentLinkedQueue<>();
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");
    private final Gson gson = new Gson();
    private volatile boolean serverRunning = false;
    private int currentX = 0;
    private int currentY = 0;
    private int currentZ = 0;
    private String currentDimension = "Overworld";
    public WebChat() {
        super(Bep.CATEGORY, "web-chat", "Displays Minecraft chat in a web browser with coordinate tracking.");
    }
    @Override
    public boolean isActive() {
        if (!persistEnabled.get() && !serverRunning) {
            return false;
        }
        return super.isActive();
    }
    @Override
    public void onActivate() {
        try {
            Class.forName("com.sun.net.httpserver.HttpServer");
            info("Attempting to start web server on port " + port.get() + "...");
            startWebServer();
            serverRunning = true;
            int actualPort = server != null ? server.getAddress().getPort() : port.get();
            info("Web chat server started successfully on port " + actualPort);
            info("Open http://localhost:" + actualPort + " in your browser");
            info("Or use the 'Open in Browser' button in the module settings");
            addSystemMessage("Web Chat server started successfully!");
        } catch (ClassNotFoundException e) {
            error("HttpServer classes not available in this environment.");
            error("The Web Chat module requires Java's built-in HTTP server which may not be available.");
            serverRunning = false;
            toggle();
        } catch (Exception e) {
            error("Failed to start web server: " + e.getMessage());
            e.printStackTrace();
            serverRunning = false;
            toggle();
        }
    }
    @Override
    public void onDeactivate() {
        serverRunning = false;
        stopWebServer();
        messageHistory.clear();
        commandQueue.clear();
        info("Web chat server stopped");
    }
    private void startWebServer() throws IOException {
        int actualPort = port.get();
        int attempts = 0;
        while (attempts < 10) {
            try {
                info("Creating HTTP server on port " + actualPort + "...");
                server = HttpServer.create(new InetSocketAddress("localhost", actualPort), 0);
                break;
            } catch (IOException e) {
                if (attempts < 9) {
                    warning("Port " + actualPort + " is in use, trying " + (actualPort + 1));
                    actualPort++;
                    attempts++;
                } else {
                    throw new IOException("Could not find an available port after 10 attempts");
                }
            }
        }
        executor = Executors.newFixedThreadPool(4);
        server.setExecutor(executor);
        info("Setting up HTTP handlers...");
        server.createContext("/", new HttpHandler() {
                @Override
                public void handle(HttpExchange exchange) throws IOException {
                    try {
                        String response = getHtmlPage();
                        byte[] responseBytes = response.getBytes(StandardCharsets.UTF_8);
                        exchange.getResponseHeaders().add("Content-Type", "text/html; charset=UTF-8");
                        exchange.sendResponseHeaders(200, responseBytes.length);
                        try (OutputStream os = exchange.getResponseBody()) {
                            os.write(responseBytes);
                            os.flush();
                        }
                        info("Served main page to " + exchange.getRemoteAddress());
                    } catch (Exception e) {
                        error("Error serving main page: " + e.getMessage());
                        e.printStackTrace();
                    }
                }
            });
        server.createContext("/api/messages", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                exchange.getResponseHeaders().add("Content-Type", "application/json; charset=UTF-8");
                exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
                JsonObject response = new JsonObject();
                response.add("messages", gson.toJsonTree(messageHistory));
                response.addProperty("showCoordinates", showCoordinates.get());
                if (showCoordinates.get()) {
                    response.addProperty("x", currentX);
                    response.addProperty("y", currentY);
                    response.addProperty("z", currentZ);
                    response.addProperty("dimension", currentDimension);
                    if (currentDimension.equals("Overworld")) {
                        response.addProperty("netherX", currentX / 8);
                        response.addProperty("netherZ", currentZ / 8);
                    } else if (currentDimension.equals("Nether")) {
                        response.addProperty("overworldX", currentX * 8);
                        response.addProperty("overworldZ", currentZ * 8);
                    }
                }
                String jsonResponse = gson.toJson(response);
                exchange.sendResponseHeaders(200, jsonResponse.getBytes(StandardCharsets.UTF_8).length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(jsonResponse.getBytes(StandardCharsets.UTF_8));
                }
            }
        });
        server.createContext("/api/send", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                if ("POST".equals(exchange.getRequestMethod())) {
                    String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                    JsonObject request = gson.fromJson(body, JsonObject.class);
                    String message = request.get("message").getAsString();
                    if (message != null && !message.isEmpty()) {
                        commandQueue.offer(message);
                    }
                    exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
                    exchange.sendResponseHeaders(200, 0);
                } else if ("OPTIONS".equals(exchange.getRequestMethod())) {
                    exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
                    exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "POST, OPTIONS");
                    exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");
                    exchange.sendResponseHeaders(204, -1);
                }
            }
        });
        server.createContext("/health", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                String response = "OK";
                exchange.sendResponseHeaders(200, response.length());
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(response.getBytes());
                }
            }
        });
        server.start();
        int listeningPort = server.getAddress().getPort();
        if (listeningPort != port.get()) {
            warning("Server started on port " + listeningPort + " instead of configured port " + port.get());
        }
        info("HTTP server started successfully on port " + listeningPort);
    }
    private void stopWebServer() {
        try {
            if (server != null) {
                info("Stopping HTTP server...");
                server.stop(0);
                server = null;
            }
            if (executor != null) {
                executor.shutdownNow();
                executor = null;
            }
        } catch (Exception e) {
            error("Error stopping server: " + e.getMessage());
        }
    }
    private void openBrowser() {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                int actualPort = server != null ? server.getAddress().getPort() : port.get();
                Desktop.getDesktop().browse(new URI("http://localhost:" + actualPort));
                info("Opened browser at http://localhost:" + actualPort);
            }
        } catch (Exception e) {
            warning("Could not open browser: " + e.getMessage());
        }
    }
    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (openBrowserButton.get()) {
            openBrowserButton.set(false);
            if (serverRunning) {
                openBrowser();
                info("Opening web chat in browser...");
            } else {
                warning("Web server is not running! Activate the module first.");
            }
        }
        while (!commandQueue.isEmpty()) {
            String message = commandQueue.poll();
            if (message != null && mc.player != null && mc.player.networkHandler != null) {
                if (message.startsWith("/")) {
                    mc.player.networkHandler.sendChatCommand(message.substring(1));
                } else {
                    mc.player.networkHandler.sendChatMessage(message);
                }
            }
        }
        if (mc.player != null && showCoordinates.get()) {
            BlockPos pos = mc.player.getBlockPos();
            currentX = pos.getX();
            currentY = pos.getY();
            currentZ = pos.getZ();
            DimensionType dimType = mc.world.getDimension();
            if (dimType.effects().toString().contains("the_nether")) {
                currentDimension = "Nether";
            } else if (dimType.effects().toString().contains("the_end")) {
                currentDimension = "End";
            } else {
                currentDimension = "Overworld";
            }
        }
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    private void onReceiveMessage(ReceiveMessageEvent event) {
        if (!serverRunning || event.getMessage() == null) return;
        Text msg = event.getMessage();
        String plainText = stripFormatting(msg.getString());
        if (!shouldShowMessage(plainText, msg)) return;
        String timestamp = showTimestamps.get() ? "[" + LocalTime.now().format(TIME_FMT) + "] " : "";
        String color = getColorForMessage(plainText, msg);
        addMessage(timestamp + plainText, color, "received");
    }
    @EventHandler
    private void onSendMessage(SendMessageEvent event) {
        if (!serverRunning || event.message == null) return;
        String timestamp = showTimestamps.get() ? "[" + LocalTime.now().format(TIME_FMT) + "] " : "";
        String displayMessage = timestamp + "<" + mc.getSession().getUsername() + "> " + event.message;
        addMessage(displayMessage, "#ffffff", "sent");
    }
    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        if (serverRunning) {
            addSystemMessage("Disconnected from server");
        }
    }
    private void addMessage(String text, String color, String type) {
        synchronized (messageHistory) {
            messageHistory.add(new ChatMessage(text, color, type));
            while (messageHistory.size() > maxMessages.get()) {
                messageHistory.remove(0);
            }
        }
    }
    private void addSystemMessage(String message) {
        String timestamp = showTimestamps.get() ? "[" + LocalTime.now().format(TIME_FMT) + "] " : "";
        addMessage(timestamp + "[SYSTEM] " + message, "#ffc864", "system");
    }
    private boolean shouldShowMessage(String plainText, Text msg) {
        if (plainText == null || plainText.isEmpty()) return false;
        boolean isPlayerChat = plainText.matches("^<[^>]+>.*") ||
                               plainText.contains(" whispers") ||
                               plainText.contains("-> me");
        if (isPlayerChat && !showPlayerMessages.get()) return false;
        if (!isPlayerChat && !showSystemMessages.get()) return false;
        if (!showDeathMessages.get() && isDeathMessage(msg)) return false;
        return true;
    }
    private boolean isDeathMessage(Text msg) {
        TextContent content = msg.getContent();
        if (content instanceof TranslatableTextContent tc) {
            String key = tc.getKey();
            return key != null && key.startsWith("death.");
        }
        return false;
    }
    private String getColorForMessage(String message, Text text) {
        Style style = text.getStyle();
        if (style != null && style.getColor() != null) {
            Formatting formatting = Formatting.byName(style.getColor().getName());
            if (formatting != null) {
                return getHexFromFormatting(formatting);
            }
        }
        if (message.contains("[Server]") || message.contains("[System]")) {
            return "#ffff55";
        } else if (message.contains("joined the game") || message.contains("left the game")) {
            return "#aaaaaa";
        } else if (message.matches("^<[^>]+>.*")) {
            return "#ffffff";
        } else if (message.contains("whispers") || message.contains("-> me")) {
            return "#ff55ff";
        } else {
            return "#c8c8c8";
        }
    }
    private String getHexFromFormatting(Formatting formatting) {
        return switch (formatting) {
            case BLACK -> "#000000";
            case DARK_BLUE -> "#0000aa";
            case DARK_GREEN -> "#00aa00";
            case DARK_AQUA -> "#00aaaa";
            case DARK_RED -> "#aa0000";
            case DARK_PURPLE -> "#aa00aa";
            case GOLD -> "#ffaa00";
            case GRAY -> "#aaaaaa";
            case DARK_GRAY -> "#555555";
            case BLUE -> "#5555ff";
            case GREEN -> "#55ff55";
            case AQUA -> "#55ffff";
            case RED -> "#ff5555";
            case LIGHT_PURPLE -> "#ff55ff";
            case YELLOW -> "#ffff55";
            case WHITE -> "#ffffff";
            default -> "#c8c8c8";
        };
    }
    private String stripFormatting(String text) {
        return text.replaceAll("§[0-9a-fklmnor]", "");
    }
    public boolean shouldHideInGameChat() {
        return hideChatInGame.get();
    }
    private String getPageTitle() {
        if (!pageTitle.get().isEmpty()) {
            return pageTitle.get();
        }
        if (mc.getCurrentServerEntry() != null) {
            return mc.getCurrentServerEntry().address;
        } else if (mc.isInSingleplayer()) {
            return "Singleplayer";
        } else {
            return "Minecraft Web Chat";
        }
    }
    private String getHtmlPage() {
        String title = getPageTitle();
        String html = """
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>""" + title + "</title>" + """
    <style>
        * {
            margin: 0;
            padding: 0;
            box-sizing: border-box;
        }
        body {
            font-family: 'Consolas', 'Monaco', monospace;
            background: linear-gradient(135deg, #1e1e2e 0%, #2d2d44 100%);
            color: #ffffff;
            height: 100vh;
            display: flex;
            flex-direction: column;
        }
        #header {
            background: rgba(0, 0, 0, 0.3);
            padding: 15px 20px;
            border-bottom: 2px solid #444;
            backdrop-filter: blur(10px);
        }
        #header h1 {
            font-size: 24px;
            color: #55ff55;
            margin-bottom: 0;
        }
        #header.no-coords h1 {
            margin-bottom: 0;
        }
        #coordinates {
            display: flex;
            gap: 20px;
            font-size: 14px;
            color: #aaaaaa;
            margin-top: 10px;
        }
        .coord-group {
            display: flex;
            align-items: center;
            gap: 10px;
        }
        .coord-label {
            color: #888;
        }
        .coord-value {
            color: #55ffff;
            font-weight: bold;
        }
        .dimension {
            color: #ffaa00;
            font-weight: bold;
        }
        #chat-container {
            flex: 1;
            overflow-y: auto;
            padding: 20px;
            background: rgba(0, 0, 0, 0.2);
            margin: 10px;
            border-radius: 10px;
            backdrop-filter: blur(5px);
        }
        .message {
            padding: 5px 10px;
            margin: 2px 0;
            border-radius: 4px;
            background: rgba(0, 0, 0, 0.3);
            word-wrap: break-word;
            animation: slideIn 0.3s ease-out;
        }
        @keyframes slideIn {
            from {
                opacity: 0;
                transform: translateX(-20px);
            }
            to {
                opacity: 1;
                transform: translateX(0);
            }
        }
        .message.sent {
            background: rgba(0, 100, 200, 0.2);
            border-left: 3px solid #0064c8;
        }
        .message.system {
            background: rgba(255, 200, 100, 0.2);
            border-left: 3px solid #ffc864;
        }
        #input-container {
            padding: 20px;
            background: rgba(0, 0, 0, 0.4);
            border-top: 2px solid #444;
            display: flex;
            gap: 10px;
        }
        #message-input {
            flex: 1;
            padding: 12px;
            background: rgba(30, 30, 30, 0.8);
            border: 1px solid #444;
            color: white;
            font-family: inherit;
            font-size: 14px;
            border-radius: 5px;
            outline: none;
            transition: border-color 0.3s;
        }
        #message-input:focus {
            border-color: #55ff55;
        }
        #send-button {
            padding: 12px 30px;
            background: linear-gradient(135deg, #55ff55 0%, #00aa00 100%);
            color: black;
            border: none;
            font-weight: bold;
            cursor: pointer;
            border-radius: 5px;
            transition: transform 0.2s, box-shadow 0.2s;
        }
        #send-button:hover {
            transform: translateY(-2px);
            box-shadow: 0 5px 15px rgba(85, 255, 85, 0.3);
        }
        #send-button:active {
            transform: translateY(0);
        }
        #status {
            position: absolute;
            top: 15px;
            right: 20px;
            padding: 5px 10px;
            background: rgba(0, 255, 0, 0.2);
            border: 1px solid #00ff00;
            border-radius: 20px;
            font-size: 12px;
            color: #00ff00;
        }
        #status.disconnected {
            background: rgba(255, 0, 0, 0.2);
            border-color: #ff0000;
            color: #ff0000;
        }
        .conversion-info {
            font-size: 12px;
            color: #888;
            margin-left: 5px;
        }
        ::-webkit-scrollbar {
            width: 10px;
        }
        ::-webkit-scrollbar-track {
            background: rgba(0, 0, 0, 0.2);
        }
        ::-webkit-scrollbar-thumb {
            background: rgba(85, 255, 85, 0.3);
            border-radius: 5px;
        }
        ::-webkit-scrollbar-thumb:hover {
            background: rgba(85, 255, 85, 0.5);
        }
    </style>
</head>
<body>
    <div id="header">
        <h1>🌐 """ + title + "</h1>" + """
        <div id="coordinates" style="display: none;">
            <div class="coord-group">
                <span class="coord-label">Dimension:</span>
                <span id="dimension" class="dimension">Overworld</span>
            </div>
            <div class="coord-group">
                <span class="coord-label">Current:</span>
                <span class="coord-value">X: <span id="x">0</span></span>
                <span class="coord-value">Y: <span id="y">0</span></span>
                <span class="coord-value">Z: <span id="z">0</span></span>
            </div>
            <div class="coord-group" id="conversion-coords" style="display: none;">
                <span class="coord-label" id="conversion-label">Nether:</span>
                <span class="coord-value">X: <span id="conv-x">0</span></span>
                <span class="coord-value">Z: <span id="conv-z">0</span></span>
            </div>
        </div>
        <div id="status">● Connected</div>
    </div>
    <div id="chat-container"></div>
    <div id="input-container">
        <input type="text" id="message-input" placeholder="Type a message or command..." autofocus>
        <button id="send-button">Send</button>
    </div>
    <script>
        const chatContainer = document.getElementById('chat-container');
        const messageInput = document.getElementById('message-input');
        const sendButton = document.getElementById('send-button');
        const statusDiv = document.getElementById('status');
        let lastMessageCount = 0;
        let connected = true;
        async function fetchMessages() {
            try {
                const response = await fetch('/api/messages');
                const data = await response.json();
                const coordsDiv = document.getElementById('coordinates');
                const headerDiv = document.getElementById('header');
                if (data.showCoordinates) {
                    coordsDiv.style.display = 'flex';
                    headerDiv.classList.remove('no-coords');
                    document.getElementById('x').textContent = data.x;
                    document.getElementById('y').textContent = data.y;
                    document.getElementById('z').textContent = data.z;
                    document.getElementById('dimension').textContent = data.dimension;
                    const conversionGroup = document.getElementById('conversion-coords');
                    const conversionLabel = document.getElementById('conversion-label');
                    if (data.dimension === 'Overworld' && data.netherX !== undefined) {
                        conversionGroup.style.display = 'flex';
                        conversionLabel.textContent = 'Nether:';
                        document.getElementById('conv-x').textContent = data.netherX;
                        document.getElementById('conv-z').textContent = data.netherZ;
                    } else if (data.dimension === 'Nether' && data.overworldX !== undefined) {
                        conversionGroup.style.display = 'flex';
                        conversionLabel.textContent = 'Overworld:';
                        document.getElementById('conv-x').textContent = data.overworldX;
                        document.getElementById('conv-z').textContent = data.overworldZ;
                    } else if (data.dimension === 'End') {
                        conversionGroup.style.display = 'none';
                    }
                } else {
                    coordsDiv.style.display = 'none';
                    headerDiv.classList.add('no-coords');
                }
                if (data.messages && data.messages.length > lastMessageCount) {
                    const newMessages = data.messages.slice(lastMessageCount);
                    newMessages.forEach(msg => {
                        const messageDiv = document.createElement('div');
                        messageDiv.className = 'message ' + msg.type;
                        messageDiv.style.color = msg.color;
                        messageDiv.textContent = msg.text;
                        chatContainer.appendChild(messageDiv);
                    });
                    lastMessageCount = data.messages.length;
                    chatContainer.scrollTop = chatContainer.scrollHeight;
                } else if (data.messages && data.messages.length < lastMessageCount) {
                    chatContainer.innerHTML = '';
                    data.messages.forEach(msg => {
                        const messageDiv = document.createElement('div');
                        messageDiv.className = 'message ' + msg.type;
                        messageDiv.style.color = msg.color;
                        messageDiv.textContent = msg.text;
                        chatContainer.appendChild(messageDiv);
                    });
                    lastMessageCount = data.messages.length;
                }
                if (!connected) {
                    connected = true;
                    statusDiv.textContent = '● Connected';
                    statusDiv.classList.remove('disconnected');
                }
            } catch (error) {
                if (connected) {
                    connected = false;
                    statusDiv.textContent = '● Disconnected';
                    statusDiv.classList.add('disconnected');
                }
            }
        }
        async function sendMessage() {
            const message = messageInput.value.trim();
            if (!message) return;
            try {
                await fetch('/api/send', {
                    method: 'POST',
                    headers: {
                        'Content-Type': 'application/json'
                    },
                    body: JSON.stringify({ message })
                });
                messageInput.value = '';
            } catch (error) {
                console.error('Failed to send message:', error);
            }
        }
        sendButton.addEventListener('click', sendMessage);
        messageInput.addEventListener('keypress', (e) => {
            if (e.key === 'Enter') {
                sendMessage();
            }
        });
        setInterval(fetchMessages, 500);
        fetchMessages();
    </script>
</body>
</html>
""";
        return html;
    }
    private static class ChatMessage {
        final String text;
        final String color;
        final String type;
        ChatMessage(String text, String color, String type) {
            this.text = text;
            this.color = color;
            this.type = type;
        }
    }
}