package bep.hax.modules.chesttracker;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
public class TrackedContainer {
    private final BlockPos position;
    private final String dimension;
    private String customName;
    private final Map<String, Integer> items;
    private final List<ItemStack> itemStacks;
    private long lastUpdated;
    private String containerType;
    public TrackedContainer(BlockPos position, String dimension, String containerType) {
        this.position = position;
        this.dimension = dimension;
        this.containerType = containerType;
        this.items = new HashMap<>();
        this.itemStacks = new ArrayList<>();
        this.customName = null;
        this.lastUpdated = System.currentTimeMillis();
    }
    public void updateContents(List<ItemStack> stacks) {
        items.clear();
        itemStacks.clear();
        for (ItemStack stack : stacks) {
            if (stack != null && !stack.isEmpty()) {
                String itemId = Registries.ITEM.getId(stack.getItem()).toString();
                items.put(itemId, items.getOrDefault(itemId, 0) + stack.getCount());
                itemStacks.add(stack.copy());
            }
        }
        this.lastUpdated = System.currentTimeMillis();
    }
    public boolean containsItem(String itemId) {
        return items.containsKey(itemId);
    }
    public boolean containsItem(Item item) {
        String itemId = Registries.ITEM.getId(item).toString();
        return items.containsKey(itemId);
    }
    public int getItemCount(String itemId) {
        return items.getOrDefault(itemId, 0);
    }
    public Map<String, Integer> getItems() {
        return new HashMap<>(items);
    }
    public List<ItemStack> getItemStacks() {
        return new ArrayList<>(itemStacks);
    }
    public BlockPos getPosition() {
        return position;
    }
    public String getDimension() {
        return dimension;
    }
    public String getCustomName() {
        return customName;
    }
    public void setCustomName(String name) {
        this.customName = name;
    }
    public long getLastUpdated() {
        return lastUpdated;
    }
    public String getContainerType() {
        return containerType;
    }
    public boolean isEmpty() {
        return items.isEmpty();
    }
    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("x", position.getX());
        json.addProperty("y", position.getY());
        json.addProperty("z", position.getZ());
        json.addProperty("dimension", dimension);
        json.addProperty("type", containerType);
        json.addProperty("lastUpdated", lastUpdated);
        if (customName != null) {
            json.addProperty("customName", customName);
        }
        JsonObject itemsJson = new JsonObject();
        for (Map.Entry<String, Integer> entry : items.entrySet()) {
            itemsJson.addProperty(entry.getKey(), entry.getValue());
        }
        json.add("items", itemsJson);
        return json;
    }
    public static TrackedContainer fromJson(JsonObject json) {
        BlockPos pos = new BlockPos(
            json.get("x").getAsInt(),
            json.get("y").getAsInt(),
            json.get("z").getAsInt()
        );
        String dimension = json.get("dimension").getAsString();
        String type = json.has("type") ? json.get("type").getAsString() : "chest";
        TrackedContainer container = new TrackedContainer(pos, dimension, type);
        if (json.has("customName")) {
            container.customName = json.get("customName").getAsString();
        }
        if (json.has("lastUpdated")) {
            container.lastUpdated = json.get("lastUpdated").getAsLong();
        }
        if (json.has("items")) {
            JsonObject itemsJson = json.getAsJsonObject("items");
            for (String key : itemsJson.keySet()) {
                container.items.put(key, itemsJson.get(key).getAsInt());
            }
        }
        return container;
    }
    public String getDisplayName() {
        if (customName != null && !customName.isEmpty()) {
            return customName;
        }
        return String.format("%s [%d, %d, %d]",
            containerType.substring(0, 1).toUpperCase() + containerType.substring(1),
            position.getX(), position.getY(), position.getZ());
    }
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof TrackedContainer other)) return false;
        return position.equals(other.position) && dimension.equals(other.dimension);
    }
    @Override
    public int hashCode() {
        return position.hashCode() * 31 + dimension.hashCode();
    }
}