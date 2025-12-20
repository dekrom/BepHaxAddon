package bep.hax.commands;
import bep.hax.modules.chesttracker.ChestTrackerModule;
import bep.hax.modules.chesttracker.TrackedContainer;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import meteordevelopment.meteorclient.commands.Command;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.command.CommandSource;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import java.util.List;
import static com.mojang.brigadier.Command.SINGLE_SUCCESS;
public class ChestTrackerCommand extends Command {
    public ChestTrackerCommand() {
        super("chesttracker", "Search for items in tracked containers.", "ct", "track");
    }
    @Override
    public void build(LiteralArgumentBuilder<CommandSource> builder) {
        builder.then(literal("search")
            .then(literal("hand").executes(context -> {
                ChestTrackerModule module = Modules.get().get(ChestTrackerModule.class);
                if (module == null || !module.isActive()) {
                    error("§cModule off!");
                    return SINGLE_SUCCESS;
                }
                if (mc.player == null) {
                    error("§cNot in-game!");
                    return SINGLE_SUCCESS;
                }
                ItemStack held = mc.player.getMainHandStack();
                if (held.isEmpty()) {
                    error("§cHand empty!");
                    return SINGLE_SUCCESS;
                }
                searchAndDisplay(module, held.getItem());
                return SINGLE_SUCCESS;
            }))
            .then(argument("item", StringArgumentType.greedyString()).executes(context -> {
                ChestTrackerModule module = Modules.get().get(ChestTrackerModule.class);
                if (module == null || !module.isActive()) {
                    error("§cModule off!");
                    return SINGLE_SUCCESS;
                }
                String itemName = StringArgumentType.getString(context, "item");
                Item item = findItem(itemName);
                if (item == null) {
                    error("§cUnknown: " + itemName);
                    return SINGLE_SUCCESS;
                }
                searchAndDisplay(module, item);
                return SINGLE_SUCCESS;
            }))
        );
        builder.then(literal("clear")
            .then(literal("all").executes(context -> {
                ChestTrackerModule module = Modules.get().get(ChestTrackerModule.class);
                if (module == null || !module.isActive()) {
                    error("§cModule off!");
                    return SINGLE_SUCCESS;
                }
                module.getData().clearAll();
                info("§aCleared all");
                return SINGLE_SUCCESS;
            }))
            .then(literal("dimension").executes(context -> {
                ChestTrackerModule module = Modules.get().get(ChestTrackerModule.class);
                if (module == null || !module.isActive()) {
                    error("§cModule off!");
                    return SINGLE_SUCCESS;
                }
                module.getData().clearCurrentDimension();
                info("§aCleared dimension");
                return SINGLE_SUCCESS;
            }))
        );
        builder.then(literal("export").executes(context -> {
            ChestTrackerModule module = Modules.get().get(ChestTrackerModule.class);
            if (module == null || !module.isActive()) {
                error("§cModule off!");
                return SINGLE_SUCCESS;
            }
            try {
                String fn = "export_" + new java.text.SimpleDateFormat("yyyyMMdd_HHmmss").format(new java.util.Date()) + ".json";
                module.getData().exportData(fn);
                info("§aExported: §f" + fn);
            } catch (Exception e) {
                error("§cFailed: " + e.getMessage());
            }
            return SINGLE_SUCCESS;
        }));
        builder.then(literal("nearby")
            .then(argument("radius", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 128))
                .executes(context -> {
                    ChestTrackerModule module = Modules.get().get(ChestTrackerModule.class);
                    if (module == null || !module.isActive()) {
                        error("§cModule off!");
                        return SINGLE_SUCCESS;
                    }
                    if (mc.player == null) {
                        error("§cNot in-game!");
                        return SINGLE_SUCCESS;
                    }
                    int r = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(context, "radius");
                    List<TrackedContainer> all = module.getData().getAllContainers();
                    int found = 0;
                    info("§e§lNearby (<" + r + "m):");
                    for (TrackedContainer c : all) {
                        BlockPos p = c.getPosition();
                        double d = Math.sqrt(mc.player.squaredDistanceTo(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5));
                        if (d <= r) {
                            String col = d <= module.getRenderDistance() ? "§a" : "§7";
                            info(String.format("%s[%d,%d,%d] §e%.0fm §b%d§7items%s",
                                col, p.getX(), p.getY(), p.getZ(), d, c.getItems().size(), c.isEmpty() ? " §c✗" : ""));
                            found++;
                        }
                    }
                    info(found > 0 ? "§a" + found + " found" : "§cNone found");
                    return SINGLE_SUCCESS;
                })
            )
        );
    }
    private void searchAndDisplay(ChestTrackerModule module, Item item) {
        List<TrackedContainer> results = module.getData().searchItem(item);
        String name = item.getName().getString();
        if (results.isEmpty()) {
            info("§cNone found: §f" + name);
            return;
        }
        String itemId = Registries.ITEM.getId(item).toString();
        int total = 0, near = 0;
        double rd = module.getRenderDistance(), rdSq = rd * rd;
        for (TrackedContainer c : results) {
            total += c.getItemCount(itemId);
            if (mc.player != null) {
                BlockPos p = c.getPosition();
                if (mc.player.squaredDistanceTo(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5) <= rdSq) near++;
            }
        }
        info(String.format("§a%,d §f%s §7in §e%d §7box%s", total, name, results.size(), results.size() > 1 ? "es" : ""));
        if (near < results.size()) info(String.format("§7Lit: §e%d §7Far: §c%d", near, results.size() - near));
        module.searchItem(item);
    }
    private Item findItem(String query) {
        query = query.toLowerCase().replace(" ", "_");
        Identifier id = Identifier.tryParse("minecraft:" + query);
        if (id != null && Registries.ITEM.containsId(id)) {
            return Registries.ITEM.get(id);
        }
        for (Identifier itemId : Registries.ITEM.getIds()) {
            String path = itemId.getPath();
            if (path.contains(query)) {
                return Registries.ITEM.get(itemId);
            }
        }
        return null;
    }
}