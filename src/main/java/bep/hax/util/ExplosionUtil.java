package bep.hax.util;
import net.minecraft.block.AnvilBlock;
import net.minecraft.block.BedBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.CobwebBlock;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.DamageUtil;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.DefaultAttributeRegistry;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.BlockView;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import static meteordevelopment.meteorclient.MeteorClient.mc;
public class ExplosionUtil {
    private static final Map<String, Integer> PROTECTION_MAP = new HashMap<>() {{
        put("protection", 1);
        put("blast_protection", 2);
        put("projectile_protection", 1);
        put("feather_falling", 1);
        put("fire_protection", 1);
    }};
    public static double getDamageTo(final Entity entity,
                                     final Vec3d explosion,
                                     boolean assumeBestArmor) {
        return getDamageTo(entity, explosion, false, assumeBestArmor);
    }
    public static double getDamageTo(final Entity entity,
                                     final Vec3d explosion,
                                     final boolean ignoreTerrain,
                                     boolean assumeBestArmor) {
        return getDamageTo(entity, explosion, ignoreTerrain, 12.0f, 0, assumeBestArmor);
    }
    public static double getDamageTo(final Entity entity,
                                     final Vec3d explosion,
                                     final boolean ignoreTerrain,
                                     int extrapolationTicks,
                                     boolean assumeBestArmor) {
        return getDamageTo(entity, explosion, ignoreTerrain, 12.0f, extrapolationTicks, assumeBestArmor);
    }
    public static double getDamageTo(final Entity entity,
                                     final Vec3d explosion,
                                     final boolean ignoreTerrain,
                                     final Set<BlockPos> ignoreBlocks,
                                     int extrapolationTicks,
                                     boolean assumeBestArmor) {
        return getDamageTo(entity, explosion, ignoreTerrain, 12.0f, ignoreBlocks, extrapolationTicks, assumeBestArmor);
    }
    public static double getDamageTo(final Entity entity,
                                     final Vec3d explosion,
                                     final boolean ignoreTerrain,
                                     float power,
                                     final Set<BlockPos> ignoreBlocks,
                                     int extrapolationTicks,
                                     boolean assumeBestArmor) {
        double x = entity.getX();
        double y = entity.getY();
        double z = entity.getZ();
        Vec3d vec3d2 = Vec3d.ZERO;
        if (extrapolationTicks != 0) {
            double ox = (x - entity.lastX) * extrapolationTicks;
            double oy = (y - entity.lastY) * extrapolationTicks * 0.3;
            double oz = (z - entity.lastZ) * extrapolationTicks;
            x += ox;
            y += oy;
            z += oz;
            vec3d2 = new Vec3d(ox, oy, oz);
        }
        Vec3d vec3d = new Vec3d(x, y, z);
        double d = Math.sqrt(vec3d.squaredDistanceTo(explosion));
        double ab = getExposure(explosion, entity.getBoundingBox().offset(vec3d2), ignoreTerrain ? IgnoreTerrain.BLAST : IgnoreTerrain.NONE, ignoreBlocks);
        double w = d / power;
        double ac = (1.0 - w) * ab;
        double dmg = (float) ((int) ((ac * ac + ac) / 2.0 * 7.0 * 12.0 + 1.0));
        dmg = getReduction(entity, mc.world.getDamageSources().explosion(null), dmg, assumeBestArmor);
        return Math.max(0.0, dmg);
    }
    public static double getDamageTo(final Entity entity,
                                     final Vec3d explosion,
                                     final boolean ignoreTerrain,
                                     float power,
                                     int extrapolationTicks,
                                     boolean assumeBestArmor) {
        double x = entity.getX();
        double y = entity.getY();
        double z = entity.getZ();
        Vec3d vec3d2 = Vec3d.ZERO;
        if (extrapolationTicks != 0) {
            double ox = (x - entity.lastX) * extrapolationTicks;
            double oy = (y - entity.lastY) * extrapolationTicks * 0.3;
            double oz = (z - entity.lastZ) * extrapolationTicks;
            x += ox;
            y += oy;
            z += oz;
            vec3d2 = new Vec3d(ox, oy, oz);
        }
        Vec3d vec3d = new Vec3d(x, y, z);
        double d = Math.sqrt(vec3d.squaredDistanceTo(explosion));
        double ab = getExposure(explosion, entity.getBoundingBox().offset(vec3d2), ignoreTerrain ? IgnoreTerrain.BLAST : IgnoreTerrain.NONE);
        double w = d / power;
        double ac = (1.0 - w) * ab;
        double dmg = (float) ((int) ((ac * ac + ac) / 2.0 * 7.0 * 12.0 + 1.0));
        dmg = getReduction(entity, mc.world.getDamageSources().explosion(null), dmg, assumeBestArmor);
        return Math.max(0.0, dmg) / 4;
    }
    private static double getReduction(Entity entity, DamageSource damageSource, double damage, boolean assumeBestArmor) {
        if (damageSource.isScaledWithDifficulty()) {
            switch (mc.world.getDifficulty()) {
                case PEACEFUL -> {
                    return 0.0;
                }
                case EASY -> damage = Math.min(damage / 2 + 1, damage);
                case HARD -> damage *= 1.5f;
            }
        }
        if (entity instanceof LivingEntity livingEntity) {
            damage = DamageUtil.getDamageLeft(livingEntity, (float) damage, damageSource, getArmor(livingEntity), (float) livingEntity.getAttributeValue(EntityAttributes.ARMOR_TOUGHNESS));
            damage = getResistanceReduction(livingEntity, damage);
            damage = getProtectionReduction(livingEntity, damage, damageSource, assumeBestArmor);
        }
        return Math.max(damage, 0);
    }
    private static float getArmor(LivingEntity entity) {
        return (float) Math.floor(entity.getAttributeValue(EntityAttributes.ARMOR));
    }
    private static float getProtectionReduction(Entity player, double damage, DamageSource source, boolean assumeBestArmor) {
        if (player instanceof LivingEntity livingEntity) {
            java.util.List<net.minecraft.item.ItemStack> armorItems = java.util.Arrays.asList(
                livingEntity.getEquippedStack(net.minecraft.entity.EquipmentSlot.FEET),
                livingEntity.getEquippedStack(net.minecraft.entity.EquipmentSlot.LEGS),
                livingEntity.getEquippedStack(net.minecraft.entity.EquipmentSlot.CHEST),
                livingEntity.getEquippedStack(net.minecraft.entity.EquipmentSlot.HEAD)
            );
            float protLevel = getProtectionAmount(armorItems, assumeBestArmor);
            return DamageUtil.getInflictedDamage((float) damage, protLevel);
        }
        return 0.0f;
    }
    private static float getProtectionAmount(Iterable<ItemStack> equipment, boolean assumeBestArmor) {
        return getProtectionAmount(equipment.iterator().next());
    }
    public static int getProtectionAmount(ItemStack armor) {
        int x = 0;
        ItemEnchantmentsComponent enchantments = EnchantmentHelper.getEnchantments(armor);
        for (RegistryEntry<Enchantment> enchantment : enchantments.getEnchantments()) {
            String id = enchantment.getIdAsString().replace("minecraft:", "");
            if (PROTECTION_MAP.containsKey(id)) {
                x += enchantments.getLevel(enchantment) * PROTECTION_MAP.get(id);
                break;
            }
        }
        return x;
    }
    private static double getResistanceReduction(LivingEntity player, double damage) {
        StatusEffectInstance resistance = player.getStatusEffect(StatusEffects.RESISTANCE);
        if (resistance != null) {
            int lvl = resistance.getAmplifier() + 1;
            damage *= (1.0f - (lvl * 0.2f));
        }
        return Math.max(damage, 0.0f);
    }
    @SuppressWarnings("unchecked")
    private static <T extends LivingEntity> DefaultAttributeContainer getDefaultForEntity(T entity) {
        return DefaultAttributeRegistry.get((EntityType<? extends LivingEntity>) entity.getType());
    }
    private static float getExposure(final Vec3d source,
                                     final Box box,
                                     final IgnoreTerrain ignoreTerrain,
                                     final Set<BlockPos> ignoreBlocks) {
        RaycastFactory raycastFactory = getRaycastFactory(ignoreTerrain, ignoreBlocks);
        return getExposure(source, box, raycastFactory);
    }
    private static float getExposure(final Vec3d source,
                                     final Box box,
                                     final IgnoreTerrain ignoreTerrain) {
        RaycastFactory raycastFactory = getRaycastFactory(ignoreTerrain);
        return getExposure(source, box, raycastFactory);
    }
    private static float getExposure(final Vec3d source,
                                     final Box box,
                                     final RaycastFactory raycastFactory) {
        double xDiff = box.maxX - box.minX;
        double yDiff = box.maxY - box.minY;
        double zDiff = box.maxZ - box.minZ;
        double xStep = 1 / (xDiff * 2 + 1);
        double yStep = 1 / (yDiff * 2 + 1);
        double zStep = 1 / (zDiff * 2 + 1);
        if (xStep > 0 && yStep > 0 && zStep > 0) {
            int misses = 0;
            int hits = 0;
            double xOffset = (1 - Math.floor(1 / xStep) * xStep) * 0.5;
            double zOffset = (1 - Math.floor(1 / zStep) * zStep) * 0.5;
            xStep = xStep * xDiff;
            yStep = yStep * yDiff;
            zStep = zStep * zDiff;
            double startX = box.minX + xOffset;
            double startY = box.minY;
            double startZ = box.minZ + zOffset;
            double endX = box.maxX + xOffset;
            double endY = box.maxY;
            double endZ = box.maxZ + zOffset;
            for (double x = startX; x <= endX; x += xStep) {
                for (double y = startY; y <= endY; y += yStep) {
                    for (double z = startZ; z <= endZ; z += zStep) {
                        Vec3d position = new Vec3d(x, y, z);
                        if (raycast(new ExposureRaycastContext(position, source), raycastFactory) == null) misses++;
                        hits++;
                    }
                }
            }
            return (float) misses / hits;
        }
        return 0f;
    }
    private static RaycastFactory getRaycastFactory(IgnoreTerrain ignoreTerrain, Set<BlockPos> ignoreBlocks) {
        if (ignoreTerrain == IgnoreTerrain.BLAST) {
            return (context, blockPos) ->
            {
                if (ignoreBlocks.contains(blockPos)) {
                    return null;
                }
                BlockState blockState = mc.world.getBlockState(blockPos);
                if (blockState.getBlock().getBlastResistance() < 600) return null;
                return blockState.getCollisionShape(mc.world, blockPos).raycast(context.start(), context.end(), blockPos);
            };
        } else if (ignoreTerrain == IgnoreTerrain.ALL) {
            return (context, blockPos) -> null;
        } else {
            return (context, blockPos) ->
            {
                if (ignoreBlocks.contains(blockPos)) {
                    return null;
                }
                BlockState blockState = mc.world.getBlockState(blockPos);
                return blockState.getCollisionShape(mc.world, blockPos).raycast(context.start(), context.end(), blockPos);
            };
        }
    }
    private static RaycastFactory getRaycastFactory(IgnoreTerrain ignoreTerrain) {
        if (ignoreTerrain == IgnoreTerrain.BLAST) {
            return (context, blockPos) ->
            {
                BlockState blockState = mc.world.getBlockState(blockPos);
                if (blockState.getBlock().getBlastResistance() < 600) return null;
                return blockState.getCollisionShape(mc.world, blockPos).raycast(context.start(), context.end(), blockPos);
            };
        } else if (ignoreTerrain == IgnoreTerrain.ALL) {
            return (context, blockPos) -> null;
        } else {
            return (context, blockPos) ->
            {
                BlockState blockState = mc.world.getBlockState(blockPos);
                return blockState.getCollisionShape(mc.world, blockPos).raycast(context.start(), context.end(), blockPos);
            };
        }
    }
    private static BlockHitResult raycast(ExposureRaycastContext context, RaycastFactory raycastFactory) {
        return BlockView.raycast(context.start, context.end, context, raycastFactory, ctx -> null);
    }
    public enum IgnoreTerrain {
        ALL,
        BLAST,
        NONE
    }
    @FunctionalInterface
    public interface RaycastFactory extends BiFunction<ExposureRaycastContext, BlockPos, BlockHitResult> { }
    public record ExposureRaycastContext(Vec3d start, Vec3d end) { }
}