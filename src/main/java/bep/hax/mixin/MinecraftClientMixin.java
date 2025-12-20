package bep.hax.mixin;
import bep.hax.accessor.InputAccessor;
import bep.hax.mixin.accessor.PlayerInventoryAccessor;
import bep.hax.util.InventoryManager;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
import org.lwjgl.glfw.GLFW;
import bep.hax.modules.RocketMan;
import net.minecraft.sound.MusicSound;
import bep.hax.modules.MusicTweaks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import net.minecraft.util.math.MathHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.MusicInstance;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import net.minecraft.client.network.ClientPlayerEntity;
import meteordevelopment.meteorclient.utils.misc.input.Input;
import meteordevelopment.meteorclient.systems.modules.Modules;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(MinecraftClient.class)
public class MinecraftClientMixin {
    @Shadow
    public ClientPlayerEntity player;
    @Unique
    private long lastFrameTime = System.nanoTime();
    @Unique
    private void changeLookDirection(ClientPlayerEntity player, double cursorDeltaX, double cursorDeltaY) {
        float f = (float) cursorDeltaY * 0.15F;
        float g = (float) cursorDeltaX * 0.15F;
        player.setYaw(player.getYaw() + g);
        player.setPitch(MathHelper.clamp(player.getPitch() + f, -90.0F, 90.0F));
    }
    @Inject(method = "render", at = @At("HEAD"))
    private void mixinRender(CallbackInfo ci) {
        long currentTime = System.nanoTime();
        float deltaTime = (currentTime - lastFrameTime) / 10000000f;
        Modules modules = Modules.get();
        if (modules == null ) return;
        RocketMan rocketMan = modules.get(RocketMan.class);
        if (!rocketMan.isActive() || !rocketMan.shouldTickRotation()) return;
        MinecraftClient mc = rocketMan.getClientInstance();
        if (mc.player == null) return;
        if (!rocketMan.hoverMode.get().equals(RocketMan.HoverMode.Off)) {
            if (mc.player.input.playerInput.sneak() && !rocketMan.shouldLockYLevel() && !rocketMan.isHovering) {
                changeLookDirection(mc.player, 0.0f, rocketMan.getPitchSpeed() * deltaTime);
            } else if (mc.player.input.playerInput.jump() && !rocketMan.shouldLockYLevel() && !rocketMan.isHovering) {
                changeLookDirection(mc.player, 0.0f, -rocketMan.getPitchSpeed() * deltaTime);
            } else if (Input.isKeyPressed(GLFW.GLFW_KEY_UP)) {
                changeLookDirection(mc.player, 0.0f, -rocketMan.getPitchSpeed() * deltaTime);
            } else if (Input.isKeyPressed(GLFW.GLFW_KEY_DOWN)) {
                changeLookDirection(mc.player, 0.0f, rocketMan.getPitchSpeed() * deltaTime);
            }
        } else {
            boolean inverted = rocketMan.shouldInvertPitch();
            RocketMan.RocketMode mode = rocketMan.usageMode.get();
            switch (mode) {
                case OnKey -> {
                    if (mc.player.input.playerInput.sneak()) {
                        changeLookDirection(mc.player, 0.0f, rocketMan.getPitchSpeed() * deltaTime);
                    } else if (mc.player.input.playerInput.jump()) {
                        changeLookDirection(mc.player, 0.0f, -rocketMan.getPitchSpeed() * deltaTime);
                    } else if (Input.isKeyPressed(GLFW.GLFW_KEY_UP)) {
                        changeLookDirection(mc.player, 0.0f, -rocketMan.getPitchSpeed() * deltaTime);
                    } else if (Input.isKeyPressed(GLFW.GLFW_KEY_DOWN)) {
                        changeLookDirection(mc.player, 0.0f, rocketMan.getPitchSpeed() * deltaTime);
                    }
                }
                case Static, Dynamic -> {
                    if (inverted) {
                        if ((mc.player.input.playerInput.forward() || mc.player.input.playerInput.sneak()) && !rocketMan.shouldLockYLevel()) {
                            changeLookDirection(mc.player, 0.0f, rocketMan.getPitchSpeed() * deltaTime);
                        } else if ((mc.player.input.playerInput.backward() || mc.player.input.playerInput.jump()) && !rocketMan.shouldLockYLevel()) {
                            changeLookDirection(mc.player, 0.0f, -rocketMan.getPitchSpeed() * deltaTime);
                        } else if (Input.isKeyPressed(GLFW.GLFW_KEY_DOWN)) {
                            changeLookDirection(mc.player, 0.0f, -rocketMan.getPitchSpeed() * deltaTime);
                        } else if (Input.isKeyPressed(GLFW.GLFW_KEY_UP)) {
                            changeLookDirection(mc.player, 0.0f, rocketMan.getPitchSpeed() * deltaTime);
                        }
                    } else {
                        if ((mc.player.input.playerInput.backward() || mc.player.input.playerInput.sneak()) && !rocketMan.shouldLockYLevel()) {
                            changeLookDirection(mc.player, 0.0f, rocketMan.getPitchSpeed() * deltaTime);
                        } else if ((mc.player.input.playerInput.forward() || mc.player.input.playerInput.jump()) && !rocketMan.shouldLockYLevel()) {
                            changeLookDirection(mc.player, 0.0f, -rocketMan.getPitchSpeed() * deltaTime);
                        }else if (Input.isKeyPressed(GLFW.GLFW_KEY_UP)) {
                            changeLookDirection(mc.player, 0.0f, -rocketMan.getPitchSpeed() * deltaTime);
                        } else if (Input.isKeyPressed(GLFW.GLFW_KEY_DOWN)) {
                            changeLookDirection(mc.player, 0.0f, rocketMan.getPitchSpeed() * deltaTime);
                        }
                    }
                }
            }
        }
        if (mc.player.input.playerInput.right() && !rocketMan.isHovering) {
            changeLookDirection(mc.player, rocketMan.getYawSpeed() * deltaTime, 0.0f);
        } else if (mc.player.input.playerInput.left() && !rocketMan.isHovering) {
            changeLookDirection(mc.player, -rocketMan.getYawSpeed() * deltaTime, 0.0f);
        } else if (Input.isKeyPressed(GLFW.GLFW_KEY_RIGHT)) {
            changeLookDirection(mc.player, rocketMan.getYawSpeed() * deltaTime, 0.0f);
        } else if (Input.isKeyPressed(GLFW.GLFW_KEY_LEFT)) {
            changeLookDirection(mc.player, -rocketMan.getYawSpeed() * deltaTime, 0.0f);
        }
        lastFrameTime = currentTime;
    }
    @Inject(method = "doItemUse", at = @At("HEAD"))
    private void onDoItemUse(CallbackInfo ci) {
        if (player == null) return;
        ItemStack mainHand = player.getMainHandStack();
        ItemStack offHand = player.getOffHandStack();
        boolean mainHandIsFood = !mainHand.isEmpty() && mainHand.get(DataComponentTypes.FOOD) != null;
        boolean offHandIsFood = !offHand.isEmpty() && offHand.get(DataComponentTypes.FOOD) != null;
        if (mainHandIsFood || offHandIsFood) {
            InventoryManager invManager = InventoryManager.getInstance();
            int currentSlot = ((PlayerInventoryAccessor) player.getInventory()).getSelectedSlot();
            int serverSlot = invManager.getServerSlot();
            if (serverSlot != currentSlot) {
                invManager.setSlotForced(currentSlot);
            }
            invManager.setEating(true);
        }
    }
    @Inject(method = "getMusicInstance", at = @At("HEAD"), cancellable = true)
    public void mixinGetMusicType(CallbackInfoReturnable<MusicInstance> cir) {
        Modules modules = Modules.get();
        if (modules == null ) return;
        MusicTweaks tweaks = modules.get(MusicTweaks.class);
        if (tweaks == null || !tweaks.isActive()) return;
        MusicSound type = tweaks.getType();
        if (type != null) {
            cir.setReturnValue(new MusicInstance(type));
        }
    }
}