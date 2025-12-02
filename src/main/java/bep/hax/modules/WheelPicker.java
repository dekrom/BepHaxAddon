package bep.hax.modules;
import bep.hax.Bep;
import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.misc.Keybind;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.KeyBinding;
import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.glfw.GLFW;
import net.minecraft.client.util.InputUtil;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.item.ItemStack;
import java.util.concurrent.ThreadLocalRandom;
public class WheelPicker extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgSlots = settings.createGroup("Slot Actions");
    private final SettingGroup sgSpam = settings.createGroup("Spam Protection");
    private final SettingGroup sgRender = settings.createGroup("Render");
    private final Setting<Keybind> activationKey = sgGeneral.add(new KeybindSetting.Builder()
        .name("activation-key")
        .description("Key to activate the wheel picker.")
        .defaultValue(Keybind.fromKey(GLFW.GLFW_KEY_V))
        .build());
    private final Setting<Integer> wheelRadius = sgGeneral.add(new IntSetting.Builder()
        .name("wheel-radius")
        .description("Radius of the wheel in pixels.")
        .defaultValue(100)
        .min(60)
        .max(10000)
        .sliderRange(60, 2000)
        .build());
    private final Setting<Integer> wheelX = sgGeneral.add(new IntSetting.Builder()
        .name("wheel-x-offset")
        .description("X offset from center of screen (negative = left, positive = right).")
        .defaultValue(0)
        .min(-100000)
        .max(10000)
        .sliderRange(-10000, 10000)
        .build());
    private final Setting<Integer> wheelY = sgGeneral.add(new IntSetting.Builder()
        .name("wheel-y-offset")
        .description("Y offset from center of screen (negative = up, positive = down).")
        .defaultValue(0)
        .min(-10000)
        .max(100000)
        .sliderRange(-10000, 10000)
        .build());
    private final SlotConfig[] slots = new SlotConfig[8];
    private final Setting<Boolean> spamProtection = sgSpam.add(new BoolSetting.Builder()
        .name("spam-protection")
        .description("Enable spam protection for messages.")
        .defaultValue(true)
        .build());
    private final Setting<Integer> messageDelay = sgSpam.add(new IntSetting.Builder()
        .name("message-delay")
        .description("Minimum delay between messages in milliseconds.")
        .defaultValue(1000)
        .min(100)
        .max(5000)
        .sliderRange(100, 5000)
        .visible(spamProtection::get)
        .build());
    private final Setting<Boolean> insertRandomBrackets = sgSpam.add(new BoolSetting.Builder()
        .name("insert-random-brackets")
        .description("Insert random text within [] brackets to bypass spam filters.")
        .defaultValue(true)
        .visible(spamProtection::get)
        .build());
    private final Setting<SettingColor> backgroundColor = sgRender.add(new ColorSetting.Builder()
        .name("background-color")
        .description("Background color of wheel sections.")
        .defaultValue(new SettingColor(40, 40, 40, 120))
        .build());
    private final Setting<SettingColor> selectedColor = sgRender.add(new ColorSetting.Builder()
        .name("selected-color")
        .description("Color of the selected section.")
        .defaultValue(new SettingColor(100, 200, 255, 160))
        .build());
    private final Setting<SettingColor> borderColor = sgRender.add(new ColorSetting.Builder()
        .name("border-color")
        .description("Border color between sections.")
        .defaultValue(new SettingColor(255, 255, 255, 100))
        .build());
    private final Setting<SettingColor> textColor = sgRender.add(new ColorSetting.Builder()
        .name("text-color")
        .description("Text color for labels.")
        .defaultValue(new SettingColor(255, 255, 255, 255))
        .build());
    private final Setting<SettingColor> moduleActiveColor = sgRender.add(new ColorSetting.Builder()
        .name("module-active-color")
        .description("Text color for active modules.")
        .defaultValue(new SettingColor(100, 255, 100, 255))
        .build());
    private final Setting<Double> textScale = sgRender.add(new DoubleSetting.Builder()
        .name("text-scale")
        .description("Scale of the text labels.")
        .defaultValue(1.0)
        .min(0.1)
        .max(5.0)
        .sliderRange(0.1, 3.0)
        .build());
    private final Setting<Double> iconScale = sgRender.add(new DoubleSetting.Builder()
        .name("icon-scale")
        .description("Scale of the item icons.")
        .defaultValue(1.0)
        .min(0.1)
        .max(5.0)
        .sliderRange(0.1, 3.0)
        .build());
    private final Setting<Boolean> showIcons = sgRender.add(new BoolSetting.Builder()
        .name("show-icons")
        .description("Show item icons on the wheel.")
        .defaultValue(true)
        .build());
    private final Setting<Boolean> showText = sgRender.add(new BoolSetting.Builder()
        .name("show-text")
        .description("Show text labels on the wheel.")
        .defaultValue(true)
        .build());
    private boolean wheelActive = false;
    private int selectedSlot = -1;
    private long lastMessageTime = 0;
    private double initialMouseX = 0;
    private double initialMouseY = 0;
    private boolean wasGrabbed = false;
    private final Module[] cachedModules = new Module[8];
    private final boolean[] cachedModuleStates = new boolean[8];
    private long lastModuleCacheUpdate = 0;
    private static final long MODULE_CACHE_INTERVAL = 100;
    private static final String RANDOM_CHARS = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final String[] SLOT_NAMES = {
        "Top", "Top-Right", "Right", "Bottom-Right",
        "Bottom", "Bottom-Left", "Left", "Top-Left"
    };
    public WheelPicker() {
        super(Bep.CATEGORY, "wheel-picker", "GTA-style wheel menu for quick macros and actions.");
        for (int i = 0; i < 8; i++) {
            slots[i] = new SlotConfig(i);
        }
    }
    @Override
    public void onActivate() {
        wheelActive = false;
        selectedSlot = -1;
    }
    @Override
    public void onDeactivate() {
        wheelActive = false;
        selectedSlot = -1;
    }
    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;
        if (mc.currentScreen != null) {
            wheelActive = false;
            return;
        }
        boolean keyPressed = activationKey.get().isPressed();
        if (keyPressed && !wheelActive) {
            wheelActive = true;
            selectedSlot = -1;
            wasGrabbed = mc.mouse.isCursorLocked();
            if (wasGrabbed) {
                mc.mouse.unlockCursor();
            }
            GLFW.glfwSetCursorPos(mc.getWindow().getHandle(),
                mc.getWindow().getWidth() / 2.0,
                mc.getWindow().getHeight() / 2.0);
            initialMouseX = mc.getWindow().getWidth() / 2.0;
            initialMouseY = mc.getWindow().getHeight() / 2.0;
            KeyBinding.unpressAll();
        } else if (!keyPressed && wheelActive) {
            if (selectedSlot >= 0 && selectedSlot < 8) {
                executeSlotAction(selectedSlot);
            }
            if (wasGrabbed) {
                mc.mouse.lockCursor();
            }
            wheelActive = false;
        }
        if (wheelActive) {
            updateSelectedSlot();
            KeyBinding.unpressAll();
        }
    }
    private void updateSelectedSlot() {
        int scaledWidth = mc.getWindow().getScaledWidth();
        int scaledHeight = mc.getWindow().getScaledHeight();
        double mouseX = mc.mouse.getX() * scaledWidth / (double)mc.getWindow().getWidth();
        double mouseY = mc.mouse.getY() * scaledHeight / (double)mc.getWindow().getHeight();
        double centerX = scaledWidth / 2.0;
        double centerY = scaledHeight / 2.0;
        double deltaX = mouseX - centerX;
        double deltaY = mouseY - centerY;
        double distance = Math.sqrt(deltaX * deltaX + deltaY * deltaY);
        if (distance < 20) {
            selectedSlot = -1;
            return;
        }
        double angle = Math.atan2(deltaY, deltaX);
        double degrees = Math.toDegrees(angle);
        if (degrees < 0) degrees += 360;
        degrees = (degrees + 90) % 360;
        selectedSlot = (int)((degrees + 22.5) / 45) % 8;
    }
    private void executeSlotAction(int slotIndex) {
        if (slotIndex < 0 || slotIndex >= 8) return;
        SlotConfig slot = slots[slotIndex];
        MacroAction action = slot.action.get();
        if (action == MacroAction.NONE) return;
        switch (action) {
            case TOGGLE_MODULE:
                String moduleName = slot.moduleName.get();
                if (!moduleName.isEmpty()) {
                    Module module = Modules.get().get(moduleName);
                    if (module != null) {
                        module.toggle();
                        info(String.format("%s: %s", moduleName,
                            module.isActive() ? "§aENABLED" : "§cDISABLED"));
                    } else {
                        warning("Module not found: " + moduleName);
                    }
                }
                break;
            case SEND_MESSAGE:
                String message = slot.message.get();
                if (!message.isEmpty()) {
                    sendMessageWithProtection(message);
                }
                break;
            case RUN_COMMAND:
                String command = slot.command.get();
                if (!command.isEmpty()) {
                    if (spamProtection.get()) {
                        command = applyRandomSubstitution(command);
                    }
                    mc.player.networkHandler.sendChatCommand(command);
                }
                break;
        }
    }
    private void sendMessageWithProtection(String message) {
        if (spamProtection.get()) {
            long currentTime = System.currentTimeMillis();
            if (currentTime - lastMessageTime < messageDelay.get()) {
                warning("Message blocked by spam protection.");
                return;
            }
            lastMessageTime = currentTime;
            message = applyRandomSubstitution(message);
            String[] invisibleChars = {"\u200B", "\u200C", "\u200D"};
            message += invisibleChars[ThreadLocalRandom.current().nextInt(invisibleChars.length)];
        }
        mc.player.networkHandler.sendChatMessage(message);
    }
    private String applyRandomSubstitution(String text) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        while (text.contains("[RANDOM]")) {
            String randomString = generateRandomString(5 + random.nextInt(4));
            text = text.replaceFirst("\\[RANDOM\\]", randomString);
        }
        if (insertRandomBrackets.get()) {
            StringBuilder result = new StringBuilder();
            int lastEnd = 0;
            int bracketStart = text.indexOf('[');
            while (bracketStart != -1) {
                int bracketEnd = text.indexOf(']', bracketStart);
                if (bracketEnd != -1) {
                    result.append(text.substring(lastEnd, bracketStart));
                    String randomString = generateRandomString(3 + random.nextInt(3));
                    result.append("[").append(randomString).append("]");
                    lastEnd = bracketEnd + 1;
                    bracketStart = text.indexOf('[', lastEnd);
                } else {
                    break;
                }
            }
            result.append(text.substring(lastEnd));
            text = result.toString();
        }
        return text;
    }
    private String generateRandomString(int length) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(RANDOM_CHARS.charAt(random.nextInt(RANDOM_CHARS.length())));
        }
        return sb.toString();
    }
    @EventHandler
    private void onRender2D(Render2DEvent event) {
        if (!wheelActive) return;
        DrawContext context = event.drawContext;
        int scaledWidth = mc.getWindow().getScaledWidth();
        int scaledHeight = mc.getWindow().getScaledHeight();
        int centerX = scaledWidth / 2 + wheelX.get();
        int centerY = scaledHeight / 2 + wheelY.get();
        int radius = wheelRadius.get();
        renderWheel(context, centerX, centerY, radius);
    }
    private void renderWheel(DrawContext context, int centerX, int centerY, int radius) {
        updateModuleCache();
        drawFilledCircleOptimized(context, centerX, centerY, radius, backgroundColor.get());
        if (selectedSlot >= 0 && selectedSlot < 8) {
            drawWheelSectionOptimized(context, centerX, centerY, radius, selectedSlot);
        }
        for (int i = 0; i < 8; i++) {
            drawSectionLabel(context, centerX, centerY, radius, i);
        }
    }
    private void drawFilledCircleOptimized(DrawContext context, int centerX, int centerY, int radius, Color color) {
        int radiusSq = radius * radius;
        int packedColor = color.getPacked();
        for (int y = -radius; y <= radius; y++) {
            int ySq = y * y;
            int xMax = (int)Math.sqrt(radiusSq - ySq);
            if (xMax > 0) {
                context.fill(centerX - xMax, centerY + y, centerX + xMax + 1, centerY + y + 1, packedColor);
            }
        }
    }
    private void drawSectionDividers(DrawContext context, int centerX, int centerY, int radius) {
        int borderColorPacked = borderColor.get().getPacked();
        for (int i = 0; i < 8; i++) {
            double angle = Math.toRadians(i * 45 - 90);
            int endX = centerX + (int)(Math.cos(angle) * radius);
            int endY = centerY + (int)(Math.sin(angle) * radius);
            drawLineOptimized(context, centerX, centerY, endX, endY, borderColorPacked);
        }
    }
    private void drawCircleOutline(DrawContext context, int centerX, int centerY, int radius, Color color, int thickness) {
        for (int t = 0; t < thickness; t++) {
            int r = radius - t;
            if (r <= 0) continue;
            int x = 0;
            int y = r;
            int d = 3 - 2 * r;
            while (y >= x) {
                drawPixel(context, centerX + x, centerY + y, color.getPacked());
                drawPixel(context, centerX - x, centerY + y, color.getPacked());
                drawPixel(context, centerX + x, centerY - y, color.getPacked());
                drawPixel(context, centerX - x, centerY - y, color.getPacked());
                drawPixel(context, centerX + y, centerY + x, color.getPacked());
                drawPixel(context, centerX - y, centerY + x, color.getPacked());
                drawPixel(context, centerX + y, centerY - x, color.getPacked());
                drawPixel(context, centerX - y, centerY - x, color.getPacked());
                x++;
                if (d > 0) {
                    y--;
                    d = d + 4 * (x - y) + 10;
                } else {
                    d = d + 4 * x + 6;
                }
            }
        }
    }
    private void drawPixel(DrawContext context, int x, int y, int color) {
        context.fill(x, y, x + 1, y + 1, color);
    }
    private void drawWheelSectionOptimized(DrawContext context, int centerX, int centerY, int radius, int sectionIndex) {
        double startAngle = Math.toRadians(sectionIndex * 45 - 90 - 22.5);
        double endAngle = startAngle + Math.toRadians(45);
        int packedColor = selectedColor.get().getPacked();
        int segments = 8;
        double angleStep = (endAngle - startAngle) / segments;
        for (int i = 0; i < segments; i++) {
            double angle1 = startAngle + angleStep * i;
            double angle2 = startAngle + angleStep * (i + 1);
            int x1 = centerX + (int)(Math.cos(angle1) * radius);
            int y1 = centerY + (int)(Math.sin(angle1) * radius);
            int x2 = centerX + (int)(Math.cos(angle2) * radius);
            int y2 = centerY + (int)(Math.sin(angle2) * radius);
            fillTriangleOptimized(context, centerX, centerY, x1, y1, x2, y2, packedColor);
        }
    }
    private void fillTriangleOptimized(DrawContext context, int x0, int y0, int x1, int y1, int x2, int y2, int color) {
        if (y1 < y0) { int t = x0; x0 = x1; x1 = t; t = y0; y0 = y1; y1 = t; }
        if (y2 < y0) { int t = x0; x0 = x2; x2 = t; t = y0; y0 = y2; y2 = t; }
        if (y2 < y1) { int t = x1; x1 = x2; x2 = t; t = y1; y1 = y2; y2 = t; }
        if (y0 == y2) return;
        if (y1 > y0) {
            int dy1 = y1 - y0;
            int dy2 = y2 - y0;
            for (int y = y0; y <= y1; y++) {
                int xa = dy1 != 0 ? x0 + (x1 - x0) * (y - y0) / dy1 : x0;
                int xb = dy2 != 0 ? x0 + (x2 - x0) * (y - y0) / dy2 : x0;
                if (xa > xb) { int t = xa; xa = xb; xb = t; }
                context.fill(xa, y, xb + 1, y + 1, color);
            }
        }
        if (y2 > y1) {
            int dy1 = y2 - y1;
            int dy2 = y2 - y0;
            for (int y = y1 + 1; y <= y2; y++) {
                int xa = dy1 != 0 ? x1 + (x2 - x1) * (y - y1) / dy1 : x1;
                int xb = dy2 != 0 ? x0 + (x2 - x0) * (y - y0) / dy2 : x0;
                if (xa > xb) { int t = xa; xa = xb; xb = t; }
                context.fill(xa, y, xb + 1, y + 1, color);
            }
        }
    }
    private void drawLineOptimized(DrawContext context, int x0, int y0, int x1, int y1, int color) {
        int dx = Math.abs(x1 - x0);
        int dy = Math.abs(y1 - y0);
        int sx = x0 < x1 ? 1 : -1;
        int sy = y0 < y1 ? 1 : -1;
        int err = dx - dy;
        while (true) {
            context.fill(x0, y0, x0 + 1, y0 + 1, color);
            if (x0 == x1 && y0 == y1) break;
            int e2 = err << 1;
            if (e2 > -dy) {
                err -= dy;
                x0 += sx;
            }
            if (e2 < dx) {
                err += dx;
                y0 += sy;
            }
        }
    }
    private void drawThickLine(DrawContext context, int x0, int y0, int x1, int y1, int color, int thickness) {
        double angle = Math.atan2(y1 - y0, x1 - x0);
        double perpAngle = angle + Math.PI / 2;
        for (int t = -thickness/2; t <= thickness/2; t++) {
            int offsetX = (int)(Math.cos(perpAngle) * t);
            int offsetY = (int)(Math.sin(perpAngle) * t);
            drawLine(context, x0 + offsetX, y0 + offsetY, x1 + offsetX, y1 + offsetY, color);
        }
    }
    private void drawLine(DrawContext context, int x0, int y0, int x1, int y1, int color) {
        int dx = Math.abs(x1 - x0);
        int dy = Math.abs(y1 - y0);
        int sx = x0 < x1 ? 1 : -1;
        int sy = y0 < y1 ? 1 : -1;
        int err = dx - dy;
        int x = x0;
        int y = y0;
        while (true) {
            context.fill(x, y, x + 1, y + 1, color);
            if (x == x1 && y == y1) break;
            int e2 = 2 * err;
            if (e2 > -dy) {
                err -= dy;
                x += sx;
            }
            if (e2 < dx) {
                err += dx;
                y += sy;
            }
        }
    }
    private void updateModuleCache() {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastModuleCacheUpdate < MODULE_CACHE_INTERVAL) return;
        lastModuleCacheUpdate = currentTime;
        for (int i = 0; i < 8; i++) {
            SlotConfig slot = slots[i];
            if (slot.action.get() == MacroAction.TOGGLE_MODULE && !slot.moduleName.get().isEmpty()) {
                if (cachedModules[i] == null) {
                    cachedModules[i] = Modules.get().get(slot.moduleName.get());
                }
                if (cachedModules[i] != null) {
                    cachedModuleStates[i] = cachedModules[i].isActive();
                }
            } else {
                cachedModules[i] = null;
                cachedModuleStates[i] = false;
            }
        }
    }
    private void drawSectionLabel(DrawContext context, int centerX, int centerY, int radius, int sectionIndex) {
        SlotConfig slot = slots[sectionIndex];
        double midAngle = Math.toRadians(sectionIndex * 45 - 90);
        int labelRadius = radius * 2 / 3;
        int labelX = centerX + (int)(Math.cos(midAngle) * labelRadius);
        int labelY = centerY + (int)(Math.sin(midAngle) * labelRadius);
        boolean isModuleActive = slot.action.get() == MacroAction.TOGGLE_MODULE &&
                                 cachedModules[sectionIndex] != null &&
                                 cachedModuleStates[sectionIndex];
        boolean hasIcon = showIcons.get() && slot.icon.get() != Items.AIR;
        boolean hasText = showText.get() && !getSlotLabel(slot, sectionIndex).isEmpty();
        if (!hasIcon && !hasText) return;
        float iconScaleValue = iconScale.get().floatValue();
        float textScaleValue = textScale.get().floatValue();
        int iconSize = (int)(16 * iconScaleValue);
        int spacing = 2;
        String label = getSlotLabel(slot, sectionIndex);
        int textHeight = (int)(mc.textRenderer.fontHeight * textScaleValue);
        int totalHeight = 0;
        if (hasIcon) totalHeight += iconSize;
        if (hasIcon && hasText) totalHeight += spacing;
        if (hasText) totalHeight += textHeight;
        int currentY = labelY - totalHeight / 2;
        if (hasIcon) {
            Item item = slot.icon.get();
            ItemStack stack = new ItemStack(item);
            context.getMatrices().pushMatrix();
            context.getMatrices().translate(labelX, currentY);
            context.getMatrices().scale(iconScaleValue, iconScaleValue);
            context.drawItem(stack, -8, 0);
            context.getMatrices().popMatrix();
            currentY += iconSize + spacing;
        }
        if (hasText) {
            Color textColor = isModuleActive ? moduleActiveColor.get() : this.textColor.get();
            int textWidth = mc.textRenderer.getWidth(label);
            context.getMatrices().pushMatrix();
            context.getMatrices().translate(labelX, currentY);
            context.getMatrices().scale(textScaleValue, textScaleValue);
            context.drawText(mc.textRenderer, label,
                -textWidth / 2,
                0,
                textColor.getPacked(), false);
            context.getMatrices().popMatrix();
        }
    }
    private String getSlotLabel(SlotConfig slot, int slotIndex) {
        MacroAction action = slot.action.get();
        if (action == MacroAction.NONE) return "";
        String custom = slot.customText.get();
        if (!custom.isEmpty()) return custom;
        switch (action) {
            case TOGGLE_MODULE:
                String module = slot.moduleName.get();
                if (module.isEmpty()) return "";
                if (cachedModules[slotIndex] != null) {
                    String state = cachedModuleStates[slotIndex] ? " ✓" : "";
                    return (module.length() > 8 ? module.substring(0, 8) : module) + state;
                }
                return module.length() > 10 ? module.substring(0, 8) + ".." : module;
            case SEND_MESSAGE:
                String msg = slot.message.get();
                return msg.isEmpty() ? "" :
                    (msg.length() > 10 ? msg.substring(0, 8) + ".." : msg);
            case RUN_COMMAND:
                String cmd = slot.command.get();
                return cmd.isEmpty() ? "" :
                    "/" + (cmd.length() > 9 ? cmd.substring(0, 7) + ".." : cmd);
            default:
                return "";
        }
    }
    private class SlotConfig {
        public final Setting<MacroAction> action;
        public final Setting<String> moduleName;
        public final Setting<String> message;
        public final Setting<String> command;
        public final Setting<String> customText;
        public final Setting<Item> icon;
        public SlotConfig(int index) {
            String slotName = SLOT_NAMES[index];
            action = sgSlots.add(new EnumSetting.Builder<MacroAction>()
                .name(slotName.toLowerCase().replace("-", "") + "-action")
                .description("Action for " + slotName + " slot")
                .defaultValue(MacroAction.NONE)
                .build());
            icon = sgSlots.add(new ItemSetting.Builder()
                .name(slotName.toLowerCase().replace("-", "") + "-icon")
                .description("Icon item for " + slotName + " slot")
                .defaultValue(Items.AIR)
                .visible(() -> action.get() != MacroAction.NONE)
                .build());
            customText = sgSlots.add(new StringSetting.Builder()
                .name(slotName.toLowerCase().replace("-", "") + "-custom-text")
                .description("Custom display text for " + slotName + " slot (leave empty for auto)")
                .defaultValue("")
                .visible(() -> action.get() != MacroAction.NONE)
                .build());
            moduleName = sgSlots.add(new StringSetting.Builder()
                .name(slotName.toLowerCase().replace("-", "") + "-module")
                .description("Module name for " + slotName)
                .defaultValue("")
                .visible(() -> action.get() == MacroAction.TOGGLE_MODULE)
                .build());
            message = sgSlots.add(new StringSetting.Builder()
                .name(slotName.toLowerCase().replace("-", "") + "-message")
                .description("Message for " + slotName + " (use [] or [RANDOM] for random text)")
                .defaultValue("")
                .visible(() -> action.get() == MacroAction.SEND_MESSAGE)
                .build());
            command = sgSlots.add(new StringSetting.Builder()
                .name(slotName.toLowerCase().replace("-", "") + "-command")
                .description("Command for " + slotName + " (without /)")
                .defaultValue("")
                .visible(() -> action.get() == MacroAction.RUN_COMMAND)
                .build());
        }
    }
    public enum MacroAction {
        NONE("None"),
        TOGGLE_MODULE("Toggle Module"),
        SEND_MESSAGE("Send Message"),
        RUN_COMMAND("Run Command");
        private final String name;
        MacroAction(String name) {
            this.name = name;
        }
        @Override
        public String toString() {
            return name;
        }
    }
}