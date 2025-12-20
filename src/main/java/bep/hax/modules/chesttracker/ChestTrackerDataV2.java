package bep.hax.modules.chesttracker;
import com.google.gson.*;
import meteordevelopment.meteorclient.MeteorClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;
public class ChestTrackerDataV2 {
    private static final Logger LOGGER = LoggerFactory.getLogger("ChestTracker");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int CURRENT_VERSION = 2;
    private final Map<String, Map<BlockPos, TrackedContainer>> containers;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final MinecraftClient mc;
    private File dataFile;
    private File backupFile;
    private File tempFile;
    private long lastSaveTime = 0;
    private int saveFailures = 0;
    public ChestTrackerDataV2() {
        this.containers = new ConcurrentHashMap<>();
        this.mc = MinecraftClient.getInstance();
        initializeFiles();
    }
    private void initializeFiles() {
        try {
            File folder = new File(MeteorClient.FOLDER, "ChestTracker");
            if (!folder.exists() && !folder.mkdirs()) {
                LOGGER.error("Failed to create ChestTracker folder");
            }
            dataFile = new File(folder, "tracked_containers.json");
            backupFile = new File(folder, "tracked_containers.backup.json");
            tempFile = new File(folder, "tracked_containers.tmp");
        } catch (Exception e) {
            LOGGER.error("Failed to initialize files", e);
        }
    }
    public void trackContainer(BlockPos pos, String dimension, String containerType, List<ItemStack> contents) {
        lock.writeLock().lock();
        try {
            Map<BlockPos, TrackedContainer> dimContainers = containers.computeIfAbsent(dimension, k -> new ConcurrentHashMap<>());
            TrackedContainer container = dimContainers.get(pos);
            if (container == null) {
                container = new TrackedContainer(pos, dimension, containerType);
                dimContainers.put(pos, container);
            }
            container.updateContents(contents);
        } finally {
            lock.writeLock().unlock();
        }
    }
    public TrackedContainer getContainer(BlockPos pos, String dimension) {
        lock.readLock().lock();
        try {
            Map<BlockPos, TrackedContainer> dimContainers = containers.get(dimension);
            return dimContainers != null ? dimContainers.get(pos) : null;
        } finally {
            lock.readLock().unlock();
        }
    }
    public List<TrackedContainer> searchItem(Item item) {
        String currentDim = getCurrentDimension();
        return searchItem(item, currentDim);
    }
    public List<TrackedContainer> searchItem(Item item, String dimension) {
        lock.readLock().lock();
        try {
            Map<BlockPos, TrackedContainer> dimContainers = containers.get(dimension);
            if (dimContainers == null) {
                return new ArrayList<>();
            }
            return dimContainers.values().stream()
                .filter(c -> c.containsItem(item))
                .sorted((a, b) -> {
                    String itemId = net.minecraft.registry.Registries.ITEM.getId(item).toString();
                    return Integer.compare(b.getItemCount(itemId), a.getItemCount(itemId));
                })
                .collect(Collectors.toList());
        } finally {
            lock.readLock().unlock();
        }
    }
    public List<TrackedContainer> getAllContainers() {
        return getAllContainers(getCurrentDimension());
    }
    public List<TrackedContainer> getAllContainers(String dimension) {
        lock.readLock().lock();
        try {
            Map<BlockPos, TrackedContainer> dimContainers = containers.get(dimension);
            return dimContainers != null ? new ArrayList<>(dimContainers.values()) : new ArrayList<>();
        } finally {
            lock.readLock().unlock();
        }
    }
    public int getTotalContainerCount() {
        lock.readLock().lock();
        try {
            return containers.values().stream()
                .mapToInt(Map::size)
                .sum();
        } finally {
            lock.readLock().unlock();
        }
    }
    public int getCurrentDimensionContainerCount() {
        String dimension = getCurrentDimension();
        lock.readLock().lock();
        try {
            Map<BlockPos, TrackedContainer> dimContainers = containers.get(dimension);
            return dimContainers != null ? dimContainers.size() : 0;
        } finally {
            lock.readLock().unlock();
        }
    }
    public int removeEmptyContainers() {
        lock.writeLock().lock();
        try {
            int removed = 0;
            for (Map<BlockPos, TrackedContainer> dimContainers : containers.values()) {
                Iterator<Map.Entry<BlockPos, TrackedContainer>> it = dimContainers.entrySet().iterator();
                while (it.hasNext()) {
                    if (it.next().getValue().isEmpty()) {
                        it.remove();
                        removed++;
                    }
                }
            }
            return removed;
        } finally {
            lock.writeLock().unlock();
        }
    }
    public int removeOldContainers(int days) {
        lock.writeLock().lock();
        try {
            long cutoff = System.currentTimeMillis() - (days * 24L * 60 * 60 * 1000);
            int removed = 0;
            for (Map<BlockPos, TrackedContainer> dimContainers : containers.values()) {
                Iterator<Map.Entry<BlockPos, TrackedContainer>> it = dimContainers.entrySet().iterator();
                while (it.hasNext()) {
                    if (it.next().getValue().getLastUpdated() < cutoff) {
                        it.remove();
                        removed++;
                    }
                }
            }
            return removed;
        } finally {
            lock.writeLock().unlock();
        }
    }
    public void clearAll() {
        lock.writeLock().lock();
        try {
            containers.clear();
        } finally {
            lock.writeLock().unlock();
        }
    }
    public void clearCurrentDimension() {
        String dimension = getCurrentDimension();
        lock.writeLock().lock();
        try {
            containers.remove(dimension);
        } finally {
            lock.writeLock().unlock();
        }
    }
    public void saveData() {
        lock.readLock().lock();
        try {
            JsonObject root = new JsonObject();
            root.addProperty("version", CURRENT_VERSION);
            root.addProperty("saveTime", System.currentTimeMillis());
            JsonObject dimensions = new JsonObject();
            for (Map.Entry<String, Map<BlockPos, TrackedContainer>> dimEntry : containers.entrySet()) {
                JsonArray dimArray = new JsonArray();
                for (TrackedContainer container : dimEntry.getValue().values()) {
                    dimArray.add(container.toJson());
                }
                dimensions.add(dimEntry.getKey(), dimArray);
            }
            root.add("dimensions", dimensions);
            try (Writer writer = new OutputStreamWriter(new FileOutputStream(tempFile), StandardCharsets.UTF_8)) {
                GSON.toJson(root, writer);
            }
            if (dataFile.exists() && dataFile.length() > 0) {
                Files.copy(dataFile.toPath(), backupFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            Files.move(tempFile.toPath(), dataFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            lastSaveTime = System.currentTimeMillis();
            saveFailures = 0;
        } catch (Exception e) {
            saveFailures++;
            LOGGER.error("Failed to save data (attempt {})", saveFailures, e);
            if (saveFailures > 3) {
                LOGGER.error("Multiple save failures, data may be lost!");
            }
        } finally {
            lock.readLock().unlock();
        }
    }
    public void saveBackup() throws IOException {
        lock.readLock().lock();
        try {
            JsonObject root = new JsonObject();
            root.addProperty("version", CURRENT_VERSION);
            root.addProperty("backupTime", System.currentTimeMillis());
            JsonObject dimensions = new JsonObject();
            for (Map.Entry<String, Map<BlockPos, TrackedContainer>> dimEntry : containers.entrySet()) {
                JsonArray dimArray = new JsonArray();
                for (TrackedContainer container : dimEntry.getValue().values()) {
                    dimArray.add(container.toJson());
                }
                dimensions.add(dimEntry.getKey(), dimArray);
            }
            root.add("dimensions", dimensions);
            try (Writer writer = new OutputStreamWriter(new FileOutputStream(backupFile), StandardCharsets.UTF_8)) {
                GSON.toJson(root, writer);
            }
        } finally {
            lock.readLock().unlock();
        }
    }
    public void loadData() {
        lock.writeLock().lock();
        try {
            containers.clear();
            if (loadFromFile(dataFile)) {
                LOGGER.info("Loaded data from main file");
                return;
            }
            if (loadFromFile(backupFile)) {
                LOGGER.warn("Main file corrupted, loaded from backup");
                saveData();
                return;
            }
            File oldFile = new File(MeteorClient.FOLDER, "ChestTracker/tracked_containers.json");
            if (oldFile.exists() && loadFromFile(oldFile)) {
                LOGGER.info("Migrated data from old format");
                saveData();
                return;
            }
            LOGGER.info("No existing data found, starting fresh");
        } finally {
            lock.writeLock().unlock();
        }
    }
    private boolean loadFromFile(File file) {
        if (!file.exists() || file.length() == 0) {
            return false;
        }
        try {
            String json = Files.readString(file.toPath(), StandardCharsets.UTF_8);
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            int version = root.has("version") ? root.get("version").getAsInt() : 1;
            if (root.has("dimensions")) {
                JsonObject dimensions = root.getAsJsonObject("dimensions");
                for (Map.Entry<String, JsonElement> dimEntry : dimensions.entrySet()) {
                    String dimension = dimEntry.getKey();
                    JsonArray dimArray = dimEntry.getValue().getAsJsonArray();
                    Map<BlockPos, TrackedContainer> dimContainers = new ConcurrentHashMap<>();
                    for (JsonElement element : dimArray) {
                        try {
                            TrackedContainer container = TrackedContainer.fromJson(element.getAsJsonObject());
                            dimContainers.put(container.getPosition(), container);
                        } catch (Exception e) {
                            LOGGER.warn("Skipped corrupted container entry", e);
                        }
                    }
                    if (!dimContainers.isEmpty()) {
                        containers.put(dimension, dimContainers);
                    }
                }
            }
            return true;
        } catch (Exception e) {
            LOGGER.error("Failed to load from file: {}", file.getName(), e);
            return false;
        }
    }
    public void exportData(String filename) throws IOException {
        lock.readLock().lock();
        try {
            File exportFile = new File(new File(MeteorClient.FOLDER, "ChestTracker"), filename);
            JsonObject export = new JsonObject();
            export.addProperty("version", CURRENT_VERSION);
            export.addProperty("exportTime", System.currentTimeMillis());
            export.addProperty("totalContainers", getTotalContainerCount());
            JsonObject dimensions = new JsonObject();
            for (Map.Entry<String, Map<BlockPos, TrackedContainer>> dimEntry : containers.entrySet()) {
                JsonArray dimArray = new JsonArray();
                for (TrackedContainer container : dimEntry.getValue().values()) {
                    dimArray.add(container.toJson());
                }
                dimensions.add(dimEntry.getKey(), dimArray);
            }
            export.add("dimensions", dimensions);
            try (Writer writer = new OutputStreamWriter(new FileOutputStream(exportFile), StandardCharsets.UTF_8)) {
                GSON.toJson(export, writer);
            }
        } finally {
            lock.readLock().unlock();
        }
    }
    private String getCurrentDimension() {
        if (mc.world == null) return "unknown";
        RegistryKey<World> key = mc.world.getRegistryKey();
        return key.getValue().toString();
    }
}