package bep.hax.util;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import java.awt.*;
import java.util.Set;
import java.util.function.Supplier;
public class SettingBuilder {
    public static Setting<Boolean> booleanSetting(SettingGroup sg, String name, String desc, boolean value) {
        return booleanSetting(sg, name, desc, value, () -> true);
    }
    public static Setting<Boolean> booleanSetting(SettingGroup sg, String name, String desc, boolean value, Supplier<Boolean> visible) {
        return sg.add(new BoolSetting.Builder()
            .name(name)
            .description(desc)
            .defaultValue(value)
            .visible(visible::get)
            .build()
        );
    }
    public static Setting<Double> doubleSetting(SettingGroup sg, String name, String desc, double value, double min, double max) {
        return sg.add(new DoubleSetting.Builder()
            .name(name)
            .description(desc)
            .defaultValue(value)
            .min(min)
            .max(max)
            .build()
        );
    }
    public static Setting<Integer> intSetting(SettingGroup sg, String name, String desc, int value, int min, int max) {
        return sg.add(new IntSetting.Builder()
            .name(name)
            .description(desc)
            .defaultValue(value)
            .min(min)
            .max(max)
            .build()
        );
    }
    public static <T extends Enum<T>> Setting<T> enumSetting(SettingGroup sg, String name, String desc, T value) {
        return sg.add(new EnumSetting.Builder<T>()
            .name(name)
            .description(desc)
            .defaultValue(value)
            .build()
        );
    }
    public static Setting<SettingColor> colorSetting(SettingGroup sg, String name, String desc, Color value) {
        return sg.add(new ColorSetting.Builder()
            .name(name)
            .description(desc)
            .defaultValue(value)
            .build()
        );
    }
}