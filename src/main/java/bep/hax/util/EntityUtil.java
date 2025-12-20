package bep.hax.util;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.*;
import net.minecraft.entity.passive.*;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.vehicle.*;
import net.minecraft.util.math.BlockPos;
public class EntityUtil {
    public static boolean isMonster(Entity entity) {
        return entity instanceof HostileEntity ||
            entity instanceof SlimeEntity ||
            entity instanceof GhastEntity ||
            entity instanceof PhantomEntity ||
            entity instanceof ShulkerEntity;
    }
    public static boolean isNeutral(Entity entity) {
        return entity instanceof ZombifiedPiglinEntity ||
            entity instanceof PiglinEntity ||
            entity instanceof EndermanEntity ||
            entity instanceof WolfEntity ||
            entity instanceof LlamaEntity ||
            entity instanceof TraderLlamaEntity ||
            entity instanceof BeeEntity ||
            entity instanceof SpiderEntity ||
            entity instanceof CaveSpiderEntity ||
            entity instanceof PolarBearEntity ||
            entity instanceof PandaEntity ||
            entity instanceof DolphinEntity ||
            entity instanceof IronGolemEntity;
    }
    public static boolean isAggressive(Entity entity) {
        if (entity instanceof EndermanEntity enderman) {
            return enderman.isAngry();
        }
        if (entity instanceof ZombifiedPiglinEntity zombifiedPiglin) {
            return zombifiedPiglin.isAttacking();
        }
        if (entity instanceof WolfEntity wolf) {
            return wolf.isAttacking();
        }
        if (entity instanceof PiglinEntity piglin) {
            return piglin.isAttacking();
        }
        if (entity instanceof BeeEntity bee) {
            return bee.hasAngerTime();
        }
        if (entity instanceof PolarBearEntity polarBear) {
            return polarBear.isAttacking();
        }
        if (entity instanceof LlamaEntity llama) {
            return llama.isAttacking();
        }
        if (entity instanceof IronGolemEntity ironGolem) {
            return ironGolem.isAttacking();
        }
        if (entity instanceof SpiderEntity || entity instanceof CaveSpiderEntity) {
            return entity.getEntityWorld().getAmbientDarkness() >= 0.5f;
        }
        return false;
    }
    public static boolean isPassive(Entity entity) {
        return entity instanceof AnimalEntity ||
            entity instanceof AmbientEntity ||
            entity instanceof WaterCreatureEntity ||
            entity instanceof VillagerEntity ||
            entity instanceof SquidEntity ||
            entity instanceof BatEntity;
    }
    public static boolean isPlayer(Entity entity) {
        return entity instanceof PlayerEntity;
    }
    public static EntityCategory getEntityCategory(Entity entity) {
        if (isPlayer(entity)) return EntityCategory.PLAYER;
        if (isMonster(entity)) return EntityCategory.MONSTER;
        if (isNeutral(entity)) return EntityCategory.NEUTRAL;
        if (isPassive(entity)) return EntityCategory.PASSIVE;
        return EntityCategory.OTHER;
    }
    public static boolean isLivingTarget(Entity entity) {
        return entity instanceof PlayerEntity ||
            entity instanceof MobEntity;
    }
    public static String getEntityName(Entity entity) {
        if (entity instanceof PlayerEntity player) {
            return player.getGameProfile().name();
        }
        return EntityType.getId(entity.getType()).getPath();
    }
    public static boolean isUndead(Entity entity) {
        return entity instanceof ZombieEntity ||
            entity instanceof SkeletonEntity ||
            entity instanceof WitherSkeletonEntity ||
            entity instanceof StrayEntity ||
            entity instanceof HuskEntity ||
            entity instanceof DrownedEntity ||
            entity instanceof ZombieVillagerEntity ||
            entity instanceof ZombifiedPiglinEntity ||
            entity instanceof net.minecraft.entity.boss.WitherEntity ||
            entity instanceof PhantomEntity;
    }
    public static boolean isArthropod(Entity entity) {
        return entity instanceof SpiderEntity ||
            entity instanceof CaveSpiderEntity ||
            entity instanceof SilverfishEntity ||
            entity instanceof EndermiteEntity ||
            entity instanceof BeeEntity;
    }
    public static boolean isVehicle(Entity entity) {
        return entity instanceof BoatEntity ||
            entity instanceof MinecartEntity ||
            entity instanceof FurnaceMinecartEntity ||
            entity instanceof ChestMinecartEntity;
    }
    public static float getHealth(Entity entity) {
        if (entity instanceof LivingEntity e) {
            return e.getHealth() + e.getAbsorptionAmount();
        }
        return 0.0f;
    }
    public static BlockPos getRoundedBlockPos(Entity entity) {
        return new BlockPos(entity.getBlockX(), (int) Math.round(entity.getY()), entity.getBlockZ());
    }
    public enum EntityCategory {
        PLAYER,
        MONSTER,
        NEUTRAL,
        PASSIVE,
        OTHER
    }
}