package bep.hax.commands;
import bep.hax.util.EnemyManager;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.arguments.StringArgumentType;
import meteordevelopment.meteorclient.commands.Command;
import meteordevelopment.meteorclient.systems.friends.Friends;
import net.minecraft.command.CommandSource;
import static com.mojang.brigadier.Command.SINGLE_SUCCESS;
public class EnemyCommand extends Command {
    public EnemyCommand() {
        super("enemy", "Manage friends marked as enemies");
    }
    @Override
    public void build(LiteralArgumentBuilder<CommandSource> builder) {
        builder.then(literal("add")
            .then(argument("name", StringArgumentType.word())
                .executes(context -> {
                    String name = context.getArgument("name", String.class);
                    if (EnemyManager.getInstance().isEnemy(name)) {
                        error("(highlight)%s(default) is already marked as an enemy.", name);
                        return SINGLE_SUCCESS;
                    }
                    if (EnemyManager.getInstance().add(name)) {
                        info("Marked (highlight)%s(default) as an enemy.", name);
                    } else {
                        error("Failed to mark (highlight)%s(default) as an enemy.", name);
                    }
                    return SINGLE_SUCCESS;
                })
            )
        );
        builder.then(literal("remove")
            .then(argument("name", StringArgumentType.word())
                .executes(context -> {
                    String name = context.getArgument("name", String.class);
                    if (!EnemyManager.getInstance().isEnemy(name)) {
                        error("(highlight)%s(default) is not marked as an enemy.", name);
                        return SINGLE_SUCCESS;
                    }
                    if (EnemyManager.getInstance().remove(name)) {
                        info("Removed (highlight)%s(default) from enemies.", name);
                    } else {
                        error("Failed to remove (highlight)%s(default) from enemies.", name);
                    }
                    return SINGLE_SUCCESS;
                })
            )
        );
        builder.then(literal("list")
            .executes(context -> {
                if (EnemyManager.getInstance().count() == 0) {
                    info("No enemies marked.");
                    return SINGLE_SUCCESS;
                }
                info("--- Enemies (highlight)(%d)(default) ---", EnemyManager.getInstance().count());
                for (String name : EnemyManager.getInstance().getEnemyNames()) {
                    info(" - (highlight)%s", name);
                }
                return SINGLE_SUCCESS;
            })
        );
        builder.then(literal("clear")
            .executes(context -> {
                int count = EnemyManager.getInstance().count();
                if (count == 0) {
                    info("No enemies to clear.");
                    return SINGLE_SUCCESS;
                }
                EnemyManager.getInstance().clear();
                info("Cleared (highlight)%d(default) enemies.", count);
                return SINGLE_SUCCESS;
            })
        );
    }
}