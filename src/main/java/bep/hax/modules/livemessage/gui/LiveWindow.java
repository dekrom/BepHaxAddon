package bep.hax.modules.livemessage.gui;
import bep.hax.modules.livemessage.LiveMessage;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;
import java.util.ArrayList;
import java.util.List;
import static bep.hax.modules.livemessage.gui.GuiUtil.*;
public class LiveWindow {
    private static final Identifier ICONS_TEXTURE = Identifier.of("livemessage", "icons.png");
    public static int titlebarHeight = 17;
    public int x;
    public int y;
    public int w = 400;
    public int h = 250;
    public int minw = 100;
    public int maxw = 9999;
    public int minh = 100;
    public int maxh = 9999;
    public int lastMouseX = 0;
    public int lastMouseY = 0;
    public String title = "Sample text";
    public boolean active = true;
    public int dragX;
    public int dragY;
    public boolean clicked = false;
    public boolean dragging = false;
    public boolean resizing = false;
    public boolean closeButton = true;
    public int primaryColor = 0;
    public TextRenderer fontRenderer;
    boolean animateIn = true;
    long animateInStart;
    protected MinecraftClient mc;
    LiveWindow() {
        mc = MinecraftClient.getInstance();
        x = (int) (Math.random() * (LivemessageGui.screenWidth - w));
        y = (int) (Math.random() * (LivemessageGui.screenHeight - h));
        fontRenderer = mc.textRenderer;
        primaryColor = GuiUtil.getWindowColor(mc.player.getUuid());
        if (LivemessageGui.liveWindows.size() > 0)
            LivemessageGui.liveWindows.get(LivemessageGui.liveWindows.size() - 1).deactivateWindow();
        animateInStart = System.currentTimeMillis();
    }
    List<LiveButton> liveButtons = new ArrayList<>();
    class LiveButton {
        public int id;
        public int bx;
        public int by;
        public int bw;
        public int bh;
        public boolean negativeX;
        public String btnText;
        public String tooltipText;
        public int iconIndex = -1;
        public boolean iconActive = false;
        public int iconColor = -1;
        public int idleColor = getSingleRGB(64);
        public int hoverColor = getSingleRGB(96);
        public int textColor = getSingleRGB(255);
        public Runnable action;
        LiveButton(int id, int x, int y, int w, int h, boolean negativeX, String btnText, String tooltipText, Runnable action) {
            this.id = id;
            this.bx = x;
            this.by = y;
            this.bw = w;
            this.bh = h;
            this.btnText = btnText;
            this.tooltipText = tooltipText;
            this.action = action;
            this.negativeX = negativeX;
        }
        LiveButton(int id, int x, int y, int w, int h, boolean negativeX, int iconIndex, String tooltipText, Runnable action) {
            this(id, x, y, w, h, negativeX, "", tooltipText, action);
            this.iconIndex = iconIndex;
        }
        public int gx() {
            return negativeX ? (w - bx) : bx;
        }
        public void runIfClicked() {
            if (isMouseOver())
                action.run();
        }
        public boolean isMouseOver() {
            return mouseInRect(gx(), by, bw, bh, lastMouseX, lastMouseY);
        }
        public void draw(DrawContext context) {
            int bgColor = isMouseOver() ? getSingleRGB(96) : getSingleRGB(64);
            drawRect(context, gx(), by, bw, bh, bgColor);
            int borderColor = isMouseOver() ? getSingleRGB(192) : getSingleRGB(96);
            context.fill(gx(), by, gx() + bw, by + 1, borderColor);
            context.fill(gx(), by + bh - 1, gx() + bw, by + bh, borderColor);
            context.fill(gx(), by, gx() + 1, by + bh, borderColor);
            context.fill(gx() + bw - 1, by, gx() + bw, by + bh, borderColor);
            if (iconIndex >= 0) {
                int iconX = gx() + 1;
                int iconY = by + 1;
                int texU = iconIndex * 9;
                context.drawTexture(RenderPipelines.GUI_TEXTURED, ICONS_TEXTURE,
                    iconX, iconY,
                    texU, 0,
                    9, 9,
                    45, 9);
                if (iconActive) {
                    int color = (iconColor != -1) ? iconColor : LiveWindow.this.primaryColor;
                    int argb = 0xFF000000 | (color & 0x00FFFFFF);
                    context.drawTexture(RenderPipelines.GUI_TEXTURED, ICONS_TEXTURE,
                        iconX, iconY,
                        texU, 0,
                        9, 9,
                        45, 9,
                        argb);
                }
            } else if (!btnText.isEmpty()) {
                context.drawText(fontRenderer, btnText, gx() + bw / 2 - fontRenderer.getWidth(btnText) / 2, by + 2, textColor, false);
            }
        }
        public void drawTooltips(DrawContext context) {
            if (!tooltipText.isEmpty() && isMouseOver() && active)
                drawTooltip(context, tooltipText);
        }
        private void drawTooltip(DrawContext context, String text) {
            if (!active) return;
            int textWidth = fontRenderer.getWidth(text);
            int textHeight = fontRenderer.fontHeight;
            int padding = 3;
            int tooltipWidth = textWidth + padding * 2;
            int tooltipHeight = textHeight + padding * 2;
            int x = lastMouseX - LiveWindow.this.x + 10;
            int y = lastMouseY - LiveWindow.this.y - tooltipHeight - 5;
            if (x + tooltipWidth > w) x = w - tooltipWidth;
            if (x < 0) x = 0;
            if (y < 0) y = lastMouseY - LiveWindow.this.y + 15;
            drawRect(context, x - 1, y - 1, tooltipWidth + 2, tooltipHeight + 2, getSingleRGB(255));
            drawRect(context, x, y, tooltipWidth, tooltipHeight, getRGB(32, 32, 32));
            context.drawText(fontRenderer, text, x + padding, y + padding, getSingleRGB(255), false);
        }
    }
    public void mouseReleased(int mouseX, int mouseY, int state) {
        dragging = false;
        resizing = false;
        clicked = false;
    }
    public void mouseMove(int mouseX, int mouseY) {
        lastMouseX = mouseX;
        lastMouseY = mouseY;
    }
    public boolean mouseInRect(int x, int y, int w, int h, int mouseX, int mouseY) {
        return (mouseX > this.x + x && mouseX < this.x + x + w && mouseY > this.y + y && mouseY < this.y + y + h);
    }
    public void keyTyped(char typedChar, int keyCode) {
        if (keyCode == GLFW.GLFW_KEY_TAB && LivemessageGui.liveWindows.size() > 1) {
            if (GLFW.glfwGetKey(mc.getWindow().getHandle(), GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS) {
                LiveWindow tempWindow = LivemessageGui.liveWindows.get(0);
                LivemessageGui.liveWindows.remove(0);
                tempWindow.activateWindow();
                LivemessageGui.liveWindows.add(tempWindow);
            } else {
                LivemessageGui.liveWindows.removeIf(it -> it == this);
                LivemessageGui.liveWindows.add(0, this);
                LivemessageGui.liveWindows.get(LivemessageGui.liveWindows.size() - 1).activateWindow();
            }
            deactivateWindow();
        }
    }
    protected net.minecraft.client.input.KeyInput lastKeyInput;
    protected net.minecraft.client.input.CharInput lastCharInput;
    public void handleKeyInput(net.minecraft.client.input.KeyInput input) {
        this.lastKeyInput = input;
    }
    public void handleCharInput(net.minecraft.client.input.CharInput input) {
        this.lastCharInput = input;
    }
    public void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        clicked = true;
        if (closeButton && mouseX > x + w - 13 && mouseX < x + w - 2 && mouseY > y + 3 && mouseY < y + 14) {
            if (LivemessageGui.liveWindows.size() > 1) {
                LivemessageGui.liveWindows.get(LivemessageGui.liveWindows.size() - 2).activateWindow();
                LivemessageGui.liveWindows.removeIf(it -> it == this);
            }
        }
        if (mouseX > x && mouseX < x + w && mouseY > y && mouseY < y + 20) {
            dragging = true;
            resizing = false;
            dragX = mouseX - x;
            dragY = mouseY - y;
        } else if (mouseX > x + w - 7 && mouseX < x + w + 3 && mouseY > y + h - 7 && mouseY < y + h + 3) {
            dragging = false;
            resizing = true;
            dragX = mouseX - x - w;
            dragY = mouseY - y - h;
        }
    }
    public boolean mouseInWindow(int mouseX, int mouseY) {
        return (mouseX > x && mouseX < x + w && mouseY > y && mouseY < y + h);
    }
    public void mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
    }
    public void handleMouseDrag(double mouseX, double mouseY) {
        if (dragging || resizing) {
            if (dragging) {
                x = Math.max(0, (int)mouseX - dragX);
                y = Math.max(0, (int)mouseY - dragY);
            } else if (resizing) {
                w = MathHelper.clamp((int)mouseX - dragX - x, minw, maxw);
                h = MathHelper.clamp((int)mouseY - dragY - y, minh, maxh);
            }
        }
    }
    public void mouseWheel(int mWheelState) {
    }
    public void activateWindow() {
        active = true;
    }
    public void deactivateWindow() {
        active = false;
        mouseReleased(lastMouseX, lastMouseY, 0);
    }
    public void preDrawWindow(DrawContext context) {
        if (fontRenderer == null)
            fontRenderer = mc.textRenderer;
        if (x + w > LivemessageGui.screenWidth)
            x = LivemessageGui.screenWidth - w;
        if (y + h > LivemessageGui.screenHeight)
            y = LivemessageGui.screenHeight - h;
        if (x < 0) x = 0;
        if (y < 0) y = 0;
        if (x + w > LivemessageGui.screenWidth)
            w = Math.max(LivemessageGui.screenWidth, minw);
        if (y + h > LivemessageGui.screenHeight)
            h = Math.max(LivemessageGui.screenHeight, minh);
        context.getMatrices().translate((float)x, (float)y);
        int bgColor = getRGB(32, 32, 32);
        int fgColor = active ? primaryColor : getRGB(128, 128, 128);
        drawWindow(context, bgColor, fgColor);
        context.getMatrices().translate((float)-x, (float)-y);
    }
    public void drawTextFields(DrawContext context) {
    }
    public void drawWindow(DrawContext context, int bgColor, int fgColor) {
        drawRect(context, 0, 0, w, h, bgColor);
        drawRectOutline(context, 0, 0, w, h, fgColor);
        drawRect(context, 0, 0, w, titlebarHeight, fgColor);
        context.drawText(fontRenderer, title, 5, 5, 0xFFFFFF, false);
        if (closeButton) {
            drawRect(context, w - 13, 3, 11, 11, bgColor);
            int closeX = w - 13;
            int closeY = 3;
            int xColor = getRGB(255, 64, 64);
            context.fill(closeX + 3, closeY + 3, closeX + 4, closeY + 9, xColor);
            context.fill(closeX + 4, closeY + 4, closeX + 5, closeY + 8, xColor);
            context.fill(closeX + 7, closeY + 3, closeX + 8, closeY + 9, xColor);
            context.fill(closeX + 6, closeY + 4, closeX + 7, closeY + 8, xColor);
        }
        drawRectHalf(context, w - 6, h - 6, 6, 6, false, fgColor);
    }
    public void drawRectHalf(DrawContext context, int x, int y, int w, int h, boolean top, int color) {
        if (top) {
            context.fill(x, y, x + w, y + h / 2, color);
        } else {
            context.fill(x + w / 2, y + h / 2, x + w, y + h, color);
        }
    }
    public void drawRectOutline(DrawContext context, int x, int y, int w, int h, int color) {
        context.fill(x, y, x + w, y + 1, color);
        context.fill(x, y + h - 1, x + w, y + h, color);
        context.fill(x, y, x + 1, y + h, color);
        context.fill(x + w - 1, y, x + w, y + h, color);
    }
}