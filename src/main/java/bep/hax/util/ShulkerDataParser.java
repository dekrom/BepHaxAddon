package bep.hax.util;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ContainerComponent;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
public class ShulkerDataParser {
    public static Map<Item, Integer> parseShulkerContents(ItemStack shulkerStack) {
        Map<Item, Integer> itemCounts = new HashMap<>();
        ContainerComponent container = shulkerStack.get(DataComponentTypes.CONTAINER);
        if (container != null) {
            List<ItemStack> items = container.stream().toList();
            for (ItemStack itemStack : items) {
                if (!itemStack.isEmpty()) {
                    itemCounts.merge(itemStack.getItem(), itemStack.getCount(), Integer::sum);
                }
            }
            if (!itemCounts.isEmpty()) {
                return itemCounts;
            }
        }
        NbtComponent customData = shulkerStack.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT);
        if (customData != null && !customData.isEmpty()) {
            NbtCompound nbt = customData.copyNbt();
            if (nbt != null && nbt.contains("BlockEntityTag")) {
                var optional = nbt.getCompound("BlockEntityTag");
                if (optional.isPresent()) {
                    NbtCompound blockEntityTag = optional.get();
                    if (blockEntityTag.contains("Items")) {
                        var itemsListOpt = blockEntityTag.getList("Items");
                        if (!itemsListOpt.isPresent()) return itemCounts;
                        NbtList items = itemsListOpt.get();
                        for (int i = 0; i < items.size(); i++) {
                            var itemOpt = items.getCompound(i);
                            if (itemOpt.isPresent()) {
                                ItemStack parsed = parseItemFromNbt(itemOpt.get());
                                if (!parsed.isEmpty()) {
                                    itemCounts.merge(parsed.getItem(), parsed.getCount(), Integer::sum);
                                }
                            }
                        }
                    }
                }
            }
        }
        return itemCounts;
    }
    private static ItemStack parseItemFromNbt(NbtCompound itemTag) {
        String id = itemTag.getString("id", "");
        if (id.isEmpty()) return ItemStack.EMPTY;
        int count = 1;
        if (itemTag.contains("count")) {
            count = itemTag.getInt("count", 1);
        } else if (itemTag.contains("Count")) {
            count = itemTag.getByte("Count", (byte) 1);
        }
        Identifier itemId = Identifier.tryParse(id);
        if (itemId == null) return ItemStack.EMPTY;
        Item item = Registries.ITEM.get(itemId);
        if (item == null || item == Registries.ITEM.get(Registries.ITEM.getDefaultId())) {
            return ItemStack.EMPTY;
        }
        return new ItemStack(item, count);
    }
}