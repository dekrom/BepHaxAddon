package bep.hax.modules;
import meteordevelopment.meteorclient.events.entity.player.InteractBlockEvent;
import meteordevelopment.meteorclient.events.entity.player.StartBreakingBlockEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.EventPriority;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import static meteordevelopment.meteorclient.MeteorClient.mc;
public class StashMoverSelectionHandler {
    private static StashMoverSelectionHandler INSTANCE;
    public static void init() {
        if (INSTANCE == null) {
            INSTANCE = new StashMoverSelectionHandler();
            meteordevelopment.meteorclient.MeteorClient.EVENT_BUS.subscribe(INSTANCE);
            System.out.println("[StashMover] Selection handler initialized and subscribed to events");
            if (meteordevelopment.meteorclient.MeteorClient.mc != null && meteordevelopment.meteorclient.MeteorClient.mc.player != null) {
                meteordevelopment.meteorclient.utils.player.ChatUtils.info("[StashMover] Selection handler ready");
            }
        } else {
            System.out.println("[StashMover] Selection handler already initialized");
        }
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    private void onInteractBlock(InteractBlockEvent event) {
        StashMover module = Modules.get().get(StashMover.class);
        if (module == null) return;
        if (!module.isSelecting()) return;
        if (event.hand != Hand.MAIN_HAND) return;
        event.cancel();
        module.handleBlockSelectionPublic(event.result.getBlockPos());
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    private void onStartBreakingBlock(StartBreakingBlockEvent event) {
        StashMover module = Modules.get().get(StashMover.class);
        if (module == null) return;
        if (!module.isSelecting()) return;
        event.cancel();
        module.handleBlockSelectionPublic(event.blockPos);
    }
    private boolean wasSelecting = false;
    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;
        StashMover module = Modules.get().get(StashMover.class);
        if (module == null) return;
        if (module.isSelecting()) {
            if (!wasSelecting) {
                wasSelecting = true;
            }
            if (mc.options.attackKey.isPressed()) {
                mc.options.attackKey.setPressed(false);
                if (mc.crosshairTarget != null && mc.crosshairTarget.getType() == HitResult.Type.BLOCK) {
                    BlockHitResult hit = (BlockHitResult) mc.crosshairTarget;
                    BlockPos pos = hit.getBlockPos();
                    module.handleBlockSelectionPublic(pos);
                }
            }
            if (mc.options.inventoryKey.wasPressed()) {
                module.cancelSelection();
                meteordevelopment.meteorclient.utils.player.ChatUtils.info("§cSelection cancelled");
                return;
            }
        } else {
            wasSelecting = false;
        }
    }
    @EventHandler
    private void onRender3D(Render3DEvent event) {
        StashMover module = Modules.get().get(StashMover.class);
        if (module == null) return;
        if (module.getSelectionMode() != StashMover.SelectionMode.NONE) {
            BlockPos selectionPos1 = module.getSelectionPos1();
            if (selectionPos1 != null) {
                BlockPos currentPos = mc.crosshairTarget != null && mc.crosshairTarget.getType() == HitResult.Type.BLOCK ?
                    ((BlockHitResult)mc.crosshairTarget).getBlockPos() : mc.player.getBlockPos();
                Box selectionBox = new Box(
                    Math.min(selectionPos1.getX(), currentPos.getX()),
                    Math.min(selectionPos1.getY(), currentPos.getY()),
                    Math.min(selectionPos1.getZ(), currentPos.getZ()),
                    Math.max(selectionPos1.getX(), currentPos.getX()) + 1,
                    Math.max(selectionPos1.getY(), currentPos.getY()) + 1,
                    Math.max(selectionPos1.getZ(), currentPos.getZ()) + 1
                );
                boolean isInput = module.getSelectionMode() == StashMover.SelectionMode.INPUT_FIRST ||
                                 module.getSelectionMode() == StashMover.SelectionMode.INPUT_SECOND;
                SettingColor color = isInput ?
                    new SettingColor(0, 255, 0, 100) :
                    new SettingColor(0, 100, 255, 100);
                event.renderer.box(selectionBox, color, color, ShapeMode.Both, 0);
                Box corner1 = new Box(
                    selectionPos1.getX(), selectionPos1.getY(), selectionPos1.getZ(),
                    selectionPos1.getX() + 1, selectionPos1.getY() + 1, selectionPos1.getZ() + 1
                );
                event.renderer.box(corner1, new SettingColor(255, 255, 0, 200),
                                 new SettingColor(255, 255, 0, 100), ShapeMode.Both, 0);
            }
        }
        module.renderAreas(event);
    }
}