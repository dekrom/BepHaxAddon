package bep.hax.commands;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import meteordevelopment.meteorclient.commands.Command;
import meteordevelopment.meteorclient.systems.modules.Modules;
import bep.hax.modules.StashMover;
import net.minecraft.command.CommandSource;
public class SetClear extends Command {
    public SetClear() {
        super("setclear", "Clear all StashMover area selections");
    }
    @Override
    public void build(LiteralArgumentBuilder<CommandSource> builder) {
        builder.executes(context -> {
            StashMover module = Modules.get().get(StashMover.class);
            if (module != null) {
                module.clearAreas();
                info("§cAll StashMover areas have been cleared");
                String prefix = meteordevelopment.meteorclient.systems.config.Config.get().prefix.get();
                info("§7Use §f" + prefix + "setinput §7and §f" + prefix + "setoutput §7to select new areas");
            } else {
                error("StashMover module not found!");
            }
            return SINGLE_SUCCESS;
        });
    }
}