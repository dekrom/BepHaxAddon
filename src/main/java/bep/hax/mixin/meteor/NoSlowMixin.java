package bep.hax.mixin.meteor;
import bep.hax.mixin.accessor.PlayerInventoryAccessor;
import bep.hax.util.InventoryManager;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.movement.NoSlow;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.BlockState;
import net.minecraft.block.CobwebBlock;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.ArrayList;
import java.util.List;
import static meteordevelopment.meteorclient.MeteorClient.mc;
@Mixin(value = NoSlow.class, remap = false)
public abstract class NoSlowMixin {
    @Shadow @Final protected SettingGroup sgGeneral;
    @Unique private Setting<Boolean> bephax$grimBypass;
    @Unique private Setting<Boolean> bephax$grimV3Bypass;
    @Unique private Setting<Boolean> bephax$grimWebBypass;
    @Unique private Setting<Boolean> bephax$strictMode;
    @Unique private Setting<Boolean> bephax$disableOnElytra;
    @Unique private Setting<Double> bephax$inputMultiplier;
    @Unique private Setting<Double> bephax$grimV3Multiplier;
    @Unique private boolean bephax$sneaking = false;
    @Unique private int bephax$sequenceId = 0;
    @Inject(method = "<init>", at = @At("TAIL"))
    private void onInit(CallbackInfo ci) {
        bephax$grimBypass = sgGeneral.add(new BoolSetting.Builder()
            .name("grim-bypass")
            .description("Bypasses GrimAC using opposite hand interaction packets")
            .defaultValue(false)
            .build()
        );
        bephax$grimV3Bypass = sgGeneral.add(new BoolSetting.Builder()
            .name("grim-v3-bypass")
            .description("Bypasses GrimAC V3 using item use timing checks")
            .defaultValue(false)
            .build()
        );
        bephax$grimWebBypass = sgGeneral.add(new BoolSetting.Builder()
            .name("grim-web-bypass")
            .description("Bypasses GrimAC web slowdown using block break packets")
            .defaultValue(false)
            .build()
        );
        bephax$strictMode = sgGeneral.add(new BoolSetting.Builder()
            .name("strict-mode")
            .description("Strict NCP bypass for ground slowdowns")
            .defaultValue(false)
            .build()
        );
        bephax$disableOnElytra = sgGeneral.add(new BoolSetting.Builder()
            .name("disable-on-elytra")
            .description("Disables NoSlow while flying with an elytra")
            .defaultValue(true)
            .build()
        );
        bephax$inputMultiplier = sgGeneral.add(new DoubleSetting.Builder()
            .name("input-multiplier")
            .description("Multiplier for movement input (Grim bypass mode)")
            .defaultValue(5.0)
            .min(1.0)
            .max(10.0)
            .sliderMin(1.0)
            .sliderMax(10.0)
            .visible(() -> !bephax$grimV3Bypass.get())
            .build()
        );
        bephax$grimV3Multiplier = sgGeneral.add(new DoubleSetting.Builder()
            .name("grimv3-multiplier")
            .description("Multiplier for GrimV3 bypass (try 3.0-5.0 if detected)")
            .defaultValue(5.0)
            .min(1.0)
            .max(10.0)
            .sliderRange(1.0, 10.0)
            .decimalPlaces(1)
            .visible(() -> bephax$grimV3Bypass.get())
            .build()
        );
    }
    @EventHandler
    @Inject(method = "onPreTick", at = @At("HEAD"), cancellable = true, require = 0)
    private void bephax$onPreTick(TickEvent.Pre event, CallbackInfo ci) {
        if (mc.player == null || mc.world == null) return;
        NoSlow noSlow = (NoSlow) (Object) this;
        if (!noSlow.isActive()) return;
        if (bephax$disableOnElytra.get() && mc.player.isGliding()) return;
        if (bephax$grimBypass.get() && mc.player.isUsingItem() && !mc.player.isSneaking()) {
            if (mc.player.getActiveHand() == Hand.OFF_HAND && bephax$checkStack(mc.player.getMainHandStack())) {
                mc.getNetworkHandler().sendPacket(new PlayerInteractItemC2SPacket(Hand.MAIN_HAND, bephax$sequenceId++, mc.player.getYaw(), mc.player.getPitch()));
            } else if (bephax$checkStack(mc.player.getOffHandStack())) {
                mc.getNetworkHandler().sendPacket(new PlayerInteractItemC2SPacket(Hand.OFF_HAND, bephax$sequenceId++, mc.player.getYaw(), mc.player.getPitch()));
            }
        }
        if ((bephax$grimBypass.get() || bephax$grimV3Bypass.get()) && bephax$grimWebBypass.get()) {
            Box bb = bephax$grimBypass.get() ? mc.player.getBoundingBox().expand(1.0) : mc.player.getBoundingBox();
            for (BlockPos pos : bephax$getIntersectingWebs(bb)) {
                mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK, pos, Direction.DOWN));
            }
        }
    }
    @Unique
    private boolean bephax$checkStack(ItemStack stack) {
        return !stack.getComponents().contains(DataComponentTypes.FOOD)
            && stack.getItem() != Items.BOW
            && stack.getItem() != Items.CROSSBOW
            && stack.getItem() != Items.SHIELD;
    }
    @Unique
    private boolean bephax$checkSlowed() {
        if (mc.player == null) return false;
        if (bephax$grimV3Bypass.get() && !bephax$checkGrimNew()) {
            return false;
        }
        return !mc.player.isRiding()
            && !mc.player.isSneaking()
            && (mc.player.isUsingItem() || (mc.player.isBlocking() && !bephax$grimV3Bypass.get() && !bephax$grimBypass.get()));
    }
    @Unique
    private boolean bephax$checkGrimNew() {
        if (mc.player == null) return true;
        return !mc.player.isSneaking()
            && !mc.player.isCrawling()
            && !mc.player.isRiding()
            && (mc.player.getItemUseTimeLeft() < 5 || ((mc.player.getItemUseTime() > 1) && mc.player.getItemUseTime() % 2 != 0));
    }
    @Unique
    private List<BlockPos> bephax$getIntersectingWebs(Box boundingBox) {
        List<BlockPos> blocks = new ArrayList<>();
        if (mc.world == null) return blocks;
        int minX = (int) Math.floor(boundingBox.minX);
        int minY = (int) Math.floor(boundingBox.minY);
        int minZ = (int) Math.floor(boundingBox.minZ);
        int maxX = (int) Math.ceil(boundingBox.maxX);
        int maxY = (int) Math.ceil(boundingBox.maxY);
        int maxZ = (int) Math.ceil(boundingBox.maxZ);
        for (int x = minX; x < maxX; x++) {
            for (int y = minY; y < maxY; y++) {
                for (int z = minZ; z < maxZ; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = mc.world.getBlockState(pos);
                    if (state.getBlock() instanceof CobwebBlock) {
                        blocks.add(pos);
                    }
                }
            }
        }
        return blocks;
    }
    @Unique
    @EventHandler
    private void onPacketSend(PacketEvent.Send event) {
        if (mc.player == null || mc.world == null) return;
        NoSlow noSlow = (NoSlow) (Object) this;
        if (!noSlow.isActive()) return;
        if (bephax$disableOnElytra.get() && mc.player.isGliding()) return;
        if (bephax$strictMode.get() && event.packet instanceof PlayerMoveC2SPacket packet) {
            if (!packet.changesPosition()) return;
            if (!bephax$checkSlowed()) return;
            InventoryManager.getInstance().setSlotForced(((PlayerInventoryAccessor) mc.player.getInventory()).getSelectedSlot());
        }
    }
}