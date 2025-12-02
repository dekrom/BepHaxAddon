package bep.hax.mixin;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.systems.modules.Modules;
import bep.hax.modules.RespawnPointBlocker;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(MinecraftClient.class)
public class RespawnPointBlockerSpamMixin {
    private long lastSpamTime = 0;
    private static final long SPAM_COOLDOWN = 750;
    @Inject(method = "tick", at = @At("HEAD"))
    private void onTick(CallbackInfo ci) {
        RespawnPointBlocker module = Modules.get().get(RespawnPointBlocker.class);
        if (!module.isActive()) return;
        ClientPlayerEntity player = MeteorClient.mc.player;
        if (player == null || MeteorClient.mc.world == null) return;
        HitResult hitResult = MeteorClient.mc.crosshairTarget;
        if (hitResult instanceof BlockHitResult) {
            BlockHitResult blockHitResult = (BlockHitResult) hitResult;
            BlockPos blockPos = blockHitResult.getBlockPos();
            BlockState blockState = MeteorClient.mc.world.getBlockState(blockPos);
            Block block = blockState.getBlock();
            if (isRespawnPointBlock(block)) {
                boolean shouldBlock = false;
                if (isBed(block) && module.blockBeds.get()) {
                    shouldBlock = true;
                } else if (block == Blocks.RESPAWN_ANCHOR && module.blockRespawnAnchors.get()) {
                    shouldBlock = true;
                }
                if (shouldBlock && System.currentTimeMillis() - lastSpamTime >= SPAM_COOLDOWN) {
                    MeteorClient.mc.crosshairTarget = null;
                    lastSpamTime = System.currentTimeMillis();
                }
            }
        }
    }
    private boolean isRespawnPointBlock(Block block) {
        return isBed(block) || block == Blocks.RESPAWN_ANCHOR;
    }
    private boolean isBed(Block block) {
        return block == Blocks.WHITE_BED || block == Blocks.ORANGE_BED || block == Blocks.MAGENTA_BED ||
               block == Blocks.LIGHT_BLUE_BED || block == Blocks.YELLOW_BED || block == Blocks.LIME_BED ||
               block == Blocks.PINK_BED || block == Blocks.GRAY_BED || block == Blocks.LIGHT_GRAY_BED ||
               block == Blocks.CYAN_BED || block == Blocks.PURPLE_BED || block == Blocks.BLUE_BED ||
               block == Blocks.BROWN_BED || block == Blocks.GREEN_BED || block == Blocks.RED_BED ||
               block == Blocks.BLACK_BED;
    }
}