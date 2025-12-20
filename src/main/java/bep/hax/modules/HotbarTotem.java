package bep.hax.modules;
import bep.hax.mixin.accessor.PlayerInventoryAccessor;
import bep.hax.Bep;
import bep.hax.util.InventoryManager;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.EventPriority;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.*;
public class HotbarTotem extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgAdvanced = settings.createGroup("Advanced");
    private final Setting<Integer> totemSlot = sgGeneral.add(new IntSetting.Builder()
        .name("totem-slot")
        .description("Hotbar slot to keep totem in (1-9)")
        .defaultValue(9)
        .min(1)
        .max(9)
        .sliderRange(1, 9)
        .build()
    );
    private final Setting<Boolean> autoRefill = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-refill")
        .description("Automatically refill totem to the designated slot")
        .defaultValue(true)
        .build()
    );
    private final Setting<Integer> refillDelay = sgAdvanced.add(new IntSetting.Builder()
        .name("refill-delay")
        .description("Delay in ticks between refill attempts")
        .defaultValue(5)
        .min(0)
        .max(20)
        .sliderRange(0, 20)
        .build()
    );
    private final Setting<Integer> inactivityDelay = sgAdvanced.add(new IntSetting.Builder()
        .name("inactivity-delay")
        .description("Ticks to wait after activity before restoring to totem")
        .defaultValue(8)
        .min(1)
        .max(60)
        .sliderRange(1, 60)
        .build()
    );
    private final Setting<Boolean> onlyWhenDamaged = sgAdvanced.add(new BoolSetting.Builder()
        .name("only-when-damaged")
        .description("Only activate when player has taken damage")
        .defaultValue(false)
        .build()
    );
    private int refillTicks = 0;
    private float lastHealth = 20.0f;
    private InventoryManager inventoryManager;
    private int ticksSinceActivity = 0;
    public HotbarTotem() {
        super(Bep.CATEGORY, "hotbar-totem", "Server-side totem with silent swaps for manual item usage");
    }
    @Override
    public void onActivate() {
        if (mc.player == null) return;
        refillTicks = 0;
        lastHealth = mc.player.getHealth();
        inventoryManager = InventoryManager.getInstance();
        ticksSinceActivity = 999;
    }
    @Override
    public void onDeactivate() {
        if (inventoryManager != null && inventoryManager.isDesynced()) {
            inventoryManager.syncToClient();
        }
    }
    @EventHandler(priority = EventPriority.LOW)
    private void onPacketSend(PacketEvent.Send event) {
        if (!isActive() || mc.player == null) return;
        int totemSlotIndex = totemSlot.get() - 1;
        int currentClientSlot = ((PlayerInventoryAccessor) mc.player.getInventory()).getSelectedSlot();
        int serverSlot = inventoryManager.getServerSlot();
        int currentPriority = inventoryManager.getCurrentPriority();
        if (currentPriority > InventoryManager.Priority.NORMAL) {
            return;
        }
        if (currentClientSlot == totemSlotIndex) {
            return;
        }
        boolean isInteraction = event.packet instanceof PlayerInteractBlockC2SPacket ||
                               event.packet instanceof PlayerInteractItemC2SPacket ||
                               event.packet instanceof PlayerInteractEntityC2SPacket;
        if (isInteraction) {
            ticksSinceActivity = 0;
            if (serverSlot != currentClientSlot) {
                inventoryManager.setSlotForced(currentClientSlot);
            }
        }
    }
    @EventHandler(priority = EventPriority.LOWEST)
    private void onTick(TickEvent.Post event) {
        if (!isActive() || mc.player == null || mc.world == null) return;
        if (mc.player.isDead() || mc.player.getHealth() <= 0.0f) return;
        if (onlyWhenDamaged.get()) {
            float currentHealth = mc.player.getHealth();
            if (currentHealth >= lastHealth) {
                lastHealth = currentHealth;
                return;
            }
            lastHealth = currentHealth;
        }
        if (autoRefill.get()) {
            refillTicks++;
            if (refillTicks >= refillDelay.get()) {
                refillTicks = 0;
                refillTotem();
            }
        }
        int totemSlotIndex = totemSlot.get() - 1;
        int currentClientSlot = ((PlayerInventoryAccessor) mc.player.getInventory()).getSelectedSlot();
        int serverSlot = inventoryManager.getServerSlot();
        int currentPriority = inventoryManager.getCurrentPriority();
        boolean isActive = mc.player.isUsingItem() ||
                          mc.options.attackKey.isPressed() ||
                          mc.options.useKey.isPressed();
        if (isActive) {
            ticksSinceActivity = 0;
        } else {
            ticksSinceActivity++;
        }
        if (currentPriority > InventoryManager.Priority.NORMAL) {
            ticksSinceActivity = 0;
            return;
        }
        boolean hasTotem = mc.player.getInventory().getStack(totemSlotIndex).getItem() == Items.TOTEM_OF_UNDYING;
        boolean serverNotOnTotem = serverSlot != totemSlotIndex;
        boolean clientNotOnTotem = currentClientSlot != totemSlotIndex;
        boolean canRestore = ticksSinceActivity >= inactivityDelay.get();
        if (hasTotem && serverNotOnTotem && clientNotOnTotem && canRestore) {
            inventoryManager.setSlot(totemSlotIndex, InventoryManager.Priority.TOTEM);
        }
    }
    private void refillTotem() {
        if (mc.player == null) return;
        int totemSlotIndex = totemSlot.get() - 1;
        if (mc.player.getInventory().getStack(totemSlotIndex).getItem() == Items.TOTEM_OF_UNDYING) {
            return;
        }
        for (int i = 9; i < 36; i++) {
            if (mc.player.getInventory().getStack(i).getItem() == Items.TOTEM_OF_UNDYING) {
                InvUtils.move().from(i).to(totemSlotIndex);
                break;
            }
        }
    }
    @Override
    public String getInfoString() {
        if (mc.player == null || inventoryManager == null) return null;
        int totemSlotIndex = totemSlot.get() - 1;
        int totemCount = 0;
        if (mc.player.getInventory().getStack(totemSlotIndex).getItem() == Items.TOTEM_OF_UNDYING) {
            totemCount = mc.player.getInventory().getStack(totemSlotIndex).getCount();
        }
        int serverSlot = inventoryManager.getServerSlot();
        boolean isProtected = serverSlot == totemSlotIndex;
        return String.format("%s (%d)", isProtected ? "Protected" : "Active", totemCount);
    }
}