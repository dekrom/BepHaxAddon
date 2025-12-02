package bep.hax.mixin.meteor;
import bep.hax.util.EnemyColorManager;
import bep.hax.util.EnemyManager;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(value = PlayerUtils.class, remap = false)
public class PlayerUtilsMixin {
    @Inject(method = "getPlayerColor", at = @At("HEAD"), cancellable = true)
    private static void injectEnemyColor(PlayerEntity entity, Color defaultColor, CallbackInfoReturnable<Color> cir) {
        if (EnemyManager.getInstance().isEnemy(entity)) {
            Setting<SettingColor> enemyColorSetting = EnemyColorManager.getEnemyColorSetting();
            if (enemyColorSetting != null) {
                SettingColor enemyColor = enemyColorSetting.get();
                cir.setReturnValue(new Color(enemyColor.r, enemyColor.g, enemyColor.b, defaultColor.a));
            }
        }
    }
}