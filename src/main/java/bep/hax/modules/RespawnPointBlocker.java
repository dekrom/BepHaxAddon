package bep.hax.modules;
import bep.hax.Bep;
import meteordevelopment.meteorclient.events.entity.player.InteractBlockEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.SoundEventListSetting;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.EventPriority;
import net.minecraft.block.Block;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import java.util.List;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
public class RespawnPointBlocker extends Module {
    private final SettingGroup sgGeneral = settings.createGroup("General");
    private final SettingGroup sgFeedback = settings.createGroup("Feedback");
    public final Setting<Boolean> blockBeds = sgGeneral.add(new BoolSetting.Builder()
        .name("block-beds")
        .description("Prevents setting respawn points with beds.")
        .defaultValue(true)
        .build()
    );
    public final Setting<Boolean> blockRespawnAnchors = sgGeneral.add(new BoolSetting.Builder()
        .name("block-respawn-anchors")
        .description("Prevents setting respawn points with respawn anchors.")
        .defaultValue(true)
        .build()
    );
    public final Setting<Boolean> chatFeedback = sgFeedback.add(new BoolSetting.Builder()
        .name("chat-feedback")
        .description("Sends a message in chat when interaction is blocked.")
        .defaultValue(true)
        .build()
    );
    public final Setting<Boolean> soundFeedback = sgFeedback.add(new BoolSetting.Builder()
        .name("sound-feedback")
        .description("Plays a sound when interaction is blocked.")
        .defaultValue(true)
        .build()
    );
    public final Setting<List<SoundEvent>> feedbackSound = sgFeedback.add(new SoundEventListSetting.Builder()
        .name("feedback-sound")
        .description("Sound to play when interaction is blocked. Only the first sound in the list will be played.")
        .defaultValue(SoundEvents.ENTITY_VILLAGER_NO)
        .build()
    );
    public final Setting<Integer> soundVolume = sgFeedback.add(new IntSetting.Builder()
        .name("sound-volume")
        .description("Volume of the feedback sound.")
        .defaultValue(100)
        .range(0, 200)
        .sliderRange(0, 200)
        .build()
    );
    public RespawnPointBlocker() {
        super(Bep.CATEGORY, "respawn-point-blocker", "Prevents setting respawn points by blocking bed and respawn anchor interactions.");
    }
    @EventHandler(priority = EventPriority.HIGH)
    private void onInteractBlock(InteractBlockEvent event) {
        BlockHitResult hitResult = event.result;
        BlockPos blockPos = hitResult.getBlockPos();
        BlockState blockState = mc.world.getBlockState(blockPos);
        Block block = blockState.getBlock();
        if (isRespawnPointBlock(block)) {
            boolean shouldBlock = false;
            String blockName = "";
            if (block == Blocks.WHITE_BED || block == Blocks.ORANGE_BED || block == Blocks.MAGENTA_BED ||
                block == Blocks.LIGHT_BLUE_BED || block == Blocks.YELLOW_BED || block == Blocks.LIME_BED ||
                block == Blocks.PINK_BED || block == Blocks.GRAY_BED || block == Blocks.LIGHT_GRAY_BED ||
                block == Blocks.CYAN_BED || block == Blocks.PURPLE_BED || block == Blocks.BLUE_BED ||
                block == Blocks.BROWN_BED || block == Blocks.GREEN_BED || block == Blocks.RED_BED ||
                block == Blocks.BLACK_BED) {
                if (blockBeds.get()) {
                    shouldBlock = true;
                    blockName = "Bed";
                }
            } else if (block == Blocks.RESPAWN_ANCHOR) {
                if (blockRespawnAnchors.get()) {
                    shouldBlock = true;
                    blockName = "Respawn Anchor";
                }
            }
            if (shouldBlock) {
                event.cancel();
            }
        }
    }
    private boolean isRespawnPointBlock(Block block) {
        return block == Blocks.WHITE_BED || block == Blocks.ORANGE_BED || block == Blocks.MAGENTA_BED ||
               block == Blocks.LIGHT_BLUE_BED || block == Blocks.YELLOW_BED || block == Blocks.LIME_BED ||
               block == Blocks.PINK_BED || block == Blocks.GRAY_BED || block == Blocks.LIGHT_GRAY_BED ||
               block == Blocks.CYAN_BED || block == Blocks.PURPLE_BED || block == Blocks.BLUE_BED ||
               block == Blocks.BROWN_BED || block == Blocks.GREEN_BED || block == Blocks.RED_BED ||
               block == Blocks.BLACK_BED || block == Blocks.RESPAWN_ANCHOR;
    }
}