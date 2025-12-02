package bep.hax.util;
import net.minecraft.entity.player.PlayerEntity;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArraySet;
import static meteordevelopment.meteorclient.MeteorClient.mc;
public class EnemyManager {
    private static EnemyManager INSTANCE;
    private final Set<UUID> enemies = new CopyOnWriteArraySet<>();
    private final Set<String> enemyNames = new CopyOnWriteArraySet<>();
    private EnemyManager() {}
    public static EnemyManager getInstance() {
        if (INSTANCE == null) INSTANCE = new EnemyManager();
        return INSTANCE;
    }
    public boolean add(String name) {
        if (name == null || name.isEmpty()) return false;
        enemyNames.add(name);
        if (mc.world != null) {
            for (PlayerEntity p : mc.world.getPlayers())
                if (p.getName().getString().equalsIgnoreCase(name)) enemies.add(p.getUuid());
        }
        return true;
    }
    public boolean add(PlayerEntity player) {
        if (player == null) return false;
        enemyNames.add(player.getName().getString());
        enemies.add(player.getUuid());
        return true;
    }
    public boolean remove(String name) {
        if (name == null || name.isEmpty()) return false;
        boolean removed = enemyNames.remove(name);
        if (mc.world != null)
            for (PlayerEntity p : mc.world.getPlayers())
                if (p.getName().getString().equalsIgnoreCase(name))
                    enemies.remove(p.getUuid());
        return removed;
    }
    public boolean remove(PlayerEntity player) {
        if (player == null) return false;
        enemyNames.remove(player.getName().getString());
        enemies.remove(player.getUuid());
        return true;
    }
    public boolean isEnemy(String name) {
        for (String stored : enemyNames)
            if (stored.equalsIgnoreCase(name)) return true;
        return false;
    }
    public boolean isEnemy(UUID uuid) { return enemies.contains(uuid); }
    public boolean isEnemy(PlayerEntity player) {
        return player != null && (isEnemy(player.getUuid()) || isEnemy(player.getName().getString()));
    }
    public Set<String> getEnemyNames() { return Set.copyOf(enemyNames); }
    public Set<UUID> getEnemyUUIDs() { return Set.copyOf(enemies); }
    public void clear() { enemies.clear(); enemyNames.clear(); }
    public int count() { return enemyNames.size(); }
    public void updateUUIDs() {
        if (mc.world == null) return;
        enemies.clear();
        for (String name : enemyNames)
            for (PlayerEntity p : mc.world.getPlayers())
                if (p.getName().getString().equalsIgnoreCase(name)) {
                    enemies.add(p.getUuid());
                    break;
                }
    }
}