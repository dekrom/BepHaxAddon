package bep.hax.mixin;
import java.util.List;
import java.util.Arrays;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.jetbrains.annotations.Nullable;
import bep.hax.modules.SignHistorian;
import bep.hax.modules.SignatureSign;
import org.spongepowered.asm.mixin.Shadow;
import net.minecraft.block.entity.SignText;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.injection.At;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.client.util.SelectionManager;
import org.spongepowered.asm.mixin.injection.Inject;
import meteordevelopment.meteorclient.systems.modules.Modules;
import bep.hax.mixin.accessor.AbstractSignEditScreenAccessor;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.client.gui.screen.ingame.AbstractSignEditScreen;
@Mixin(AbstractSignEditScreen.class)
public abstract class AbstractSignEditScreenMixin extends Screen {
    @Shadow
    private int currentRow;
    @Shadow
    public abstract void close();
    @Shadow
    @Final
    protected SignBlockEntity blockEntity;
    @Shadow
    private @Nullable SelectionManager selectionManager;
    @Shadow
    protected abstract void setCurrentRowMessage(String message);
    protected AbstractSignEditScreenMixin(Text title) { super(title); }
    @Inject(method = "init", at = @At("TAIL"))
    public void stardustMixinInit(CallbackInfo ci) {
        if (this.client == null) return;
        Modules modules = Modules.get();
        if (modules == null) return;
        SignHistorian signHistorian = modules.get(SignHistorian.class);
        SignatureSign signatureSign = modules.get(SignatureSign.class);
        if (!signatureSign.isActive() && !signHistorian.isActive()) return;
        if (signatureSign.getAutoConfirm()) return;
        SignText restoration = signHistorian.getRestoration(this.blockEntity);
        if ((!signHistorian.isActive() || restoration == null) && signatureSign.isActive()) {
            SignText signature = signatureSign.getSignature(this.blockEntity);
            List<String> msgs = Arrays.stream(signature.getMessages(false)).map(Text::getString).toList();
            String[] messages = new String[msgs.size()];
            messages = msgs.toArray(messages);
            ((AbstractSignEditScreenAccessor) this).setText(signature);
            ((AbstractSignEditScreenAccessor) this).setMessages(messages);
            if ((signatureSign.isActive() && signatureSign.signFreedom.get())) {
                AbstractSignEditScreenAccessor accessor = ((AbstractSignEditScreenAccessor) this);
                this.selectionManager = new SelectionManager(
                    () -> accessor.getMessages()[this.currentRow], this::setCurrentRowMessage,
                    SelectionManager.makeClipboardGetter(this.client), SelectionManager.makeClipboardSetter(this.client),
                    string -> true
                );
            }
            if (signatureSign.needsDisabling()) {
                signatureSign.disable();
            }
        }
    }
}