package bep.hax.modules;
import bep.hax.Bep;
import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.containers.WVerticalList;
import meteordevelopment.meteorclient.gui.widgets.containers.WHorizontalList;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.gui.widgets.input.WTextBox;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.misc.ISerializable;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.TrapdoorBlock;
import net.minecraft.block.BlockState;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtElement;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.MathHelper;
import java.util.List;
import java.util.ArrayList;
import baritone.api.BaritoneAPI;
import baritone.api.pathing.goals.GoalBlock;
public class PearlLoader extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgCoordinates = settings.createGroup("Coordinates");
    private final SettingGroup sgWhitelist = settings.createGroup("Whitelist");
    private final SettingGroup sgLoadLocations = settings.createGroup("Load Locations");
    private final Setting<BlockPos> walkPoint1 = sgCoordinates.add(new BlockPosSetting.Builder()
        .name("Walk Point 1")
        .description("First position for anti-AFK walking loop")
        .defaultValue(new BlockPos(0, 64, 0))
        .build()
    );
    private final Setting<BlockPos> walkPoint2 = sgCoordinates.add(new BlockPosSetting.Builder()
        .name("Walk Point 2")
        .description("Second position for anti-AFK walking loop")
        .defaultValue(new BlockPos(10, 64, 0))
        .build()
    );
    private final Setting<Boolean> useWhitelist = sgWhitelist.add(new BoolSetting.Builder()
        .name("Use Whitelist")
        .description("Only accept triggers from whitelisted players")
        .defaultValue(true)
        .build()
    );
    private final Setting<List<String>> whitelistedPlayers = sgWhitelist.add(new StringListSetting.Builder()
        .name("Whitelisted Players")
        .description("Players who can trigger pearl loading")
        .defaultValue(new ArrayList<>())
        .visible(useWhitelist::get)
        .build()
    );
    private final Setting<Double> reachThreshold = sgGeneral.add(new DoubleSetting.Builder()
        .name("Reach Threshold")
        .description("Fallback distance threshold if Baritone check fails")
        .defaultValue(0.5)
        .min(0.1)
        .max(5.0)
        .sliderRange(0.1, 5.0)
        .build()
    );
    private final Setting<Boolean> debugMode = sgGeneral.add(new BoolSetting.Builder()
        .name("Debug Mode")
        .description("Show detailed debug information")
        .defaultValue(false)
        .build()
    );
    private final Setting<Integer> arrivalWaitTicks = sgGeneral.add(new IntSetting.Builder()
        .name("Arrival Wait Ticks")
        .description("Ticks to wait after arriving at position for lag")
        .defaultValue(20)
        .min(0)
        .max(100)
        .sliderRange(0, 100)
        .build()
    );
    private List<LoadLocation> loadLocations = new ArrayList<>();
    public static class LoadLocation implements ISerializable<LoadLocation> {
        public String triggerKeyword = "!pearl";
        public LoadMode mode = LoadMode.TRAPDOOR;
        public BlockPos position = new BlockPos(0, 64, 0);
        public double trapdoorCloseTime = 2.0;
        public double standTime = 1.0;
        public LoadLocation() {}
        public LoadLocation(String keyword, LoadMode mode, BlockPos pos, double closeTime, double standTime) {
            this.triggerKeyword = keyword;
            this.mode = mode;
            this.position = pos;
            this.trapdoorCloseTime = closeTime;
            this.standTime = standTime;
        }
        @Override
        public NbtCompound toTag() {
            NbtCompound tag = new NbtCompound();
            tag.putString("keyword", triggerKeyword);
            tag.putString("mode", mode.name());
            tag.putInt("x", position.getX());
            tag.putInt("y", position.getY());
            tag.putInt("z", position.getZ());
            tag.putDouble("closeTime", trapdoorCloseTime);
            tag.putDouble("standTime", standTime);
            return tag;
        }
        @Override
        public LoadLocation fromTag(NbtCompound tag) {
            triggerKeyword = tag.getString("keyword").orElse("");
            mode = LoadMode.valueOf(tag.getString("mode").orElse("TRAPDOOR"));
            position = new BlockPos(tag.getInt("x").orElse(0), tag.getInt("y").orElse(0), tag.getInt("z").orElse(0));
            trapdoorCloseTime = tag.getDouble("closeTime").orElse(1.0);
            standTime = tag.getDouble("standTime").orElse(0.0);
            return this;
        }
    }
    public enum LoadMode {
        TRAPDOOR("Trapdoor - Interact with trapdoor to load pearl"),
        WALK_TO("Walk To - Walk to position and return");
        private final String description;
        LoadMode(String description) {
            this.description = description;
        }
        @Override
        public String toString() {
            return description;
        }
    }
    private enum State {
        WALKING_TO_POINT1,
        WALKING_TO_POINT2,
        WALKING_TO_TRAPDOOR,
        ARRIVED_AT_TRAPDOOR,
        ROTATING_TO_TRAPDOOR,
        CLOSING_TRAPDOOR,
        WAITING_CLOSED,
        OPENING_TRAPDOOR,
        WALKING_TO_LOAD_POSITION,
        ARRIVED_AT_LOAD_POSITION,
        STANDING_AT_LOAD,
        RETURNING_FROM_LOAD
    }
    private State currentState = State.WALKING_TO_POINT2;
    private boolean isActive = false;
    private boolean pearlLoadTriggered = false;
    private long stateStartTime = 0;
    private long lastInteractionTime = 0;
    private BlockPos currentTarget = null;
    private int rotationTicks = 0;
    private float targetYaw = 0;
    private float targetPitch = 0;
    private boolean trapdoorWasClosed = false;
    private LoadLocation currentLoadLocation = null;
    public PearlLoader() {
        super(Bep.CATEGORY, "PearlLoader", "Anti-AFK loop with pearl loading capability");
    }
    @Override
    public NbtCompound toTag() {
        NbtCompound tag = super.toTag();
        NbtList locationsList = new NbtList();
        for (LoadLocation location : loadLocations) {
            locationsList.add(location.toTag());
        }
        tag.put("loadLocations", locationsList);
        return tag;
    }
    @Override
    public Module fromTag(NbtCompound tag) {
        super.fromTag(tag);
        loadLocations.clear();
        if (tag.contains("loadLocations")) {
            java.util.Optional<NbtList> locationsListOpt = tag.getList("loadLocations");
            if (locationsListOpt.isPresent()) {
                NbtList locationsList = locationsListOpt.get();
                for (int i = 0; i < locationsList.size(); i++) {
                    java.util.Optional<NbtCompound> compoundOpt = locationsList.getCompound(i);
                    if (compoundOpt.isPresent()) {
                        LoadLocation location = new LoadLocation();
                        location.fromTag(compoundOpt.get());
                        loadLocations.add(location);
                    }
                }
            }
        }
        return this;
    }
    @Override
    public void onActivate() {
        isActive = true;
        currentState = State.WALKING_TO_POINT2;
        pearlLoadTriggered = false;
        stateStartTime = System.currentTimeMillis();
        currentTarget = walkPoint2.get();
        startPathing(currentTarget);
        info("Pearl Loader activated - Starting anti-AFK loop");
    }
    @Override
    public void onDeactivate() {
        isActive = false;
        stopPathing();
        info("Pearl Loader deactivated");
    }
    @EventHandler
    private void onReceiveMessage(ReceiveMessageEvent event) {
        if (!isActive || pearlLoadTriggered) return;
        String fullMessage = event.getMessage().getString();
        String messageLower = fullMessage.toLowerCase();
        for (LoadLocation location : loadLocations) {
            String keyword = location.triggerKeyword.toLowerCase();
            if (!messageLower.contains(keyword)) continue;
            if (useWhitelist.get()) {
                String sender = extractSenderName(fullMessage);
                if (sender == null || !isPlayerWhitelisted(sender)) {
                    if (debugMode.get()) {
                        info("Trigger ignored - player not whitelisted: " + sender);
                    }
                    continue;
                }
                info("Pearl load triggered by " + sender + " with keyword: " + location.triggerKeyword);
            } else {
                info("Pearl load triggered by keyword: " + location.triggerKeyword);
            }
            triggerPearlLoad(location);
            return;
        }
    }
    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (!isActive || mc.player == null || mc.world == null) return;
        if (pearlLoadTriggered) {
            handlePearlLoadingSequence();
            return;
        }
        handleWalkingLoop();
    }
    private void handleWalkingLoop() {
        if (currentTarget == null) return;
        if (isPathingDone()) {
            if (currentState == State.WALKING_TO_POINT1) {
                currentState = State.WALKING_TO_POINT2;
                currentTarget = walkPoint2.get();
                if (debugMode.get()) info("Reached Point 1, walking to Point 2");
            } else if (currentState == State.WALKING_TO_POINT2) {
                currentState = State.WALKING_TO_POINT1;
                currentTarget = walkPoint1.get();
                if (debugMode.get()) info("Reached Point 2, walking to Point 1");
            }
            startPathing(currentTarget);
        }
    }
    private void handlePearlLoadingSequence() {
        switch (currentState) {
            case WALKING_TO_TRAPDOOR -> handleWalkingToTrapdoor();
            case ARRIVED_AT_TRAPDOOR -> handleArrivedAtTrapdoor();
            case WALKING_TO_LOAD_POSITION -> handleWalkingToLoadPosition();
            case ARRIVED_AT_LOAD_POSITION -> handleArrivedAtLoadPosition();
            case ROTATING_TO_TRAPDOOR -> handleRotatingToTrapdoor();
            case CLOSING_TRAPDOOR -> handleClosingTrapdoor();
            case WAITING_CLOSED -> handleWaitingClosed();
            case OPENING_TRAPDOOR -> handleOpeningTrapdoor();
            case STANDING_AT_LOAD -> handleStandingAtLoad();
            case RETURNING_FROM_LOAD -> handleReturningFromLoad();
        }
    }
    private void handleWalkingToTrapdoor() {
        BlockPos targetPos = getTrapdoorApproachPosition();
        double distance = getDistanceToTarget(targetPos);
        if (distance <= reachThreshold.get()) {
            stopPathing();
            currentState = State.ARRIVED_AT_TRAPDOOR;
            stateStartTime = System.currentTimeMillis();
            if (debugMode.get()) info("Within threshold of trapdoor approach position, waiting for settle");
        } else if (isPathingDone()) {
            startPathing(targetPos);
            if (debugMode.get()) info("Pathing done but not close enough, restarting pathing to approach position");
        }
    }
    private void handleArrivedAtTrapdoor() {
        long elapsed = System.currentTimeMillis() - stateStartTime;
        long waitTime = arrivalWaitTicks.get() * 50;
        BlockPos targetPos = getTrapdoorApproachPosition();
        double distance = getDistanceToTarget(targetPos);
        if (distance > reachThreshold.get()) {
            currentState = State.WALKING_TO_TRAPDOOR;
            startPathing(targetPos);
            if (debugMode.get()) info("Drifted away during wait, returning to walking");
            return;
        }
        if (elapsed >= waitTime) {
            currentState = State.ROTATING_TO_TRAPDOOR;
            stateStartTime = System.currentTimeMillis();
            rotationTicks = 0;
            if (debugMode.get()) info("Wait after arrival complete, proceeding to rotate");
        }
    }
    private void handleWalkingToLoadPosition() {
        BlockPos target = currentLoadLocation.position;
        double distance = getDistanceToTarget(target);
        if (distance <= reachThreshold.get()) {
            stopPathing();
            currentState = State.ARRIVED_AT_LOAD_POSITION;
            stateStartTime = System.currentTimeMillis();
            if (debugMode.get()) info("Within threshold of load position, waiting for settle");
        } else if (isPathingDone()) {
            startPathing(target);
            if (debugMode.get()) info("Pathing done but not close enough, restarting pathing to load position");
        }
    }
    private void handleArrivedAtLoadPosition() {
        long elapsed = System.currentTimeMillis() - stateStartTime;
        long waitTime = arrivalWaitTicks.get() * 50;
        BlockPos target = currentLoadLocation.position;
        double distance = getDistanceToTarget(target);
        if (distance > reachThreshold.get()) {
            currentState = State.WALKING_TO_LOAD_POSITION;
            startPathing(target);
            if (debugMode.get()) info("Drifted away during wait, returning to walking");
            return;
        }
        if (elapsed >= waitTime) {
            currentState = State.STANDING_AT_LOAD;
            stateStartTime = System.currentTimeMillis();
            if (debugMode.get()) info("Wait after arrival complete, starting stand time");
        }
    }
    private void handleRotatingToTrapdoor() {
        if (rotateToBlock(currentLoadLocation.position)) {
            BlockState state = mc.world.getBlockState(currentLoadLocation.position);
            if (!(state.getBlock() instanceof TrapdoorBlock)) {
                error("No trapdoor found at specified position!");
                resetToLoop();
                return;
            }
            boolean isOpen = state.get(TrapdoorBlock.OPEN);
            if (debugMode.get()) info("Rotation complete, trapdoor is currently " + (isOpen ? "OPEN" : "CLOSED") + ", proceeding to interact");
            currentState = State.CLOSING_TRAPDOOR;
            stateStartTime = System.currentTimeMillis();
        }
    }
    private void handleClosingTrapdoor() {
        double distToTrapdoor = getDistanceToTarget(currentLoadLocation.position);
        if (distToTrapdoor > 5.0) {
            error("Too far from trapdoor to interact safely! Restarting approach.");
            currentState = State.WALKING_TO_TRAPDOOR;
            BlockPos approachPos = getTrapdoorApproachPosition();
            startPathing(approachPos);
            return;
        }
        BlockState state = mc.world.getBlockState(currentLoadLocation.position);
        boolean isOpen = state.get(TrapdoorBlock.OPEN);
        if (isOpen) {
            interactWithTrapdoor();
            trapdoorWasClosed = true;
            if (debugMode.get()) info("Closed trapdoor");
        } else {
            if (debugMode.get()) info("Trapdoor already closed");
            trapdoorWasClosed = false;
        }
        currentState = State.WAITING_CLOSED;
        stateStartTime = System.currentTimeMillis();
    }
    private void handleWaitingClosed() {
        long elapsed = System.currentTimeMillis() - stateStartTime;
        long waitTime = (long)(currentLoadLocation.trapdoorCloseTime * 1000);
        if (elapsed > 500) {
            BlockState state = mc.world.getBlockState(currentLoadLocation.position);
            boolean isOpen = state.get(TrapdoorBlock.OPEN);
            if (isOpen) {
                if (debugMode.get()) info("Trapdoor not closed properly, retrying close");
                currentState = State.CLOSING_TRAPDOOR;
                stateStartTime = System.currentTimeMillis();
                return;
            }
        }
        if (debugMode.get() && elapsed % 1000 < 50) {
            info(String.format("Waiting with trapdoor closed: %.1f / %.1f seconds",
                elapsed / 1000.0, currentLoadLocation.trapdoorCloseTime));
        }
        if (elapsed >= waitTime) {
            currentState = State.OPENING_TRAPDOOR;
            stateStartTime = System.currentTimeMillis();
            if (debugMode.get()) info("Wait complete after " + (elapsed/1000.0) + " seconds, now opening trapdoor");
        }
    }
    private void handleOpeningTrapdoor() {
        double distToTrapdoor = getDistanceToTarget(currentLoadLocation.position);
        if (distToTrapdoor > 5.0) {
            error("Too far from trapdoor to interact safely! Restarting approach.");
            currentState = State.WALKING_TO_TRAPDOOR;
            BlockPos approachPos = getTrapdoorApproachPosition();
            startPathing(approachPos);
            return;
        }
        BlockState state = mc.world.getBlockState(currentLoadLocation.position);
        boolean isOpen = state.get(TrapdoorBlock.OPEN);
        if (!isOpen) {
            interactWithTrapdoor();
            if (debugMode.get()) info("Opened trapdoor");
        } else {
            if (debugMode.get()) info("Trapdoor already open");
        }
        info("Pearl loading complete");
        resetToLoop();
    }
    private void handleStandingAtLoad() {
        long elapsed = System.currentTimeMillis() - stateStartTime;
        long waitTime = (long)(currentLoadLocation.standTime * 1000);
        if (elapsed >= waitTime) {
            currentState = State.RETURNING_FROM_LOAD;
            currentTarget = walkPoint1.get();
            startPathing(currentTarget);
            if (debugMode.get()) info("Stand time complete, returning to loop");
        }
    }
    private void handleReturningFromLoad() {
        if (isPathingDone() || getDistanceToTarget(currentTarget) <= reachThreshold.get()) {
            info("Pearl loading complete");
            resetToLoop();
        }
    }
    private void triggerPearlLoad(LoadLocation location) {
        if (pearlLoadTriggered) return;
        pearlLoadTriggered = true;
        currentLoadLocation = location;
        stopPathing();
        if (location.mode == LoadMode.TRAPDOOR) {
            currentState = State.WALKING_TO_TRAPDOOR;
            BlockPos approachPos = getTrapdoorApproachPosition();
            startPathing(approachPos);
            if (debugMode.get()) info("Starting trapdoor pearl load sequence for: " + location.triggerKeyword);
        } else {
            currentState = State.WALKING_TO_LOAD_POSITION;
            startPathing(location.position);
            if (debugMode.get()) info("Starting walk-to pearl load sequence for: " + location.triggerKeyword);
        }
        stateStartTime = System.currentTimeMillis();
    }
    private void resetToLoop() {
        pearlLoadTriggered = false;
        currentLoadLocation = null;
        currentState = State.WALKING_TO_POINT1;
        currentTarget = walkPoint1.get();
        startPathing(currentTarget);
        stateStartTime = System.currentTimeMillis();
    }
    private boolean rotateToBlock(BlockPos pos) {
        Vec3d target = Vec3d.ofCenter(pos);
        Vec3d playerEyes = mc.player.getEyePos();
        Vec3d lookVec = target.subtract(playerEyes);
        double dx = lookVec.x;
        double dy = lookVec.y;
        double dz = lookVec.z;
        double distance = Math.sqrt(dx * dx + dz * dz);
        targetYaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        targetPitch = (float) Math.toDegrees(Math.atan2(-dy, distance));
        targetPitch = MathHelper.clamp(targetPitch, -90.0f, 90.0f);
        rotationTicks++;
        float yawDiff = wrapDegrees(targetYaw - mc.player.getYaw());
        float pitchDiff = targetPitch - mc.player.getPitch();
        float rotSpeed = 0.1f;
        mc.player.setYaw(mc.player.getYaw() + yawDiff * rotSpeed);
        mc.player.setPitch(mc.player.getPitch() + pitchDiff * rotSpeed);
        return Math.abs(yawDiff) < 2.0f && Math.abs(pitchDiff) < 2.0f || rotationTicks > 50;
    }
    private void interactWithTrapdoor() {
        if (System.currentTimeMillis() - lastInteractionTime < 500) return;
        Vec3d hitVec = Vec3d.ofCenter(currentLoadLocation.position);
        Direction hitSide = getClosestSide(currentLoadLocation.position);
        BlockHitResult hitResult = new BlockHitResult(
            hitVec,
            hitSide,
            currentLoadLocation.position,
            false
        );
        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hitResult);
        mc.player.swingHand(Hand.MAIN_HAND);
        lastInteractionTime = System.currentTimeMillis();
    }
    private BlockPos getTrapdoorApproachPosition() {
        BlockPos trapPos = currentLoadLocation.position;
        Direction[] dirs = {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};
        BlockPos bestPos = null;
        double minDist = Double.MAX_VALUE;
        for (Direction dir : dirs) {
            BlockPos checkPos = trapPos.offset(dir);
            BlockState state = mc.world.getBlockState(checkPos);
            BlockState below = mc.world.getBlockState(checkPos.down());
            if (state.isAir() && below.isSolidBlock(mc.world, checkPos.down())) {
                double dist = mc.player.getEntityPos().distanceTo(Vec3d.ofCenter(checkPos));
                if (dist < minDist) {
                    minDist = dist;
                    bestPos = checkPos;
                }
            }
        }
        return bestPos != null ? bestPos : trapPos.north();
    }
    private Direction getClosestSide(BlockPos pos) {
        Vec3d playerPos = mc.player.getEntityPos();
        Vec3d blockCenter = Vec3d.ofCenter(pos);
        Vec3d diff = playerPos.subtract(blockCenter);
        if (Math.abs(diff.x) > Math.abs(diff.z)) {
            return diff.x > 0 ? Direction.EAST : Direction.WEST;
        } else {
            return diff.z > 0 ? Direction.SOUTH : Direction.NORTH;
        }
    }
    private double getDistanceToTarget(BlockPos target) {
        if (mc.player == null || target == null) return Double.MAX_VALUE;
        return mc.player.getEntityPos().distanceTo(Vec3d.ofCenter(target));
    }
    private void startPathing(BlockPos target) {
        if (target == null) return;
        try {
            Class.forName("baritone.api.BaritoneAPI");
            BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess().setGoalAndPath(new GoalBlock(target));
        } catch (ClassNotFoundException e) {
            error("Baritone not available!");
        }
    }
    private void stopPathing() {
        try {
            Class.forName("baritone.api.BaritoneAPI");
            BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().cancelEverything();
        } catch (ClassNotFoundException ignored) {}
    }
    private boolean isPathingDone() {
        try {
            return !BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().isPathing();
        } catch (Exception e) {
            return false;
        }
    }
    private String extractSenderName(String message) {
        if (message.contains(" whispers: ")) {
            String beforeWhispers = message.substring(0, message.indexOf(" whispers: "));
            beforeWhispers = beforeWhispers.replaceAll("§[0-9a-fk-or]", "");
            String[] parts = beforeWhispers.split(" ");
            if (parts.length >= 1) {
                String name = parts[parts.length - 1].trim();
                if (debugMode.get()) info("Extracted whisper sender: " + name);
                return name;
            }
        }
        else if (message.contains(": ")) {
            String beforeColon = message.substring(0, message.indexOf(": "));
            beforeColon = beforeColon.replaceAll("§[0-9a-fk-or]", "");
            if (beforeColon.contains("<") && beforeColon.contains(">")) {
                int start = beforeColon.lastIndexOf("<");
                int end = beforeColon.lastIndexOf(">");
                if (start < end) {
                    String name = beforeColon.substring(start + 1, end).trim();
                    if (debugMode.get()) info("Extracted public sender: " + name);
                    return name;
                }
            }
            String[] parts = beforeColon.split(" ");
            if (parts.length > 0) {
                String name = parts[parts.length - 1].replaceAll("[<>\\[\\]]", "").trim();
                if (debugMode.get()) info("Extracted sender: " + name);
                return name;
            }
        }
        if (debugMode.get()) warning("Could not extract sender from message: " + message);
        return null;
    }
    private boolean isPlayerWhitelisted(String playerName) {
        if (playerName == null) return false;
        for (String whitelisted : whitelistedPlayers.get()) {
            if (whitelisted.equalsIgnoreCase(playerName)) {
                return true;
            }
        }
        return false;
    }
    private float wrapDegrees(float degrees) {
        degrees = degrees % 360.0f;
        if (degrees >= 180.0f) degrees -= 360.0f;
        if (degrees < -180.0f) degrees += 360.0f;
        return degrees;
    }
    @Override
    public WWidget getWidget(GuiTheme theme) {
        WVerticalList mainList = theme.verticalList();
        WButton addButton = mainList.add(theme.button("Add Load Location")).widget();
        addButton.action = () -> {
            loadLocations.add(new LoadLocation("!pearl" + (loadLocations.size() + 1), LoadMode.TRAPDOOR, new BlockPos(0, 64, 0), 2.0, 1.0));
            info("Load location added. Close and reopen settings to see changes.");
        };
        for (int i = 0; i < loadLocations.size(); i++) {
            final int index = i;
            LoadLocation location = loadLocations.get(i);
            mainList.add(theme.horizontalSeparator()).expandX();
            WHorizontalList headerList = mainList.add(theme.horizontalList()).expandX().widget();
            headerList.add(theme.label("Location " + (i + 1) + ":")).expandX();
            WButton removeButton = headerList.add(theme.button("-")).widget();
            removeButton.action = () -> {
                if (loadLocations.remove(location)) {
                    info("Load location removed. Close and reopen settings to see changes.");
                } else {
                    error("Failed to remove load location. Please close and reopen settings.");
                }
            };
            WButton triggerButton = headerList.add(theme.button("Trigger")).widget();
            triggerButton.action = () -> {
                if (!pearlLoadTriggered && isActive) {
                    info("Manually triggering pearl load: " + location.triggerKeyword);
                    triggerPearlLoad(location);
                }
            };
            WTextBox keywordBox = mainList.add(theme.textBox(location.triggerKeyword)).expandX().widget();
            keywordBox.action = () -> {
                location.triggerKeyword = keywordBox.get();
            };
            mainList.add(theme.label("Position: " + location.position.toShortString()));
            WHorizontalList posButtons = mainList.add(theme.horizontalList()).expandX().widget();
            WButton setPosButton = posButtons.add(theme.button("Set to Player Pos")).widget();
            setPosButton.action = () -> {
                if (mc.player != null) {
                    location.position = mc.player.getBlockPos();
                }
            };
            mainList.add(theme.label("Mode: " + location.mode.toString()));
            WHorizontalList modeButtons = mainList.add(theme.horizontalList()).expandX().widget();
            WButton trapdoorButton = modeButtons.add(theme.button("Trapdoor")).widget();
            trapdoorButton.action = () -> {
                location.mode = LoadMode.TRAPDOOR;
            };
            WButton walkToButton = modeButtons.add(theme.button("Walk To")).widget();
            walkToButton.action = () -> {
                location.mode = LoadMode.WALK_TO;
            };
            if (location.mode == LoadMode.TRAPDOOR) {
                mainList.add(theme.label("Close Time: " + location.trapdoorCloseTime + "s"));
            } else {
                mainList.add(theme.label("Stand Time: " + location.standTime + "s"));
            }
        }
        mainList.add(theme.horizontalSeparator()).expandX();
        WButton testLoop = mainList.add(theme.button("Test Walking Loop")).widget();
        testLoop.action = () -> {
            if (isActive) {
                info("Testing walking loop");
                resetToLoop();
            }
        };
        return mainList;
    }
}