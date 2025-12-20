package bep.hax.mixin;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.systems.config.Config;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(value = Config.class, remap = false)
public class ConfigMixin {
    @Unique public Setting<SettingColor> bephax$enemyColor;
    @Inject(method = "<init>", at = @At("TAIL"))
    private void addEnemyColor(CallbackInfo ci) {
        Config self = (Config) (Object) this;
        bephax$enemyColor = self.settings.getDefaultGroup().add(new ColorSetting.Builder()
            .name("enemy-color")
            .description("Color for enemy players in nametags and tablist.")
            .defaultValue(new SettingColor(255, 0, 0, 255))
            .build()
        );
    }
    @Unique
    public Setting<SettingColor> bephax$getEnemyColor() {
        return bephax$enemyColor;
    }
}