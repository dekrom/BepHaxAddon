package bep.hax.util;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
public class EnemyColorManager {
    private static Setting<SettingColor> enemyColorSetting;
    public static void setEnemyColorSetting(Setting<SettingColor> setting) {
        enemyColorSetting = setting;
    }
    public static Setting<SettingColor> getEnemyColorSetting() {
        return enemyColorSetting;
    }
}