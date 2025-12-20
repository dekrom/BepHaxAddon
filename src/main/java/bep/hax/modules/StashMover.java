package bep.hax.modules;
import bep.hax.accessor.InputAccessor;
import bep.hax.mixin.accessor.PlayerInventoryAccessor;
import bep.hax.Bep;
import baritone.api.BaritoneAPI;
import baritone.api.pathing.goals.GoalBlock;
import baritone.api.pathing.goals.GoalNear;
import baritone.api.pathing.goals.GoalGetToBlock;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.util.ActionResult;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.events.entity.player.InteractBlockEvent;
import meteordevelopment.meteorclient.events.entity.player.StartBreakingBlockEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.render.RenderUtils;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.entity.BarrelBlockEntity;
import net.minecraft.block.entity.TrappedChestBlockEntity;
import net.minecraft.block.enums.ChestType;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.ClientStatusC2SPacket;
import net.minecraft.network.packet.s2c.play.*;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.state.property.Properties;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
public class StashMover extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgInput = settings.createGroup("Input");
    private final SettingGroup sgPearl = settings.createGroup("Pearl Loading");
    private final SettingGroup sgGoBack = settings.createGroup("Go Back");
    private final SettingGroup sgResetPearl = settings.createGroup("Reset Pearl");
    private final SettingGroup sgDelays = settings.createGroup("Delays");
    private final Setting<Double> containerReach = sgGeneral.add(new DoubleSetting.Builder()
        .name("container-reach")
        .description("Maximum reach distance for opening containers")
        .defaultValue(4.0)
        .min(2.5)
        .max(5.0)
        .sliderRange(2.5, 5.0)
        .build()
    );
    private final SettingGroup sgRendering = settings.createGroup("Rendering");
    private final Setting<Boolean> pauseOnLag = sgGeneral.add(new BoolSetting.Builder()
        .name("pause-on-lag")
        .description("Pause when server is lagging")
        .defaultValue(true)
        .build()
    );
    private final Setting<Integer> maxRetries = sgGeneral.add(new IntSetting.Builder()
        .name("max-retries")
        .description("Maximum retries for failed actions")
        .defaultValue(3)
        .min(1)
        .max(10)
        .build()
    );
    private final Setting<Boolean> debugMode = sgGeneral.add(new BoolSetting.Builder()
        .name("debug-mode")
        .description("Show debug messages and state transitions")
        .defaultValue(false)
        .build()
    );
    private final Setting<Boolean> onlyShulkers = sgInput.add(new BoolSetting.Builder()
        .name("only-shulkers")
        .description("Only take shulker boxes from input chests")
        .defaultValue(true)
        .build()
    );
    private final Setting<Boolean> breakEmptyContainers = sgInput.add(new BoolSetting.Builder()
        .name("break-empty")
        .description("Break empty containers after emptying them")
        .defaultValue(false)
        .build()
    );
    private final Setting<Boolean> fillEnderChest = sgInput.add(new BoolSetting.Builder()
        .name("fill-enderchest")
        .description("Fill ender chest to maximize movement")
        .defaultValue(true)
        .build()
    );
    private final Setting<String> pearlPlayerName = sgPearl.add(new StringSetting.Builder()
        .name("pearl-player")
        .description("Player name to message for pearl loading (Input→Output)")
        .defaultValue("PlayerName")
        .build()
    );
    private final Setting<String> pearlCommand = sgPearl.add(new StringSetting.Builder()
        .name("pearl-command")
        .description("Command to send for pearl loading (Input→Output)")
        .defaultValue("pearl")
        .build()
    );
    private final Setting<Integer> pearlTimeout = sgPearl.add(new IntSetting.Builder()
        .name("pearl-timeout")
        .description("Timeout for pearl loading in seconds")
        .defaultValue(10)
        .min(5)
        .max(30)
        .build()
    );
    private final Setting<Integer> pearlRetryDelay = sgPearl.add(new IntSetting.Builder()
        .name("pearl-retry-delay")
        .description("Delay between pearl command retries in ticks")
        .defaultValue(100)
        .min(20)
        .max(200)
        .build()
    );
    private final Setting<GoBackMethod> goBackMethod = sgGoBack.add(new EnumSetting.Builder<GoBackMethod>()
        .name("go-back-method")
        .description("Method to go back to input area")
        .defaultValue(GoBackMethod.PEARL)
        .build()
    );
    private final Setting<String> goBackPlayerName = sgGoBack.add(new StringSetting.Builder()
        .name("go-back-player")
        .description("Player name for go back pearl loading (Output→Input)")
        .defaultValue("PlayerName")
        .visible(() -> goBackMethod.get() == GoBackMethod.PEARL)
        .build()
    );
    private final Setting<String> goBackCommand = sgGoBack.add(new StringSetting.Builder()
        .name("go-back-command")
        .description("Command for go back pearl loading (Output→Input)")
        .defaultValue("back")
        .visible(() -> goBackMethod.get() == GoBackMethod.PEARL)
        .build()
    );
    private final Setting<BlockPos> outputPearlPickupPos = sgResetPearl.add(new BlockPosSetting.Builder()
        .name("output-pickup-pos")
        .description("Position for pearl pickup at output")
        .defaultValue(new BlockPos(0, 64, 0))
        .build()
    );
    private final Setting<BlockPos> outputPearlThrowPos = sgResetPearl.add(new BlockPosSetting.Builder()
        .name("output-throw-pos")
        .description("Position for pearl throw at output")
        .defaultValue(new BlockPos(0, 64, 0))
        .build()
    );
    private final Setting<Double> outputPearlThrowPitch = sgResetPearl.add(new DoubleSetting.Builder()
        .name("output-throw-pitch")
        .description("Pitch for throwing pearl at output (90 = straight down)")
        .defaultValue(90.0)
        .sliderRange(-90, 90)
        .build()
    );
    private final Setting<Double> outputPearlThrowYaw = sgResetPearl.add(new DoubleSetting.Builder()
        .name("output-throw-yaw")
        .description("Yaw for throwing pearl at output")
        .defaultValue(0.0)
        .sliderRange(-180, 180)
        .build()
    );
    private final Setting<BlockPos> inputPearlPickupPos = sgResetPearl.add(new BlockPosSetting.Builder()
        .name("input-pickup-pos")
        .description("Position for pearl pickup at input")
        .defaultValue(new BlockPos(0, 64, 0))
        .visible(() -> goBackMethod.get() == GoBackMethod.PEARL)
        .build()
    );
    private final Setting<BlockPos> inputPearlThrowPos = sgResetPearl.add(new BlockPosSetting.Builder()
        .name("input-throw-pos")
        .description("Position for pearl throw at input")
        .defaultValue(new BlockPos(0, 64, 0))
        .visible(() -> goBackMethod.get() == GoBackMethod.PEARL)
        .build()
    );
    private final Setting<Double> inputPearlThrowPitch = sgResetPearl.add(new DoubleSetting.Builder()
        .name("input-throw-pitch")
        .description("Pitch for throwing pearl at input (90 = straight down)")
        .defaultValue(90.0)
        .sliderRange(-90, 90)
        .visible(() -> goBackMethod.get() == GoBackMethod.PEARL)
        .build()
    );
    private final Setting<Double> inputPearlThrowYaw = sgResetPearl.add(new DoubleSetting.Builder()
        .name("input-throw-yaw")
        .description("Yaw for throwing pearl at input")
        .defaultValue(0.0)
        .sliderRange(-180, 180)
        .visible(() -> goBackMethod.get() == GoBackMethod.PEARL)
        .build()
    );
    private final Setting<Integer> pearlWaitTime = sgResetPearl.add(new IntSetting.Builder()
        .name("pearl-wait-time")
        .description("Time to wait after throwing pearl (seconds)")
        .defaultValue(5)
        .min(1)
        .max(10)
        .build()
    );
    private final Setting<Double> positionTolerance = sgResetPearl.add(new DoubleSetting.Builder()
        .name("position-tolerance")
        .description("How close to target position before throwing pearl (blocks)")
        .defaultValue(0.3)
        .min(0.1)
        .max(2.0)
        .sliderRange(0.1, 2.0)
        .build()
    );
    private final Setting<Double> trapdoorEdgeDistance = sgResetPearl.add(new DoubleSetting.Builder()
        .name("trapdoor-edge-distance")
        .description("Distance from trapdoor edge when positioning (blocks)")
        .defaultValue(0.4)
        .min(0.2)
        .max(1.0)
        .sliderRange(0.2, 1.0)
        .build()
    );
    private final Setting<Integer> openDelay = sgDelays.add(new IntSetting.Builder()
        .name("open-delay")
        .description("Delay after opening container in ticks")
        .defaultValue(10)
        .min(5)
        .max(30)
        .build()
    );
    private final Setting<Integer> transferDelay = sgDelays.add(new IntSetting.Builder()
        .name("transfer-delay")
        .description("Delay between item transfers in ticks")
        .defaultValue(2)
        .min(0)
        .max(10)
        .build()
    );
    private final Setting<Integer> closeDelay = sgDelays.add(new IntSetting.Builder()
        .name("close-delay")
        .description("Delay after closing container in ticks")
        .defaultValue(5)
        .min(0)
        .max(20)
        .build()
    );
    private final Setting<Integer> moveDelay = sgDelays.add(new IntSetting.Builder()
        .name("move-delay")
        .description("Delay between movements in ticks")
        .defaultValue(20)
        .min(5)
        .max(50)
        .build()
    );
    private final Setting<Boolean> renderSelection = sgRendering.add(new BoolSetting.Builder()
        .name("render-selection")
        .description("Render selection areas")
        .defaultValue(true)
        .build()
    );
    private final Setting<Integer> outlineWidth = sgRendering.add(new IntSetting.Builder()
        .name("outline-width")
        .description("Width of area outlines")
        .defaultValue(2)
        .min(1)
        .max(5)
        .sliderRange(1, 5)
        .build()
    );
    private final Setting<SettingColor> inputAreaColor = sgRendering.add(new ColorSetting.Builder()
        .name("input-area-outline")
        .description("Outline color for input area")
        .defaultValue(new SettingColor(0, 255, 0, 255))
        .build()
    );
    private final Setting<SettingColor> outputAreaColor = sgRendering.add(new ColorSetting.Builder()
        .name("output-area-outline")
        .description("Outline color for output area")
        .defaultValue(new SettingColor(0, 100, 255, 255))
        .build()
    );
    private final Setting<SettingColor> inputContainerColor = sgRendering.add(new ColorSetting.Builder()
        .name("input-container-color")
        .description("Color for input containers (not empty)")
        .defaultValue(new SettingColor(0, 255, 0, 100))
        .build()
    );
    private final Setting<SettingColor> outputContainerColor = sgRendering.add(new ColorSetting.Builder()
        .name("output-container-color")
        .description("Color for output containers (not full)")
        .defaultValue(new SettingColor(0, 100, 255, 100))
        .build()
    );
    private final Setting<SettingColor> activeContainerColor = sgRendering.add(new ColorSetting.Builder()
        .name("active-container-color")
        .description("Color for currently active container")
        .defaultValue(new SettingColor(255, 255, 0, 150))
        .build()
    );
    private final Setting<SettingColor> emptyContainerColor = sgRendering.add(new ColorSetting.Builder()
        .name("empty-container-color")
        .description("Color for empty containers")
        .defaultValue(new SettingColor(128, 128, 128, 50))
        .build()
    );
    private final Setting<SettingColor> fullContainerColor = sgRendering.add(new ColorSetting.Builder()
        .name("full-container-color")
        .description("Color for full containers")
        .defaultValue(new SettingColor(255, 0, 0, 50))
        .build()
    );
    public enum GoBackMethod {
        KILL("Kill"),
        PEARL("Pearl Loading");
        private final String name;
        GoBackMethod(String name) {
            this.name = name;
        }
        @Override
        public String toString() {
            return name;
        }
    }
    public static enum SelectionMode {
        NONE,
        INPUT_FIRST,
        INPUT_SECOND,
        OUTPUT_FIRST,
        OUTPUT_SECOND
    }
    public enum ProcessState {
        IDLE,
        CHECKING_LOCATION,
        INPUT_PROCESS,
        LOADING_PEARL,
        RESET_PEARL_PICKUP,
        RESET_PEARL_PLACE_SHULKER,
        RESET_PEARL_APPROACH,
        RESET_PEARL_PREPARE,
        RESET_PEARL_THROW,
        RESET_PEARL_WAIT,
        OUTPUT_PROCESS,
        GOING_BACK,
        OPENING_CONTAINER,
        TRANSFERRING_ITEMS,
        CLOSING_CONTAINER,
        BREAKING_CONTAINER,
        MOVING_TO_CONTAINER,
        MANUAL_MOVING,
        OPENING_ENDERCHEST,
        FILLING_ENDERCHEST,
        EMPTYING_ENDERCHEST,
        WAITING,
        MOVING_FORWARD_RETRY
    }
    private ProcessState currentState = ProcessState.IDLE;
    private ProcessState lastDebugState = ProcessState.IDLE;
    private int stateTimer = 0;
    private int retryCount = 0;
    private long lastActionTime = 0;
    private boolean isSelecting = false;
    private static BlockPos inputAreaPos1 = null;
    private static BlockPos inputAreaPos2 = null;
    private static BlockPos outputAreaPos1 = null;
    private static BlockPos outputAreaPos2 = null;
    private static BlockPos selectionPos1 = null;
    private static SelectionMode selectionMode = SelectionMode.NONE;
    private static final Set<ContainerInfo> inputContainers = ConcurrentHashMap.newKeySet();
    private static final Set<ContainerInfo> outputContainers = ConcurrentHashMap.newKeySet();
    private ContainerInfo currentContainer = null;
    private BlockPos enderChestPos = null;
    private Direction approachDirection = null;
    private BlockPos lastBaritoneGoal = null;
    private int containerOpenFailures = 0;
    private int pathfindingFailures = 0;
    private Vec3d lastPlayerPos = null;
    private int stuckCounter = 0;
    private int stuckRecoveryAttempts = 0;
    private int jumpTimer = 0;
    private long lastPearlMessageTime = 0;
    private String lastRandomString = "";
    private boolean waitingForPearl = false;
    private int pearlRetryCount = 0;
    private Vec3d initialPlayerPos = null;
    private boolean hasThrownPearl = false;
    private long pearlThrowTime = 0;
    private boolean hasPlacedShulker = false;
    private boolean isGoingToInput = false;
    private ItemStack offhandBackup = ItemStack.EMPTY;
    private int pearlFailRetries = 0;
    private int previousSlot = -1;
    private int rotationStabilizationTimer = 0;
    private boolean rotationSet = false;
    private BlockPos safeRetreatPos = null;
    private boolean waitingForRespawn = false;
    private long lastKillTime = 0;
    private int killRetryCount = 0;
    private int itemsTransferred = 0;
    private int containersProcessed = 0;
    private boolean inventoryFull = false;
    private boolean enderChestFull = false;
    private boolean enderChestHasItems = false;
    private boolean enderChestEmptied = false;
    private static class ContainerInfo {
        public final BlockPos pos;
        public final ContainerType type;
        public boolean isEmpty = false;
        public boolean isFull = false;
        public int slotsFilled = 0;
        public int totalSlots = 27;
        public ContainerInfo(BlockPos pos, ContainerType type) {
            this.pos = pos;
            this.type = type;
            if (type == ContainerType.BARREL) {
                this.totalSlots = 27;
            } else if (type == ContainerType.DOUBLE_CHEST || type == ContainerType.DOUBLE_TRAPPED_CHEST) {
                this.totalSlots = 54;
            }
        }
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ContainerInfo that = (ContainerInfo) o;
            return pos.equals(that.pos);
        }
        @Override
        public int hashCode() {
            return pos.hashCode();
        }
    }
    private enum ContainerType {
        CHEST, DOUBLE_CHEST, TRAPPED_CHEST, DOUBLE_TRAPPED_CHEST, BARREL, ENDER_CHEST
    }
    private static StashMover INSTANCE;
    public StashMover() {
        super(Bep.CATEGORY, "stash-mover", "Automatically moves items between stash areas using pearl loading");
        INSTANCE = this;
    }
    @Override
    public void onActivate() {
        stateTimer = 0;
        retryCount = 0;
        itemsTransferred = 0;
        containersProcessed = 0;
        inventoryFull = false;
        enderChestFull = false;
        currentContainer = null;
        waitingForPearl = false;
        pearlRetryCount = 0;
        containerOpenFailures = 0;
        String prefix = meteordevelopment.meteorclient.systems.config.Config.get().prefix.get();
        info("StashMover activated");
        if (inputAreaPos1 != null && inputAreaPos2 != null) {
            info("§aInput area set with §f" + inputContainers.size() + "§a containers");
        } else {
            info("§7Use §f" + prefix + "setinput §7to select input area");
        }
        if (outputAreaPos1 != null && outputAreaPos2 != null) {
            info("§bOutput area set with §f" + outputContainers.size() + "§b containers");
        } else {
            info("§7Use §f" + prefix + "setoutput §7to select output area");
        }
        if (hasValidAreas()) {
            info("§eStarting automated transfer process...");
            currentState = ProcessState.CHECKING_LOCATION;
            stateTimer = 0;
        } else {
            info("§cConfigure both input and output areas to start");
            currentState = ProcessState.IDLE;
            stateTimer = 20;
        }
    }
    @Override
    public void onDeactivate() {
        if (mc.currentScreen instanceof GenericContainerScreen) {
            mc.player.closeHandledScreen();
        }
        if (BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().isPathing()) {
            BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().cancelEverything();
        }
        mc.options.sneakKey.setPressed(false);
        mc.options.forwardKey.setPressed(false);
        mc.options.backKey.setPressed(false);
        mc.options.leftKey.setPressed(false);
        mc.options.rightKey.setPressed(false);
        mc.options.sprintKey.setPressed(false);
        if (mc.player != null && mc.player.input != null) {
            ((InputAccessor) mc.player.input).setMovementForward(0.0f);
            ((InputAccessor) mc.player.input).setMovementSideways(0.0f);
        }
        currentState = ProcessState.IDLE;
        info("StashMover deactivated");
    }
    public void handleBlockSelectionPublic(BlockPos pos) {
        handleBlockSelection(pos);
    }
    private void handleBlockSelection(BlockPos pos) {
        switch (selectionMode) {
            case INPUT_FIRST -> {
                selectionPos1 = pos;
                selectionMode = SelectionMode.INPUT_SECOND;
                info("§aInput area first corner set");
                info("§eLeft-click another block to set the second corner");
            }
            case INPUT_SECOND -> {
                if (pos.equals(selectionPos1)) {
                    warning("Second corner must be different from the first!");
                    return;
                }
                setInputArea(selectionPos1, pos);
                selectionMode = SelectionMode.NONE;
                selectionPos1 = null;
                info("§aInput area selection complete!");
            }
            case OUTPUT_FIRST -> {
                selectionPos1 = pos;
                selectionMode = SelectionMode.OUTPUT_SECOND;
                info("§bOutput area first corner set");
                info("§eLeft-click another block to set the second corner");
            }
            case OUTPUT_SECOND -> {
                if (pos.equals(selectionPos1)) {
                    warning("Second corner must be different from the first!");
                    return;
                }
                setOutputArea(selectionPos1, pos);
                selectionMode = SelectionMode.NONE;
                selectionPos1 = null;
                info("§bOutput area selection complete!");
            }
        }
    }
    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;
        if (!isActive()) return;
        if (stateTimer > 0) {
            stateTimer--;
            if (currentState != ProcessState.IDLE) {
                return;
            }
        }
        if (pauseOnLag.get() && isServerLagging()) {
            return;
        }
        handleCurrentState();
    }
    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (!isActive() && selectionMode == SelectionMode.NONE) return;
        if (!renderSelection.get() && selectionMode == SelectionMode.NONE) return;
        if (inputAreaPos1 != null && inputAreaPos2 != null) {
            Box inputBox = new Box(
                inputAreaPos1.getX(), inputAreaPos1.getY(), inputAreaPos1.getZ(),
                inputAreaPos2.getX() + 1, inputAreaPos2.getY() + 1, inputAreaPos2.getZ() + 1
            );
            event.renderer.box(inputBox, inputAreaColor.get(), inputAreaColor.get(), ShapeMode.Lines, outlineWidth.get());
        }
        if (outputAreaPos1 != null && outputAreaPos2 != null) {
            Box outputBox = new Box(
                outputAreaPos1.getX(), outputAreaPos1.getY(), outputAreaPos1.getZ(),
                outputAreaPos2.getX() + 1, outputAreaPos2.getY() + 1, outputAreaPos2.getZ() + 1
            );
            event.renderer.box(outputBox, outputAreaColor.get(), outputAreaColor.get(), ShapeMode.Lines, outlineWidth.get());
        }
        if (isActive()) {
            for (ContainerInfo container : inputContainers) {
                if (!container.isEmpty) {
                    SettingColor color = container == currentContainer ? activeContainerColor.get() : inputContainerColor.get();
                    renderContainer(event, container, color);
                }
            }
            for (ContainerInfo container : outputContainers) {
                if (!container.isFull) {
                    SettingColor color = container == currentContainer ? activeContainerColor.get() : outputContainerColor.get();
                    renderContainer(event, container, color);
                }
            }
        }
        if (selectionMode != SelectionMode.NONE && selectionPos1 != null) {
            BlockPos currentPos = mc.crosshairTarget != null && mc.crosshairTarget.getType() == HitResult.Type.BLOCK ?
                ((BlockHitResult)mc.crosshairTarget).getBlockPos() : mc.player.getBlockPos();
            Box selectionBox = new Box(
                Math.min(selectionPos1.getX(), currentPos.getX()),
                Math.min(selectionPos1.getY(), currentPos.getY()),
                Math.min(selectionPos1.getZ(), currentPos.getZ()),
                Math.max(selectionPos1.getX(), currentPos.getX()) + 1,
                Math.max(selectionPos1.getY(), currentPos.getY()) + 1,
                Math.max(selectionPos1.getZ(), currentPos.getZ()) + 1
            );
            SettingColor color = (selectionMode == SelectionMode.INPUT_FIRST || selectionMode == SelectionMode.INPUT_SECOND) ?
                new SettingColor(0, 255, 0, 100) : new SettingColor(0, 100, 255, 100);
            event.renderer.box(selectionBox, color, color, ShapeMode.Both, 0);
            Box corner1 = new Box(
                selectionPos1.getX(), selectionPos1.getY(), selectionPos1.getZ(),
                selectionPos1.getX() + 1, selectionPos1.getY() + 1, selectionPos1.getZ() + 1
            );
            event.renderer.box(corner1, new SettingColor(255, 255, 0, 200),
                new SettingColor(255, 255, 0, 100), ShapeMode.Both, 0);
        }
    }
    private void renderContainer(Render3DEvent event, ContainerInfo container, SettingColor color) {
        Box box = new Box(
            container.pos.getX(), container.pos.getY(), container.pos.getZ(),
            container.pos.getX() + 1, container.pos.getY() + 1, container.pos.getZ() + 1
        );
        if (container.type == ContainerType.DOUBLE_CHEST ||
            container.type == ContainerType.DOUBLE_TRAPPED_CHEST) {
            BlockState state = mc.world.getBlockState(container.pos);
            if (state.contains(Properties.CHEST_TYPE)) {
                ChestType chestType = state.get(Properties.CHEST_TYPE);
                Direction facing = state.get(Properties.HORIZONTAL_FACING);
                if (chestType == ChestType.LEFT) {
                    BlockPos otherPos = container.pos.offset(facing.rotateYClockwise());
                    box = box.union(new Box(
                        otherPos.getX(), otherPos.getY(), otherPos.getZ(),
                        otherPos.getX() + 1, otherPos.getY() + 1, otherPos.getZ() + 1
                    ));
                } else if (chestType == ChestType.RIGHT) {
                    BlockPos otherPos = container.pos.offset(facing.rotateYCounterclockwise());
                    box = box.union(new Box(
                        otherPos.getX(), otherPos.getY(), otherPos.getZ(),
                        otherPos.getX() + 1, otherPos.getY() + 1, otherPos.getZ() + 1
                    ));
                }
            }
        }
        event.renderer.box(box, color, color, ShapeMode.Both, 1);
    }
    private void handleCurrentState() {
        if (debugMode.get() && currentState != lastDebugState) {
            info("State: " + currentState + " (timer: " + stateTimer + ")");
            lastDebugState = currentState;
        }
        switch (currentState) {
            case IDLE -> handleIdleState();
            case CHECKING_LOCATION -> checkLocation();
            case INPUT_PROCESS -> handleInputProcess();
            case LOADING_PEARL -> handlePearlLoading();
            case RESET_PEARL_PICKUP -> handleResetPearlPickup();
            case RESET_PEARL_PLACE_SHULKER -> handleResetPearlPlaceShulker();
            case RESET_PEARL_APPROACH -> handleResetPearlApproach();
            case RESET_PEARL_PREPARE -> handleResetPearlPrepare();
            case RESET_PEARL_THROW -> handleResetPearlThrow();
            case RESET_PEARL_WAIT -> handleResetPearlWait();
            case OUTPUT_PROCESS -> handleOutputProcess();
            case GOING_BACK -> handleGoingBack();
            case OPENING_CONTAINER -> handleOpeningContainer();
            case TRANSFERRING_ITEMS -> handleTransferringItems();
            case CLOSING_CONTAINER -> handleClosingContainer();
            case BREAKING_CONTAINER -> handleBreakingContainer();
            case MOVING_TO_CONTAINER -> handleMovingToContainer();
            case MANUAL_MOVING -> handleManualMoving();
            case OPENING_ENDERCHEST -> handleOpeningEnderChest();
            case FILLING_ENDERCHEST -> handleFillingEnderChest();
            case EMPTYING_ENDERCHEST -> handleEmptyingEnderChest();
            case WAITING -> handleWaiting();
            case MOVING_FORWARD_RETRY -> handleMovingForwardRetry();
        }
    }
    public void startInputSelection() {
        selectionMode = SelectionMode.INPUT_FIRST;
        selectionPos1 = null;
        info("§aInput area selection started - §fLeft-click §afirst corner block");
    }
    public void startOutputSelection() {
        selectionMode = SelectionMode.OUTPUT_FIRST;
        selectionPos1 = null;
        info("§bOutput area selection started - §fLeft-click §bfirst corner block");
    }
    public void cancelSelection() {
        selectionMode = SelectionMode.NONE;
        selectionPos1 = null;
        info("§cSelection cancelled");
    }
    public void setInputArea(BlockPos pos1, BlockPos pos2) {
        inputAreaPos1 = new BlockPos(
            Math.min(pos1.getX(), pos2.getX()),
            Math.min(pos1.getY(), pos2.getY()),
            Math.min(pos1.getZ(), pos2.getZ())
        );
        inputAreaPos2 = new BlockPos(
            Math.max(pos1.getX(), pos2.getX()),
            Math.max(pos1.getY(), pos2.getY()),
            Math.max(pos1.getZ(), pos2.getZ())
        );
        detectContainersInArea(inputAreaPos1, inputAreaPos2, true);
        info("§aInput area set with §f" + inputContainers.size() + " §acontainers");
    }
    public void setOutputArea(BlockPos pos1, BlockPos pos2) {
        outputAreaPos1 = new BlockPos(
            Math.min(pos1.getX(), pos2.getX()),
            Math.min(pos1.getY(), pos2.getY()),
            Math.min(pos1.getZ(), pos2.getZ())
        );
        outputAreaPos2 = new BlockPos(
            Math.max(pos1.getX(), pos2.getX()),
            Math.max(pos1.getY(), pos2.getY()),
            Math.max(pos1.getZ(), pos2.getZ())
        );
        detectContainersInArea(outputAreaPos1, outputAreaPos2, false);
        info("§bOutput area set with §f" + outputContainers.size() + " §bcontainers");
    }
    private void detectContainersInArea(BlockPos pos1, BlockPos pos2, boolean isInput) {
        Set<ContainerInfo> containers = isInput ? inputContainers : outputContainers;
        containers.clear();
        Set<BlockPos> processedPositions = new HashSet<>();
        for (int x = pos1.getX(); x <= pos2.getX(); x++) {
            for (int y = pos1.getY(); y <= pos2.getY(); y++) {
                for (int z = pos1.getZ(); z <= pos2.getZ(); z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (processedPositions.contains(pos)) continue;
                    BlockState state = mc.world.getBlockState(pos);
                    Block block = state.getBlock();
                    ContainerInfo container = null;
                    if (block instanceof ChestBlock && !(block instanceof TrappedChestBlock)) {
                        if (state.contains(Properties.CHEST_TYPE)) {
                            ChestType chestType = state.get(Properties.CHEST_TYPE);
                            if (chestType != ChestType.SINGLE) {
                                Direction facing = state.get(Properties.HORIZONTAL_FACING);
                                BlockPos otherPos = null;
                                if (chestType == ChestType.LEFT) {
                                    otherPos = pos.offset(facing.rotateYClockwise());
                                } else {
                                    otherPos = pos.offset(facing.rotateYCounterclockwise());
                                }
                                processedPositions.add(otherPos);
                                container = new ContainerInfo(pos, ContainerType.DOUBLE_CHEST);
                            } else {
                                container = new ContainerInfo(pos, ContainerType.CHEST);
                            }
                        } else {
                            container = new ContainerInfo(pos, ContainerType.CHEST);
                        }
                    } else if (block instanceof TrappedChestBlock) {
                        if (state.contains(Properties.CHEST_TYPE)) {
                            ChestType chestType = state.get(Properties.CHEST_TYPE);
                            if (chestType != ChestType.SINGLE) {
                                Direction facing = state.get(Properties.HORIZONTAL_FACING);
                                BlockPos otherPos = null;
                                if (chestType == ChestType.LEFT) {
                                    otherPos = pos.offset(facing.rotateYClockwise());
                                } else {
                                    otherPos = pos.offset(facing.rotateYCounterclockwise());
                                }
                                processedPositions.add(otherPos);
                                container = new ContainerInfo(pos, ContainerType.DOUBLE_TRAPPED_CHEST);
                            } else {
                                container = new ContainerInfo(pos, ContainerType.TRAPPED_CHEST);
                            }
                        } else {
                            container = new ContainerInfo(pos, ContainerType.TRAPPED_CHEST);
                        }
                    } else if (block instanceof BarrelBlock) {
                        container = new ContainerInfo(pos, ContainerType.BARREL);
                    }
                    if (container != null) {
                        containers.add(container);
                        processedPositions.add(pos);
                    }
                }
            }
        }
    }
    private void startProcess() {
        if (!hasValidAreas()) {
            error("Please set input and output areas first!");
            return;
        }
        currentState = ProcessState.CHECKING_LOCATION;
        info("Starting StashMover process...");
    }
    public void startProcessManually() {
        startProcess();
    }
    private void stopCurrentProcess() {
        if (mc.currentScreen instanceof GenericContainerScreen) {
            mc.player.closeHandledScreen();
        }
        if (BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().isPathing()) {
            BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().cancelEverything();
        }
        currentState = ProcessState.IDLE;
        currentContainer = null;
        waitingForPearl = false;
        pearlRetryCount = 0;
        stateTimer = 0;
        retryCount = 0;
        waitingForRespawn = false;
        killRetryCount = 0;
        initialPlayerPos = null;
        hasThrownPearl = false;
        hasPlacedShulker = false;
        enderChestHasItems = false;
        enderChestFull = false;
        pearlFailRetries = 0;
        offhandBackup = ItemStack.EMPTY;
        info("Process stopped");
    }
    public void stopProcessManually() {
        stopCurrentProcess();
    }
    private void checkLocation() {
        if (isNearInputArea()) {
            info("Near input area, starting input process");
            enderChestEmptied = false;
            detectContainersInArea(inputAreaPos1, inputAreaPos2, true);
            info("Found " + inputContainers.size() + " input containers");
            currentState = ProcessState.INPUT_PROCESS;
            stateTimer = 0;
        } else if (isNearOutputArea()) {
            info("Near output area, resetting pearl first");
            detectContainersInArea(outputAreaPos1, outputAreaPos2, false);
            info("Found " + outputContainers.size() + " output containers");
            currentState = ProcessState.RESET_PEARL_PICKUP;
            hasThrownPearl = false;
            hasPlacedShulker = false;
            isGoingToInput = false;
        } else {
            warning("Not near any configured area! Will retry in 5 seconds...");
            if (debugMode.get()) {
                warning("Input area not set");
                warning("Output area not set");
            }
            currentState = ProcessState.IDLE;
            stateTimer = 100;
        }
    }
    private void handleInputProcess() {
        if (currentState == ProcessState.OPENING_ENDERCHEST) {
            return;
        }
        if (isInventoryFull()) {
            if (fillEnderChest.get() && !isEnderChestFull()) {
                info("Inventory full, checking enderchest...");
                findOrPlaceEnderChest();
                return;
            } else {
                info("Inventory full and enderchest not available/full, starting pearl loading");
                currentState = ProcessState.LOADING_PEARL;
                return;
            }
        }
        findNextInputContainer();
    }
    private void findNextInputContainer() {
        currentContainer = inputContainers.stream()
            .filter(c -> !c.isEmpty)
            .min(Comparator.comparingDouble(c -> mc.player.getEntityPos().distanceTo(Vec3d.ofCenter(c.pos))))
            .orElse(null);
        if (currentContainer == null) {
            if (isInventoryFull() || (fillEnderChest.get() && hasItemsInEnderChest())) {
                info("All input containers processed, starting pearl loading!");
                currentState = ProcessState.LOADING_PEARL;
            } else {
                info("No containers with items found, rescanning...");
                detectContainersInArea(inputAreaPos1, inputAreaPos2, true);
                currentContainer = inputContainers.stream()
                    .filter(c -> !c.isEmpty)
                    .min(Comparator.comparingDouble(c -> mc.player.getEntityPos().distanceTo(Vec3d.ofCenter(c.pos))))
                    .orElse(null);
                if (currentContainer != null) {
                    info("Found container after rescan");
                    moveToContainer(currentContainer);
                } else {
                    info("No containers found, waiting 5 seconds...");
                    currentState = ProcessState.IDLE;
                    stateTimer = 100;
                }
            }
        } else {
            info("Moving to container");
            moveToContainer(currentContainer);
        }
    }
    private void moveToContainer(ContainerInfo container) {
        if (currentContainer != container) {
            containerOpenFailures = 0;
            stuckCounter = 0;
            stuckRecoveryAttempts = 0;
            lastPlayerPos = null;
        }
        Vec3d eyePos = mc.player.getEyePos();
        double distance = eyePos.distanceTo(Vec3d.ofCenter(container.pos));
        if (distance > 3.5) {
            BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().cancelEverything();
            BlockPos validPosition = findValidStandingPositionNear(container.pos);
            if (validPosition != null) {
                GoalBlock goal = new GoalBlock(validPosition);
                BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess().setGoalAndPath(goal);
                currentState = ProcessState.MOVING_TO_CONTAINER;
                if (distance > 20) {
                    stateTimer = 200;
                } else if (distance > 10) {
                    stateTimer = 120;
                } else {
                    stateTimer = 80;
                }
                info("Moving to container (distance: " + String.format("%.1f", distance) + "m)");
            } else {
                int nearDistance = Math.max(2, containerReach.get().intValue());
                GoalNear goal = new GoalNear(container.pos, nearDistance);
                BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess().setGoalAndPath(goal);
                currentState = ProcessState.MOVING_TO_CONTAINER;
                stateTimer = 120;
                warning("No ideal position found, using GoalNear for container");
            }
        } else {
            currentState = ProcessState.OPENING_CONTAINER;
            stateTimer = 5;
        }
    }
    private BlockPos findValidStandingPositionNear(BlockPos containerPos) {
        for (int yOffset = 0; yOffset >= -2; yOffset--) {
            for (Direction dir : Direction.Type.HORIZONTAL) {
                BlockPos checkPos = containerPos.offset(dir).add(0, yOffset, 0);
                if (isValidStandingSpot(checkPos)) {
                    Vec3d standingEyePos = Vec3d.of(checkPos).add(0.5, 1.62, 0.5);
                    double reach = standingEyePos.distanceTo(Vec3d.ofCenter(containerPos));
                    if (reach <= 4.2) {
                        return checkPos;
                    }
                }
            }
            BlockPos[] diagonals = {
                containerPos.add(1, yOffset, 1),
                containerPos.add(1, yOffset, -1),
                containerPos.add(-1, yOffset, 1),
                containerPos.add(-1, yOffset, -1)
            };
            for (BlockPos checkPos : diagonals) {
                if (isValidStandingSpot(checkPos)) {
                    Vec3d standingEyePos = Vec3d.of(checkPos).add(0.5, 1.62, 0.5);
                    double reach = standingEyePos.distanceTo(Vec3d.ofCenter(containerPos));
                    if (reach <= 4.2) {
                        return checkPos;
                    }
                }
            }
        }
        for (Direction dir : Direction.Type.HORIZONTAL) {
            for (int yOffset = 0; yOffset >= -2; yOffset--) {
                BlockPos checkPos = containerPos.offset(dir, 2).add(0, yOffset, 0);
                if (isValidStandingSpot(checkPos)) {
                    Vec3d standingEyePos = Vec3d.of(checkPos).add(0.5, 1.62, 0.5);
                    double reach = standingEyePos.distanceTo(Vec3d.ofCenter(containerPos));
                    if (reach <= 4.2) {
                        return checkPos;
                    }
                }
            }
        }
        return null;
    }
    private boolean isValidStandingSpot(BlockPos pos) {
        BlockState below = mc.world.getBlockState(pos.down());
        BlockState at = mc.world.getBlockState(pos);
        BlockState above = mc.world.getBlockState(pos.up());
        return below.isSolidBlock(mc.world, pos.down()) &&
            !below.isAir() &&
            (at.isAir() || !at.isSolidBlock(mc.world, pos)) &&
            (above.isAir() || !above.isSolidBlock(mc.world, pos.up()));
    }
    private void handleMovingToContainer() {
        if (currentContainer == null) {
            currentState = ProcessState.INPUT_PROCESS;
            return;
        }
        Vec3d eyePos = mc.player.getEyePos();
        Vec3d playerPos = mc.player.getEntityPos();
        double distance = eyePos.distanceTo(Vec3d.ofCenter(currentContainer.pos));
        if (lastPlayerPos != null) {
            double movementDelta = playerPos.distanceTo(lastPlayerPos);
            if (movementDelta < 0.1) {
                stuckCounter++;
            } else {
                stuckCounter = Math.max(0, stuckCounter - 2);
            }
            if (stuckCounter >= 40) {
                warning("Stuck detected! Attempting recovery...");
                performStuckRecovery();
                return;
            }
        }
        lastPlayerPos = playerPos;
        if (distance <= 3.5) {
            BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().cancelEverything();
            currentState = ProcessState.OPENING_CONTAINER;
            stateTimer = 5;
            info("Reached container, opening...");
            pathfindingFailures = 0;
            stuckCounter = 0;
            stuckRecoveryAttempts = 0;
            lastPlayerPos = null;
        } else if (!BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().isPathing()) {
            pathfindingFailures++;
            if (pathfindingFailures >= 2) {
                warning("Pathfinding failed, using improved manual movement");
                improvedManualMovement(currentContainer);
                pathfindingFailures = 0;
            } else {
                warning("Pathfinding stopped, trying alternative path");
                alternativePathToContainer(currentContainer);
            }
        } else if (stateTimer > 0) {
            stateTimer--;
            if (stateTimer == 0) {
                warning("Movement timeout, switching to manual");
                BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().cancelEverything();
                improvedManualMovement(currentContainer);
            }
        }
    }
    private void handleOpeningContainer() {
        if (currentContainer == null) {
            if (isNearOutputArea()) {
                currentState = ProcessState.OUTPUT_PROCESS;
            } else {
                currentState = ProcessState.INPUT_PROCESS;
            }
            return;
        }
        Vec3d eyePos = mc.player.getEyePos();
        Vec3d playerPos = mc.player.getEntityPos();
        double distance = eyePos.distanceTo(Vec3d.ofCenter(currentContainer.pos));
        double horizontalDistance = Math.sqrt(
            Math.pow(currentContainer.pos.getX() + 0.5 - playerPos.x, 2) +
                Math.pow(currentContainer.pos.getZ() + 0.5 - playerPos.z, 2)
        );
        double verticalDiff = Math.abs(currentContainer.pos.getY() - eyePos.y);
        boolean isDiagonal = verticalDiff > 1.5 && horizontalDistance < 2.5;
        double effectiveReach = isDiagonal ? 3.2 : 3.5;
        if (distance > effectiveReach) {
            if (retryCount < 2) {
                info("Too far (" + String.format("%.1f", distance) + "m), moving closer...");
                improvedManualMovement(currentContainer);
                retryCount++;
                return;
            } else {
                warning("Can't reach, trying manual approach");
                manualMoveToContainer(currentContainer);
                return;
            }
        }
        retryCount = 0;
        Vec3d containerCenter = Vec3d.ofCenter(currentContainer.pos);
        double targetYaw = Rotations.getYaw(containerCenter);
        double targetPitch = Rotations.getPitch(containerCenter);
        mc.player.setYaw((float)targetYaw);
        mc.player.setPitch((float)targetPitch);
        for (int i = 0; i < 2; i++) {
            mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(
                (float)targetYaw, (float)targetPitch, mc.player.isOnGround(), mc.player.horizontalCollision));
        }
        info("Opening container (attempt " + (containerOpenFailures + 1) + ")");
        performImprovedInteraction(containerCenter);
        currentState = ProcessState.WAITING;
        stateTimer = 15;
    }
    private void performStandardInteraction(Vec3d containerCenter) {
        Vec3d eyePos = mc.player.getEyePos();
        Direction clickFace = getOptimalClickFace(currentContainer.pos, eyePos);
        Vec3d hitVec = calculatePreciseHitVector(currentContainer.pos, clickFace, eyePos);
        double yaw = Rotations.getYaw(hitVec);
        double pitch = Rotations.getPitch(hitVec);
        mc.player.setYaw((float)yaw);
        mc.player.setPitch((float)pitch);
        mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(
            (float)yaw, (float)pitch, mc.player.isOnGround(), mc.player.horizontalCollision));
        BlockHitResult hitResult = new BlockHitResult(
            hitVec,
            clickFace,
            currentContainer.pos,
            false
        );
        ActionResult result = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hitResult);
        if (result != ActionResult.SUCCESS && result != ActionResult.CONSUME) {
            result = mc.interactionManager.interactBlock(mc.player, Hand.OFF_HAND, hitResult);
        }
        if (debugMode.get()) {
            info("Interaction: face=" + clickFace + ", result=" + result);
        }
    }
    private Vec3d calculatePreciseHitVector(BlockPos pos, Direction face, Vec3d eyePos) {
        double x = pos.getX() + 0.5;
        double y = pos.getY() + 0.5;
        double z = pos.getZ() + 0.5;
        double heightDiff = pos.getY() - eyePos.y;
        switch (face) {
            case UP -> {
                y = pos.getY() + 1.0;
                x = pos.getX() + 0.5;
                z = pos.getZ() + 0.5;
            }
            case DOWN -> {
                y = pos.getY();
                x = pos.getX() + 0.5;
                z = pos.getZ() + 0.5;
            }
            case NORTH -> {
                z = pos.getZ();
                if (heightDiff > 1.5) {
                    y = pos.getY() + 0.3;
                } else if (heightDiff > 0.5) {
                    y = pos.getY() + 0.4;
                }
            }
            case SOUTH -> {
                z = pos.getZ() + 1.0;
                if (heightDiff > 1.5) {
                    y = pos.getY() + 0.3;
                } else if (heightDiff > 0.5) {
                    y = pos.getY() + 0.4;
                }
            }
            case WEST -> {
                x = pos.getX();
                if (heightDiff > 1.5) {
                    y = pos.getY() + 0.3;
                } else if (heightDiff > 0.5) {
                    y = pos.getY() + 0.4;
                }
            }
            case EAST -> {
                x = pos.getX() + 1.0;
                if (heightDiff > 1.5) {
                    y = pos.getY() + 0.3;
                } else if (heightDiff > 0.5) {
                    y = pos.getY() + 0.4;
                }
            }
        }
        return new Vec3d(x, y, z);
    }
    private void performInteractionWithMovement(Vec3d containerCenter) {
        Vec3d eyePos = mc.player.getEyePos();
        Vec3d currentPos = mc.player.getEntityPos();
        mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.Full(
            currentPos.x, currentPos.y, currentPos.z,
            mc.player.getYaw(), mc.player.getPitch(),
            mc.player.isOnGround(), mc.player.horizontalCollision));
        Direction bestFace = getOptimalClickFace(currentContainer.pos, eyePos);
        Vec3d[] hitPositions = new Vec3d[3];
        if (bestFace == Direction.UP || bestFace == Direction.DOWN) {
            hitPositions[0] = Vec3d.ofCenter(currentContainer.pos);
            hitPositions[1] = Vec3d.ofCenter(currentContainer.pos).add(0.2, 0, 0.2);
            hitPositions[2] = Vec3d.ofCenter(currentContainer.pos).add(-0.2, 0, -0.2);
        } else {
            hitPositions[0] = calculatePreciseHitVector(currentContainer.pos, bestFace, eyePos);
            hitPositions[1] = hitPositions[0].add(0, 0.1, 0);
            hitPositions[2] = hitPositions[0].add(0, -0.1, 0);
        }
        for (int i = 0; i < hitPositions.length; i++) {
            Vec3d hitPos = hitPositions[i];
            double yaw = Rotations.getYaw(hitPos);
            double pitch = Rotations.getPitch(hitPos);
            mc.player.setYaw((float)yaw);
            mc.player.setPitch((float)pitch);
            BlockHitResult hitResult = new BlockHitResult(
                hitPos,
                bestFace,
                currentContainer.pos,
                false
            );
            ActionResult result = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hitResult);
            if (result != ActionResult.SUCCESS && result != ActionResult.CONSUME) {
                result = mc.interactionManager.interactBlock(mc.player, Hand.OFF_HAND, hitResult);
            }
            if (result == ActionResult.SUCCESS || result == ActionResult.CONSUME) {
                if (debugMode.get()) {
                    info("Container opened with position " + i + ", face: " + bestFace);
                }
                return;
            }
        }
        if (debugMode.get()) {
            info("All interaction attempts failed, face: " + bestFace);
        }
    }
    private void performAggressiveInteraction(Vec3d containerCenter) {
        Vec3d eyePos = mc.player.getEyePos();
        Vec3d pos = mc.player.getEntityPos();
        mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.Full(
            pos.x, pos.y, pos.z,
            mc.player.getYaw(), mc.player.getPitch(),
            mc.player.isOnGround(), mc.player.horizontalCollision
        ));
        double heightDiff = currentContainer.pos.getY() - eyePos.y;
        Direction[] facesToTry;
        if (heightDiff > 2.0) {
            facesToTry = new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};
        } else if (heightDiff > 1.0) {
            facesToTry = new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST, Direction.UP};
        } else {
            facesToTry = new Direction[]{Direction.UP, Direction.NORTH, Direction.SOUTH};
        }
        for (Direction face : facesToTry) {
            Vec3d hitVec = calculatePreciseHitVector(currentContainer.pos, face, eyePos);
            double yaw = Rotations.getYaw(hitVec);
            double pitch = Rotations.getPitch(hitVec);
            mc.player.setYaw((float)yaw);
            mc.player.setPitch((float)pitch);
            mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(
                (float)yaw, (float)pitch, mc.player.isOnGround(), mc.player.horizontalCollision));
            BlockHitResult hitResult = new BlockHitResult(
                hitVec,
                face,
                currentContainer.pos,
                false
            );
            ActionResult result = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hitResult);
            if (result != ActionResult.SUCCESS && result != ActionResult.CONSUME) {
                result = mc.interactionManager.interactBlock(mc.player, Hand.OFF_HAND, hitResult);
            }
            if (result == ActionResult.SUCCESS || result == ActionResult.CONSUME) {
                if (debugMode.get()) {
                    info("Opened with face: " + face);
                }
                return;
            }
        }
        if (debugMode.get()) {
            warning("Failed to open chest at Y=" + currentContainer.pos.getY() + ", height diff=" + String.format("%.1f", heightDiff));
        }
    }
    private void handleOpeningContainer_OLD() {
        if (currentContainer == null) {
            if (isNearOutputArea()) {
                currentState = ProcessState.OUTPUT_PROCESS;
            } else {
                currentState = ProcessState.INPUT_PROCESS;
            }
            return;
        }
        double distance = mc.player.getEntityPos().distanceTo(Vec3d.ofCenter(currentContainer.pos));
        if (distance > containerReach.get()) {
            if (retryCount < 3) {
                info("Too far from container (" + String.format("%.1f", distance) + "m), moving closer...");
                moveToContainer(currentContainer);
                retryCount++;
                return;
            } else {
                warning("Baritone pathfinding failed, attempting manual approach");
                manualMoveToContainer(currentContainer);
                return;
            }
        }
        retryCount = 0;
        if (containerOpenFailures >= 2) {
            if (containerOpenFailures % 2 == 0) {
                mc.options.leftKey.setPressed(true);
            } else {
                mc.options.leftKey.setPressed(false);
                mc.options.rightKey.setPressed(true);
            }
            if (containerOpenFailures % 3 == 0) {
                mc.options.leftKey.setPressed(false);
                mc.options.rightKey.setPressed(false);
            }
        }
        Vec3d containerCenter;
        double offsetX = 0, offsetY = 0, offsetZ = 0;
        switch (containerOpenFailures % 9) {
            case 0 -> containerCenter = Vec3d.ofCenter(currentContainer.pos);
            case 1 -> { offsetY = 0.25; containerCenter = Vec3d.ofCenter(currentContainer.pos).add(0, offsetY, 0); }
            case 2 -> { offsetY = -0.25; containerCenter = Vec3d.ofCenter(currentContainer.pos).add(0, offsetY, 0); }
            case 3 -> { offsetX = 0.2; containerCenter = Vec3d.ofCenter(currentContainer.pos).add(offsetX, 0, 0); }
            case 4 -> { offsetX = -0.2; containerCenter = Vec3d.ofCenter(currentContainer.pos).add(offsetX, 0, 0); }
            case 5 -> { offsetZ = 0.2; containerCenter = Vec3d.ofCenter(currentContainer.pos).add(0, 0, offsetZ); }
            case 6 -> { offsetZ = -0.2; containerCenter = Vec3d.ofCenter(currentContainer.pos).add(0, 0, offsetZ); }
            case 7 -> { offsetX = 0.15; offsetY = 0.15; containerCenter = Vec3d.ofCenter(currentContainer.pos).add(offsetX, offsetY, 0); }
            case 8 -> { offsetX = -0.15; offsetZ = 0.15; containerCenter = Vec3d.ofCenter(currentContainer.pos).add(offsetX, 0, offsetZ); }
            default -> containerCenter = Vec3d.ofCenter(currentContainer.pos);
        }
        double yaw = Rotations.getYaw(containerCenter);
        double pitch = Rotations.getPitch(containerCenter);
        mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.LookAndOnGround((float)yaw, (float)pitch, mc.player.isOnGround(), mc.player.horizontalCollision));
        mc.player.setYaw((float)yaw);
        mc.player.setPitch((float)pitch);
        if (containerOpenFailures == 0 && stateTimer <= 0) {
            stateTimer = 5;
        }
        if (stateTimer > 0) {
            stateTimer--;
            mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.LookAndOnGround((float)yaw, (float)pitch, mc.player.isOnGround(), mc.player.horizontalCollision));
            return;
        }
        if (containerOpenFailures >= 2) {
            mc.options.leftKey.setPressed(false);
            mc.options.rightKey.setPressed(false);
            mc.options.forwardKey.setPressed(false);
            mc.options.backKey.setPressed(false);
            switch (containerOpenFailures % 4) {
                case 0 -> {
                    mc.options.leftKey.setPressed(true);
                    mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(
                        mc.player.getX() - 0.01, mc.player.getY(), mc.player.getZ(), mc.player.isOnGround(), mc.player.horizontalCollision));
                    mc.options.leftKey.setPressed(false);
                }
                case 1 -> {
                    mc.options.rightKey.setPressed(true);
                    mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(
                        mc.player.getX() + 0.01, mc.player.getY(), mc.player.getZ(), mc.player.isOnGround(), mc.player.horizontalCollision));
                    mc.options.rightKey.setPressed(false);
                }
                case 2 -> {
                    mc.options.forwardKey.setPressed(true);
                    mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(
                        mc.player.getX(), mc.player.getY(), mc.player.getZ() - 0.01, mc.player.isOnGround(), mc.player.horizontalCollision));
                    mc.options.forwardKey.setPressed(false);
                }
                case 3 -> {
                    mc.options.backKey.setPressed(true);
                    mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(
                        mc.player.getX(), mc.player.getY(), mc.player.getZ() + 0.01, mc.player.isOnGround(), mc.player.horizontalCollision));
                    mc.options.backKey.setPressed(false);
                }
            }
        }
        info("Attempting to open container (attempt " + (containerOpenFailures + 1) + "/8)");
        Direction clickFace = getOptimalClickFace(currentContainer.pos, mc.player.getEyePos());
        BlockHitResult hitResult = new BlockHitResult(
            containerCenter,
            clickFace,
            currentContainer.pos,
            false
        );
        mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.LookAndOnGround((float)yaw, (float)pitch, mc.player.isOnGround(), mc.player.horizontalCollision));
        mc.options.leftKey.setPressed(false);
        mc.options.rightKey.setPressed(false);
        mc.options.forwardKey.setPressed(false);
        mc.options.backKey.setPressed(false);
        mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.LookAndOnGround((float)yaw, (float)pitch, mc.player.isOnGround(), mc.player.horizontalCollision));
        ActionResult result = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hitResult);
        if (result != ActionResult.SUCCESS && result != ActionResult.CONSUME) {
            result = mc.interactionManager.interactBlock(mc.player, Hand.OFF_HAND, hitResult);
        }
        if (result == ActionResult.SUCCESS || result == ActionResult.CONSUME) {
            info("Interaction sent successfully");
        } else {
            info("Interaction result: " + result);
        }
        currentState = ProcessState.WAITING;
        stateTimer = 5;
    }
    private void handleWaiting() {
        if (mc.currentScreen instanceof GenericContainerScreen) {
            containerOpenFailures = 0;
            pathfindingFailures = 0;
            retryCount = 0;
            mc.options.leftKey.setPressed(false);
            mc.options.rightKey.setPressed(false);
            mc.options.forwardKey.setPressed(false);
            mc.options.backKey.setPressed(false);
            mc.options.sneakKey.setPressed(false);
            currentState = ProcessState.TRANSFERRING_ITEMS;
            stateTimer = transferDelay.get();
            info("Container opened successfully!");
            return;
        }
        if (stateTimer > 0) {
            stateTimer--;
            if (stateTimer % 2 == 0 && currentContainer != null) {
                Vec3d containerCenter = Vec3d.ofCenter(currentContainer.pos);
                double yaw = Rotations.getYaw(containerCenter);
                double pitch = Rotations.getPitch(containerCenter);
                mc.player.setYaw((float)yaw);
                mc.player.setPitch((float)pitch);
                mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(
                    (float)yaw, (float)pitch, mc.player.isOnGround(), mc.player.horizontalCollision
                ));
                Direction[] faces = {Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};
                Direction face = faces[stateTimer % faces.length];
                Vec3d hitVec = calculatePreciseHitVector(currentContainer.pos, face, mc.player.getEyePos());
                BlockHitResult hitResult = new BlockHitResult(
                    hitVec, face, currentContainer.pos, false
                );
                ActionResult result = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hitResult);
                if (result != ActionResult.SUCCESS && result != ActionResult.CONSUME) {
                    mc.interactionManager.interactBlock(mc.player, Hand.OFF_HAND, hitResult);
                }
            }
            return;
        }
        containerOpenFailures++;
        info("Container didn't open, attempt " + containerOpenFailures + "/10");
        if (containerOpenFailures >= 10) {
            warning("Cannot open container at Y=" + currentContainer.pos.getY() + " after 10 attempts");
            stopAllMovement();
            if (isNearInputArea()) {
                currentContainer.isEmpty = true;
                info("Marked input container as empty/inaccessible");
            } else if (isNearOutputArea()) {
                currentContainer.isFull = true;
                info("Marked output container as full/inaccessible");
            }
            currentContainer = null;
            containerOpenFailures = 0;
            retryCount = 0;
            currentState = isNearOutputArea() ? ProcessState.OUTPUT_PROCESS : ProcessState.INPUT_PROCESS;
        } else {
            if (currentContainer != null) {
                Vec3d eyePos = mc.player.getEyePos();
                Vec3d containerCenter = Vec3d.ofCenter(currentContainer.pos);
                double distance = eyePos.distanceTo(containerCenter);
                if (containerOpenFailures >= 5) {
                    performSmartRepositioning(currentContainer.pos, distance);
                    currentState = ProcessState.MOVING_FORWARD_RETRY;
                    stateTimer = 20;
                } else if (distance > 3.0 && containerOpenFailures < 3) {
                    double yaw = Rotations.getYaw(containerCenter);
                    double pitch = Rotations.getPitch(containerCenter);
                    mc.player.setYaw((float)yaw);
                    mc.player.setPitch((float)pitch);
                    if (distance > 4.0) {
                        stateTimer = 20;
                    } else {
                        stateTimer = 20;
                    }
                    info("Moving closer to container (distance: " + String.format("%.1f", distance) + ")");
                    currentState = ProcessState.MOVING_FORWARD_RETRY;
                } else if (containerOpenFailures >= 3 && distance > 2.5) {
                    info("Trying side approach after " + containerOpenFailures + " failures");
                    Vec3d playerPos = mc.player.getEntityPos();
                    Vec3d toContainer = Vec3d.of(currentContainer.pos).add(0.5, 0, 0.5).subtract(playerPos);
                    mc.options.leftKey.setPressed(true);
                    mc.options.forwardKey.setPressed(true);
                    stateTimer = 10;
                    currentState = ProcessState.MOVING_FORWARD_RETRY;
                } else if (distance < 2.0) {
                    info("Too close to container, backing up...");
                    mc.options.backKey.setPressed(true);
                    stateTimer = 5;
                    currentState = ProcessState.MOVING_FORWARD_RETRY;
                } else {
                    currentState = ProcessState.OPENING_CONTAINER;
                    stateTimer = 2;
                }
            } else {
                currentState = isNearOutputArea() ? ProcessState.OUTPUT_PROCESS : ProcessState.INPUT_PROCESS;
            }
        }
    }
    private void handleMovingForwardRetry() {
        if (mc.currentScreen instanceof GenericContainerScreen) {
            stopAllMovement();
            containerOpenFailures = 0;
            pathfindingFailures = 0;
            retryCount = 0;
            currentState = ProcessState.TRANSFERRING_ITEMS;
            stateTimer = transferDelay.get();
            info("Container opened successfully!");
            return;
        }
        if (currentContainer == null) {
            stopAllMovement();
            currentState = isNearOutputArea() ? ProcessState.OUTPUT_PROCESS : ProcessState.INPUT_PROCESS;
            return;
        }
        Vec3d containerCenter = Vec3d.ofCenter(currentContainer.pos);
        Vec3d eyePos = mc.player.getEyePos();
        Vec3d playerPos = mc.player.getEntityPos();
        double distance = eyePos.distanceTo(containerCenter);
        Vec3d toContainer = Vec3d.of(currentContainer.pos).add(0.5, 0, 0.5).subtract(playerPos);
        double horizontalDistance = Math.sqrt(toContainer.x * toContainer.x + toContainer.z * toContainer.z);
        double targetYaw = Rotations.getYaw(containerCenter);
        double targetPitch = Rotations.getPitch(containerCenter);
        float currentYaw = mc.player.getYaw();
        float currentPitch = mc.player.getPitch();
        float yawDiff = (float)(targetYaw - currentYaw);
        while (yawDiff > 180) yawDiff -= 360;
        while (yawDiff < -180) yawDiff += 360;
        float pitchDiff = (float)(targetPitch - currentPitch);
        float smoothingFactor = 0.6f;
        float newYaw = currentYaw + yawDiff * smoothingFactor;
        float newPitch = currentPitch + pitchDiff * smoothingFactor;
        mc.player.setYaw(newYaw);
        mc.player.setPitch(newPitch);
        mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(
            newYaw, newPitch, mc.player.isOnGround(), mc.player.horizontalCollision));
        if (containerOpenFailures >= 3 && mc.options.leftKey.isPressed()) {
            if (stateTimer > 3) {
                mc.options.leftKey.setPressed(true);
                mc.options.forwardKey.setPressed(true);
            } else {
                stopAllMovement();
            }
        } else {
            if (stateTimer > 3) {
                if (horizontalDistance > 3.5) {
                    mc.options.forwardKey.setPressed(true);
                    mc.options.sprintKey.setPressed(true);
                } else if (horizontalDistance > 2.0) {
                    mc.options.forwardKey.setPressed(true);
                    mc.options.sprintKey.setPressed(false);
                    mc.options.sneakKey.setPressed(false);
                } else if (horizontalDistance > 1.2) {
                    mc.options.forwardKey.setPressed(true);
                    mc.options.sneakKey.setPressed(true);
                    mc.options.sprintKey.setPressed(false);
                } else {
                    stopAllMovement();
                }
            } else {
                stopAllMovement();
            }
        }
        if (distance <= 4.5 && stateTimer % 4 == 0) {
            attemptContainerInteraction(containerCenter, eyePos, stateTimer);
        }
        stateTimer--;
        if (stateTimer <= 0) {
            stopAllMovement();
            double finalDistance = eyePos.distanceTo(containerCenter);
            if (finalDistance <= 4.0) {
                info("At good distance (" + String.format("%.1f", finalDistance) + "), attempting to open");
                currentState = ProcessState.OPENING_CONTAINER;
                stateTimer = 5;
            } else if (containerOpenFailures >= 5) {
                warning("Failed to reach container after " + containerOpenFailures + " attempts, using Baritone");
                moveToContainer(currentContainer);
            } else {
                info("Distance still " + String.format("%.1f", finalDistance) + ", trying alternative approach");
                attemptAlternativeApproach();
            }
        }
    }
    private void handleIntelligentMovement(double horizontalDistance, Vec3d toContainer) {
        boolean blocked = isPathBlocked(toContainer);
        boolean needsJump = shouldJump();
        boolean canStrafe = canStrafeAround();
        mc.options.forwardKey.setPressed(false);
        mc.options.backKey.setPressed(false);
        mc.options.leftKey.setPressed(false);
        mc.options.rightKey.setPressed(false);
        mc.options.sneakKey.setPressed(false);
        mc.options.jumpKey.setPressed(false);
        if (horizontalDistance < 1.2) {
            return;
        }
        if (blocked) {
            if (needsJump) {
                mc.options.jumpKey.setPressed(true);
                mc.options.forwardKey.setPressed(true);
                if (debugMode.get()) info("Jumping over obstacle");
            } else if (canStrafe) {
                handleStrafeMovement(toContainer);
            } else {
                attemptAlternativeApproach();
            }
        } else {
            if (horizontalDistance > 3.0) {
                mc.options.forwardKey.setPressed(true);
                mc.options.sprintKey.setPressed(true);
            } else if (horizontalDistance > 1.8) {
                mc.options.forwardKey.setPressed(true);
                mc.options.sprintKey.setPressed(false);
            } else {
                mc.options.forwardKey.setPressed(true);
                mc.options.sneakKey.setPressed(true);
            }
        }
    }
    private boolean isPathBlocked(Vec3d toContainer) {
        Vec3d checkPos = mc.player.getEntityPos().add(toContainer.normalize().multiply(1.0));
        BlockPos blockPos = BlockPos.ofFloored(checkPos);
        BlockPos blockPosAbove = blockPos.up();
        BlockState state = mc.world.getBlockState(blockPos);
        BlockState stateAbove = mc.world.getBlockState(blockPosAbove);
        return !state.isAir() && state.isSolidBlock(mc.world, blockPos) ||
            !stateAbove.isAir() && stateAbove.isSolidBlock(mc.world, blockPosAbove);
    }
    private boolean shouldJump() {
        Vec3d feetPos = mc.player.getEntityPos();
        Vec3d forwardPos = feetPos.add(mc.player.getRotationVector().multiply(1.0));
        BlockPos feetBlock = BlockPos.ofFloored(forwardPos);
        BlockPos headBlock = feetBlock.up();
        BlockPos aboveBlock = feetBlock.up(2);
        BlockState feetState = mc.world.getBlockState(feetBlock);
        BlockState headState = mc.world.getBlockState(headBlock);
        BlockState aboveState = mc.world.getBlockState(aboveBlock);
        return !feetState.isAir() && headState.isAir() && aboveState.isAir();
    }
    private boolean canStrafeAround() {
        Vec3d leftCheck = mc.player.getEntityPos().add(mc.player.getRotationVector().rotateY((float)Math.toRadians(90)));
        Vec3d rightCheck = mc.player.getEntityPos().add(mc.player.getRotationVector().rotateY((float)Math.toRadians(-90)));
        BlockPos leftBlock = BlockPos.ofFloored(leftCheck);
        BlockPos rightBlock = BlockPos.ofFloored(rightCheck);
        return mc.world.getBlockState(leftBlock).isAir() || mc.world.getBlockState(rightBlock).isAir();
    }
    private void handleStrafeMovement(Vec3d toContainer) {
        Vec3d left = mc.player.getRotationVector().rotateY((float)Math.toRadians(90));
        Vec3d right = mc.player.getRotationVector().rotateY((float)Math.toRadians(-90));
        Vec3d leftCheck = mc.player.getEntityPos().add(left);
        Vec3d rightCheck = mc.player.getEntityPos().add(right);
        BlockPos leftBlock = BlockPos.ofFloored(leftCheck);
        BlockPos rightBlock = BlockPos.ofFloored(rightCheck);
        boolean leftClear = mc.world.getBlockState(leftBlock).isAir();
        boolean rightClear = mc.world.getBlockState(rightBlock).isAir();
        mc.options.forwardKey.setPressed(true);
        if (leftClear && !rightClear) {
            mc.options.leftKey.setPressed(true);
            if (debugMode.get()) info("Strafing left around obstacle");
        } else if (rightClear && !leftClear) {
            mc.options.rightKey.setPressed(true);
            if (debugMode.get()) info("Strafing right around obstacle");
        } else if (leftClear && rightClear) {
            Vec3d containerPos = Vec3d.of(currentContainer.pos);
            double leftDist = leftCheck.distanceTo(containerPos);
            double rightDist = rightCheck.distanceTo(containerPos);
            if (leftDist < rightDist) {
                mc.options.leftKey.setPressed(true);
            } else {
                mc.options.rightKey.setPressed(true);
            }
        }
    }
    private void attemptAlternativeApproach() {
        if (currentContainer == null) return;
        Vec3d playerPos = mc.player.getEntityPos();
        Vec3d containerPos = Vec3d.of(currentContainer.pos).add(0.5, 0, 0.5);
        Vec3d[] approachPoints = {
            containerPos.add(2, 0, 0),
            containerPos.add(-2, 0, 0),
            containerPos.add(0, 0, 2),
            containerPos.add(0, 0, -2),
            containerPos.add(1.5, 0, 1.5),
            containerPos.add(-1.5, 0, 1.5),
            containerPos.add(1.5, 0, -1.5),
            containerPos.add(-1.5, 0, -1.5)
        };
        Vec3d bestPoint = null;
        double bestDistance = Double.MAX_VALUE;
        for (Vec3d point : approachPoints) {
            BlockPos checkPos = BlockPos.ofFloored(point);
            if (mc.world.getBlockState(checkPos).isAir() &&
                mc.world.getBlockState(checkPos.up()).isAir()) {
                double dist = playerPos.distanceTo(point);
                if (dist < bestDistance) {
                    bestDistance = dist;
                    bestPoint = point;
                }
            }
        }
        if (bestPoint != null) {
            info("Trying alternative approach angle");
            GoalBlock goal = new GoalBlock(BlockPos.ofFloored(bestPoint));
            BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess().setGoalAndPath(goal);
            currentState = ProcessState.MOVING_TO_CONTAINER;
            stateTimer = 60;
        } else {
            warning("No valid approach to container, skipping");
            currentContainer.isEmpty = true;
            currentContainer = null;
            currentState = isNearOutputArea() ? ProcessState.OUTPUT_PROCESS : ProcessState.INPUT_PROCESS;
        }
    }
    private void attemptContainerInteraction(Vec3d containerCenter, Vec3d eyePos, int timer) {
        Direction optimalFace = getOptimalClickFace(currentContainer.pos, eyePos);
        double yaw = Rotations.getYaw(containerCenter);
        double pitch = Rotations.getPitch(containerCenter);
        mc.player.setYaw((float)yaw);
        mc.player.setPitch((float)pitch);
        Vec3d pos = mc.player.getEntityPos();
        mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.Full(
            pos.x, pos.y, pos.z, (float)yaw, (float)pitch,
            mc.player.isOnGround(), mc.player.horizontalCollision));
        Vec3d optimalTarget = calculateOptimalTargetPoint(currentContainer.pos, eyePos);
        Vec3d[] hitPositions = {
            containerCenter,
            containerCenter.add(0, 0.15, 0),
            containerCenter.add(0, -0.15, 0),
            optimalTarget
        };
        for (int i = 0; i < 2; i++) {
            Vec3d hitPos = hitPositions[timer % hitPositions.length];
            BlockHitResult hitResult = new BlockHitResult(
                hitPos,
                optimalFace,
                currentContainer.pos,
                false
            );
            ActionResult result = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hitResult);
            if (result != ActionResult.SUCCESS && result != ActionResult.CONSUME) {
                result = mc.interactionManager.interactBlock(mc.player, Hand.OFF_HAND, hitResult);
            }
            if (result == ActionResult.SUCCESS || result == ActionResult.CONSUME) {
                if (debugMode.get()) {
                    info("Interaction successful!");
                }
                break;
            }
        }
    }
    private Vec3d calculateOptimalTargetPoint(BlockPos containerPos, Vec3d eyePos) {
        double heightDiff = containerPos.getY() - eyePos.y;
        double yOffset = 0.0;
        if (heightDiff > 1.5) {
            yOffset = -0.3;
        } else if (heightDiff > 0.5) {
            yOffset = -0.15;
        } else if (heightDiff < -1.5) {
            yOffset = 0.3;
        } else if (heightDiff < -0.5) {
            yOffset = 0.15;
        }
        if (containerOpenFailures > 0) {
            double variation = (containerOpenFailures % 3 - 1) * 0.1;
            yOffset += variation;
        }
        return Vec3d.ofCenter(containerPos).add(0, yOffset, 0);
    }
    private void stopAllMovement() {
        mc.options.forwardKey.setPressed(false);
        mc.options.backKey.setPressed(false);
        mc.options.leftKey.setPressed(false);
        mc.options.rightKey.setPressed(false);
        mc.options.jumpKey.setPressed(false);
        mc.options.sneakKey.setPressed(false);
        mc.options.sprintKey.setPressed(false);
    }
    private void handleTransferringItems() {
        if (currentContainer != null && mc.currentScreen instanceof GenericContainerScreen) {
            Vec3d containerCenter = Vec3d.ofCenter(currentContainer.pos);
            double yaw = Rotations.getYaw(containerCenter);
            double pitch = Rotations.getPitch(containerCenter);
            mc.player.setYaw((float)yaw);
            mc.player.setPitch((float)pitch);
        }
        if (isNearInputArea()) {
            handleInputTransferringItems();
        } else if (isNearOutputArea()) {
            handleOutputTransferringItems();
        } else {
            currentState = ProcessState.CHECKING_LOCATION;
        }
    }
    private void handleInputTransferringItems() {
        if (!(mc.currentScreen instanceof GenericContainerScreen)) {
            if (currentContainer != null && !currentContainer.isEmpty && !currentContainer.isFull) {
                boolean inventoryHasSpace = false;
                for (int j = 0; j < 36; j++) {
                    if (mc.player.getInventory().getStack(j).isEmpty()) {
                        inventoryHasSpace = true;
                        break;
                    }
                }
                if (inventoryHasSpace) {
                    warning("Container window closed unexpectedly! Reopening...");
                    currentState = ProcessState.OPENING_CONTAINER;
                    stateTimer = 5;
                    containerOpenFailures++;
                    if (containerOpenFailures > 3) {
                        warning("Failed to reopen container multiple times, skipping");
                        currentContainer = null;
                        containerOpenFailures = 0;
                        currentState = ProcessState.INPUT_PROCESS;
                    }
                    return;
                }
            }
            currentState = ProcessState.INPUT_PROCESS;
            return;
        }
        if (stateTimer > 0) {
            stateTimer--;
            return;
        }
        if (!(mc.player.currentScreenHandler instanceof GenericContainerScreenHandler handler)) {
            currentState = ProcessState.CLOSING_CONTAINER;
            return;
        }
        if (onlyShulkers.get()) {
            for (int j = 0; j < 36; j++) {
                ItemStack invStack = mc.player.getInventory().getStack(j);
                if (!invStack.isEmpty() && !isShulkerBox(invStack.getItem())) {
                    mc.player.closeHandledScreen();
                    InvUtils.drop().slot(j);
                    info("Dropping non-shulker: " + invStack.getItem().getName().getString());
                    currentState = ProcessState.OPENING_CONTAINER;
                    stateTimer = 5;
                    return;
                }
            }
        }
        if (isInventoryFull()) {
            currentState = ProcessState.CLOSING_CONTAINER;
            return;
        }
        boolean transferredItem = false;
        for (int i = 0; i < currentContainer.totalSlots; i++) {
            Slot slot = handler.getSlot(i);
            ItemStack stack = slot.getStack();
            if (!stack.isEmpty()) {
                if (onlyShulkers.get() && !isShulkerBox(stack.getItem())) {
                    continue;
                }
                mc.interactionManager.clickSlot(
                    handler.syncId,
                    slot.id,
                    0,
                    SlotActionType.QUICK_MOVE,
                    mc.player
                );
                transferredItem = true;
                itemsTransferred++;
                stateTimer = transferDelay.get();
                return;
            }
        }
        if (!transferredItem) {
            boolean containerActuallyEmpty = true;
            for (int i = 0; i < currentContainer.totalSlots; i++) {
                Slot slot = handler.getSlot(i);
                ItemStack stack = slot.getStack();
                if (!stack.isEmpty()) {
                    if (onlyShulkers.get() && !isShulkerBox(stack.getItem())) {
                        continue;
                    }
                    containerActuallyEmpty = false;
                    break;
                }
            }
            if (containerActuallyEmpty) {
                currentContainer.isEmpty = true;
                info("Container is now empty");
            }
            currentState = ProcessState.CLOSING_CONTAINER;
        }
    }
    private void handleClosingContainer() {
        if (mc.currentScreen instanceof GenericContainerScreen) {
            mc.player.closeHandledScreen();
        }
        stateTimer = closeDelay.get();
        if (isNearOutputArea()) {
            if (onlyShulkers.get()) {
                for (int i = 0; i < 36; i++) {
                    ItemStack stack = mc.player.getInventory().getStack(i);
                    if (!stack.isEmpty() && !isShulkerBox(stack.getItem())) {
                        InvUtils.drop().slot(i);
                        info("Dropped non-shulker at output: " + stack.getItem().getName().getString());
                        stateTimer = 5;
                        return;
                    }
                }
            }
            boolean inventoryEmpty = !hasItemsToTransfer();
            if (inventoryEmpty && enderChestHasItems && fillEnderChest.get()) {
                info("Getting items from enderchest to continue depositing");
                enderChestPos = findNearbyEnderChest();
                if (enderChestPos != null) {
                    currentState = ProcessState.OPENING_ENDERCHEST;
                    stateTimer = 5;
                } else {
                    FindItemResult enderChest = InvUtils.findInHotbar(Items.ENDER_CHEST);
                    if (enderChest.found()) {
                        BlockPos placePos = findSuitablePlacePos();
                        if (placePos != null) {
                            placeEnderChest(placePos, enderChest.slot());
                        }
                    }
                }
                return;
            }
            if (inventoryEmpty && !enderChestHasItems) {
                info("All items deposited, going back to input");
                currentContainer = null;
                currentState = ProcessState.GOING_BACK;
                return;
            }
            currentContainer = null;
            currentState = ProcessState.OUTPUT_PROCESS;
        } else if (isNearInputArea()) {
            if (onlyShulkers.get()) {
                boolean foundNonShulker = false;
                for (int i = 0; i < 36; i++) {
                    ItemStack stack = mc.player.getInventory().getStack(i);
                    if (!stack.isEmpty() && !isShulkerBox(stack.getItem())) {
                        InvUtils.drop().slot(i);
                        info("Dropped non-shulker: " + stack.getItem().getName().getString());
                        stateTimer = 5;
                        foundNonShulker = true;
                        if (currentContainer != null && !currentContainer.isEmpty) {
                            currentState = ProcessState.OPENING_CONTAINER;
                        } else {
                            currentState = ProcessState.INPUT_PROCESS;
                        }
                        return;
                    }
                }
                if (!foundNonShulker) {
                    info("No non-shulker items to drop");
                }
            }
            if (currentContainer != null && currentContainer.isEmpty && breakEmptyContainers.get()) {
                currentState = ProcessState.BREAKING_CONTAINER;
                return;
            }
            if (isInventoryFull()) {
                info("Inventory full");
                if (fillEnderChest.get() && !isEnderChestFull()) {
                    info("Checking enderchest...");
                    findOrPlaceEnderChest();
                } else {
                    info("Inventory and enderchest full, starting pearl loading");
                    currentContainer = null;
                    currentState = ProcessState.LOADING_PEARL;
                }
            } else {
                currentContainer = null;
                currentState = ProcessState.INPUT_PROCESS;
            }
        } else {
            currentState = ProcessState.CHECKING_LOCATION;
        }
    }
    private void handleBreakingContainer() {
        if (currentContainer == null) {
            currentState = ProcessState.INPUT_PROCESS;
            return;
        }
        mc.interactionManager.updateBlockBreakingProgress(currentContainer.pos, Direction.UP);
        if (mc.world.getBlockState(currentContainer.pos).isAir()) {
            inputContainers.remove(currentContainer);
            containersProcessed++;
            currentContainer = null;
            currentState = ProcessState.INPUT_PROCESS;
            stateTimer = moveDelay.get();
        }
    }
    private void findOrPlaceEnderChest() {
        enderChestPos = findNearbyEnderChest();
        if (enderChestPos != null) {
            currentState = ProcessState.OPENING_ENDERCHEST;
            stateTimer = 0;
            GoalNear goal = new GoalNear(enderChestPos, 2);
            BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess().setGoalAndPath(goal);
            info("Moving to enderchest");
        } else {
            FindItemResult enderChest = InvUtils.findInHotbar(Items.ENDER_CHEST);
            if (enderChest.found()) {
                BlockPos placePos = findSuitablePlacePos();
                if (placePos != null) {
                    placeEnderChest(placePos, enderChest.slot());
                }
            } else {
                currentContainer = null;
                currentState = ProcessState.INPUT_PROCESS;
            }
        }
    }
    private void handleOpeningEnderChest() {
        if (enderChestPos == null) {
            enderChestPos = findNearbyEnderChest();
            if (enderChestPos == null) {
                FindItemResult enderChest = InvUtils.findInHotbar(Items.ENDER_CHEST);
                if (enderChest.found()) {
                    BlockPos placePos = findSuitablePlacePos();
                    if (placePos != null) {
                        placeEnderChest(placePos, enderChest.slot());
                        return;
                    }
                }
                warning("No enderchest found or available to place");
                currentState = ProcessState.INPUT_PROCESS;
                return;
            }
        }
        if (mc.currentScreen instanceof GenericContainerScreen) {
            containerOpenFailures = 0;
            if (isNearOutputArea()) {
                currentState = ProcessState.EMPTYING_ENDERCHEST;
            } else {
                currentState = ProcessState.FILLING_ENDERCHEST;
            }
            stateTimer = transferDelay.get();
            return;
        }
        Vec3d eyePos = mc.player.getEyePos();
        double distance = eyePos.distanceTo(Vec3d.ofCenter(enderChestPos));
        if (distance <= 4.5) {
            Vec3d enderChestCenter = Vec3d.ofCenter(enderChestPos);
            double targetYaw = Rotations.getYaw(enderChestCenter);
            double targetPitch = Rotations.getPitch(enderChestCenter);
            mc.player.setYaw((float)targetYaw);
            mc.player.setPitch((float)targetPitch);
            BlockHitResult hitResult = new BlockHitResult(
                enderChestCenter,
                Direction.UP,
                enderChestPos,
                false
            );
            mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hitResult);
            stateTimer = 10;
        } else {
            if (!BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().isPathing()) {
                GoalNear goal = new GoalNear(enderChestPos, 2);
                BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess().setGoalAndPath(goal);
                info("Moving to enderchest");
            }
        }
        if (stateTimer > 0) {
            stateTimer--;
        }
    }
    private void handleFillingEnderChest() {
        if (!(mc.currentScreen instanceof GenericContainerScreen)) {
            checkNextStepAfterEnderChest();
            return;
        }
        if (stateTimer > 0) {
            stateTimer--;
            return;
        }
        if (!(mc.player.currentScreenHandler instanceof GenericContainerScreenHandler handler)) {
            mc.player.closeHandledScreen();
            checkNextStepAfterEnderChest();
            return;
        }
        boolean transferred = false;
        boolean enderChestHasSpace = false;
        for (int j = 0; j < 27; j++) {
            if (handler.getSlot(j).getStack().isEmpty()) {
                enderChestHasSpace = true;
                break;
            }
        }
        if (!enderChestHasSpace) {
            enderChestFull = true;
            info("Enderchest is full");
            mc.player.closeHandledScreen();
            stateTimer = closeDelay.get();
            checkNextStepAfterEnderChest();
            return;
        }
        for (int i = 27; i < 63; i++) {
            Slot slot = handler.getSlot(i);
            ItemStack stack = slot.getStack();
            if (!stack.isEmpty()) {
                if (onlyShulkers.get() && !isShulkerBox(stack.getItem())) {
                    continue;
                }
                mc.interactionManager.clickSlot(
                    handler.syncId,
                    slot.id,
                    0,
                    SlotActionType.QUICK_MOVE,
                    mc.player
                );
                transferred = true;
                enderChestHasItems = true;
                stateTimer = transferDelay.get();
                info("Transferred item to enderchest");
                return;
            }
        }
        if (!transferred) {
            boolean inventoryHasItems = false;
            for (int i = 0; i < 36; i++) {
                ItemStack invStack = mc.player.getInventory().getStack(i);
                if (!invStack.isEmpty()) {
                    if (onlyShulkers.get() && !isShulkerBox(invStack.getItem())) {
                        continue;
                    }
                    inventoryHasItems = true;
                    break;
                }
            }
            if (inventoryHasItems) {
                stateTimer = transferDelay.get();
                return;
            }
            mc.player.closeHandledScreen();
            stateTimer = closeDelay.get();
            checkNextStepAfterEnderChest();
        }
    }
    private void checkNextStepAfterEnderChest() {
        if (isInventoryFull() && enderChestFull) {
            info("Both inventory and enderchest are full, starting pearl loading");
            currentContainer = null;
            currentState = ProcessState.LOADING_PEARL;
        } else {
            currentContainer = null;
            currentState = ProcessState.INPUT_PROCESS;
        }
    }
    private void handleEmptyingEnderChest() {
        if (!(mc.currentScreen instanceof GenericContainerScreen)) {
            if (hasItemsToTransfer()) {
                currentState = ProcessState.OUTPUT_PROCESS;
            } else {
                enderChestEmptied = true;
                currentState = ProcessState.OUTPUT_PROCESS;
            }
            return;
        }
        if (stateTimer > 0) {
            stateTimer--;
            return;
        }
        if (!(mc.player.currentScreenHandler instanceof GenericContainerScreenHandler handler)) {
            mc.player.closeHandledScreen();
            currentState = ProcessState.OUTPUT_PROCESS;
            return;
        }
        boolean transferred = false;
        boolean inventoryHasSpace = false;
        for (int i = 0; i < 36; i++) {
            if (mc.player.getInventory().getStack(i).isEmpty()) {
                inventoryHasSpace = true;
                break;
            }
        }
        if (!inventoryHasSpace) {
            info("Inventory full, closing enderchest to deposit items");
            mc.player.closeHandledScreen();
            currentState = ProcessState.OUTPUT_PROCESS;
            return;
        }
        boolean enderChestIsEmpty = true;
        for (int i = 0; i < 27; i++) {
            if (!handler.getSlot(i).getStack().isEmpty()) {
                enderChestIsEmpty = false;
                break;
            }
        }
        if (enderChestIsEmpty) {
            enderChestHasItems = false;
            enderChestEmptied = true;
            info("Enderchest is empty, all items transferred");
            mc.player.closeHandledScreen();
            stateTimer = closeDelay.get();
            currentState = ProcessState.OUTPUT_PROCESS;
            return;
        }
        for (int i = 0; i < 27; i++) {
            Slot slot = handler.getSlot(i);
            ItemStack stack = slot.getStack();
            if (!stack.isEmpty()) {
                if (onlyShulkers.get() && !isShulkerBox(stack.getItem())) {
                    continue;
                }
                mc.interactionManager.clickSlot(
                    handler.syncId,
                    slot.id,
                    0,
                    SlotActionType.QUICK_MOVE,
                    mc.player
                );
                transferred = true;
                stateTimer = transferDelay.get();
                info("Retrieved item from enderchest");
                return;
            }
        }
        if (!transferred && !enderChestIsEmpty) {
            if (inventoryHasSpace && onlyShulkers.get()) {
                info("Only non-shulkers left in enderchest");
                enderChestHasItems = false;
                mc.player.closeHandledScreen();
                currentState = ProcessState.OUTPUT_PROCESS;
            } else {
                info("Inventory full, depositing items first");
                mc.player.closeHandledScreen();
                currentState = ProcessState.OUTPUT_PROCESS;
            }
        }
    }
    private void handlePearlLoading() {
        if (!waitingForPearl) {
            sendPearlCommand();
            waitingForPearl = true;
            lastPearlMessageTime = System.currentTimeMillis();
            pearlRetryCount = 0;
            initialPlayerPos = mc.player.getEntityPos();
        }
        Vec3d currentPos = mc.player.getEntityPos();
        double distance = currentPos.distanceTo(initialPlayerPos);
        if (distance > 100) {
            if (isNearOutputArea()) {
                info("Successfully pearl loaded to output area!");
                waitingForPearl = false;
                ensureOffhandHasItem();
                currentState = ProcessState.RESET_PEARL_PICKUP;
                hasThrownPearl = false;
                hasPlacedShulker = false;
                isGoingToInput = false;
                return;
            } else if (!isNearInputArea()) {
                warning("Teleported but not to output area, retrying...");
                waitingForPearl = false;
                currentState = ProcessState.LOADING_PEARL;
                return;
            }
        }
        if (System.currentTimeMillis() - lastPearlMessageTime > pearlTimeout.get() * 1000) {
            if (pearlRetryCount < maxRetries.get()) {
                pearlRetryCount++;
                info("Pearl loading timeout, retrying (attempt " + pearlRetryCount + "/" + maxRetries.get() + ")");
                sendPearlCommand();
                lastPearlMessageTime = System.currentTimeMillis();
            } else {
                error("Pearl loading failed after " + maxRetries.get() + " retries!");
                currentState = ProcessState.IDLE;
                waitingForPearl = false;
            }
        }
    }
    private void sendPearlCommand() {
        String randomSuffix = generateRandomString(8);
        String command = String.format("/msg %s %s %s",
            pearlPlayerName.get(),
            pearlCommand.get(),
            randomSuffix);
        ChatUtils.sendPlayerMsg(command);
        lastRandomString = randomSuffix;
        info("Sent pearl command: " + command);
    }
    private void handleResetPearlPickup() {
        BlockPos pickupPos;
        if (isGoingToInput) {
            pickupPos = inputPearlPickupPos.get();
        } else {
            pickupPos = outputPearlPickupPos.get();
        }
        ensureOffhandHasItem();
        double distance = mc.player.getEntityPos().distanceTo(Vec3d.ofCenter(pickupPos));
        if (distance > 3) {
            GoalBlock goal = new GoalBlock(pickupPos);
            BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess().setGoalAndPath(goal);
            stateTimer = moveDelay.get();
        } else {
            currentState = ProcessState.RESET_PEARL_PLACE_SHULKER;
            stateTimer = 10;
        }
    }
    private void handleResetPearlPlaceShulker() {
        if (!hasPlacedShulker) {
            ItemStack slot0 = mc.player.getInventory().getStack(0);
            if (slot0.getItem() == Items.ENDER_PEARL) {
                for (int i = 1; i < 36; i++) {
                    if (mc.player.getInventory().getStack(i).isEmpty()) {
                        InvUtils.move().from(36).to(i);
                        info("Moved pearl from slot 0 temporarily");
                        break;
                    }
                }
            }
            ensureOffhandHasItem();
            slot0 = mc.player.getInventory().getStack(0);
            if (isShulkerBox(slot0.getItem())) {
                offhandBackup = mc.player.getOffHandStack().copy();
                if (!mc.player.getOffHandStack().isEmpty()) {
                    for (int i = 1; i < 36; i++) {
                        if (mc.player.getInventory().getStack(i).isEmpty()) {
                            InvUtils.move().fromOffhand().to(i);
                            break;
                        }
                    }
                }
                InvUtils.move().from(36).toOffhand();
                hasPlacedShulker = true;
                info("Placed shulker in offhand, now walking to pressure plate");
                BlockPos pickupPos = isGoingToInput ? inputPearlPickupPos.get() : outputPearlPickupPos.get();
                GoalBlock goal = new GoalBlock(pickupPos);
                BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess().setGoalAndPath(goal);
                stateTimer = 30;
            } else {
                info("No shulker in slot 0, continuing without offhand shulker");
                hasPlacedShulker = true;
                stateTimer = 5;
            }
        } else {
            FindItemResult pearl = InvUtils.find(Items.ENDER_PEARL);
            if (pearl.found()) {
                info("Pearl picked up, moving to throw location");
                currentState = ProcessState.RESET_PEARL_APPROACH;
                stateTimer = 5;
            } else {
                BlockPos pickupPos = isGoingToInput ? inputPearlPickupPos.get() : outputPearlPickupPos.get();
                double distance = mc.player.getEntityPos().distanceTo(Vec3d.ofCenter(pickupPos));
                if (distance > 1.0) {
                    GoalBlock goal = new GoalBlock(pickupPos);
                    BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess().setGoalAndPath(goal);
                }
                stateTimer = 5;
            }
        }
    }
    private void handleResetPearlApproach() {
        BlockPos throwPos;
        if (isGoingToInput) {
            throwPos = inputPearlThrowPos.get();
        } else {
            throwPos = outputPearlThrowPos.get();
        }
        double distance = mc.player.getEntityPos().distanceTo(Vec3d.ofCenter(throwPos));
        if (distance <= 1.5) {
            if (BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().isPathing()) {
                BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().cancelEverything();
            }
            lastBaritoneGoal = null;
            safeRetreatPos = mc.player.getBlockPos();
            info("Starting precise positioning from adjacent block - stored safe retreat position");
            currentState = ProcessState.RESET_PEARL_PREPARE;
            stateTimer = 5;
            return;
        }
        if (BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().isPathing()) {
            stateTimer = 5;
            return;
        }
        BlockPos goalPos = null;
        safeRetreatPos = null;
        Direction[] preferredDirections = {Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};
        for (Direction dir : preferredDirections) {
            BlockPos adjacent = throwPos.offset(dir);
            BlockState adjacentState = mc.world.getBlockState(adjacent);
            BlockState belowState = mc.world.getBlockState(adjacent.down());
            if (adjacentState.isAir() && belowState.isSolidBlock(mc.world, adjacent.down())) {
                goalPos = adjacent;
                safeRetreatPos = adjacent;
                approachDirection = dir.getOpposite();
                info("Found safe approach position from " + dir + " side");
                break;
            }
        }
        if (goalPos == null) {
            for (Direction dir : preferredDirections) {
                BlockPos candidate = throwPos.offset(dir, 2);
                BlockState state = mc.world.getBlockState(candidate);
                BlockState belowState = mc.world.getBlockState(candidate.down());
                if (state.isAir() && belowState.isSolidBlock(mc.world, candidate.down())) {
                    goalPos = candidate;
                    safeRetreatPos = throwPos.offset(dir);
                    approachDirection = dir.getOpposite();
                    info("Using fallback approach position from " + dir + " side");
                    break;
                }
            }
        }
        if (goalPos == null) {
            goalPos = throwPos.offset(Direction.NORTH, 2);
            safeRetreatPos = throwPos.offset(Direction.NORTH);
            approachDirection = Direction.SOUTH;
            warning("Using fallback approach position");
        }
        if (lastBaritoneGoal == null || !lastBaritoneGoal.equals(goalPos)) {
            lastBaritoneGoal = goalPos;
            GoalBlock goal = new GoalBlock(goalPos);
            BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess().setGoalAndPath(goal);
            info("Pathing to approach position");
        }
        stateTimer = moveDelay.get();
    }
    private void handleResetPearlPrepare() {
        BlockPos throwPos;
        double throwYaw, throwPitch;
        if (isGoingToInput) {
            throwPos = inputPearlThrowPos.get();
            throwYaw = inputPearlThrowYaw.get();
            throwPitch = inputPearlThrowPitch.get();
        } else {
            throwPos = outputPearlThrowPos.get();
            throwYaw = outputPearlThrowYaw.get();
            throwPitch = outputPearlThrowPitch.get();
        }
        mc.options.sneakKey.setPressed(true);
        BlockState throwState = mc.world.getBlockState(throwPos);
        boolean isTrapdoor = throwState.getBlock() instanceof TrapdoorBlock;
        double targetX = throwPos.getX() + 0.5;
        double targetZ = throwPos.getZ() + 0.5;
        double dx = targetX - mc.player.getX();
        double dz = targetZ - mc.player.getZ();
        double horizontalDistance = Math.sqrt(dx * dx + dz * dz);
        double requiredYaw = Math.toDegrees(Math.atan2(-dx, dz));
        if (approachDirection == null && horizontalDistance > 0.1) {
            double approachAngle = Math.toDegrees(Math.atan2(dx, -dz));
            if (Math.abs(approachAngle) <= 45) {
                approachDirection = Direction.NORTH;
            } else if (Math.abs(approachAngle) >= 135) {
                approachDirection = Direction.SOUTH;
            } else if (approachAngle > 45 && approachAngle < 135) {
                approachDirection = Direction.EAST;
            } else {
                approachDirection = Direction.WEST;
            }
            info("Approach direction: " + approachDirection);
        }
        double requiredPitch = 15.0;
        mc.player.setYaw((float)requiredYaw);
        mc.player.setPitch((float)requiredPitch);
        boolean inPosition = horizontalDistance < positionTolerance.get();
        if (!inPosition) {
            mc.options.forwardKey.setPressed(false);
            mc.options.backKey.setPressed(false);
            mc.options.leftKey.setPressed(false);
            mc.options.rightKey.setPressed(false);
            mc.options.sneakKey.setPressed(true);
            mc.options.forwardKey.setPressed(true);
            stateTimer++;
            if (stateTimer % 20 == 0) {
                info(String.format("Approaching water (%.2f blocks away) Yaw: %.1f",
                    horizontalDistance, requiredYaw));
            }
            if (stateTimer > 60 && horizontalDistance > 2.0) {
                if (stateTimer % 40 < 5) {
                    mc.options.forwardKey.setPressed(false);
                    mc.options.backKey.setPressed(true);
                    info("Backing up briefly to unstick");
                } else {
                    mc.options.backKey.setPressed(false);
                    mc.options.forwardKey.setPressed(true);
                }
            }
            if (stateTimer > 120) {
                if (horizontalDistance < positionTolerance.get() * 1.5) {
                    info("Close enough after timeout");
                    inPosition = true;
                } else {
                    info(String.format("Still approaching (%.2f blocks away)", horizontalDistance));
                }
            }
            return;
        }
        if (inPosition) {
            mc.options.forwardKey.setPressed(false);
            mc.options.backKey.setPressed(false);
            mc.options.leftKey.setPressed(false);
            mc.options.rightKey.setPressed(false);
            mc.options.sneakKey.setPressed(true);
            Rotations.rotate(throwYaw, throwPitch);
            mc.player.setYaw((float)throwYaw);
            mc.player.setPitch((float)throwPitch);
            info("Switching to throw angle - Yaw: " + String.format("%.3f", throwYaw) +
                " Pitch: " + String.format("%.3f", throwPitch));
            info("In position, ready to throw");
            currentState = ProcessState.RESET_PEARL_THROW;
            stateTimer = 5;
        }
    }
    private void handleResetPearlThrow() {
        BlockPos throwPos;
        double throwYaw, throwPitch;
        if (isGoingToInput) {
            throwPos = inputPearlThrowPos.get();
            throwYaw = inputPearlThrowYaw.get();
            throwPitch = inputPearlThrowPitch.get();
        } else {
            throwPos = outputPearlThrowPos.get();
            throwYaw = outputPearlThrowYaw.get();
            throwPitch = outputPearlThrowPitch.get();
        }
        if (!hasThrownPearl) {
            mc.options.sneakKey.setPressed(true);
            BlockState throwState = mc.world.getBlockState(throwPos);
            boolean isTrapdoor = throwState.getBlock() instanceof TrapdoorBlock;
            if (!rotationSet) {
                Rotations.rotate(throwYaw, throwPitch);
                mc.player.setYaw((float)throwYaw);
                mc.player.setPitch((float)throwPitch);
                info("Set exact throw angle: Yaw=" + String.format("%.3f", throwYaw) +
                    " Pitch=" + String.format("%.3f", throwPitch));
                rotationSet = true;
                rotationStabilizationTimer = 10;
                return;
            }
            if (rotationStabilizationTimer > 0) {
                Rotations.rotate(throwYaw, throwPitch);
                mc.player.setYaw((float)throwYaw);
                mc.player.setPitch((float)throwPitch);
                rotationStabilizationTimer--;
                if (rotationStabilizationTimer == 0) {
                    info("Rotation stabilized, ready to throw");
                }
                return;
            }
            FindItemResult pearl = InvUtils.find(Items.ENDER_PEARL);
            if (pearl.found()) {
                if (mc.player.getMainHandStack().getItem() != Items.ENDER_PEARL) {
                    previousSlot = ((PlayerInventoryAccessor) mc.player.getInventory()).getSelectedSlot();
                    InvUtils.swap(pearl.slot(), false);
                    stateTimer = 3;
                    return;
                }
                if (mc.player.getMainHandStack().getItem() != Items.ENDER_PEARL) {
                    warning("Pearl not in hand, retrying swap");
                    return;
                }
                if (stateTimer > 1) {
                    stateTimer--;
                    return;
                }
                initialPlayerPos = mc.player.getEntityPos();
                info("Throwing pearl");
                mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
                hasThrownPearl = true;
                pearlThrowTime = System.currentTimeMillis();
                info("Pearl thrown! Walking back immediately!");
                mc.options.sneakKey.setPressed(true);
                mc.options.forwardKey.setPressed(false);
                mc.options.leftKey.setPressed(false);
                mc.options.rightKey.setPressed(false);
                mc.options.sprintKey.setPressed(false);
                mc.options.backKey.setPressed(true);
                ((InputAccessor) mc.player.input).setMovementForward(-1.0f);
                ((InputAccessor) mc.player.input).setMovementSideways(0.0f);
                rotationSet = false;
                stateTimer = 20;
                currentState = ProcessState.RESET_PEARL_WAIT;
            } else {
                error("No ender pearl found!");
                mc.options.sneakKey.setPressed(false);
                currentState = ProcessState.OUTPUT_PROCESS;
            }
        }
    }
    private void handleResetPearlWait() {
        if (stateTimer > 0) {
            mc.options.sneakKey.setPressed(true);
            double throwYaw, throwPitch;
            if (isGoingToInput) {
                throwYaw = inputPearlThrowYaw.get();
                throwPitch = inputPearlThrowPitch.get();
            } else {
                throwYaw = outputPearlThrowYaw.get();
                throwPitch = outputPearlThrowPitch.get();
            }
            Rotations.rotate(throwYaw, throwPitch);
            mc.player.setYaw((float)throwYaw);
            mc.player.setPitch((float)throwPitch);
            mc.options.forwardKey.setPressed(false);
            mc.options.leftKey.setPressed(false);
            mc.options.rightKey.setPressed(false);
            mc.options.sprintKey.setPressed(false);
            mc.options.backKey.setPressed(true);
            ((InputAccessor) mc.player.input).setMovementForward(-1.0f);
            ((InputAccessor) mc.player.input).setMovementSideways(0.0f);
            if (safeRetreatPos != null) {
                double distanceToSafe = mc.player.getEntityPos().distanceTo(Vec3d.ofCenter(safeRetreatPos));
                if (distanceToSafe < 0.5) {
                    info("Reached safe position!");
                    stateTimer = 0;
                }
            }
            stateTimer--;
            if (stateTimer == 19) {
                info("Walking backward to safe position!");
            } else if (stateTimer == 10) {
                info("Still backing up...");
            }
            if (stateTimer == 0) {
                info("Safe distance reached");
                mc.options.forwardKey.setPressed(false);
                mc.options.backKey.setPressed(false);
                ((InputAccessor) mc.player.input).setMovementForward(0.0f);
                mc.options.sneakKey.setPressed(false);
                Rotations.rotate(mc.player.getYaw(), mc.player.getPitch());
                if (initialPlayerPos == null) {
                    initialPlayerPos = mc.player.getEntityPos();
                }
            }
            return;
        }
        mc.options.sneakKey.setPressed(false);
        mc.options.forwardKey.setPressed(false);
        mc.options.backKey.setPressed(false);
        mc.options.leftKey.setPressed(false);
        mc.options.rightKey.setPressed(false);
        mc.options.sprintKey.setPressed(false);
        Rotations.rotate(mc.player.getYaw(), mc.player.getPitch());
        if (System.currentTimeMillis() - pearlThrowTime > pearlWaitTime.get() * 1000) {
            BlockPos throwPos;
            if (isGoingToInput) {
                throwPos = inputPearlThrowPos.get();
            } else {
                throwPos = outputPearlThrowPos.get();
            }
            double distance = mc.player.getEntityPos().distanceTo(initialPlayerPos);
            FindItemResult pearlCheck = InvUtils.find(Items.ENDER_PEARL);
            boolean stillHasPearl = pearlCheck.found() && pearlCheck.count() > 0;
            if (distance < 5 && !stillHasPearl) {
                info("Pearl successfully placed in stasis (no pearl in inventory)");
                restoreOffhandItem();
                if (previousSlot >= 0 && previousSlot < 9) {
                    ((PlayerInventoryAccessor) mc.player.getInventory()).setSelectedSlot(previousSlot);
                    previousSlot = -1;
                }
                hasThrownPearl = false;
                hasPlacedShulker = false;
                pearlFailRetries = 0;
                approachDirection = null;
                lastBaritoneGoal = null;
                rotationSet = false;
                rotationStabilizationTimer = 0;
                safeRetreatPos = null;
                Rotations.rotate(mc.player.getYaw(), mc.player.getPitch());
                if (isNearInputArea()) {
                    info("Continuing to input process");
                    currentState = ProcessState.INPUT_PROCESS;
                    findNextInputContainer();
                } else if (isNearOutputArea()) {
                    info("Continuing to output process");
                    currentState = ProcessState.OUTPUT_PROCESS;
                    stateTimer = 5;
                } else {
                    currentState = ProcessState.CHECKING_LOCATION;
                }
            } else {
                warning("Pearl was loaded! Teleportation detected");
                pearlFailRetries++;
                if (pearlFailRetries < maxRetries.get()) {
                    warning("Pearl throw failed, retrying (attempt " + pearlFailRetries + "/" + maxRetries.get() + ")");
                    restoreOffhandItem();
                    hasThrownPearl = false;
                    hasPlacedShulker = false;
                    rotationSet = false;
                    rotationStabilizationTimer = 0;
                    currentState = ProcessState.RESET_PEARL_PICKUP;
                } else {
                    error("Pearl throw failed after " + maxRetries.get() + " attempts!");
                    restoreOffhandItem();
                    currentState = ProcessState.IDLE;
                }
            }
        }
    }
    private void restoreOffhandItem() {
        ItemStack offhandItem = mc.player.getOffHandStack();
        if (!offhandItem.isEmpty() && isShulkerBox(offhandItem.getItem())) {
            mc.interactionManager.clickSlot(
                mc.player.currentScreenHandler.syncId,
                45,
                0,
                SlotActionType.PICKUP,
                mc.player
            );
            mc.interactionManager.clickSlot(
                mc.player.currentScreenHandler.syncId,
                36,
                0,
                SlotActionType.PICKUP,
                mc.player
            );
            mc.interactionManager.clickSlot(
                mc.player.currentScreenHandler.syncId,
                45,
                0,
                SlotActionType.PICKUP,
                mc.player
            );
            info("Moved shulker back to hotbar slot 0");
        }
        if (!offhandBackup.isEmpty()) {
            for (int i = 0; i < 36; i++) {
                ItemStack stack = mc.player.getInventory().getStack(i);
                if (ItemStack.areEqual(stack, offhandBackup)) {
                    mc.interactionManager.clickSlot(
                        mc.player.currentScreenHandler.syncId,
                        45,
                        0,
                        SlotActionType.PICKUP,
                        mc.player
                    );
                    mc.interactionManager.clickSlot(
                        mc.player.currentScreenHandler.syncId,
                        i < 9 ? i + 36 : i,
                        0,
                        SlotActionType.PICKUP,
                        mc.player
                    );
                    mc.interactionManager.clickSlot(
                        mc.player.currentScreenHandler.syncId,
                        45,
                        0,
                        SlotActionType.PICKUP,
                        mc.player
                    );
                    info("Restored original offhand item");
                    break;
                }
            }
        }
        offhandBackup = ItemStack.EMPTY;
    }
    private void handleOutputProcess() {
        if (stateTimer > 0) {
            stateTimer--;
            Rotations.rotate(mc.player.getYaw(), mc.player.getPitch());
            return;
        }
        enderChestFull = false;
        if (hasItemsToTransfer()) {
            if (currentContainer == null) {
                findNextOutputContainer();
            } else {
                moveToContainer(currentContainer);
            }
            return;
        }
        if (fillEnderChest.get()) {
            if (!enderChestEmptied) {
                info("Inventory empty, checking enderchest for items...");
                enderChestPos = findNearbyEnderChest();
                if (enderChestPos != null) {
                    currentState = ProcessState.OPENING_ENDERCHEST;
                    stateTimer = 5;
                    return;
                } else {
                    FindItemResult enderChest = InvUtils.findInHotbar(Items.ENDER_CHEST);
                    if (enderChest.found()) {
                        BlockPos placePos = findSuitablePlacePos();
                        if (placePos != null) {
                            info("Placing enderchest to check for items");
                            placeEnderChest(placePos, enderChest.slot());
                            return;
                        }
                    }
                    warning("No enderchest available, skipping enderchest check");
                    enderChestEmptied = true;
                }
            } else {
                info("All items deposited and enderchest verified empty, going back to input");
                currentState = ProcessState.GOING_BACK;
                enderChestEmptied = false;
            }
        } else {
            info("All items deposited, going back to input");
            currentState = ProcessState.GOING_BACK;
        }
    }
    private void findNextOutputContainer() {
        currentContainer = outputContainers.stream()
            .filter(c -> !c.isFull)
            .min(Comparator.comparingDouble(c -> mc.player.getEntityPos().distanceTo(Vec3d.ofCenter(c.pos))))
            .orElse(null);
        if (currentContainer == null) {
            info("All output containers full, rescanning...");
            detectContainersInArea(outputAreaPos1, outputAreaPos2, false);
            currentContainer = outputContainers.stream()
                .filter(c -> !c.isFull)
                .min(Comparator.comparingDouble(c -> mc.player.getEntityPos().distanceTo(Vec3d.ofCenter(c.pos))))
                .orElse(null);
            if (currentContainer == null) {
                warning("All output containers are still full! Going back to input.");
                currentState = ProcessState.GOING_BACK;
            } else {
                info("Found available container after rescan");
                moveToContainer(currentContainer);
            }
        } else {
            info("Moving to output container");
            moveToContainer(currentContainer);
        }
    }
    private void handleOutputTransferringItems() {
        if (!(mc.currentScreen instanceof GenericContainerScreen)) {
            if (currentContainer != null && !currentContainer.isFull) {
                boolean hasItems = false;
                for (int i = 0; i < 36; i++) {
                    if (!mc.player.getInventory().getStack(i).isEmpty()) {
                        hasItems = true;
                        break;
                    }
                }
                if (hasItems) {
                    warning("Container window closed unexpectedly! Reopening...");
                    currentState = ProcessState.OPENING_CONTAINER;
                    stateTimer = 5;
                    containerOpenFailures++;
                    if (containerOpenFailures > 3) {
                        warning("Failed to reopen container multiple times, marking as full");
                        currentContainer.isFull = true;
                        currentContainer = null;
                        containerOpenFailures = 0;
                        currentState = ProcessState.OUTPUT_PROCESS;
                    }
                    return;
                }
            }
            currentState = ProcessState.OUTPUT_PROCESS;
            return;
        }
        if (stateTimer > 0) {
            stateTimer--;
            return;
        }
        if (!(mc.player.currentScreenHandler instanceof GenericContainerScreenHandler handler)) {
            currentState = ProcessState.CLOSING_CONTAINER;
            return;
        }
        boolean transferredItem = false;
        boolean containerHasSpace = false;
        for (int i = 0; i < currentContainer.totalSlots; i++) {
            if (handler.getSlot(i).getStack().isEmpty()) {
                containerHasSpace = true;
                break;
            }
        }
        if (!containerHasSpace) {
            currentContainer.isFull = true;
            info("Container is now full");
            currentState = ProcessState.CLOSING_CONTAINER;
            return;
        }
        boolean hasItems = false;
        for (int i = 0; i < 36; i++) {
            if (!mc.player.getInventory().getStack(i).isEmpty()) {
                hasItems = true;
                break;
            }
        }
        if (!hasItems) {
            info("No items left to transfer");
            currentState = ProcessState.CLOSING_CONTAINER;
            return;
        }
        int playerInventoryStart = currentContainer.totalSlots;
        for (int i = playerInventoryStart; i < playerInventoryStart + 36; i++) {
            Slot slot = handler.getSlot(i);
            ItemStack stack = slot.getStack();
            if (!stack.isEmpty()) {
                if (onlyShulkers.get() && !isShulkerBox(stack.getItem())) {
                    continue;
                }
                mc.interactionManager.clickSlot(
                    handler.syncId,
                    slot.id,
                    0,
                    SlotActionType.QUICK_MOVE,
                    mc.player
                );
                transferredItem = true;
                itemsTransferred++;
                stateTimer = transferDelay.get();
                return;
            }
        }
        if (!transferredItem) {
            boolean inventoryEmpty = true;
            for (int i = 0; i < 36; i++) {
                if (!mc.player.getInventory().getStack(i).isEmpty()) {
                    inventoryEmpty = false;
                    break;
                }
            }
            if (inventoryEmpty) {
                if (enderChestHasItems && fillEnderChest.get()) {
                    info("Inventory empty but enderchest has items, retrieving from enderchest");
                    currentState = ProcessState.CLOSING_CONTAINER;
                } else {
                    info("Inventory and enderchest empty");
                    currentState = ProcessState.CLOSING_CONTAINER;
                }
            } else {
                currentContainer.isFull = true;
                info("Container is now full");
                currentState = ProcessState.CLOSING_CONTAINER;
            }
        }
    }
    private boolean hasItemsInEnderChest() {
        return enderChestHasItems && !enderChestEmptied;
    }
    private void handleGoingBack() {
        switch (goBackMethod.get()) {
            case KILL -> {
                if (!waitingForRespawn) {
                    String killCommand = "/kill";
                    if (killRetryCount > 0) {
                        killCommand = "/kill " + generateRandomString(6);
                    }
                    ChatUtils.sendPlayerMsg(killCommand);
                    info("Sent kill command: " + killCommand);
                    waitingForRespawn = true;
                    lastKillTime = System.currentTimeMillis();
                    initialPlayerPos = mc.player.getEntityPos();
                }
                if (mc.player.isDead() || mc.player.getHealth() <= 0) {
                    mc.getNetworkHandler().sendPacket(new ClientStatusC2SPacket(ClientStatusC2SPacket.Mode.PERFORM_RESPAWN));
                    info("Sent respawn packet");
                }
                Vec3d currentPos = mc.player.getEntityPos();
                double distance = currentPos.distanceTo(initialPlayerPos);
                if ((distance > 100 || mc.player.getHealth() > 0) && System.currentTimeMillis() - lastKillTime > 1000) {
                    if (isNearInputArea()) {
                        info("Respawned at input area!");
                        waitingForRespawn = false;
                        killRetryCount = 0;
                        currentState = ProcessState.INPUT_PROCESS;
                        findNextInputContainer();
                    } else if (System.currentTimeMillis() - lastKillTime > 5000) {
                        killRetryCount++;
                        waitingForRespawn = false;
                        warning("Kill command may have been spam filtered, retrying with random suffix...");
                    }
                }
            }
            case PEARL -> {
                if (!waitingForPearl) {
                    sendGoBackPearlCommand();
                    waitingForPearl = true;
                    lastPearlMessageTime = System.currentTimeMillis();
                    pearlRetryCount = 0;
                    initialPlayerPos = mc.player.getEntityPos();
                }
                Vec3d currentPos = mc.player.getEntityPos();
                double distance = currentPos.distanceTo(initialPlayerPos);
                if (distance > 100) {
                    if (isNearInputArea()) {
                        info("Successfully returned to input area via pearl!");
                        waitingForPearl = false;
                        ensureOffhandHasItem();
                        currentState = ProcessState.RESET_PEARL_PICKUP;
                        hasThrownPearl = false;
                        hasPlacedShulker = false;
                        isGoingToInput = true;
                    } else if (!isNearOutputArea()) {
                        warning("Teleported but not to input area, retrying...");
                        waitingForPearl = false;
                    }
                } else {
                    if (System.currentTimeMillis() - lastPearlMessageTime > pearlTimeout.get() * 1000) {
                        if (pearlRetryCount < maxRetries.get()) {
                            pearlRetryCount++;
                            info("Go back pearl timeout, retrying (attempt " + pearlRetryCount + "/" + maxRetries.get() + ")");
                            sendGoBackPearlCommand();
                            lastPearlMessageTime = System.currentTimeMillis();
                        } else {
                            error("Go back pearl loading failed after " + maxRetries.get() + " retries!");
                            currentState = ProcessState.IDLE;
                            waitingForPearl = false;
                        }
                    }
                }
            }
        }
    }
    private void sendGoBackPearlCommand() {
        String randomSuffix = generateRandomString(8);
        String command = String.format("/msg %s %s %s",
            goBackPlayerName.get(),
            goBackCommand.get(),
            randomSuffix);
        ChatUtils.sendPlayerMsg(command);
        info("Sent go back command: " + command);
    }
    private boolean hasValidAreas() {
        return inputAreaPos1 != null && inputAreaPos2 != null &&
            outputAreaPos1 != null && outputAreaPos2 != null;
    }
    private boolean isNearInputArea() {
        if (inputAreaPos1 == null || inputAreaPos2 == null) return false;
        BlockPos playerPos = mc.player.getBlockPos();
        return playerPos.getX() >= inputAreaPos1.getX() - 10 &&
            playerPos.getX() <= inputAreaPos2.getX() + 10 &&
            playerPos.getY() >= inputAreaPos1.getY() - 5 &&
            playerPos.getY() <= inputAreaPos2.getY() + 5 &&
            playerPos.getZ() >= inputAreaPos1.getZ() - 10 &&
            playerPos.getZ() <= inputAreaPos2.getZ() + 10;
    }
    private boolean isNearOutputArea() {
        if (outputAreaPos1 == null || outputAreaPos2 == null) return false;
        BlockPos playerPos = mc.player.getBlockPos();
        return playerPos.getX() >= outputAreaPos1.getX() - 10 &&
            playerPos.getX() <= outputAreaPos2.getX() + 10 &&
            playerPos.getY() >= outputAreaPos1.getY() - 5 &&
            playerPos.getY() <= outputAreaPos2.getY() + 5 &&
            playerPos.getZ() >= outputAreaPos1.getZ() - 10 &&
            playerPos.getZ() <= outputAreaPos2.getZ() + 10;
    }
    private boolean isServerLagging() {
        return false;
    }
    private boolean isInventoryFull() {
        if (onlyShulkers.get()) {
            int shulkerCount = 0;
            for (int i = 0; i < 36; i++) {
                ItemStack stack = mc.player.getInventory().getStack(i);
                if (!stack.isEmpty() && isShulkerBox(stack.getItem())) {
                    shulkerCount++;
                }
            }
            return shulkerCount >= 36;
        } else {
            for (int i = 0; i < 36; i++) {
                ItemStack stack = mc.player.getInventory().getStack(i);
                if (stack.isEmpty()) {
                    return false;
                }
            }
            return true;
        }
    }
    private boolean isEnderChestFull() {
        return enderChestFull;
    }
    private boolean hasItemsToTransfer() {
        for (int i = 0; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (!stack.isEmpty()) {
                if (!onlyShulkers.get() || isShulkerBox(stack.getItem())) {
                    return true;
                }
            }
        }
        return false;
    }
    private boolean isShulkerBox(Item item) {
        return item == Items.SHULKER_BOX ||
            item == Items.WHITE_SHULKER_BOX ||
            item == Items.ORANGE_SHULKER_BOX ||
            item == Items.MAGENTA_SHULKER_BOX ||
            item == Items.LIGHT_BLUE_SHULKER_BOX ||
            item == Items.YELLOW_SHULKER_BOX ||
            item == Items.LIME_SHULKER_BOX ||
            item == Items.PINK_SHULKER_BOX ||
            item == Items.GRAY_SHULKER_BOX ||
            item == Items.LIGHT_GRAY_SHULKER_BOX ||
            item == Items.CYAN_SHULKER_BOX ||
            item == Items.PURPLE_SHULKER_BOX ||
            item == Items.BLUE_SHULKER_BOX ||
            item == Items.BROWN_SHULKER_BOX ||
            item == Items.GREEN_SHULKER_BOX ||
            item == Items.RED_SHULKER_BOX ||
            item == Items.BLACK_SHULKER_BOX;
    }
    private boolean isContainerItem(Item item) {
        return item == Items.CHEST ||
            item == Items.TRAPPED_CHEST ||
            item == Items.BARREL ||
            item == Items.ENDER_CHEST;
    }
    private BlockPos findNearbyEnderChest() {
        int searchRadius = 32;
        BlockPos playerPos = mc.player.getBlockPos();
        BlockPos closestEnderChest = null;
        double closestDistance = Double.MAX_VALUE;
        for (int x = -searchRadius; x <= searchRadius; x++) {
            for (int y = -5; y <= 5; y++) {
                for (int z = -searchRadius; z <= searchRadius; z++) {
                    BlockPos pos = playerPos.add(x, y, z);
                    double dist = playerPos.getSquaredDistance(pos);
                    if (dist > searchRadius * searchRadius) continue;
                    if (!mc.world.getChunkManager().isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4)) continue;
                    Block block = mc.world.getBlockState(pos).getBlock();
                    if (block instanceof EnderChestBlock) {
                        if (dist < closestDistance) {
                            closestDistance = dist;
                            closestEnderChest = pos;
                        }
                    }
                }
            }
        }
        if (closestEnderChest != null) {
            info("Found enderchest nearby");
        }
        return closestEnderChest;
    }
    private BlockPos findSuitablePlacePos() {
        BlockPos playerPos = mc.player.getBlockPos();
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                BlockPos pos = playerPos.add(x, 0, z);
                if (mc.world.getBlockState(pos).isAir() &&
                    mc.world.getBlockState(pos.down()).isSolidBlock(mc.world, pos.down())) {
                    return pos;
                }
            }
        }
        return null;
    }
    private void placeEnderChest(BlockPos pos, int slot) {
        InvUtils.swap(slot, false);
        BlockHitResult hitResult = new BlockHitResult(
            Vec3d.ofCenter(pos),
            Direction.UP,
            pos.down(),
            false
        );
        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hitResult);
        enderChestPos = pos;
        currentState = ProcessState.OPENING_ENDERCHEST;
        stateTimer = openDelay.get();
    }
    private String generateRandomString(int length) {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        StringBuilder result = new StringBuilder();
        java.util.concurrent.ThreadLocalRandom random = java.util.concurrent.ThreadLocalRandom.current();
        for (int i = 0; i < length; i++) {
            result.append(chars.charAt(random.nextInt(chars.length())));
        }
        return result.toString();
    }
    private void handleIdleState() {
        if (stateTimer <= 0) {
            if (hasValidAreas()) {
                info("Rechecking location...");
                currentState = ProcessState.CHECKING_LOCATION;
            } else {
                stateTimer = 100;
            }
        }
    }
    private void handleManualMoving() {
        if (stateTimer > 0) {
            stateTimer--;
            return;
        }
        mc.options.forwardKey.setPressed(false);
        mc.options.sneakKey.setPressed(false);
        if (currentContainer != null) {
            Vec3d eyePos = mc.player.getEyePos();
            double distance = eyePos.distanceTo(Vec3d.ofCenter(currentContainer.pos));
            if (distance <= 3.5) {
                currentState = ProcessState.OPENING_CONTAINER;
                stateTimer = 5;
            } else {
                manualMoveToContainer(currentContainer);
            }
        } else {
            if (isNearOutputArea()) {
                currentState = ProcessState.OUTPUT_PROCESS;
            } else {
                currentState = ProcessState.INPUT_PROCESS;
            }
        }
    }
    private void manualMoveToContainer(ContainerInfo container) {
        if (container == null) return;
        Vec3d targetPos = Vec3d.ofCenter(container.pos);
        Vec3d eyePos = mc.player.getEyePos();
        double distance = eyePos.distanceTo(targetPos);
        double yaw = Rotations.getYaw(targetPos);
        double pitch = Rotations.getPitch(targetPos);
        mc.player.setYaw((float)yaw);
        mc.player.setPitch((float)pitch);
        if (distance > 3.2) {
            info("Manual move to container, distance from eye: " + String.format("%.1f", distance));
            mc.options.forwardKey.setPressed(true);
            mc.options.sneakKey.setPressed(true);
            currentState = ProcessState.MANUAL_MOVING;
            stateTimer = 20;
        } else {
            mc.options.forwardKey.setPressed(false);
            mc.options.sneakKey.setPressed(false);
            info("Close enough to container (eye distance: " + String.format("%.1f", distance) + "), opening...");
            currentState = ProcessState.OPENING_CONTAINER;
            stateTimer = 5;
        }
    }
    private Direction getOptimalClickFace(BlockPos containerPos, Vec3d eyePos) {
        double heightDiff = containerPos.getY() - eyePos.y;
        if (heightDiff > 2.0) {
            double dx = eyePos.x - (containerPos.getX() + 0.5);
            double dz = eyePos.z - (containerPos.getZ() + 0.5);
            if (Math.abs(dx) > Math.abs(dz)) {
                return dx > 0 ? Direction.WEST : Direction.EAST;
            } else {
                return dz > 0 ? Direction.NORTH : Direction.SOUTH;
            }
        }
        Vec3d containerCenter = Vec3d.ofCenter(containerPos);
        Vec3d toContainer = containerCenter.subtract(eyePos).normalize();
        Direction bestFace = Direction.UP;
        double bestDot = Double.NEGATIVE_INFINITY;
        for (Direction face : Direction.values()) {
            Vec3d faceNormal = Vec3d.of(face.getVector());
            double dot = toContainer.dotProduct(faceNormal);
            if (dot > bestDot) {
                bestDot = dot;
                bestFace = face;
            }
        }
        if (bestFace == Direction.DOWN) {
            bestFace = Direction.UP;
        }
        return bestFace;
    }
    public SelectionMode getSelectionMode() {
        return selectionMode;
    }
    public BlockPos getSelectionPos1() {
        return selectionPos1;
    }
    public boolean isSelecting() {
        return selectionMode != SelectionMode.NONE;
    }
    public ProcessState getCurrentState() {
        return currentState;
    }
    public int getItemsTransferred() {
        return itemsTransferred;
    }
    public int getContainersProcessed() {
        return containersProcessed;
    }
    public boolean hasInputArea() {
        return inputAreaPos1 != null && inputAreaPos2 != null;
    }
    public boolean hasOutputArea() {
        return outputAreaPos1 != null && outputAreaPos2 != null;
    }
    public int getInputContainerCount() {
        return inputContainers.size();
    }
    public int getOutputContainerCount() {
        return outputContainers.size();
    }
    public void clearAreas() {
        inputAreaPos1 = null;
        inputAreaPos2 = null;
        outputAreaPos1 = null;
        outputAreaPos2 = null;
        inputContainers.clear();
        outputContainers.clear();
        selectionMode = SelectionMode.NONE;
        info("All areas cleared");
    }
    public void renderAreas(Render3DEvent event) {
        if (inputAreaPos1 != null && inputAreaPos2 != null) {
            Box inputBox = new Box(
                inputAreaPos1.getX(), inputAreaPos1.getY(), inputAreaPos1.getZ(),
                inputAreaPos2.getX() + 1, inputAreaPos2.getY() + 1, inputAreaPos2.getZ() + 1
            );
            SettingColor inputColor = new SettingColor(0, 255, 0, 50);
            event.renderer.box(inputBox, inputColor, inputColor, ShapeMode.Both, 0);
        }
        if (outputAreaPos1 != null && outputAreaPos2 != null) {
            Box outputBox = new Box(
                outputAreaPos1.getX(), outputAreaPos1.getY(), outputAreaPos1.getZ(),
                outputAreaPos2.getX() + 1, outputAreaPos2.getY() + 1, outputAreaPos2.getZ() + 1
            );
            SettingColor outputColor = new SettingColor(0, 0, 255, 50);
            event.renderer.box(outputBox, outputColor, outputColor, ShapeMode.Both, 0);
        }
    }
    private boolean validateChestTarget(BlockPos chestPos) {
        HitResult hitResult = mc.crosshairTarget;
        if (hitResult == null || hitResult.getType() != HitResult.Type.BLOCK) {
            return false;
        }
        BlockHitResult blockHit = (BlockHitResult) hitResult;
        BlockPos targetPos = blockHit.getBlockPos();
        return targetPos.equals(chestPos);
    }
    private void performImprovedInteraction(Vec3d containerCenter) {
        Vec3d eyePos = mc.player.getEyePos();
        Vec3d pos = mc.player.getEntityPos();
        mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.Full(
            pos.x, pos.y, pos.z,
            mc.player.getYaw(), mc.player.getPitch(),
            mc.player.isOnGround(), mc.player.horizontalCollision
        ));
        Direction optimalFace = calculateOptimalFace(currentContainer.pos, eyePos);
        boolean success = false;
        success = tryDirectInteraction(currentContainer.pos, optimalFace, eyePos);
        if (!success && containerOpenFailures >= 2) {
            success = tryAllFacesInteraction(currentContainer.pos, eyePos);
        }
        if (!success && containerOpenFailures >= 4) {
            performPacketSpamInteraction(currentContainer.pos, eyePos);
        }
    }
    private boolean tryDirectInteraction(BlockPos pos, Direction face, Vec3d eyePos) {
        Vec3d hitVec = calculatePreciseHitVector(pos, face, eyePos);
        double yaw = Rotations.getYaw(hitVec);
        double pitch = Rotations.getPitch(hitVec);
        mc.player.setYaw((float)yaw);
        mc.player.setPitch((float)pitch);
        mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(
            (float)yaw, (float)pitch, mc.player.isOnGround(), mc.player.horizontalCollision
        ));
        BlockHitResult hitResult = new BlockHitResult(
            hitVec, face, pos, false
        );
        ActionResult result = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hitResult);
        if (result != ActionResult.SUCCESS && result != ActionResult.CONSUME) {
            result = mc.interactionManager.interactBlock(mc.player, Hand.OFF_HAND, hitResult);
        }
        return result == ActionResult.SUCCESS || result == ActionResult.CONSUME;
    }
    private boolean tryAllFacesInteraction(BlockPos pos, Vec3d eyePos) {
        Direction[] facesToTry = {
            Direction.UP, Direction.NORTH, Direction.SOUTH,
            Direction.EAST, Direction.WEST, Direction.DOWN
        };
        for (Direction face : facesToTry) {
            if (tryDirectInteraction(pos, face, eyePos)) {
                if (debugMode.get()) {
                    info("Successfully interacted using face: " + face);
                }
                return true;
            }
        }
        return false;
    }
    private void performPacketSpamInteraction(BlockPos pos, Vec3d eyePos) {
        Vec3d[] hitPositions = {
            Vec3d.ofCenter(pos),
            Vec3d.ofCenter(pos).add(0, 0.25, 0),
            Vec3d.ofCenter(pos).add(0.25, 0, 0),
            Vec3d.ofCenter(pos).add(0, 0, 0.25),
            Vec3d.ofCenter(pos).add(-0.25, 0, 0),
            Vec3d.ofCenter(pos).add(0, 0, -0.25)
        };
        for (int i = 0; i < 3; i++) {
            Vec3d hitPos = hitPositions[i % hitPositions.length];
            Direction face = i == 0 ? Direction.UP : (i == 1 ? Direction.NORTH : Direction.EAST);
            BlockHitResult hitResult = new BlockHitResult(
                hitPos, face, pos, false
            );
            ActionResult result = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hitResult);
            if (result != ActionResult.SUCCESS && result != ActionResult.CONSUME) {
                mc.interactionManager.interactBlock(mc.player, Hand.OFF_HAND, hitResult);
            }
        }
        if (debugMode.get()) {
            info("Sent packet spam interaction for stubborn chest");
        }
    }
    private Direction calculateOptimalFace(BlockPos pos, Vec3d eyePos) {
        double dx = pos.getX() + 0.5 - eyePos.x;
        double dy = pos.getY() + 0.5 - eyePos.y;
        double dz = pos.getZ() + 0.5 - eyePos.z;
        double absDx = Math.abs(dx);
        double absDy = Math.abs(dy);
        double absDz = Math.abs(dz);
        if (absDy > absDx && absDy > absDz) {
            return dy > 0 ? Direction.DOWN : Direction.UP;
        } else if (absDx > absDz) {
            return dx > 0 ? Direction.WEST : Direction.EAST;
        } else {
            return dz > 0 ? Direction.NORTH : Direction.SOUTH;
        }
    }
    private void performSmartRepositioning(BlockPos chestPos, double currentDistance) {
        Vec3d chestCenter = Vec3d.ofCenter(chestPos);
        Vec3d playerPos = mc.player.getEntityPos();
        double optimalDistance = 2.8;
        Vec3d direction = playerPos.subtract(chestCenter).normalize();
        Vec3d optimalPos = chestCenter.add(direction.multiply(optimalDistance));
        double dx = optimalPos.x - playerPos.x;
        double dz = optimalPos.z - playerPos.z;
        if (Math.abs(dx) > Math.abs(dz)) {
            if (dx > 0.2) {
                mc.options.rightKey.setPressed(true);
                info("Repositioning: moving right");
            } else if (dx < -0.2) {
                mc.options.leftKey.setPressed(true);
                info("Repositioning: moving left");
            }
        } else {
            if (dz > 0.2) {
                mc.options.backKey.setPressed(true);
                info("Repositioning: moving back");
            } else if (dz < -0.2) {
                mc.options.forwardKey.setPressed(true);
                info("Repositioning: moving forward");
            }
        }
        if (currentDistance > optimalDistance + 0.5) {
            mc.options.forwardKey.setPressed(true);
        } else if (currentDistance < optimalDistance - 0.5) {
            mc.options.backKey.setPressed(true);
        }
        if (debugMode.get()) {
            info(String.format("Smart repositioning: current=%.1f, optimal=%.1f", currentDistance, optimalDistance));
        }
    }
    private void improvedManualMovement(ContainerInfo container) {
        if (container == null) return;
        Vec3d targetPos = Vec3d.ofCenter(container.pos);
        Vec3d playerPos = mc.player.getEntityPos();
        Vec3d eyePos = mc.player.getEyePos();
        double distance = eyePos.distanceTo(targetPos);
        double horizontalDistance = Math.sqrt(
            Math.pow(targetPos.x - playerPos.x, 2) +
                Math.pow(targetPos.z - playerPos.z, 2)
        );
        stopAllMovement();
        double yaw = Rotations.getYaw(targetPos);
        double pitch = Rotations.getPitch(targetPos);
        mc.player.setYaw((float)yaw);
        mc.player.setPitch((float)pitch);
        if (distance > 3.2) {
            if (horizontalDistance > 10) {
                mc.options.forwardKey.setPressed(true);
                mc.options.sprintKey.setPressed(true);
                info("Sprinting to container (" + String.format("%.1f", distance) + "m)");
            } else if (horizontalDistance > 4) {
                mc.options.forwardKey.setPressed(true);
                mc.options.sprintKey.setPressed(false);
                info("Walking to container (" + String.format("%.1f", distance) + "m)");
            } else {
                mc.options.forwardKey.setPressed(true);
                mc.options.sneakKey.setPressed(true);
                info("Sneaking to container (" + String.format("%.1f", distance) + "m)");
            }
            if (isBlockedAhead()) {
                mc.options.jumpKey.setPressed(true);
                jumpTimer = 5;
            } else if (jumpTimer > 0) {
                jumpTimer--;
                if (jumpTimer == 0) {
                    mc.options.jumpKey.setPressed(false);
                }
            }
            currentState = ProcessState.MANUAL_MOVING;
            stateTimer = 30;
        } else {
            stopAllMovement();
            info("Reached container, opening...");
            currentState = ProcessState.OPENING_CONTAINER;
            stateTimer = 5;
        }
    }
    private void alternativePathToContainer(ContainerInfo container) {
        if (container == null) return;
        BlockPos targetPos = container.pos;
        BlockPos[] alternatives = {
            targetPos.north(2),
            targetPos.south(2),
            targetPos.east(2),
            targetPos.west(2),
            targetPos.north(2).up(),
            targetPos.south(2).up(),
            targetPos.east(2).up(),
            targetPos.west(2).up()
        };
        BlockPos bestAlternative = null;
        double bestDistance = Double.MAX_VALUE;
        for (BlockPos alt : alternatives) {
            if (isValidStandingPosition(alt)) {
                double dist = mc.player.getEntityPos().distanceTo(Vec3d.ofCenter(alt));
                if (dist < bestDistance) {
                    bestDistance = dist;
                    bestAlternative = alt;
                }
            }
        }
        if (bestAlternative != null) {
            GoalBlock goal = new GoalBlock(bestAlternative);
            BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess().setGoalAndPath(goal);
            currentState = ProcessState.MOVING_TO_CONTAINER;
            stateTimer = 100;
            info("Using alternative path via " + bestAlternative);
        } else {
            warning("No alternative paths found, using manual movement");
            improvedManualMovement(container);
        }
    }
    private void ensureOffhandHasItem() {
        ItemStack offhandStack = mc.player.getOffHandStack();
        ItemStack slot0 = mc.player.getInventory().getStack(0);
        if (!slot0.isEmpty() && slot0.getItem() != Items.ENDER_PEARL) {
            if (offhandStack.isEmpty()) {
                mc.interactionManager.clickSlot(
                    mc.player.currentScreenHandler.syncId,
                    36,
                    0,
                    SlotActionType.PICKUP,
                    mc.player
                );
                mc.interactionManager.clickSlot(
                    mc.player.currentScreenHandler.syncId,
                    45,
                    0,
                    SlotActionType.PICKUP,
                    mc.player
                );
                info("Moved " + slot0.getItem().getName().getString() + " from slot 0 to offhand");
            } else {
                for (int i = 9; i < 36; i++) {
                    if (mc.player.getInventory().getStack(i).isEmpty()) {
                        mc.interactionManager.clickSlot(
                            mc.player.currentScreenHandler.syncId,
                            36,
                            0,
                            SlotActionType.PICKUP,
                            mc.player
                        );
                        mc.interactionManager.clickSlot(
                            mc.player.currentScreenHandler.syncId,
                            i,
                            0,
                            SlotActionType.PICKUP,
                            mc.player
                        );
                        info("Moved slot 0 to inventory to free space for pearl");
                        break;
                    }
                }
            }
            return;
        }
        if (offhandStack.isEmpty()) {
            for (int i = 1; i < 9; i++) {
                ItemStack stack = mc.player.getInventory().getStack(i);
                if (!stack.isEmpty() && stack.getItem() != Items.ENDER_PEARL) {
                    mc.interactionManager.clickSlot(
                        mc.player.currentScreenHandler.syncId,
                        36 + i,
                        0,
                        SlotActionType.PICKUP,
                        mc.player
                    );
                    mc.interactionManager.clickSlot(
                        mc.player.currentScreenHandler.syncId,
                        45,
                        0,
                        SlotActionType.PICKUP,
                        mc.player
                    );
                    info("Moved " + stack.getItem().getName().getString() + " to offhand");
                    return;
                }
            }
            for (int i = 9; i < 36; i++) {
                ItemStack stack = mc.player.getInventory().getStack(i);
                if (!stack.isEmpty() && stack.getItem() != Items.ENDER_PEARL && !isShulkerBox(stack.getItem())) {
                    mc.interactionManager.clickSlot(
                        mc.player.currentScreenHandler.syncId,
                        i,
                        0,
                        SlotActionType.PICKUP,
                        mc.player
                    );
                    mc.interactionManager.clickSlot(
                        mc.player.currentScreenHandler.syncId,
                        45,
                        0,
                        SlotActionType.PICKUP,
                        mc.player
                    );
                    info("Moved item from inventory to offhand");
                    return;
                }
            }
        }
    }
    private void performStuckRecovery() {
        BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().cancelEverything();
        stopAllMovement();
        stuckRecoveryAttempts++;
        if (stuckRecoveryAttempts >= 3) {
            warning("Failed to recover from stuck state, skipping container");
            if (currentContainer != null) {
                currentContainer.isEmpty = true;
                currentContainer = null;
            }
            currentState = isNearOutputArea() ? ProcessState.OUTPUT_PROCESS : ProcessState.INPUT_PROCESS;
            stuckRecoveryAttempts = 0;
            stuckCounter = 0;
            lastPlayerPos = null;
            return;
        }
        switch (stuckRecoveryAttempts) {
            case 1 -> {
                info("Stuck recovery: jumping backward");
                mc.options.jumpKey.setPressed(true);
                mc.options.backKey.setPressed(true);
                currentState = ProcessState.MANUAL_MOVING;
                stateTimer = 20;
            }
            case 2 -> {
                info("Stuck recovery: strafing");
                mc.options.jumpKey.setPressed(true);
                mc.options.leftKey.setPressed(true);
                currentState = ProcessState.MANUAL_MOVING;
                stateTimer = 20;
            }
            default -> {
                info("Stuck recovery: manual movement");
                improvedManualMovement(currentContainer);
            }
        }
        stuckCounter = 0;
    }
    private boolean isBlockedAhead() {
        Vec3d playerPos = mc.player.getEntityPos();
        Vec3d lookVec = mc.player.getRotationVector();
        Vec3d checkPos = playerPos.add(lookVec.multiply(1.0));
        BlockPos blockPos = BlockPos.ofFloored(checkPos);
        BlockPos blockAbove = blockPos.up();
        return !mc.world.getBlockState(blockPos).isAir() ||
            !mc.world.getBlockState(blockAbove).isAir();
    }
    private boolean isValidStandingPosition(BlockPos pos) {
        BlockState groundState = mc.world.getBlockState(pos.down());
        BlockState feetState = mc.world.getBlockState(pos);
        BlockState headState = mc.world.getBlockState(pos.up());
        return groundState.isSolidBlock(mc.world, pos.down()) &&
            feetState.isAir() &&
            headState.isAir();
    }
}