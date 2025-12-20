package bep.hax.mixin.meteor;
import bep.hax.mixin.accessor.PlayerInventoryAccessor;
import bep.hax.util.InventoryManager;
import bep.hax.util.RenderUtils.RenderMode;
import bep.hax.util.RotationUtils;
import bep.hax.util.InventoryManager.SwapMode;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.combat.CrystalAura;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.combat.KillAura;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.EventPriority;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.AttributeModifiersComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.item.*;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.util.Hand;
import org.apache.commons.lang3.mutable.MutableDouble;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.List;
@Mixin(value = KillAura.class, remap = false)
public abstract class KillAuraMixin extends Module {
    public KillAuraMixin(Category category, String name, String description) {
        super(category, name, description);
    }
    @Shadow @Final private SettingGroup sgGeneral;
    @Shadow @Final private Setting<Boolean> autoSwitch;
    @Shadow @Final private Setting<KillAura.RotationMode> rotation;
    @Shadow @Final private Setting<Double> range;
    @Shadow @Final private Setting<Boolean> tpsSync;
    @Shadow @Final private Setting<Boolean> customDelay;
    @Shadow @Final private Setting<Integer> hitDelay;
    @Shadow @Final private Setting<Integer> switchDelay;
    @Shadow private List<Entity> targets;
    @Shadow public boolean attacking;
    @Shadow private int hitTimer;
    @Shadow private int switchTimer;
    @Unique private SettingGroup bephax$sgRotation;
    @Unique private Setting<Boolean> bephax$grimRotate;
    @Unique private Setting<Boolean> bephax$silentRotate;
    @Unique private Setting<Boolean> bephax$yawStep;
    @Unique private Setting<Integer> bephax$yawStepLimit;
    @Unique private Setting<RotationUtils.HitVector> bephax$hitVector;
    @Unique private SettingGroup bephax$sgSwap;
    @Unique private Setting<SwapMode> bephax$swapMode;
    @Unique private SettingGroup bephax$sgRender;
    @Unique private Setting<Boolean> bephax$render;
    @Unique private Setting<RenderMode> bephax$renderMode;
    @Unique private Setting<ShapeMode> bephax$shapeMode;
    @Unique private Setting<SettingColor> bephax$sideColor;
    @Unique private Setting<SettingColor> bephax$lineColor;
    @Unique private RotationUtils bephax$rotationManager;
    @Unique private InventoryManager bephax$inventoryManager;
    @Unique private long bephax$lastAttackTime = 0;
    @Unique private boolean bephax$rotated = false;
    @Unique private float[] bephax$silentRotations = null;
    @Unique private long bephax$switchTimer = 0;
    @Unique private long bephax$autoSwapTimer = 0;
    @Unique private boolean bephax$silentSwapped = false;
    @Inject(method = "<init>", at = @At("TAIL"))
    private void onInit(CallbackInfo ci) {
        bephax$rotationManager = RotationUtils.getInstance();
        bephax$inventoryManager = InventoryManager.getInstance();
        bephax$sgRotation = settings.createGroup("Grim Rotations");
        bephax$grimRotate = bephax$sgRotation.add(new BoolSetting.Builder()
            .name("grim-rotate")
            .description("Use PVP rotation system")
            .defaultValue(false)
            .build()
        );
        bephax$hitVector = bephax$sgRotation.add(new EnumSetting.Builder<RotationUtils.HitVector>()
            .name("hit-vector")
            .description("Which part of the entity to aim for")
            .defaultValue(RotationUtils.HitVector.TORSO)
            .visible(bephax$grimRotate::get)
            .build()
        );
        bephax$silentRotate = bephax$sgRotation.add(new BoolSetting.Builder()
            .name("silent-rotate")
            .description("Rotates silently server-side")
            .defaultValue(false)
            .visible(bephax$grimRotate::get)
            .build()
        );
        bephax$yawStep = bephax$sgRotation.add(new BoolSetting.Builder()
            .name("yaw-step")
            .description("Limits rotation speed to avoid flags")
            .defaultValue(false)
            .visible(bephax$grimRotate::get)
            .build()
        );
        bephax$yawStepLimit = bephax$sgRotation.add(new IntSetting.Builder()
            .name("yaw-step-limit")
            .description("Maximum yaw rotation per tick")
            .defaultValue(180)
            .min(1)
            .max(180)
            .sliderRange(1, 180)
            .visible(() -> bephax$grimRotate.get() && bephax$yawStep.get())
            .build()
        );
        bephax$sgSwap = settings.createGroup("Grim Swap");
        bephax$swapMode = bephax$sgSwap.add(new EnumSetting.Builder<SwapMode>()
            .name("swap-mode")
            .description("How to swap to weapon")
            .defaultValue(SwapMode.Normal)
            .build()
        );
        bephax$sgRender = settings.createGroup("Grim Render");
        bephax$render = bephax$sgRender.add(new BoolSetting.Builder()
            .name("render")
            .description("Renders a box over the target entity")
            .defaultValue(true)
            .build()
        );
        bephax$renderMode = bephax$sgRender.add(new EnumSetting.Builder<RenderMode>()
            .name("render-mode")
            .description("How the target box is rendered")
            .defaultValue(RenderMode.Fade)
            .visible(bephax$render::get)
            .build()
        );
        bephax$shapeMode = bephax$sgRender.add(new EnumSetting.Builder<ShapeMode>()
            .name("shape-mode")
            .description("How the shapes are rendered")
            .defaultValue(ShapeMode.Both)
            .visible(bephax$render::get)
            .build()
        );
        bephax$sideColor = bephax$sgRender.add(new ColorSetting.Builder()
            .name("side-color")
            .description("The side color of the target box")
            .defaultValue(new SettingColor(255, 0, 0, 50))
            .visible(bephax$render::get)
            .build()
        );
        bephax$lineColor = bephax$sgRender.add(new ColorSetting.Builder()
            .name("line-color")
            .description("The line color of the target box")
            .defaultValue(new SettingColor(255, 0, 0, 255))
            .visible(bephax$render::get)
            .build()
        );
    }
    @Inject(method = "onActivate", at = @At("TAIL"))
    private void onActivateInject(CallbackInfo ci) {
        bephax$lastAttackTime = 0;
        bephax$rotated = false;
        bephax$silentRotations = null;
        bephax$switchTimer = System.currentTimeMillis();
        bephax$autoSwapTimer = System.currentTimeMillis();
        bephax$silentSwapped = false;
    }
    @Inject(method = "onDeactivate", at = @At("TAIL"))
    private void onDeactivateInject(CallbackInfo ci) {
        bephax$silentRotations = null;
        bephax$rotated = false;
        if (bephax$silentSwapped && bephax$swapMode.get() == SwapMode.Silent) {
            bephax$inventoryManager.syncToClient();
            bephax$silentSwapped = false;
        }
    }
    @Unique
    @EventHandler(priority = EventPriority.HIGHEST)
    private void onPreTickHighest(TickEvent.Pre event) {
        if (!isActive() || mc.player == null) return;
        if (!bephax$grimRotate.get()) return;
        if (rotation.get() != KillAura.RotationMode.None) {
            rotation.set(KillAura.RotationMode.None);
        }
        if (bephax$swapMode.get() == SwapMode.Silent) {
            if (autoSwitch.get()) {
                autoSwitch.set(false);
            }
        }
        if (targets == null || targets.isEmpty() || !attacking) {
            bephax$silentRotations = null;
            return;
        }
        Entity target = targets.get(0);
        if (target == null) return;
        if (!bephax$switchTimerPassed()) {
            bephax$silentRotations = null;
            return;
        }
        if ((mc.player.isUsingItem() && mc.player.getActiveHand() == Hand.MAIN_HAND) ||
            mc.options.attackKey.isPressed() || bephax$isHotbarKeysPressed()) {
            bephax$autoSwapTimer = System.currentTimeMillis();
        }
        int slot = bephax$getBestWeaponSlot();
        if (slot != -1) {
            switch (bephax$swapMode.get()) {
                case Normal -> {
                    if (!bephax$isHoldingWeapon() && bephax$autoSwapTimerPassed()) {
                        ((PlayerInventoryAccessor) mc.player.getInventory()).setSelectedSlot(slot);
                    }
                }
                case Silent -> {
                    int currentServerSlot = bephax$inventoryManager.getServerSlot();
                    if (currentServerSlot != slot) {
                        bephax$inventoryManager.setSlot(slot);
                        bephax$silentSwapped = true;
                    }
                }
            }
        }
        if (!bephax$isHoldingWeapon() && bephax$swapMode.get() != SwapMode.Silent) {
            return;
        }
        if (slot == -1 && bephax$swapMode.get() == SwapMode.Silent) {
            return;
        }
        bephax$handlePVPRotation(target);
        if (!bephax$rotated) {
            if (bephax$silentSwapped) {
                bephax$inventoryManager.syncToClient();
                bephax$silentSwapped = false;
            }
            return;
        }
        Vec3d eyepos = mc.player.getEyePos();
        if (!bephax$isInAttackRange(target, eyepos)) {
            if (bephax$silentSwapped) {
                bephax$inventoryManager.syncToClient();
                bephax$silentSwapped = false;
            }
            return;
        }
    }
    @Inject(method = "attack", at = @At("HEAD"), cancellable = true)
    private void onAttack(Entity target, CallbackInfo ci) {
        if (!bephax$grimRotate.get()) return;
        ci.cancel();
        bephax$handleAttackDelay(target);
    }
    @Unique
    @EventHandler(priority = EventPriority.LOWEST)
    private void onPostTickLowest(TickEvent.Post event) {
        if (!isActive() || mc.player == null) return;
        if (bephax$silentSwapped && !attacking) {
            bephax$inventoryManager.syncToClient();
            bephax$silentSwapped = false;
        }
    }
    @Unique
    @EventHandler(priority = EventPriority.HIGHEST)
    private void onSendPacketHighest(PacketEvent.Send event) {
        if (!isActive() || mc.player == null) return;
        if (event.packet instanceof UpdateSelectedSlotC2SPacket) {
            bephax$switchTimer = System.currentTimeMillis();
        }
    }
    @Unique
    private boolean bephax$switchTimerPassed() {
        return System.currentTimeMillis() - bephax$switchTimer >= 0;
    }
    @Unique
    private boolean bephax$autoSwapTimerPassed() {
        return System.currentTimeMillis() - bephax$autoSwapTimer >= 500;
    }
    @Unique
    private boolean bephax$isHotbarKeysPressed() {
        for (int i = 0; i < 9; i++) {
            if (mc.options.hotbarKeys[i].isPressed()) {
                return true;
            }
        }
        return false;
    }
    @Unique
    private void bephax$handlePVPRotation(Entity target) {
        float[] rotation = RotationUtils.getRotationsTo(mc.player.getEyePos(),
            bephax$getAttackRotateVec(target));
        if (!bephax$silentRotate.get() && bephax$yawStep.get()) {
            float serverYaw = bephax$rotationManager.getWrappedYaw();
            float diff = serverYaw - rotation[0];
            float diff1 = Math.abs(diff);
            if (diff1 > 180.0f) {
                diff += diff > 0.0f ? -360.0f : 360.0f;
            }
            int dir = diff > 0.0f ? -1 : 1;
            float deltaYaw = dir * bephax$yawStepLimit.get();
            float yaw;
            if (diff1 > bephax$yawStepLimit.get()) {
                yaw = serverYaw + deltaYaw;
                bephax$rotated = false;
            } else {
                yaw = rotation[0];
                bephax$rotated = true;
            }
            rotation[0] = yaw;
        } else {
            bephax$rotated = true;
        }
        if (bephax$silentRotate.get()) {
            bephax$silentRotations = rotation;
        } else {
            mc.player.setYaw(rotation[0]);
            mc.player.setPitch(MathHelper.clamp(rotation[1], -90.0f, 90.0f));
        }
    }
    @Unique
    private Vec3d bephax$getAttackRotateVec(Entity entity) {
        Vec3d feetPos = entity.getEntityPos();
        return switch (bephax$hitVector.get()) {
            case FEET -> feetPos;
            case TORSO -> feetPos.add(0.0, entity.getHeight() / 2.0f, 0.0);
            case EYES -> entity.getEyePos();
            case CLOSEST -> {
                Vec3d eyePos = mc.player.getEyePos();
                Vec3d torsoPos = feetPos.add(0.0, entity.getHeight() / 2.0f, 0.0);
                Vec3d eyesPos = entity.getEyePos();
                double feetDist = eyePos.squaredDistanceTo(feetPos);
                double torsoDist = eyePos.squaredDistanceTo(torsoPos);
                double eyesDist = eyePos.squaredDistanceTo(eyesPos);
                if (feetDist <= torsoDist && feetDist <= eyesDist) {
                    yield feetPos;
                } else if (torsoDist <= eyesDist) {
                    yield torsoPos;
                } else {
                    yield eyesPos;
                }
            }
        };
    }
    @Unique
    private boolean bephax$isInAttackRange(Entity target, Vec3d eyepos) {
        Vec3d targetPos = bephax$getAttackRotateVec(target);
        return eyepos.distanceTo(targetPos) <= range.get();
    }
    @Unique
    private boolean bephax$isHoldingWeapon() {
        ItemStack stack = mc.player.getMainHandStack();
        String itemName = stack.getItem().toString().toLowerCase();
        return itemName.contains("sword") ||
            stack.getItem() instanceof AxeItem ||
            stack.getItem() instanceof TridentItem ||
            stack.getItem() instanceof MaceItem;
    }
    @Unique
    private int bephax$getBestWeaponSlot() {
        float bestDamage = 0.0f;
        int bestSlot = -1;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.isEmpty()) continue;
            MutableDouble damageMutable = new MutableDouble(0.0);
            AttributeModifiersComponent attributeModifiers = stack.get(DataComponentTypes.ATTRIBUTE_MODIFIERS);
            if (attributeModifiers != null) {
                attributeModifiers.applyModifiers(EquipmentSlot.MAINHAND, (entry, modifier) -> {
                    if (entry == EntityAttributes.ATTACK_DAMAGE) {
                        damageMutable.add(modifier.value());
                    }
                });
            }
            float damage = (float) damageMutable.doubleValue();
            if (damage > bestDamage) {
                bestDamage = damage;
                bestSlot = i;
            }
        }
        return bestSlot;
    }
    @Unique
    private void bephax$handleAttackDelay(Entity target) {
        if (!bephax$rotated) {
            return;
        }
        if (bephax$lastAttackTime == 0) {
            if (bephax$attackTarget(target)) {
                bephax$lastAttackTime = System.currentTimeMillis();
            }
            return;
        }
        int weaponSlot = bephax$getBestWeaponSlot();
        int slotToUse = weaponSlot == -1 ? ((PlayerInventoryAccessor) mc.player.getInventory()).getSelectedSlot() : weaponSlot;
        ItemStack weapon = mc.player.getInventory().getStack(slotToUse);
        MutableDouble attackSpeedAttr = new MutableDouble(
            mc.player.getAttributeBaseValue(EntityAttributes.ATTACK_SPEED));
        AttributeModifiersComponent attributeModifiers = weapon.get(DataComponentTypes.ATTRIBUTE_MODIFIERS);
        if (attributeModifiers != null) {
            attributeModifiers.applyModifiers(EquipmentSlot.MAINHAND, (entry, modifier) -> {
                if (entry.equals(EntityAttributes.ATTACK_SPEED)) {
                    attackSpeedAttr.add(modifier.value());
                }
            });
        }
        double attackCooldownTicks = 1.0 / attackSpeedAttr.getValue() * 20.0;
        float ticks = 0.0f;
        float currentTime = (System.currentTimeMillis() - bephax$lastAttackTime) + (ticks * 50.0f);
        if ((currentTime / 50.0f) >= attackCooldownTicks && bephax$attackTarget(target)) {
            bephax$lastAttackTime = System.currentTimeMillis();
        }
    }
    @Unique
    private boolean bephax$attackTarget(Entity entity) {
        int weaponSlot = bephax$getBestWeaponSlot();
        if (bephax$swapMode.get() == SwapMode.Silent && weaponSlot != -1) {
            int currentServerSlot = bephax$inventoryManager.getServerSlot();
            if (currentServerSlot != weaponSlot) {
                bephax$inventoryManager.setSlot(weaponSlot);
                bephax$silentSwapped = true;
            }
        }
        if (bephax$silentRotate.get() && bephax$silentRotations != null) {
            bephax$rotationManager.setRotationSilent(bephax$silentRotations[0], bephax$silentRotations[1]);
        }
        PlayerInteractEntityC2SPacket packet = PlayerInteractEntityC2SPacket.attack(entity, mc.player.isSneaking());
        mc.getNetworkHandler().sendPacket(packet);
        mc.player.swingHand(Hand.MAIN_HAND);
        if (bephax$silentRotate.get()) {
            bephax$rotationManager.setRotationSilentSync();
        }
        if (bephax$silentSwapped && bephax$swapMode.get() == SwapMode.Silent) {
            bephax$inventoryManager.syncToClient();
            bephax$silentSwapped = false;
        }
        return true;
    }
    @Unique
    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (!isActive() || mc.player == null || !bephax$render.get() || !bephax$grimRotate.get()) return;
        CrystalAura crystalAura = Modules.get().get(CrystalAura.class);
        if (crystalAura != null && crystalAura.isActive() && crystalAura.kaTimer > 0) return;
        if (targets == null || targets.isEmpty() || !attacking) return;
        Entity target = targets.get(0);
        if (target == null || !target.isAlive()) return;
        if (!(bephax$isHoldingWeapon() || bephax$swapMode.get() == SwapMode.Silent)) return;
        double x = MathHelper.lerp(event.tickDelta, target.lastX, target.getX());
        double y = MathHelper.lerp(event.tickDelta, target.lastY, target.getY());
        double z = MathHelper.lerp(event.tickDelta, target.lastZ, target.getZ());
        Box box = target.getBoundingBox().offset(-target.getX(), -target.getY(), -target.getZ()).offset(x, y, z);
        Color sideColor;
        Color lineColor;
        switch (bephax$renderMode.get()) {
            case Solid -> {
                sideColor = bephax$sideColor.get();
                lineColor = bephax$lineColor.get();
            }
            case Fade -> {
                long timeSinceAttack = System.currentTimeMillis() - bephax$lastAttackTime;
                float fade = 1.0f - MathHelper.clamp(timeSinceAttack / 1000.0f, 0.0f, 1.0f);
                int sideAlpha = (int) (bephax$sideColor.get().a * fade);
                int lineAlpha = (int) (bephax$lineColor.get().a * fade);
                sideColor = new Color(
                    bephax$sideColor.get().r,
                    bephax$sideColor.get().g,
                    bephax$sideColor.get().b,
                    Math.max(sideAlpha, 0)
                );
                lineColor = new Color(
                    bephax$lineColor.get().r,
                    bephax$lineColor.get().g,
                    bephax$lineColor.get().b,
                    Math.max(lineAlpha, 0)
                );
            }
            case Pulse -> {
                double pulse = Math.sin(System.currentTimeMillis() / 200.0) * 0.5 + 0.5;
                int sideAlpha = (int) (bephax$sideColor.get().a * pulse);
                int lineAlpha = (int) (bephax$lineColor.get().a * pulse);
                sideColor = new Color(
                    bephax$sideColor.get().r,
                    bephax$sideColor.get().g,
                    bephax$sideColor.get().b,
                    Math.max(sideAlpha, 10)
                );
                lineColor = new Color(
                    bephax$lineColor.get().r,
                    bephax$lineColor.get().g,
                    bephax$lineColor.get().b,
                    Math.max(lineAlpha, 10)
                );
            }
            case Shrink -> {
                long timeSinceAttack = System.currentTimeMillis() - bephax$lastAttackTime;
                float shrink = MathHelper.clamp(timeSinceAttack / 500.0f, 0.0f, 1.0f);
                double expansion = 0.1 * (1.0 - shrink);
                box = box.expand(expansion);
                sideColor = bephax$sideColor.get();
                lineColor = bephax$lineColor.get();
            }
            default -> {
                sideColor = bephax$sideColor.get();
                lineColor = bephax$lineColor.get();
            }
        }
        event.renderer.box(box, sideColor, lineColor, bephax$shapeMode.get(), 0);
    }
}