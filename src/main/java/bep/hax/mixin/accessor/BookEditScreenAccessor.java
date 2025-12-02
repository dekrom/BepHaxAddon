package bep.hax.mixin.accessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import net.minecraft.client.gui.widget.EditBoxWidget;
import net.minecraft.client.gui.screen.ingame.BookEditScreen;
@Mixin(BookEditScreen.class)
public interface BookEditScreenAccessor {
    @Accessor
    EditBoxWidget getEditBox();
}