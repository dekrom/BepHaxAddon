package bep.hax.util;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.*;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.ColorHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4f;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.render.GameRenderer;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.systems.modules.render.blockesp.ESPBlockData;
import static meteordevelopment.meteorclient.MeteorClient.mc;
public class RenderUtils {
    private static final VertexConsumerProvider.Immediate vertex = VertexConsumerProvider.immediate(new BufferAllocator(2048));
    public static boolean shouldRenderBox(ESPBlockData esp) {
        return switch (esp.shapeMode) {
            case Both -> esp.lineColor.a > 0 || esp.sideColor.a > 0;
            case Lines -> esp.lineColor.a > 0;
            case Sides -> esp.sideColor.a > 0;
        };
    }
    public static boolean shouldRenderTracer(ESPBlockData esp) {
        return esp.tracer && esp.tracerColor.a > 0;
    }
    public static void renderTracerTo(Render3DEvent event, @NotNull BlockPos pos, Color tracerColor) {
        Vec3d tracerPos = pos.toCenterPos();
        event.renderer.line(
            meteordevelopment.meteorclient.utils.render.RenderUtils.center.x,
            meteordevelopment.meteorclient.utils.render.RenderUtils.center.y,
            meteordevelopment.meteorclient.utils.render.RenderUtils.center.z,
            tracerPos.x, tracerPos.y, tracerPos.z, tracerColor
        );
    }
    public static void renderBlock(Render3DEvent event, BlockPos pos, Color lineColor, Color sideColor, ShapeMode mode) {
        event.renderer.box(
            pos.getX(), pos.getY(), pos.getZ(),
            pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1, sideColor, lineColor, mode, 0
        );
    }
    public static void renderBlock(Render3DEvent event, BlockPos pos, Color color) {
        event.renderer.box(
            pos.getX(), pos.getY(), pos.getZ(),
            pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1, color, color, ShapeMode.Lines, 0
        );
    }
    public static void text(String text, MatrixStack stack, float x, float y, int color) {
        mc.textRenderer.draw(text, x, y, color, false, stack.peek().getPositionMatrix(), vertex, TextRenderer.TextLayerType.NORMAL, 0, 15728880);
        vertex.draw();
    }
    public enum RenderMode {
        Solid,
        Fade,
        Pulse,
        Shrink
    }
}