package bep.hax.mixin;

import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import bep.hax.config.StardustConfig;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.network.ServerInfo;
import org.spongepowered.asm.mixin.injection.At;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.gui.screen.TitleScreen;
import org.spongepowered.asm.mixin.injection.Inject;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.screen.SplashTextRenderer;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * @author Tas [0xTas] <root@0xTas.dev>
 **/
@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin extends Screen {
    @Unique
    private static final ServerInfo OLD_SERVER = new ServerInfo("2b2t", "2b2t.org", ServerInfo.ServerType.OTHER);

    @Shadow
    private @Nullable SplashTextRenderer splashText;

    protected TitleScreenMixin(Text title) {
        super(title);
    }

    @Unique
    private int timer = 0;
    @Unique
    private @Nullable MinecraftClient mc = null;

    @Unique
    private void onClick2b2tButton(ButtonWidget btn) {
        if (mc == null) mc = MinecraftClient.getInstance();
        ConnectScreen.connect(mc.currentScreen, mc,
            ServerAddress.parse(OLD_SERVER.address), OLD_SERVER, true, null
        );
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void mixinInit(CallbackInfo ci) {
        if (StardustConfig.directConnectButtonSetting.get()) {
            this.addDrawableChild(ButtonWidget.builder(
                    Text.of("§c§l2§a§lB"), this::onClick2b2tButton)
                .dimensions(this.width / 2 + 104, this.height / 4 + 72, 20, 20)
                .build()
            );
        }
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void mixinTick(CallbackInfo ci) {
        if (mc == null) {
            mc = MinecraftClient.getInstance();
            return;
        }

        ++timer;
        if (timer >= 420 && StardustConfig.rotateSplashTextSetting.get()) {
            timer = 0;
            splashText = mc.getSplashTextLoader().get();
        }
    }
}
