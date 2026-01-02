package com.osmb.script.oneclick50fm;

import com.osmb.api.ScriptCore;
import com.osmb.api.item.ItemGroupResult;
import com.osmb.api.item.ItemID;
import com.osmb.api.item.ItemSearchResult;
import com.osmb.api.input.MenuEntry;
import com.osmb.api.input.MenuHook;
import com.osmb.api.location.position.types.WorldPosition;
import com.osmb.api.location.position.types.LocalPosition;
import com.osmb.api.scene.RSObject;
import com.osmb.api.script.Script;
import com.osmb.api.script.ScriptDefinition;
import com.osmb.api.script.SkillCategory;
import com.osmb.api.shape.Polygon;
import com.osmb.api.ui.component.ComponentSearchResult;
import com.osmb.api.ui.component.minimap.xpcounter.XPDropsComponent;
import com.osmb.api.ui.component.tabs.SettingsTabComponent;
import com.osmb.api.ui.component.chatbox.ChatboxComponent;
import com.osmb.api.ui.component.tabs.skill.SkillType;
import com.osmb.api.utils.RandomUtils;
import com.osmb.api.visual.PixelAnalyzer;
import com.osmb.api.visual.color.ColorModel;
import com.osmb.api.visual.color.tolerance.impl.SingleThresholdComparator;
import com.osmb.api.visual.drawing.Canvas;
import com.osmb.api.visual.image.SearchableImage;
import com.osmb.api.walker.WalkConfig;
import com.osmb.script.oneclick50fm.data.Tree;
import com.osmb.api.utils.timing.Timer;
import com.osmb.api.utils.timing.Stopwatch;
import com.osmb.api.utils.UIResultList;
import com.osmb.api.ui.chatbox.dialogue.DialogueType;
import com.osmb.api.ui.chatbox.ChatboxFilterTab;
import com.osmb.api.profile.WorldProvider;
import com.osmb.api.profile.ProfileManager;
import com.osmb.api.trackers.experience.XPTracker;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.regex.Pattern;

import static com.osmb.api.utils.RandomUtils.uniformRandom;
import static com.osmb.script.oneclick50fm.data.AreaManager.BONFIRE_AREA;

@ScriptDefinition(name = "One Click 50FM", description = "1-50fm with one click", version = 1.0, author = "Jose", skillCategory = SkillCategory.FIREMAKING)
public class OneClick50Fm extends Script {

    static final long BLACKLIST_TIMEOUT = TimeUnit.SECONDS.toMillis(10);
    public static final int[] LOGS = new int[]{ItemID.LOGS, ItemID.OAK_LOGS, ItemID.WILLOW_LOGS,};
    private static final Map<WorldPosition, Long> TREE_BLACKLIST = new HashMap<>();
    private static final Set<Integer> ITEM_IDS_TO_NOT_DEPOSIT = Set.of(ItemID.TINDERBOX, ItemID.BRONZE_AXE, ItemID.IRON_AXE, ItemID.STEEL_AXE, ItemID.BLACK_AXE, ItemID.MITHRIL_AXE, ItemID.ADAMANT_AXE,
            ItemID.RUNE_AXE, ItemID.DRAGON_AXE, ItemID.DRAGON_AXE_OR, ItemID.DRAGON_AXE_OR_30352, ItemID.CRYSTAL_AXE_23862, ItemID.INFERNAL_AXE, ItemID.INFERNAL_AXE_OR, ItemID.INFERNAL_AXE_OR_30347,
            ItemID.BRONZE_FELLING_AXE, ItemID.IRON_FELLING_AXE, ItemID.STEEL_FELLING_AXE, ItemID.BLACK_FELLING_AXE, ItemID.MITHRIL_FELLING_AXE, ItemID.ADAMANT_FELLING_AXE, ItemID.RUNE_FELLING_AXE, ItemID.DRAGON_FELLING_AXE, ItemID.CRYSTAL_FELLING_AXE);

    private static final List<String> PREVIOUS_CHATBOX_LINES = new ArrayList<>();
    private static final Set<Integer> ALWAYS_KEEP = new HashSet<>();
    static {
        for (int id : LOGS) ALWAYS_KEEP.add(id);
        ALWAYS_KEEP.add(ItemID.TINDERBOX);
        ALWAYS_KEEP.addAll(ITEM_IDS_TO_NOT_DEPOSIT);
    }

    private static final Pattern USE_LOG_TO_FIRE =
            Pattern.compile("^use\\s+(.+?)\\s*->\\s*(fire|forester's campfire)$",
                    Pattern.CASE_INSENSITIVE);
    private final com.osmb.api.location.area.Area bonfireArea;

    private enum Phase { CHOP, BURN }
    private Phase phase = Phase.CHOP;
    private long bonfireSetAtMs = 0L;
    private long bonfireSearchStart = 0L;

    private WorldPosition lastOffscreenTarget = null;
    private double lastTargetDistance = Double.MAX_VALUE;
    private int sameTargetTries = 0;
    private final Stopwatch offscreenStuckTick = new Stopwatch();
    private Tree selectedTree;

    private final SkillTracker wcTracker;
    private final SkillTracker fmTracker;

    private final Map<String, com.osmb.api.visual.image.Image> paintImages = new HashMap<>();
    private boolean initialLevelsChecked = false;
    private int cachedWcLevel = 0;
    private int cachedFmLevel = 0;

    private final long startTime = System.currentTimeMillis();

    private Task task;
    private WorldPosition bonfirePosition;
    private boolean forceNewPosition = false;
    private ItemGroupResult inventorySnapshot;
    private int tries = 0;
    private int burnRetryCount = 0;
    private int amountChangeTimeout = 8000;
    private boolean setZoom = false;
    private boolean firstBack = false;

    private final String scriptVersion = "1.0";

    public OneClick50Fm(Object scriptCore) {
        super(scriptCore);
        bonfireArea = BONFIRE_AREA;

        this.wcTracker = new SkillTracker((ScriptCore) scriptCore, 214);
        this.fmTracker = new SkillTracker((ScriptCore) scriptCore, 213);

    }

    @Override
    public void onStart() {

        if (!checkForUpdates()) {
            log("SYSTEM", "Outdated version.");
            stop();
            return;
        }
    }

    @Override
    public void onNewFrame() {
        if (wcTracker != null) wcTracker.checkXP();
        if (fmTracker != null) fmTracker.checkXP();
    }

    @Override
    public int poll() {
        WorldPosition worldPosition  = getWorldPosition();
        if (worldPosition == null) {
            log(OneClick50Fm.class, "Position is null");
            return 0;
        }

        if (wcTracker != null){
            wcTracker.ensureCounterActive();
    }

    if (bonfirePosition != null) {
        Polygon p = getSceneProjector().getTilePoly(bonfirePosition, true);
        if (p == null || !getWidgetManager().insideGameScreen(p, java.util.Collections.emptyList())) {
            bonfirePosition = null;
            getScreen().removeCanvasDrawable("highlightFireTile");
        }
    }

        task = decideTask(worldPosition);
            if (task == null) {
                return 0;
        }
        executeTask(task);
        return 0;
    }

    private Task decideTask(WorldPosition worldPosition) {
        if (!setZoom) return Task.SET_ZOOM;
        if (!initialLevelsChecked) return Task.CHECK_INIT_LEVELS;
        if (isDialogueVisible()) return Task.HANDLE_DIALOGUE;

        inventorySnapshot = getWidgetManager().getInventory().search(getMonitoredIds());
        if (inventorySnapshot == null) return null;

        if (dropUnwantedIfFull()) {
            inventorySnapshot = getWidgetManager().getInventory().search(getMonitoredIds());
            if (inventorySnapshot == null) return null;
        }

        boolean anyLogs = hasAnyLogsInInventory();
        boolean isFull  = inventorySnapshot.isFull();

        if (!hasTinderbox() && anyLogs) {
            log(getClass().getSimpleName(), "Tinderbox not found, stopping script...");
            stop();
            return null;
        }

        if (phase == Phase.CHOP) {
            if (!isFull) {
                if (inventorySnapshot.getSelectedSlot() != null) {
                    getWidgetManager().getInventory().unSelectItemIfSelected();
                }
                ensureSelectedTree();
                return Task.CHOP_TREES;
            }

            phase = Phase.BURN;
            bonfireSearchStart = System.currentTimeMillis();

        }

        if (phase == Phase.BURN) {

            if (!anyLogs) {
                phase = Phase.CHOP;
                bonfirePosition = null;
                getScreen().removeCanvasDrawable("highlightFireTile");
                if (inventorySnapshot.getSelectedSlot() != null) {
                    getWidgetManager().getInventory().unSelectItemIfSelected();
                }
                ensureSelectedTree();
                return Task.CHOP_TREES;
            }

            if (bonfirePosition != null && !isBonfireAlive()) {
                bonfirePosition = null;
                forceNewPosition = true;
            }

            if (bonfirePosition == null) {
                if (inventorySnapshot.getSelectedSlot() != null && getSelectedLog() == null) {
                    if (!getWidgetManager().getInventory().unSelectItemIfSelected()) return null;
                }

                if (System.currentTimeMillis() - bonfireSearchStart > 5000) {
                    log(getClass().getSimpleName(), "5s timeout trying to reach area. Forcing random position.");
                    forceNewPosition = true;
                    bonfireSearchStart = System.currentTimeMillis(); // Reset timer
                    return Task.MOVE_LIGHT_POSITION;
                }

                if (bonfireArea != null && !bonfireArea.contains(getWorldPosition())) {

                    double distance = bonfireArea.distanceTo(getWorldPosition());

                    if (distance > 2.0) {
                        WorldPosition target = bonfireArea.getClosestPosition(getWorldPosition());
                        if (target == null) target = bonfireArea.getRandomPosition();

                        if (target != null) {
                            WalkConfig.Builder b = new WalkConfig.Builder();
                            b.breakDistance(1).tileRandomisationRadius(1);
                            getWalker().walkTo(target, b.build());
                            return null;
                        }
                    }
                }

                if (forceNewPosition) return Task.MOVE_LIGHT_POSITION;
                return Task.START_BONFIRE;
            }

            debugState();
            return Task.BURN_LOGS;
        }

        // Fallback
        ensureSelectedTree();
        return Task.CHOP_TREES;
    }

    private void chopTrees() {
        inventorySnapshot = getWidgetManager().getInventory().search(getMonitoredIds());
        if (inventorySnapshot != null && inventorySnapshot.getSelectedSlot() != null) {
            getWidgetManager().getInventory().unSelectItemIfSelected();
        }
        if (!ensureSelectedTree()) {
            log(OneClick50Fm.class, "Selected tree not ready yet; will check WC level first.");
            return;
        }
        WorldPosition myPosition = getWorldPosition();
        if (myPosition == null) {
            log(OneClick50Fm.class, "Position is null");
            return;
        }
        if (!selectedTree.getTreeArea().contains(myPosition)) {
            walkToTreeArea();
            return;
        }
        // get the all the tree's matching the selected tree type
        List<RSObject> trees = getObjectManager().getObjects(rsObject -> {
            String name = rsObject.getName();
            return name != null && name.equalsIgnoreCase(selectedTree.getObjectName()) && selectedTree.getTreeArea().contains(rsObject.getWorldPosition());
        });

        if (trees.isEmpty()) {
            log(OneClick50Fm.class, "Walking to tree area...");
            getWalker().walkTo(selectedTree.getTreeArea().getRandomPosition());
            return;
        }
        // get all selected tree types visible
        List<RSObject> visibleTrees = getVisibleTrees(trees, myPosition);
        List<RSObject> activeTrees = getActiveTrees(selectedTree, visibleTrees);
        if (activeTrees.isEmpty()) {
             // If we are outside the area, prioritize entering the area.
            if (!selectedTree.getTreeArea().contains(myPosition)) {
                walkToTreeArea();
                return;
            }
            // No active trees on screen: try going to the nearest off-screen location.
            trees.removeAll(visibleTrees);
            trees.sort(Comparator.comparingDouble(value -> value.distance(myPosition)));

            if (!trees.isEmpty()) {
                int index = 0;
                if (firstBack) {
                    index = Math.min(2, trees.size() - 1);
                }

                RSObject target = trees.get(index);
                WorldPosition targetPos = target.getWorldPosition();
                double distNow = targetPos != null ? targetPos.distanceTo(myPosition) : Double.MAX_VALUE;

                // reset
                if (lastOffscreenTarget == null || !lastOffscreenTarget.equalsPrecisely(targetPos)) {
                    lastOffscreenTarget = targetPos;
                    lastTargetDistance = distNow;
                    sameTargetTries = 0;
                    offscreenStuckTick.reset(0);
                } else {

                    if (offscreenStuckTick.hasFinished()) {

                        offscreenStuckTick.reset(uniformRandom(1800, 2800));

                        boolean noImprovement = (distNow >= lastTargetDistance - 0.2);
                        if (noImprovement) sameTargetTries++;
                        lastTargetDistance = distNow;


                        if (sameTargetTries >= 4) {
                            log(getClass().getSimpleName(), "Stuck walking to off-screen tree -> walking to random tile in tree area");

                            WalkConfig.Builder b = new WalkConfig.Builder();
                            b.breakDistance(1);
                            b.tileRandomisationRadius(2);
                            getWalker().walkTo(selectedTree.getTreeArea().getRandomPosition(), b.build());

                            sameTargetTries = 0;
                            lastOffscreenTarget = null;
                            lastTargetDistance = Double.MAX_VALUE;
                            return;
                        }
                    }
                }

                log("Closest treeType off screen: " + target.getName() + " at " + targetPos);
                WalkConfig.Builder builder = new WalkConfig.Builder();
                builder.breakCondition(target::isInteractableOnScreen);
                builder.tileRandomisationRadius(3);
                getWalker().walkTo(target, builder.build());
            } else {

                log(OneClick50Fm.class, "No trees available; walking to tree area...");
                WalkConfig.Builder b = new WalkConfig.Builder();
                b.tileRandomisationRadius(2);
                getWalker().walkTo(selectedTree.getTreeArea().getRandomPosition(), b.build());
            }
            return;
        }
        sameTargetTries = 0;
        lastOffscreenTarget = null;
        lastTargetDistance = Double.MAX_VALUE;
        offscreenStuckTick.reset(0);

        int index = 0;
        if (firstBack) {
            index = Math.min(2, activeTrees.size() - 1);
            firstBack = false;
        }
        index = Math.max(0, index);
        // if we have a tree on screen, lets chop it down
        RSObject closestTree = activeTrees.get(index);
        // draw active trees on the canvas & highlight the closest tree
        drawActiveTrees(activeTrees, closestTree);
        // get the convex hull of the closest tree
        Polygon closestTreePolygon = closestTree.getConvexHull();
        // interact with the closest tree
        String action = "chop down";
        if (getFinger().tapGameScreen(closestTreePolygon, action + " " + selectedTree.getObjectName())) {
            // wait until we start chopping
            waitUntilFinishedChopping(selectedTree, closestTree);
        } else {
            // temporarily add to ignore list (which will be skipped when searching for trees next time polling) this is to handle a false positive
            TREE_BLACKLIST.put(closestTree.getWorldPosition(), System.currentTimeMillis());
        }
    }

    public List<RSObject> getActiveTrees(Tree treeType, List<RSObject> trees) {
        if (treeType.getCluster() == null) {
            // use respawn circle logic
            Map<RSObject, PixelAnalyzer.RespawnCircle> respawnCircleMap = getPixelAnalyzer().getRespawnCircleObjects(trees, PixelAnalyzer.RespawnCircleDrawType.TOP_CENTER, 0, 10);
            List<RSObject> treesCopy = new ArrayList<>(trees);
            treesCopy.removeIf(respawnCircleMap::containsKey);
            return treesCopy;
        } else {
            List<RSObject> activeTrees = new ArrayList<>();
            for (RSObject tree : trees) {
                Polygon treePolygon = tree.getConvexHull();
                if (treePolygon == null || (treePolygon = treePolygon.getResized(0.5)) == null) {
                    continue;
                }
                if (getWidgetManager().insideGameScreenFactor(
                        treePolygon,
                        List.of(
                                ChatboxComponent.class,
                                com.osmb.api.ui.component.tabs.InventoryTabComponent.class
                        )
                ) < 0.90) {
                    continue;

                }
                if (getPixelAnalyzer().findPixels(treePolygon, treeType.getCluster()).size() < 20) {
                    continue;
                }
                activeTrees.add(tree);
            }
            return activeTrees;
        }
    }

    private void executeTask(Task task) {
        switch (task) {
            case SET_ZOOM -> setZoom();

            case CHECK_INIT_LEVELS -> performInitialLevelCheck();

            case CHOP_TREES -> chopTrees();

            case START_BONFIRE -> {
                log(getClass().getSimpleName(), "Lighting bonfire...");
                inventorySnapshot = getWidgetManager().getInventory().search(getMonitoredIds());
                if (inventorySnapshot == null) { log(getClass().getSimpleName(), "Inventory not visible."); return; }
                if (!inventorySnapshot.contains(ItemID.TINDERBOX)) { log(getClass().getSimpleName(), "No tinderbox in inventory."); return; }

                ItemSearchResult tinder = inventorySnapshot.getItem(ItemID.TINDERBOX);
                ItemSearchResult anyLog = pickAnyLog(inventorySnapshot);
                if (tinder == null || anyLog == null) { log(getClass().getSimpleName(), "Missing tinder/logs to light bonfire; returning to chop."); return; }

                lightBonfire(tinder, anyLog);
                listenChatbox();
            }

            case BURN_LOGS -> {
                log(getClass().getSimpleName(), "Bonfire active");
                ItemGroupResult snap = getWidgetManager().getInventory().search(getMonitoredIds());
                if (snap == null) return;
                burnLogsOnBonfire(snap);
                listenChatbox();
            }

            case HANDLE_DIALOGUE -> {
                log(getClass().getSimpleName(), "Handling dialogue");
                handleDialogue();
            }

            case MOVE_LIGHT_POSITION -> {
                log(getClass().getSimpleName(), "Moving to new light position...");
                moveToNewPosition();
            }
        }
    }

    private void drawActiveTrees(List<RSObject> activeVisibleTrees, RSObject closestTree) {
        getScreen().queueCanvasDrawable("activeTrees", (canvas) -> {
            for (RSObject rsObject : activeVisibleTrees) {
                Polygon treePolygon = rsObject.getConvexHull();
                if (treePolygon != null) {
                    canvas.fillPolygon(treePolygon, Color.GREEN.getRGB(), 0.3);
                    int color = rsObject.equals(closestTree) ? Color.CYAN.getRGB() : Color.GREEN.getRGB();
                    canvas.drawPolygon(treePolygon, color, 1);
                }
            }
        });
    }

    private List<RSObject>  getVisibleTrees(List<RSObject> trees, WorldPosition myPosition) {
        return trees.stream()
                .filter(rsObject -> {
                    WorldPosition position = rsObject.getWorldPosition();
                    if (position == null) {
                        return false; // Skip if the position is null
                    }
                    Long time = TREE_BLACKLIST.get(position);
                    if (time != null) {
                        if ((System.currentTimeMillis() - time) < BLACKLIST_TIMEOUT) {
                            return false;
                        } else {
                            TREE_BLACKLIST.remove(position);
                        }
                    }
                    if (!selectedTree.getTreeArea().contains(position)) {
                        return false;
                    }
                    Polygon treePolygon = rsObject.getConvexHull();
                    if (treePolygon == null || (treePolygon = treePolygon.getResized(0.5)) == null) {
                        return false; // Skip if the polygon is null
                    }
                    if (getWidgetManager().insideGameScreenFactor(
                            treePolygon,
                            List.of(
                                    ChatboxComponent.class,
                                    com.osmb.api.ui.component.tabs.InventoryTabComponent.class

                            )
                    ) < 0.90) {
                        return false;
                    }
                    return rsObject.canReach() && rsObject.getTileDistance(position) <= 15;
                })
                // sort by distance to player
                .sorted((a, b) -> {
                    double distA = a.getWorldPosition().distanceTo(myPosition);
                    double distB = b.getWorldPosition().distanceTo(myPosition);
                    return Double.compare(distA, distB);
                })
                .toList();
    }

    private void walkToTreeArea() {
        if (selectedTree == null) return;
        WorldPosition me = getWorldPosition();
        if (me == null) return;

        if (selectedTree.getTreeArea().contains(me)) return;

        log(getClass().getSimpleName(), "Walking to tree area (" + selectedTree.name() + ")...");
        WalkConfig.Builder b = new WalkConfig.Builder();
        b.tileRandomisationRadius(2);
        b.breakCondition(() -> {
            WorldPosition p = getWorldPosition();
            return p != null && selectedTree.getTreeArea().contains(p);
        });
        getWalker().walkTo(selectedTree.getTreeArea().getRandomPosition(), b.build());
    }

    private boolean ensureSelectedTree() {
        int currentLevel = 0;

        if (wcTracker != null) {
            XPTracker tracker = wcTracker.getTracker();
            if (tracker != null) {
                currentLevel = tracker.getLevel();
            }
        }

        if (currentLevel == 0) {
            currentLevel = cachedWcLevel;
        }

        if (currentLevel <= 0) return false;

        Tree optimal = Tree.getTreeForLevel(currentLevel);

        if (optimal == null) optimal = Tree.NORMAL;

        if (selectedTree != optimal) {
            log(OneClick50Fm.class, "LEVEL UP (Tracker: " + currentLevel + ")! Switching: " +
                    (selectedTree == null ? "None" : selectedTree.getObjectName()) + " -> " + optimal.getObjectName());

            selectedTree = optimal;

            lastOffscreenTarget = null;
            firstBack = true;

            return false;
        }
        return true;
    }

    private void performInitialLevelCheck() {
        var wcSkill = getWidgetManager().getSkillTab().getSkillLevel(SkillType.WOODCUTTING);
        var fmSkill = getWidgetManager().getSkillTab().getSkillLevel(SkillType.FIREMAKING);

        if (wcSkill != null) {
            cachedWcLevel = wcSkill.getLevel();
        }

        if (fmSkill != null) {
            cachedFmLevel = fmSkill.getLevel();
        }

        if (cachedWcLevel > 0) {
            initialLevelsChecked = true;
        }
    }

    private void waitUntilFinishedChopping(Tree treeType, RSObject tree) {
        WorldPosition worldPosition = getWorldPosition();
        if (worldPosition == null) {
            log(OneClick50Fm.class, "Position is null");
            return;
        }
        if (tree.getTileDistance(worldPosition) > 1) {
            log(OneClick50Fm.class, "Waiting until we've started moving...");
            pollFramesUntil(() -> getLastPositionChangeMillis() < 600, uniformRandom(1000, 3000));
            log(OneClick50Fm.class, "Waiting until we've stopped moving...");
            pollFramesUntil(() -> {
                WorldPosition worldPosition_ = getWorldPosition();
                if (worldPosition_ == null) {
                    log(OneClick50Fm.class, "Position is null");
                    return false;
                }
                long lastPositionChange = getLastPositionChangeMillis();
                return lastPositionChange > 800 && tree.getTileDistance(worldPosition) == 1;
            }, uniformRandom(5000, 12000));
        }
        if (tree.getTileDistance(worldPosition) > 1) {
            log(OneClick50Fm.class, "We didn't reach the tree, returning...");
            return;
        }
        log(OneClick50Fm.class, "Waiting until we're finished chopping...");
        pollFramesUntil(() -> {
                    WorldPosition myPosition = getWorldPosition();
                    if (myPosition == null) {
                        log(OneClick50Fm.class, "Position is null");
                        return false;
                    }

                    Polygon treePolygon = tree.getConvexHull();
                    if (treePolygon == null || (treePolygon = treePolygon.getResized(0.5)) == null) {
                        return false; // Skip if the polygon is null
                    }
                    if (treeType.getCluster() == null) {
                        Map<RSObject, PixelAnalyzer.RespawnCircle> results = getPixelAnalyzer().getRespawnCircleObjects(List.of(tree), PixelAnalyzer.RespawnCircleDrawType.TOP_CENTER, 0, 10);
                        if (!results.isEmpty()) {
                            // respawn circle found
                            log(OneClick50Fm.class, "Found respawn circle!");
                            return true;
                        }

                    } else {
                        if (getPixelAnalyzer().findPixels(treePolygon, treeType.getCluster()).size() < 20) {
                            log(OneClick50Fm.class, "No pixels found for treeType " + treeType.getObjectName() + ", returning...");
                            return true;
                        }
                    }

                    ItemGroupResult inventorySnapshot = getWidgetManager().getInventory().search(Set.of(treeType.getLogID()));
                    if (inventorySnapshot == null) {
                        log(OneClick50Fm.class, "Failed to get inventory snapshot.");
                        return false;
                    }
                    if (inventorySnapshot.isFull()) {
                        log(OneClick50Fm.class, "Inventory is full");
                        return true;
                    }
                    return false;
                },RandomUtils.uniformRandom(55000, 95000)
        );
        if (uniformRandom(0, 3) == 0) {
            // Randomly wait before chopping again
            pollFramesHuman(() -> false, RandomUtils.exponentialRandom(1800, 700, 8000));
        }
    }

    private void debugState() {
        boolean invFull = (inventorySnapshot != null && inventorySnapshot.isFull());
        log(getClass().getSimpleName(),
                "[SM] " + "decideTask-preReturn" +
                        "  phase=" + phase +
                        "  full=" + invFull +
                        "  anyLogs=" + hasAnyLogsInInventory() +
                        "  bonfirePos=" + (bonfirePosition==null?"null":bonfirePosition.toString()) +
                        "  alive=" + (bonfirePosition==null?"-":""+isBonfireAlive()) +
                        "  forceNewPos=" + forceNewPosition
        );
    }

    private void hopWorlds() {
        ProfileManager pm = getProfileManager();
        if (pm == null) {
            log(getClass().getSimpleName(), "ProfileManager null; Can't hop.");
            return;
        }
        if (!pm.hasHopProfile()) {
            log(getClass().getSimpleName(), "No hop profile set; select one first.");
            return;
        }

        WorldProvider provider = worlds -> {
            if (worlds == null || worlds.isEmpty()) return null;
            return worlds.get(0);
        };

        pm.forceHop(provider);
    }

    private boolean isDialogueVisible() {
        return getWidgetManager().getDialogue().getDialogueType() == DialogueType.ITEM_OPTION;
    }

    private void handleDialogue() {
        if (!isDialogueVisible()) return;
        // Search the inventory for any valid log from the LOGS list.
        ItemGroupResult inv = getWidgetManager().getInventory()
                .search(Arrays.stream(LOGS).boxed().collect(java.util.stream.Collectors.toSet()));
        if (inv == null) {
            log(getClass().getSimpleName(), "Inventory not visible.");
            return;
        }
        // Choose any one of the ones you have (if you don't have any, there's nothing to burn).
        ItemSearchResult logItem = inv.getRandomItem(LOGS);
        if (logItem == null) {
            log(getClass().getSimpleName(), "No logs in inventory to select.");
            return;
        }
        if (!isDialogueVisible()) return;

        boolean ok = getWidgetManager().getDialogue().selectItem(logItem.getId());
        if (!ok) {
            log(getClass().getSimpleName(), "Failed to select item in dialogue.");
            return;
        }
        this.task = Task.BURN_LOGS;
        waitUntilFinishedBurning(LOGS);
    }

    private void lightBonfire(ItemSearchResult tinderbox, ItemSearchResult logs) {
        if (!tinderbox.interact()) {
            return;
        }
        // select random log
        if (!logs.interact()) {
            return;
        }

        final int initialSlots = inventorySnapshot.getFreeSlots();
        boolean lightingFire = pollFramesUntil(() -> {
            inventorySnapshot = getWidgetManager().getInventory().search(Collections.emptySet());
            if (inventorySnapshot == null) {
                // inventory not visible, re-poll
                return false;
            }
            // wait until we have more free slots - this tells us the log has been used successfully
            return inventorySnapshot.getFreeSlots() > initialSlots;
        }, 3500);
        WorldPosition lightPosition = getWorldPosition();
        // if we failed to light the fire, usually means we're in a spot you can't make fires
        if (!lightingFire) {
            forceNewPosition = true;
        } else {
            waitForFireToLight(lightPosition);
        }
    }

    private boolean hasAnyLogsInInventory() {
        Set<Integer> idSet = Arrays.stream(LOGS).boxed().collect(Collectors.toSet());
        ItemGroupResult inv = getWidgetManager().getInventory().search(idSet);
        if (inv == null) return false;
        for (int id : LOGS) if (inv.contains(id)) return true;
        return false;
    }

    private boolean hasTinderbox() {
        ItemGroupResult inv = getWidgetManager().getInventory().search(Set.of(ItemID.TINDERBOX));
        return inv != null && inv.contains(ItemID.TINDERBOX);
    }

    private Set<Integer> getMonitoredIds() {
        Set<Integer> ids = Arrays.stream(LOGS).boxed().collect(Collectors.toSet());
        ids.add(ItemID.TINDERBOX);
        return ids;
    }

    private ItemSearchResult pickAnyLog(ItemGroupResult inv) {
        return inv.getRandomItem(LOGS);
    }

    private void moveToNewPosition() {
        // If we have a defined area, we choose a random tile WITHIN that area.
        if (bonfireArea != null) {
            WorldPosition here = getWorldPosition();
            if (here == null) {
                log(getClass().getSimpleName(), "Position is null.");
                return;
            }
            // Try to choose a point within the area <= 6 tiles away from the player.
            WorldPosition target = null;
            for (int i = 0; i < 15; i++) { // up to 15 attempts
                WorldPosition candidate = bonfireArea.getRandomPosition();
                if (candidate != null && here.distanceTo(candidate) <= 6.0) {
                    target = candidate;
                    break;
                }
            }

            if (target == null) {
                target = bonfireArea.getRandomPosition();
            }

            if (target != null) {
                WalkConfig.Builder b = new WalkConfig.Builder();
                b.breakDistance(1);
                b.tileRandomisationRadius(1);
                getWalker().walkTo(target, b.build());
                forceNewPosition = false;
                return;
            } else {
                log(getClass().getSimpleName(), "BONFIRE_AREA.getRandomPosition() return null.");
            }
        }

        List<LocalPosition> reachableTiles = getWalker().getCollisionManager().findReachableTiles(getLocalPosition(), 6);
        if (reachableTiles.isEmpty()) {
            log(getClass().getSimpleName(), "No reachable tiles found.");
            return;
        }
        WalkConfig.Builder builder = new WalkConfig.Builder();
        builder.breakDistance(1);
        builder.tileRandomisationRadius(1);
        LocalPosition randomPos = reachableTiles.get(uniformRandom(reachableTiles.size()));
        getWalker().walkTo(randomPos);
        forceNewPosition = false;
    }

    private void waitForFireToLight(WorldPosition lightPosition) {
        log(getClass().getSimpleName(), "Waiting for fire to light...");
        boolean result = pollFramesHuman(() -> {
            WorldPosition currentPos = getWorldPosition();
            return currentPos != null && !currentPos.equals(lightPosition);
        }, 14000);

        if (result) {
            log(OneClick50Fm.class, "Fire successfully lit!");
            bonfirePosition = lightPosition;
            bonfireSetAtMs = System.currentTimeMillis();
            forceNewPosition = false;
            phase = Phase.BURN;
        }
    }

    private Polygon getBonfireTile() {
        Polygon tilePoly = getSceneProjector().getTilePoly(bonfirePosition, true);
        if (tilePoly == null) {
            return null;
        }
        tilePoly = tilePoly.getResized(0.4);
        if (tilePoly == null || !getWidgetManager().insideGameScreen(tilePoly, Collections.emptyList())) {
            return null;
        }
        return tilePoly;
    }

    private boolean isBonfireAlive() {
        if (bonfirePosition == null) return false;
        if (System.currentTimeMillis() - bonfireSetAtMs < 8000) {
            return true;
        }

        List<RSObject> fires = getObjectManager().getObjects(o -> {
            WorldPosition p = o.getWorldPosition();
            if (p == null || !p.equals(bonfirePosition)) return false;
            String n = o.getName();
            return n != null && (n.equalsIgnoreCase("Fire") || n.equalsIgnoreCase("Forester's Campfire"));
        });

        return !fires.isEmpty();
    }

    private boolean dropUnwantedIfFull() {
        inventorySnapshot = getWidgetManager().getInventory().search(Collections.emptySet());
        if (inventorySnapshot == null || !inventorySnapshot.isFull()) return false;

        getWidgetManager().getInventory().unSelectItemIfSelected();

        List<Integer> toDrop = new ArrayList<>();
        for (ItemSearchResult item : inventorySnapshot.getRecognisedItems()) {
            int id = item.getId();
            if (!ALWAYS_KEEP.contains(id)) {
                toDrop.add(id);
            }
        }

        if (!toDrop.isEmpty()) {
            log(getClass().getSimpleName(), "Dropping unwanted items: " + toDrop);
            getWidgetManager().getInventory().dropItems(toDrop.stream().mapToInt(i -> i).toArray());
            return true;
        }

        return false;
    }

    private void burnLogsOnBonfire(ItemGroupResult inventorySnapshot) {
        if (bonfirePosition == null) return;

        if (!isBonfireAlive()) {
            log(getClass().getSimpleName(), "Bonfire turn off -> relight");
            bonfirePosition = null;
            forceNewPosition = true;
            burnRetryCount = 0;
            return;
        }

        Polygon tilePoly = getBonfireTile();
        if (tilePoly == null || !getWidgetManager().insideGameScreen(tilePoly, Collections.emptyList())) {
            log(getClass().getSimpleName(), "Walking to bonfire");
            walkToBonfire();
            return;
        }

        log(getClass().getSimpleName(), "Burning logs on the bonfire...");
        if (!interactAndWaitForDialogue(inventorySnapshot)) {
            burnRetryCount++;
            log(getClass().getSimpleName(), "Fail burn try =" + burnRetryCount);

            if (burnRetryCount >= 3) {
                log(getClass().getSimpleName(), "3 fails -> Move and restart");
                bonfirePosition = null;
                forceNewPosition = true;
                burnRetryCount = 0;
            }
            return;
        }
        burnRetryCount = 0;
    }

    private void walkToBonfire() {
        WalkConfig.Builder builder = new WalkConfig.Builder();
        builder.breakDistance(2).tileRandomisationRadius(2);
        builder.breakCondition(() -> getBonfireTile() != null);
        getWalker().walkTo(bonfirePosition, builder.build());
    }

    private void listenChatbox() {
        if (getWidgetManager().getDialogue().getDialogueType() == null && getWidgetManager().getChatbox().getActiveFilterTab() != ChatboxFilterTab.GAME) {
            getWidgetManager().getChatbox().openFilterTab(ChatboxFilterTab.GAME);
            return;
        }
        UIResultList<String> textLines = getWidgetManager().getChatbox().getText();
        if (textLines.isNotVisible()) {
            log("Chatbox not visible");
            return;
        }
        List<String> currentLines = textLines.asList();
        if (currentLines.isEmpty()) {
            return;
        }
        int firstDifference = 0;
        if (!PREVIOUS_CHATBOX_LINES.isEmpty()) {
            if (currentLines.equals(PREVIOUS_CHATBOX_LINES)) {
                return;
            }
            int currSize = currentLines.size();
            int prevSize = PREVIOUS_CHATBOX_LINES.size();
            for (int i = 0; i < currSize; i++) {
                int suffixLen = currSize - i;
                if (suffixLen > prevSize) continue;
                boolean match = true;
                for (int j = 0; j < suffixLen; j++) {
                    if (!currentLines.get(i + j).equals(PREVIOUS_CHATBOX_LINES.get(j))) {
                        match = false;
                        break;
                    }
                }
                if (match) {
                    firstDifference = i;
                    break;
                }
            }
        } else {
            PREVIOUS_CHATBOX_LINES.addAll(currentLines);
        }
        List<String> newLines = currentLines.subList(0, firstDifference);
        PREVIOUS_CHATBOX_LINES.clear();
        PREVIOUS_CHATBOX_LINES.addAll(currentLines);
        onNewChatBoxMessage(newLines);
    }

    private void onNewChatBoxMessage(List<String> newLines) {
        for (String line : newLines) {
            String l = line.toLowerCase();
            log("Chatbox listener", "New line: " + line);
            if (line.endsWith("further away.")) {
                hopWorlds();
                bonfirePosition = null;
            } else if (line.endsWith("light a fire here.")) {
                forceNewPosition = true;
                bonfirePosition = null;

            }
        }
    }

    public boolean interactAndWaitForDialogue(ItemGroupResult inventorySnapshot) {
        ItemSearchResult log = null;

        if (inventorySnapshot.getSelectedSlot() != null) {
            log = getSelectedLog();

            if (log == null && !getWidgetManager().getInventory().unSelectItemIfSelected()) {
                return true;
            }
        }
        if (log == null) {
            log = inventorySnapshot.getRandomItem(LOGS);
        }
        if (!log.interact()) {
            return true;
        }

        Polygon tilePoly = getBonfireTile();
        if (tilePoly == null) {
            return true;
        }

        getScreen().queueCanvasDrawable("highlightFireTile", canvas -> canvas.drawPolygon(tilePoly, Color.GREEN.getRGB(), 1));
        MenuHook menuHook = menuEntries -> {
            for (MenuEntry entry : menuEntries) {
                String rawText = entry.getRawText();
                if (rawText == null) continue;
                String t = rawText.trim().toLowerCase(Locale.ROOT);
                log(OneClick50Fm.class, "Menu text: " + rawText);
                if (USE_LOG_TO_FIRE.matcher(t).matches()) {
                    return entry;
                }
            }
            return null;
        };

        if (!getFinger().tapGameScreen(tilePoly, menuHook)) {

            getScreen().removeCanvasDrawable("highlightFireTile");

            return !failed();
        }
        // reset tries, as we successfully interacted with the fire
        tries = 0;
        // remove the highlight after interacting
        getScreen().removeCanvasDrawable("highlightFireTile");

        log(getClass().getSimpleName(), "Waiting for dialogue");
        // sleep until dialogue is visible
        return pollFramesUntil(() -> {
            listenChatbox();
            return getWidgetManager().getDialogue().getDialogueType() == DialogueType.ITEM_OPTION;
        }, uniformRandom(5000, 8000));
    }

    private boolean failed() {
        if (tries > 2) {
            bonfirePosition = null;
            return true;
        }
        tries++;
        return false;
    }

    private ItemSearchResult getSelectedLog() {
        for (ItemSearchResult recognisedItem : inventorySnapshot.getRecognisedItems()) {
            if (recognisedItem.isSelected()) {
                int selectedItemID = recognisedItem.getId();
                if (Arrays.stream(LOGS).anyMatch(id -> id == selectedItemID)) {
                    return recognisedItem;
                }
                return null;
            }
        }
        return null;
    }

    private void setZoom() {
        log(getClass().getSimpleName(), "Setting zoom level to 11...");

        if (!getWidgetManager().getSettings().openSubTab(SettingsTabComponent.SettingsSubTabType.DISPLAY_TAB)) {
            return;
        }

        var zoomResult = getWidgetManager().getSettings().getZoomLevel();
        if (!zoomResult.isFound()) {
            log(getClass().getSimpleName(), "Zoom level not found.");
            return;
        }

        int currentZoom = zoomResult.get();
        log(getClass().getSimpleName(), "Current zoom: " + currentZoom);

        if (currentZoom == 11) {
            setZoom = true;
            log(getClass().getSimpleName(), "Zoom already at 11.");
            return;
        }

        if (getWidgetManager().getSettings().setZoomLevel(11)) {
            log(getClass().getSimpleName(), "Zoom level set to 11.");
            setZoom = true;
        } else {
            log(getClass().getSimpleName(), "Failed to set zoom, will retry... (current zoom: " + currentZoom + ")");
        }
    }

    public void waitUntilFinishedBurning(int[] logsIds) {
        Timer amountChangeTimer = new Timer();
        AtomicInteger previousTotal = new AtomicInteger(-1);

        if (amountChangeTimeout < 4000) {
            amountChangeTimeout = 8000;
        }

        pollFramesUntil(() -> {
            DialogueType dialogueType = getWidgetManager().getDialogue().getDialogueType();
            if (dialogueType == DialogueType.TAP_HERE_TO_CONTINUE) {
                pollFramesUntil(() -> false, uniformRandom(1000, 4000));
                return false;
            }

            if (amountChangeTimer.timeElapsed() > amountChangeTimeout) {
                amountChangeTimeout = uniformRandom(6200, 9000);
                bonfirePosition = null;
                return true;
            }

            java.util.Set<Integer> idSet = java.util.Arrays.stream(logsIds).boxed()
                    .collect(java.util.stream.Collectors.toSet());
            inventorySnapshot = getWidgetManager().getInventory().search(idSet);
            if (inventorySnapshot == null) return false;

            int total = 0;
            for (int id : logsIds) {
                total += inventorySnapshot.getAmount(id);
            }

            if (total == 0) {
                bonfirePosition = null;
                phase = Phase.CHOP;
                return true;
            }

            int prev = previousTotal.get();
            if (prev == -1) {
                previousTotal.set(total);

            } else if (total < prev) {
                previousTotal.set(total);
                amountChangeTimer.reset();

            }

            return false;
        }, 160000, false, true);
    }

    @Override
    public int[] regionsToPrioritise() {
        return new int[]{11571};
    }

    enum Task {
        CHECK_INIT_LEVELS,
        CHOP_TREES,
        START_BONFIRE,
        BURN_LOGS,
        HANDLE_DIALOGUE,
        MOVE_LIGHT_POSITION,
        SET_ZOOM
    }

    public static class SkillTracker {

        private final ScriptCore core;
        private final SearchableImage skillSprite;
        private final XPDropsComponent xpDropsComponent;
        private XPTracker xpTracker;

        public SkillTracker(ScriptCore core, int spriteId) {
            this.core = core;
            this.xpDropsComponent = (XPDropsComponent) core.getWidgetManager().getComponent(XPDropsComponent.class);

            SearchableImage fullSprite = new SearchableImage(spriteId, core, new SingleThresholdComparator(15), ColorModel.RGB);

            this.skillSprite = fullSprite.subImage(fullSprite.width / 2, 0, fullSprite.width / 2, fullSprite.height);
        }

        public XPTracker getTracker() {
            return xpTracker;
        }

        public void checkXP() {
            Integer currentXP = getXpCounter();
            if (currentXP != null) {

                if (xpTracker == null) {
                    xpTracker = new XPTracker(core, currentXP);

                } else {
                    double xp = xpTracker.getXp();
                    double gainedXP = currentXP - xp;

                    if (gainedXP > 0 && gainedXP < 50000) {
                        xpTracker.incrementXp(gainedXP);
                    }
                }
            }
        }

        private Integer getXpCounter() {
            com.osmb.api.shape.Rectangle bounds = getXPDropsBounds();
            if (bounds == null) return null;

            boolean isMySkill = core.getImageAnalyzer().findLocation(bounds, skillSprite) != null;
            if (!isMySkill) return null;

            String xpText = core.getOCR().getText(com.osmb.api.visual.ocr.fonts.Font.SMALL_FONT, bounds, -1).replaceAll("[^0-9]", "");
            if (xpText.isEmpty()) return null;

            try {
                return Integer.parseInt(xpText);
            } catch (NumberFormatException e) {
                return null;
            }
        }

        public void ensureCounterActive() {
            com.osmb.api.shape.Rectangle bounds = xpDropsComponent.getBounds();
            if (bounds == null) return;

            ComponentSearchResult<Integer> result = xpDropsComponent.getResult();

            if (result.getComponentImage().getGameFrameStatusType() != 1) {
                core.getFinger().tap(bounds);
            }
        }

        private com.osmb.api.shape.Rectangle getXPDropsBounds() {
            com.osmb.api.shape.Rectangle bounds = xpDropsComponent.getBounds();
            if (bounds == null) return null;

            ComponentSearchResult<Integer> result = xpDropsComponent.getResult();
            if (result.getComponentImage().getGameFrameStatusType() != 1) return null;

            return new com.osmb.api.shape.Rectangle(bounds.x - 140, bounds.y - 1, 119, 38);
        }
    }

    @Override
    public void onPaint(Canvas c) {
        //draw bonfire
        if (bonfirePosition != null) {
            Polygon polygon = getSceneProjector().getTilePoly(bonfirePosition, true);
            if (polygon != null) {
                c.fillPolygon(polygon, Color.ORANGE.getRGB(), 0.5);
                c.drawPolygon(polygon, Color.GREEN.getRGB(), 1);
            }
        }

        int x = 15, y = 35;
        int panelW = 320;
        int panelH = 270;
        int padding = 15;

        int COL_BG = new Color(12, 12, 18, 240).getRGB();
        int COL_BORDER = new Color(255, 69, 0, 200).getRGB();
        int COL_LABEL = new Color(190, 190, 190).getRGB();
        int COL_VALUE = Color.WHITE.getRGB();

        Font fontTitle = new Font("Impact", Font.ITALIC, 22);
        Font fontBold = new Font("SansSerif", Font.BOLD, 13);

        c.fillRect(x, y, panelW, panelH, COL_BG);
        c.drawRect(x, y, panelW, panelH, COL_BORDER);

        drawCenteredText(c, "ONE CLICK 50FM", x + panelW / 2, y + 28, fontTitle, Color.ORANGE.getRGB());

        int cursorY = y + 55;
        long elapsed = System.currentTimeMillis() - startTime;

        drawRow(c, "Version:", "v1.0", x + padding, x + 110, cursorY, COL_LABEL, new Color(255, 200, 50).getRGB(), fontBold);
        cursorY += 20;

        drawRow(c, "Runtime:", formatTime(elapsed), x + padding, x + 110, cursorY, COL_LABEL, COL_VALUE, fontBold);
        cursorY += 20;

        String taskName = (task != null) ? task.name() : "IDLE";
        drawRow(c, "Task:", taskName, x + padding, x + 110, cursorY, COL_LABEL, new Color(255, 200, 50).getRGB(), fontBold);
        cursorY += 20;

        String treeName = (selectedTree != null) ? selectedTree.getObjectName() : "None";
        drawRow(c, "Target Tree:", treeName, x + padding, x + 110, cursorY, COL_LABEL, Color.GREEN.getRGB(), fontBold);
        cursorY += 25;

        XPTracker tWC = (wcTracker != null) ? wcTracker.getTracker() : null;
        int wcLvl = (tWC != null) ? tWC.getLevel() : Math.max(1, cachedWcLevel);
        int wcProg = (tWC != null) ? tWC.getLevelProgressPercentage() : 0;
        String wcRate = (tWC != null) ? formatK(tWC.getXpPerHour()) + " xp/h" : "0 xp/h";
        String wcTTL = (tWC != null) ? "TTL: " + tWC.timeToNextLevelString() : "TTL: -";

        XPTracker tFM = (fmTracker != null) ? fmTracker.getTracker() : null;
        int fmLvl = (tFM != null) ? tFM.getLevel() : Math.max(1, cachedFmLevel);
        int fmProg = (tFM != null) ? tFM.getLevelProgressPercentage() : 0;
        String fmRate = (tFM != null) ? formatK(tFM.getXpPerHour()) + " xp/h" : "0 xp/h";
        String fmTTL = (tFM != null) ? "TTL: " + tFM.timeToNextLevelString() : "TTL: -";

        String urlFM = "https://oldschool.runescape.wiki/images/Firemaking_icon.png";
        String urlWC = "https://oldschool.runescape.wiki/images/Woodcutting_icon.png";
        com.osmb.api.visual.image.Image iconFm = getIcon("FM_ICON", urlFM);
        com.osmb.api.visual.image.Image iconWc = getIcon("WC_ICON", urlWC);

        int sectionH = 55;

        drawSkillBar(c, "FIREMAKING", fmLvl, fmRate, fmTTL, fmProg, new Color(255, 60, 0).getRGB(), iconFm, x + padding, cursorY, panelW - (padding * 2), sectionH);
        cursorY += sectionH + 5;

        drawSkillBar(c, "WOODCUTTING", wcLvl, wcRate, wcTTL, wcProg, new Color(0, 180, 60).getRGB(), iconWc, x + padding, cursorY, panelW - (padding * 2), sectionH);
    }

    private void drawSkillBar(Canvas c, String skillName, int level, String rightText, String bottomText, int progressPercent, int barColor, com.osmb.api.visual.image.Image icon, int x, int y, int w, int h) {
        Font fontHead = new Font("Arial", Font.BOLD, 12);
        Font fontRate = new Font("Arial", Font.BOLD, 12);
        Font fontBar  = new Font("Arial", Font.BOLD, 11);
        Font fontBottom = new Font("Arial", Font.PLAIN, 10);

        int barH = 18;
        int iconSize = 30;

        int iconCenteredY = y + (h / 2) - (iconSize / 2);

        if (icon != null) {
            try {
                c.drawAtOn(icon, x, iconCenteredY);
            } catch (Exception e) {
                log("Paint", "Error drawing icon: " + e.getMessage());
            }
        } else {
            c.fillRect(x, iconCenteredY, iconSize, iconSize, barColor);
        }

        int contentX = x + iconSize + 10;
        int contentW = w - (iconSize + 10);

        String title = skillName + " (" + level + ")";
        c.drawText(title, contentX, y + 10, barColor, fontHead);

        FontMetrics fm = c.getFontMetrics(fontRate);
        int rateW = fm.stringWidth(rightText);
        c.drawText(rightText, contentX + contentW - rateW, y + 10, Color.WHITE.getRGB(), fontRate);

        int barY = y + 18;
        c.fillRect(contentX, barY, contentW, barH, new Color(40, 40, 40).getRGB());

        if (progressPercent > 0) {
            int fillW = (int) (contentW * (Math.min(100, progressPercent) / 100.0));
            c.fillRect(contentX, barY, fillW, barH, barColor);
        }
        c.drawRect(contentX, barY, contentW, barH, new Color(100, 100, 100).getRGB());

        if (progressPercent > 0) {
            String progStr = progressPercent + "%";
            drawCenteredText(c, progStr, contentX + (contentW / 2), barY + 13, fontBar, Color.WHITE.getRGB());
        }
        c.drawText(bottomText, contentX, barY + barH + 12, Color.LIGHT_GRAY.getRGB(), fontBottom);
    }

    private void drawRow(Canvas c, String label, String value, int xL, int xV, int y, int colL, int colV, Font f) {
        c.drawText(label, xL, y, colL, f);
        c.drawText(value, xV, y, colV, f);
    }

    private String formatK(int number) {
        if (number >= 1000) return String.format("%.1fk", number / 1000.0);
        return String.valueOf(number);
    }

    private void drawCenteredText(Canvas c, String text, int centerX, int y, Font font, int color) {
        FontMetrics metrics = c.getFontMetrics(font);
        int x = centerX - (metrics.stringWidth(text) / 2);
        c.drawText(text, x, y, color, font);
    }

    private com.osmb.api.visual.image.Image getIcon(String name, String urlAddress) {
        if (paintImages.containsKey(name)) return paintImages.get(name);

        try {
            URL url = new URL(urlAddress);
            BufferedImage bImg = ImageIO.read(url);

            if (bImg != null) {
                int w = bImg.getWidth();
                int h = bImg.getHeight();
                int[] pixels = new int[w * h];
                bImg.getRGB(0, 0, w, h, pixels, 0, w);

                com.osmb.api.visual.image.Image img = new com.osmb.api.visual.image.Image(pixels, w, h);

                paintImages.put(name, img);
                return img;
            }
        } catch (Exception e) {
            log("Error loading icon (" + name + "): " + e.getMessage());
            paintImages.put(name, null);
        }
        return null;
    }

    private String formatTime(long ms) {
        long hours = TimeUnit.MILLISECONDS.toHours(ms);
        long minutes = TimeUnit.MILLISECONDS.toMinutes(ms) % 60;
        long seconds = TimeUnit.MILLISECONDS.toSeconds(ms) % 60;
        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }

    public static int compareVersions(String v1, String v2) {
        String[] a = v1.split("\\.");
        String[] b = v2.split("\\.");
        int len = Math.max(a.length, b.length);
        for (int i = 0; i < len; i++) {
            int n1 = i < a.length ? Integer.parseInt(a[i]) : 0;
            int n2 = i < b.length ? Integer.parseInt(b[i]) : 0;
            if (n1 != n2) return Integer.compare(n1, n2);
        }
        return 0;
    }

    private boolean checkForUpdates() {
        String url = ("https://raw.githubusercontent.com/joseOSMB/JOSE-OSMB-SCRIPTS/refs/heads/main/OneClick50Fm/version.txt");
        String latest = getLatestVersion(url);
        if (latest == null) {
            log("VERSION", "Can't verify the version.");
            return true;
        }

        if (compareVersions(scriptVersion, latest) < 0) {
            log("VERSION", "❌ New version v" + latest + " detected!");
            log("VERSION", "Please update you script in GitHub.");
            log("VERSION","https://github.com/joseOSMB/JOSE-OSMB-SCRIPTS/tree/main/OneClick50Fm/jar");
            return false;
        }

        log("VERSION", "✅ You have the lastest version (v" + scriptVersion + ").");
        return true;
    }

    private String getLatestVersion(String url) {
        try {
            HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
            c.setRequestMethod("GET");
            c.setConnectTimeout(3000);
            c.setReadTimeout(3000);
            c.setUseCaches(false);

            if (c.getResponseCode() != 200) return null;

            try (BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream()))) {
                String line = r.readLine();
                if (line != null && !line.isEmpty()) {
                    return line.trim();
                }
            }
        } catch (Exception e) {
            log("VERSION", "Error searching update: " + e.getMessage());
        }
        return null;
    }

}
