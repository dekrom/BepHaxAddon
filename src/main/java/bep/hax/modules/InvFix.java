package bep.hax.modules;
import bep.hax.Bep;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
public class InvFix extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final Setting<Boolean> fixBundles = sgGeneral.add(new BoolSetting.Builder()
        .name("fix-bundle-selection")
        .description("Fixes bundle item selection on 2b2t by inverting the selection index.")
        .defaultValue(true)
        .build()
    );
    private final Setting<Boolean> fixUnstackableDrag = sgGeneral.add(new BoolSetting.Builder()
        .name("fix-unstackable-dragging")
        .description("Prevents dragging unstackable items to avoid ghost items.")
        .defaultValue(true)
        .build()
    );
    private final Setting<Boolean> preventFullContainerClicks = sgGeneral.add(new BoolSetting.Builder()
        .name("prevent-full-container-clicks")
        .description("Prevents shift-clicking items when the target container is full. Helps avoid kicks on 2b2t.")
        .defaultValue(true)
        .build()
    );
    public InvFix() {
        super(Bep.CATEGORY, "2B2TInvFix", "Fixes inventory issues specific to 2b2t (bundle selection, unstackable dragging, full container clicks).");
    }
    public boolean shouldFixBundles() {
        return isActive() && fixBundles.get();
    }
    public boolean shouldFixUnstackableDrag() {
        return isActive() && fixUnstackableDrag.get();
    }
    public boolean shouldPreventFullContainerClicks() {
        return isActive() && preventFullContainerClicks.get();
    }
}