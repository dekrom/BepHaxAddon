package bep.hax.mixin.meteor;
import bep.hax.util.EnemyColorManager;
import bep.hax.util.EnemyManager;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.systems.modules.render.BetterTab;
import org.spongepowered.asm.mixin.Shadow;
@Mixin(value = BetterTab.class, remap = false)
public class BetterTabMixin {
    @Shadow private SettingGroup sgGeneral;
    @Unique
    private Setting<SettingColor> bephax$enemyColor;
    @Inject(method = "<init>", at = @At("TAIL"))
    private void addEnemyColorSetting(CallbackInfo ci) {
        bephax$enemyColor = sgGeneral.add(new ColorSetting.Builder()
            .name("enemy-color")
            .description("Color for enemy players in tablist and nametags.")
            .defaultValue(new SettingColor(255, 0, 0, 255))
            .build()
        );
        EnemyColorManager.setEnemyColorSetting(bephax$enemyColor);
    }
    @Inject(method = "getPlayerName", at = @At("RETURN"), cancellable = true)
    private void injectEnemyColor(PlayerListEntry playerListEntry, CallbackInfoReturnable<Text> cir) {
        if (playerListEntry == null || playerListEntry.getProfile() == null) return;
        String playerName = playerListEntry.getProfile().name();
        if (EnemyManager.getInstance().isEnemy(playerName) && bephax$enemyColor != null) {
            int color = bephax$enemyColor.get().getPacked();
            Text original = cir.getReturnValue();
            String textContent = original.getString();
            cir.setReturnValue(Text.literal(textContent).styled(style -> style.withColor(color)));
        }
    }
}