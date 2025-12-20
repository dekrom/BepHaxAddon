package bep.hax.mixin.accessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import net.minecraft.client.gui.EditBox;
import net.minecraft.client.gui.widget.EditBoxWidget;
@Mixin(EditBoxWidget.class)
public interface EditBoxWidgetAccessor {
    @Accessor
    EditBox getEditBox();
}