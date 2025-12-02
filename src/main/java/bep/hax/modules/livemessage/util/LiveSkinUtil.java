package bep.hax.modules.livemessage.util;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.util.DefaultSkinHelper;
import net.minecraft.util.Identifier;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
public class LiveSkinUtil {
    private static final Map<UUID, LiveSkinUtil> SKIN_CACHE = new ConcurrentHashMap<>();
    private final UUID uuid;
    private LiveSkinUtil(UUID uuid) {
        this.uuid = uuid;
    }
    public static LiveSkinUtil get(UUID uuid) {
        return SKIN_CACHE.computeIfAbsent(uuid, LiveSkinUtil::new);
    }
    public static void clearCache() {
        SKIN_CACHE.clear();
    }
    public void reloadSkin() {
    }
    public boolean hasLocationSkin() {
        return getLocationSkin() != null;
    }
    public String getSkinType() {
        return "default";
    }
    public boolean customSkinLoaded() {
        return true;
    }
    public Identifier getLocationSkin() {
        return DefaultSkinHelper.getTexture();
    }
    public Identifier getLocationCape() {
        return null;
    }
    public Identifier getLocationElytra() {
        return null;
    }
}