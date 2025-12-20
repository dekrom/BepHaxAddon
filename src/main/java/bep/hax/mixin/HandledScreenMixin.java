package bep.hax.mixin;
import bep.hax.modules.ShulkerOverviewModule;
import bep.hax.modules.ItemSearchBar;
import bep.hax.modules.AutoCraft;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.CraftingScreenHandler;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.gui.Click;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.jetbrains.annotations.Nullable;
@Mixin(HandledScreen.class)
public abstract class HandledScreenMixin extends Screen {
    protected HandledScreenMixin(Text title) {
        super(title);
    }
    @Shadow protected int x;
    @Shadow protected int y;
    @Shadow public abstract ScreenHandler getScreenHandler();
    @Unique private TextFieldWidget itemSearchField;
    @Unique private ItemSearchBar itemSearchModule;
    @Inject(method = "init", at = @At("TAIL"))
    private void onInit(CallbackInfo ci) {
        itemSearchModule = Modules.get().get(ItemSearchBar.class);
        if (itemSearchModule == null || !itemSearchModule.isActive() || !itemSearchModule.shouldShowSearchField()) return;
        itemSearchField = new TextFieldWidget(
            MinecraftClient.getInstance().textRenderer,
            this.x + itemSearchModule.getOffsetX(),
            this.y + itemSearchModule.getOffsetY(),
            itemSearchModule.getFieldWidth(),
            itemSearchModule.getFieldHeight(),
            Text.of("Search items...")
        );
        itemSearchField.setPlaceholder(Text.of("Search items..."));
        itemSearchField.setMaxLength(100);
        String currentQuery = itemSearchModule.searchQuery.get();
        if (currentQuery != null && !currentQuery.isEmpty()) {
            itemSearchField.setText(currentQuery);
        }
        itemSearchField.setChangedListener(text -> {
            if (itemSearchModule != null) {
                itemSearchModule.updateSearchQuery(text);
            }
        });
        itemSearchField.setFocused(false);
        itemSearchField.setEditable(true);
        itemSearchField.setVisible(true);
        this.addDrawableChild(itemSearchField);
    }
    @Inject(method = "render", at = @At("TAIL"))
    private void onRender(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (itemSearchModule == null || !itemSearchModule.isActive() || !itemSearchModule.shouldShowSearchField()) return;
        if (itemSearchField == null) return;
        itemSearchField.setX(this.x + itemSearchModule.getOffsetX());
        itemSearchField.setY(this.y + itemSearchModule.getOffsetY());
        itemSearchField.setVisible(true);
    }
    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void onKeyPressed(KeyInput input, CallbackInfoReturnable<Boolean> cir) {
        if (itemSearchModule == null || !itemSearchModule.isActive() || !itemSearchModule.shouldShowSearchField()) return;
        if (itemSearchField == null) return;
        int keyCode = input.key();
        if (keyCode == 258) {
            this.setFocused(itemSearchField);
            itemSearchField.setFocused(true);
            cir.setReturnValue(true);
            return;
        }
        if (keyCode == 256 && itemSearchField.isFocused()) {
            this.setFocused(null);
            itemSearchField.setFocused(false);
            cir.setReturnValue(true);
            return;
        }
        if (itemSearchField.isFocused()) {
            itemSearchField.keyPressed(input);
            if (keyCode != 256) {
                cir.setReturnValue(true);
            }
        }
    }
    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void onMouseClicked(Click click, boolean pressed, CallbackInfoReturnable<Boolean> cir) {
        if (itemSearchModule == null || !itemSearchModule.isActive() || !itemSearchModule.shouldShowSearchField()) return;
        if (itemSearchField == null) return;
        double mouseX = click.x();
        double mouseY = click.y();
        boolean clickedOnField = mouseX >= itemSearchField.getX() &&
                                mouseX < itemSearchField.getX() + itemSearchField.getWidth() &&
                                mouseY >= itemSearchField.getY() &&
                                mouseY < itemSearchField.getY() + itemSearchField.getHeight();
        if (clickedOnField) {
            this.setFocused(itemSearchField);
            itemSearchField.setFocused(true);
            if (itemSearchField.mouseClicked(click, pressed)) {
                cir.setReturnValue(true);
                return;
            }
        } else {
            if (this.getFocused() == itemSearchField) {
                this.setFocused(null);
            }
            itemSearchField.setFocused(false);
        }
    }
    @Override
    public boolean charTyped(net.minecraft.client.input.CharInput input) {
        if (itemSearchModule != null && itemSearchModule.isActive() && itemSearchModule.shouldShowSearchField()) {
            if (itemSearchField != null && itemSearchField.isFocused()) {
                if (itemSearchField.charTyped(input)) {
                    return true;
                }
            }
        }
        return super.charTyped(input);
    }
    @Inject(method = "drawSlot", at = @At("HEAD"))
    private void onDrawSlotHead(DrawContext context, Slot slot, CallbackInfo ci) {
        if (itemSearchModule != null && itemSearchModule.isActive() && slot.hasStack()) {
            if (itemSearchModule.shouldHighlightSlot(slot.getStack())) {
                context.fill(slot.x, slot.y, slot.x + 16, slot.y + 16,
                    itemSearchModule.highlightColor.get().getPacked());
            }
        }
    }
    @Inject(method = "drawSlot", at = @At("TAIL"))
    private void onDrawSlotTail(DrawContext context, Slot slot, CallbackInfo ci) {
        ShulkerOverviewModule shulkerModule = Modules.get().get(ShulkerOverviewModule.class);
        if (shulkerModule != null && shulkerModule.isActive()) {
            shulkerModule.renderShulkerOverlay(context, slot.x, slot.y, slot.getStack());
        }
    }
}