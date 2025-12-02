package bep.hax;
import bep.hax.hud.*;
import bep.hax.modules.*;
import bep.hax.modules.searcharea.SearchArea;
import bep.hax.config.StardustConfig;
import meteordevelopment.meteorclient.addons.GithubRepo;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.commands.Commands;
import meteordevelopment.meteorclient.systems.hud.Hud;
import meteordevelopment.meteorclient.systems.hud.HudGroup;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Modules;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import bep.hax.managers.PacketManager;
import net.minecraft.item.Items;
public class Bep extends MeteorAddon {
    public static final Logger LOG = LoggerFactory.getLogger("BepHax");
    public static final Category CATEGORY = new Category("Bephax", Items.ENCHANTED_GOLDEN_APPLE.getDefaultStack());
    public static final Category STASH = new Category("Stash Hunt", Items.ELYTRA.getDefaultStack());
    public static final Category STARDUST = new Category("Stardust", Items.TRIDENT.getDefaultStack());
    public static final HudGroup HUD_GROUP = new HudGroup("Bephax");
    private PacketManager packetManager;
    @Override
    public void onInitialize() {
        LOG.info("BEPHAX LOADING.");
        bep.hax.modules.StashMoverSelectionHandler.init();
        Hud.get().register(ItemCounterHud.INFO);
        Hud.get().register(EntityList.INFO);
        Hud.get().register(DimensionCoords.INFO);
        Hud.get().register(SpeedKMH.INFO);
        Hud.get().register(DubCounterHud.INFO);
        Hud.get().register(MobInfo.INFO);
        Modules.get().add(new AutoSmith());
        Modules.get().add(new BepMine());
        Modules.get().add(new BepCrystal());
        Modules.get().add(new YawLock());
        Modules.get().add(new UnfocusedFpsLimiter());
        Modules.get().add(new ShulkerOverviewModule());
        Modules.get().add(new ItemSearchBar());
        Modules.get().add(new MineESP());
        Modules.get().add(new Surround());
        Modules.get().add(new Phase());
        Modules.get().add(new Criticals());
        Modules.get().add(new PearlLoader());
        Modules.get().add(new SignRender());
        Modules.get().add(new WheelPicker());
        Modules.get().add(new NoHurtCam());
        Modules.get().add(new ElytraSwap());
        Modules.get().add(new HotbarTotem());
        Modules.get().add(new InvFix());
        Modules.get().add(new WebChat());
        Modules.get().add(new bep.hax.modules.livemessage.LiveMessage());
        Modules.get().add(new Replenish());
        Modules.get().add(new GhostMode());
        Modules.get().add(new AutoBreed());
        Modules.get().add(new StashMover());
        Commands.add(new bep.hax.commands.SetInput());
        Commands.add(new bep.hax.commands.SetOutput());
        Commands.add(new bep.hax.commands.StashStatus());
        Commands.add(new bep.hax.commands.SetClear());
        Commands.add(new bep.hax.commands.EnemyCommand());
        Modules.get().add(new KillEffects());
        Modules.get().add(new RespawnPointBlocker());
        Modules.get().add(new MapDuplicator());
        Modules.get().add(new ElytraFlyPlusPlus());
        Modules.get().add(new AFKVanillaFly());
        Modules.get().add(new NoJumpDelay());
        Modules.get().add(new AutoEXPPlus());
        Modules.get().add(new AutoPortal());
        Modules.get().add(new ChestIndex());
        Modules.get().add(new bep.hax.modules.chesttracker.ChestTrackerModule());
        Modules.get().add(new GotoPosition());
        Modules.get().add(new HighlightOldLava());
        Modules.get().add(new Pitch40Util());
        Modules.get().add(new GrimAirPlace());
        Modules.get().add(new GrimScaffold());
        Modules.get().add(new TrailFollower());
        Modules.get().add(new Stripper());
        Modules.get().add(new VanityESP());
        Modules.get().add(new BetterStashFinder());
        Modules.get().add(new OldChunkNotifier());
        Modules.get().add(new SearchArea());
        Modules.get().add(new DiscordNotifs());
        Modules.get().add(new TrailMaker());
        Commands.add(new bep.hax.commands.Coordinates());
        Commands.add(new bep.hax.commands.Center());
        Modules.get().add(new AutoCraft());
        Commands.add(new bep.hax.commands.FirstSeen2b2t());
        Commands.add(new bep.hax.commands.LastSeen2b2t());
        Commands.add(new bep.hax.commands.Playtime2b2t());
        Commands.add(new bep.hax.commands.Stats2b2t());
        Commands.add(new bep.hax.commands.Panorama());
        Commands.add(new bep.hax.commands.Loadout());
        Commands.add(new bep.hax.commands.ChestTrackerCommand());
        Modules.get().add(new AdBlocker());
        Modules.get().add(new Loadouts());
        Modules.get().add(new AntiToS());
        Modules.get().add(new ChatSigns());
        Modules.get().add(new Archaeology());
        Modules.get().add(new AutoDrawDistance());
        Modules.get().add(new AutoDyeShulkers());
        Modules.get().add(new AxolotlTools());
        Modules.get().add(new BannerData());
        Modules.get().add(new BookTools());
        Modules.get().add(new Honker());
        Modules.get().add(new LoreLocator());
        Modules.get().add(new MusicTweaks());
        Modules.get().add(new PagePirate());
        Modules.get().add(new RapidFire());
        Modules.get().add(new RoadTrip());
        Modules.get().add(new RocketJump());
        Modules.get().add(new RocketMan());
        Modules.get().add(new SignHistorian());
        Modules.get().add(new SignatureSign());
        Modules.get().add(new StashBrander());
        Modules.get().add(new WaxAura());
        Modules.get().add(new Updraft());
        Modules.get().add(new Grinder());
        Modules.get().add(new AutoDoors());
        Modules.get().add(new AutoMason());
        Modules.get().add(new DisconnectSound());
        Modules.get().add(new AutoRespond());
        packetManager = new PacketManager();
        StardustConfig.initialize();
        LOG.info("BEPHAX LOADED.");
    }
    @Override
    public void onRegisterCategories() {
        Modules.registerCategory(CATEGORY);
        Modules.registerCategory(STASH);
        Modules.registerCategory(STARDUST);
    }
    @Override
    public String getWebsite() { return "https://github.com/dekrom/BephaxAddon"; }
    @Override
    public String getPackage() {
        return "bep.hax";
    }
    @Override
    public GithubRepo getRepo() {
        return new GithubRepo("BepHaxAddon", "bephaxaddon");
    }
}