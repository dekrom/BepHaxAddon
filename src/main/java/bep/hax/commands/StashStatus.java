package bep.hax.commands;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import meteordevelopment.meteorclient.commands.Command;
import meteordevelopment.meteorclient.systems.modules.Modules;
import bep.hax.modules.StashMover;
import net.minecraft.command.CommandSource;
import static meteordevelopment.meteorclient.MeteorClient.mc;
public class StashStatus extends Command {
    public StashStatus() {
        super("stashstatus", "Check StashMover areas and configuration");
    }
    @Override
    public void build(LiteralArgumentBuilder<CommandSource> builder) {
        builder.executes(context -> {
            if (mc.player == null) return 0;
            StashMover module = Modules.get().get(StashMover.class);
            if (module != null) {
                String prefix = meteordevelopment.meteorclient.systems.config.Config.get().prefix.get();
                info("§6=== StashMover Status ===");
                if (module.isActive()) {
                    info("§aModule: §fACTIVE");
                    info("§7State: §f" + module.getCurrentState());
                    info("§7Items moved: §f" + module.getItemsTransferred());
                    info("§7Containers processed: §f" + module.getContainersProcessed());
                } else {
                    info("§cModule: §fINACTIVE");
                    info("§7Use §f" + prefix + "stash-mover §7to activate");
                }
                info("");
                boolean hasInput = module.hasInputArea();
                boolean hasOutput = module.hasOutputArea();
                if (hasInput) {
                    info("§aInput Area: §fSET");
                    info("§7  Containers: §f" + module.getInputContainerCount());
                } else {
                    info("§cInput Area: §fNOT SET");
                    info("§7  Use §f" + prefix + "setinput §7to select");
                }
                if (hasOutput) {
                    info("§bOutput Area: §fSET");
                    info("§7  Containers: §f" + module.getOutputContainerCount());
                } else {
                    info("§cOutput Area: §fNOT SET");
                    info("§7  Use §f" + prefix + "setoutput §7to select");
                }
                if (hasInput && hasOutput) {
                    info("");
                    info("§aReady to use! §7Enable module to start.");
                }
            } else {
                error("StashMover module not found!");
            }
            return SINGLE_SUCCESS;
        });
    }
}