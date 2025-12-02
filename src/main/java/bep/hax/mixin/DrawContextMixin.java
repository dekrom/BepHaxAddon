package bep.hax.mixin;
import net.minecraft.world.World;
import net.minecraft.item.ItemStack;
import bep.hax.modules.LoreLocator;
import bep.hax.modules.ItemSearchBar;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import net.minecraft.client.gui.DrawContext;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import meteordevelopment.meteorclient.systems.modules.Modules;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(DrawContext.class)
public abstract class DrawContextMixin {
    @Shadow
    public abstract void fill(int x1, int y1, int x2, int y2, int color);
    @Inject(method = "drawItem(Lnet/minecraft/item/ItemStack;II)V", at = @At("HEAD"))
    private void highlightNamedItems(ItemStack stack, int x, int y, CallbackInfo ci) {
        Modules modules = Modules.get();
        if (modules == null) return;
        LoreLocator ll = modules.get(LoreLocator.class);
        if (ll != null && ll.isActive() && ll.shouldHighlightSlot(stack)) {
            this.fill(x - 1, y - 1, x + 17, y + 17, ll.color.get().getPacked());
            return;
        }
        ItemSearchBar isb = modules.get(ItemSearchBar.class);
        if (isb != null && isb.isActive() && isb.shouldHighlightSlot(stack)) {
            this.fill(x - 1, y - 1, x + 17, y + 17, isb.highlightColor.get().getPacked());
        }
    }
    @Inject(method = "drawItemWithoutEntity(Lnet/minecraft/item/ItemStack;III)V", at = @At("HEAD"))
    private void highlightNamedItemsNoEntity(ItemStack stack, int x, int y, int seed, CallbackInfo ci) {
        Modules modules = Modules.get();
        if (modules == null) return;
        LoreLocator ll = modules.get(LoreLocator.class);
        if (ll != null && ll.isActive() && ll.shouldHighlightSlot(stack)) {
            this.fill(x - 1, y - 1, x + 17, y + 17, ll.color.get().getPacked());
            return;
        }
        ItemSearchBar isb = modules.get(ItemSearchBar.class);
        if (isb != null && isb.isActive() && isb.shouldHighlightSlot(stack)) {
            this.fill(x - 1, y - 1, x + 17, y + 17, isb.highlightColor.get().getPacked());
        }
    }
}