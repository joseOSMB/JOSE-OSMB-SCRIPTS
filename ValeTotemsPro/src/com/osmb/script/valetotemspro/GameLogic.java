package com.osmb.script.valetotemspro;

import com.osmb.api.ScriptCore;
import com.osmb.api.input.MenuEntry;
import com.osmb.api.input.MenuHook;
import com.osmb.api.item.ItemSearchResult;
import com.osmb.api.location.area.Area;
import com.osmb.api.location.position.types.WorldPosition;
import com.osmb.api.scene.RSObject;
import com.osmb.api.script.Script;
import com.osmb.api.shape.Polygon;
import com.osmb.api.shape.Rectangle;
import com.osmb.api.trackers.experience.XPTracker;
import com.osmb.api.ui.chatbox.ChatboxFilterTab;
import com.osmb.api.ui.component.ComponentSearchResult;
import com.osmb.api.ui.component.minimap.xpcounter.XPDropsComponent;
import com.osmb.api.ui.component.tabs.skill.SkillType;
import com.osmb.api.utils.UIResultList;
import com.osmb.api.visual.SearchablePixel;
import com.osmb.api.visual.color.ColorModel;
import com.osmb.api.visual.color.tolerance.impl.SingleThresholdComparator;
import com.osmb.api.visual.drawing.Canvas;
import com.osmb.api.visual.image.Image;
import com.osmb.api.visual.image.SearchableImage;
import com.osmb.api.visual.ocr.fonts.Font;
import com.osmb.script.valetotemspro.data.AreaManager;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.net.URL;
import java.util.*;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.osmb.script.valetotemspro.data.AreaManager.WALL_ZONE_MAIN_ROUTE;
import static com.osmb.script.valetotemspro.data.AreaManager.WALL_ZONE_OAK;

public class GameLogic {

    private final Script ctx;
    private final String scriptVersion;

    private final Object lock = new Object();

    private boolean reportSent = false;
    private String localUser = "Free User";

    private final int KNIFE_ID = 946;
    private int selectedLogID = 1521;
    private int selectedUnstrungID = 62;
    private static final Set<Integer> BASKET_IDS = Set.of(28140, 28142, 28143, 28145);

    private boolean useLogBasket = false;
    private boolean usePreMadeItems = false;
    private int offeringsCollected = 0;
    private boolean fletchingKnifeEquipped = false;

    private List<Integer> premadeItemIds = new ArrayList<>();

    private String logNameDisplay = "Loading...";
    private String productDisplay = "Loading...";
    private long startTime;
    private int totemsCompleted = 0;
    private int tripsCounter = 0;
    private int tripsToNextOffering = 5;
    private boolean offeringActive = false;
    private boolean committedToTotem = false;
    private int cachedFletchLevel = 0;

    private long lastProgressTime = System.currentTimeMillis();
    private int lastXpCheck = 0;

    private final SkillTracker fletchTracker;
    private final Map<String, Image> paintImages = new HashMap<>();

    private final int TEXT_COLOR_ID = -16385800;
    private static final Map<String, Integer> SLOT_MAP = Map.of("buffalo", 1, "jaguar", 2, "eagle", 3, "snake", 4, "scorpion", 5);

    private static final Set<String> TARGET_NAMES = Set.of("jaguar", "buffalo", "snake", "eagle", "scorpion");
    private static final Rectangle FIRST_SLOT_BOUNDS = new Rectangle(10, 45, 91, 68);
    private static final int SLOT_STEP_X = 101;

    private final Random random = new Random();

    private Set<String> persistentSpiritCache = new LinkedHashSet<>();

    private final WorldPosition TILE_LOG_1_2 = new WorldPosition(1453, 3335, 0);
    private final WorldPosition TILE_LOG_4_5 = new WorldPosition(1401, 3290, 0);
    private final WorldPosition TILE_ROCKS = new WorldPosition(1395, 3323, 0);
    private final WorldPosition TILE_WALL_OAK = new WorldPosition(1390, 3309, 0);
    private final WorldPosition TILE_WALL_MAIN = new WorldPosition(1387, 3302, 0);

    private int agilityLevel = -1;
    private boolean configLoaded = false;
    private boolean firstRunCheck = true;

    private List<Task> taskSequence = new ArrayList<>();
    private int sequenceIndex = 0;
    private Task currentTask = Task.WAIT_FOR_CONFIG;
    private List<String> debugSpirits = new ArrayList<>();
    private String lastAction = "None";
    private long spiritSearchStart = 0;
    private boolean scanningAfterMove = false;
    private long scanningModeStartTime = 0;

    private enum Task {
        WAIT_FOR_CONFIG,
        BANK_AUBURNVALE, BANK_NEMUS,
        EMPTY_BASKET,
        TOTEM_1, TOTEM_2, TOTEM_3, TOTEM_4,
        TOTEM_5, TOTEM_6, TOTEM_7, TOTEM_8,
        SHORTCUT_LOG_1_TO_2, SHORTCUT_LOG_4_TO_5, SHORTCUT_MAIN_WALL_TO_NEMUS,
        EXIT_ROCKS_DOWN, EXIT_WALL_UP
    }

    public GameLogic(Script context, String version) {
        this.ctx = context;
        this.scriptVersion = version;
        this.startTime = System.currentTimeMillis();

        this.fletchTracker = new SkillTracker(context, 208);
    }

    public void log(String tag, String msg) {
        if(ctx != null) ctx.log(tag, msg);
        System.out.println("[" + tag + "] " + msg);
    }

    public void log(String msg) {
        log("GameLogic", msg);
    }

    public void StartConfiguration(UI ui, String usuario) {
        synchronized (lock) { reportSent = false; }

        this.localUser = (usuario != null && !usuario.isEmpty()) ? usuario : "Free User";

        this.selectedLogID = ui.getSelectedLogID();
        this.selectedUnstrungID = ui.getSelectedProductID();
        this.logNameDisplay = ui.getSelectedLogName();
        this.usePreMadeItems = ui.isUsePreMadeItems();

        if (this.usePreMadeItems) {
            this.productDisplay = ui.getSelectedPreMadeItemName();
        } else {
            this.productDisplay = ui.getSelectedProductName();
        }

        this.useLogBasket = ui.isUseLogBasket();
        this.fletchingKnifeEquipped = ui.isFletchingKnifeEquipped();
        this.premadeItemIds = ui.getSelectedPreMadeItemIDs();

        if (usePreMadeItems) {
            log("Mode Pre-Made: ON. Items Pool: " + premadeItemIds.size());
            this.useLogBasket = false;
            if (fletchingKnifeEquipped) {
                log("🔪 Knife Equipped: ON.");
            }
        }

        this.tripsToNextOffering = 5 + random.nextInt(6);
        this.configLoaded = true;
        taskSequence.clear();

        GlobalStatsTracker.startTracking(this.localUser, this);
        log("Configuration Loaded. Script Started.");
    }

    private int getCombinedPremadeCount() {
        if (premadeItemIds.isEmpty()) return 0;
        int total = 0;
        for (int id : premadeItemIds) {
            total += getInventoryCount(id);
        }
        return total;
    }

    private void ensureGameTabOpen() {
        if (ctx.getWidgetManager() == null || ctx.getWidgetManager().getChatbox() == null) {
            log("Chatbox manager not available yet.");
            return;
        }

        ChatboxFilterTab currentTab = ctx.getWidgetManager().getChatbox().getActiveFilterTab();
        log("Checking Chatbox. Active Tab found: " + (currentTab != null ? currentTab.name() : "NULL"));

        if (currentTab != ChatboxFilterTab.GAME) {
            log("Switching to GAME tab...");
            boolean switched = ctx.getWidgetManager().getChatbox().openFilterTab(ChatboxFilterTab.GAME);
            if (switched) {
                log("OK.");
            } else {
                log("Fail.");
            }
        } else {
            log("Game tab is already active.");
        }
    }

    public int loopPrincipal() {
        checkAntiLoop();

        if (ctx.getWorldPosition() == null) return 1000;

        ctx.pollFramesHuman(() -> false, 50, false);
        Task nextTask = decideTaskDispatcher();
        this.currentTask = nextTask;
        executeTask(nextTask);
        return (nextTask == Task.WAIT_FOR_CONFIG) ? 1000 : 200;
    }

    public void onNewFrame() {
        if (fletchTracker != null) fletchTracker.checkXP();
    }

    public void onStop() {
        synchronized (lock) {
            if (reportSent) return;
            GlobalStatsTracker.stopTracking(this.localUser, this);
            reportSent = true;
        }
    }

    private Task decideTaskDispatcher() {
        if (firstRunCheck) {
            if (useLogBasket) {
                if (ctx.getWidgetManager().getInventory().search(BASKET_IDS) == null) {
                    log("ERROR", "STARTUP ERROR: 'Log Basket' selected but NOT found in inventory.");
                    ctx.stop();
                    return Task.WAIT_FOR_CONFIG;
                }
            }
            firstRunCheck = false;
        }

        if (agilityLevel == -1) { if (!trySetupEnvironment()) return Task.WAIT_FOR_CONFIG; }
        if (taskSequence.isEmpty()) buildSequence();

        if (sequenceIndex >= taskSequence.size()) sequenceIndex = 0;
        return taskSequence.get(sequenceIndex);
    }

    private void buildSequence() {
        taskSequence.clear();
        sequenceIndex = 0;
        boolean isMain = (selectedLogID != 1521);

        if (useLogBasket && isMain) {
            taskSequence.add(Task.BANK_AUBURNVALE);
            taskSequence.add(Task.TOTEM_1);
            if (agilityLevel >= 45) taskSequence.add(Task.SHORTCUT_LOG_1_TO_2);
            taskSequence.add(Task.TOTEM_2);
            taskSequence.add(Task.TOTEM_3);
            taskSequence.add(Task.TOTEM_4);
            if (agilityLevel >= 45) taskSequence.add(Task.SHORTCUT_LOG_4_TO_5);
            taskSequence.add(Task.TOTEM_5);
            taskSequence.add(Task.EMPTY_BASKET);
            taskSequence.add(Task.TOTEM_6);
            taskSequence.add(Task.TOTEM_7);
            taskSequence.add(Task.TOTEM_8);

        } else if (isMain) {
            taskSequence.add(Task.BANK_AUBURNVALE);
            taskSequence.add(Task.TOTEM_1);
            if (agilityLevel >= 45) taskSequence.add(Task.SHORTCUT_LOG_1_TO_2);
            taskSequence.add(Task.TOTEM_2);
            taskSequence.add(Task.TOTEM_3);
            taskSequence.add(Task.TOTEM_4);
            if (agilityLevel >= 45) taskSequence.add(Task.SHORTCUT_LOG_4_TO_5);
            taskSequence.add(Task.TOTEM_5);
            if (agilityLevel >= 25) taskSequence.add(Task.SHORTCUT_MAIN_WALL_TO_NEMUS);
            taskSequence.add(Task.BANK_NEMUS);
            taskSequence.add(Task.TOTEM_6);
            taskSequence.add(Task.TOTEM_7);
            taskSequence.add(Task.TOTEM_8);
        } else if (agilityLevel >= 41) {
            taskSequence.add(Task.BANK_NEMUS);
            taskSequence.add(Task.TOTEM_6);
            taskSequence.add(Task.TOTEM_7);
            taskSequence.add(Task.TOTEM_8);
            taskSequence.add(Task.EXIT_ROCKS_DOWN);
            taskSequence.add(Task.EXIT_WALL_UP);
        } else {
            taskSequence.add(Task.BANK_AUBURNVALE);
            taskSequence.add(Task.TOTEM_1);
            taskSequence.add(Task.TOTEM_8);
            taskSequence.add(Task.TOTEM_7);
        }
        log("SYSTEM", "Constructed sequence: " + taskSequence.size() + " steps.");
    }

    private void executeTask(Task task) {
        boolean completed = false;
        switch (task) {
            case EMPTY_BASKET -> completed = handleEmptyBasket();
            case BANK_AUBURNVALE -> completed = handleBanking(AreaManager.AUBURNVALE_BANK);
            case BANK_NEMUS -> completed = handleBanking(AreaManager.NEMUS_BANK);
            case TOTEM_1 -> completed = handleTotem(AreaManager.TOTEM_01);
            case TOTEM_2 -> completed = handleTotem(AreaManager.TOTEM_02);
            case TOTEM_3 -> completed = handleTotem(AreaManager.TOTEM_03);
            case TOTEM_4 -> completed = handleTotem(AreaManager.TOTEM_04);
            case TOTEM_5 -> completed = handleTotem(AreaManager.TOTEM_05);
            case TOTEM_6 -> completed = handleTotem(AreaManager.TOTEM_06);
            case TOTEM_7 -> completed = handleTotem(AreaManager.TOTEM_07);
            case TOTEM_8 -> completed = handleTotem(AreaManager.TOTEM_08);

            case SHORTCUT_LOG_1_TO_2 -> completed = performShortcut(TILE_LOG_1_2, "Log balance", "Walk-across", AreaManager.WALK_ACROSS_TOTE01SIDE);
            case SHORTCUT_LOG_4_TO_5 -> completed = performShortcut(TILE_LOG_4_5, "Log balance", "Walk-across", AreaManager.WALK_ACROSS_TOTE04SIDE);
            case SHORTCUT_MAIN_WALL_TO_NEMUS -> completed = performWallJump(TILE_WALL_MAIN, "Broken wall", AreaManager.NEMUS_BANK, WALL_ZONE_MAIN_ROUTE);
            case EXIT_ROCKS_DOWN -> completed = performRocksDown();
            case EXIT_WALL_UP -> completed = performWallJump(TILE_WALL_OAK, "Broken Wall", AreaManager.NEMUS_BANK, WALL_ZONE_OAK);
        }

        if (completed) {
            sequenceIndex++;
            log("Sequence completed: " + task + ". Step " + sequenceIndex);

            lastAction = "None";
            persistentSpiritCache.clear();
            spiritSearchStart = 0;
        }
    }

    /*
     * 1. Check "Decorate" state. Wait for finish -> update totem count.
     * 2. Check "Scan Mode" (active if initial scan failed).
     * 3. Find Totem object in the target area.
     * 4. Walk to area if out of range.
     * 5. Check resources (logs/items).
     * 6. Dialogue open? -> Call handleSpiritDialoguePhase().
     * 7. No dialogue? -> Interact (Build/Carve/Decorate).
     */

    private boolean handleTotem(Area area) {
        if (lastAction.equals("Decorate")) {
            boolean messageFound = ctx.pollFramesUntil(() -> {
                UIResultList<String> result = ctx.getWidgetManager().getChatbox().getText();
                if (result == null) return false;
                List<String> chatLines = result.asList();
                if (chatLines == null || chatLines.isEmpty()) return false;
                int limit = Math.min(3, chatLines.size());
                for (int i = 0; i < limit; i++) {
                    String line = chatLines.get(i);
                    if (line != null && line.contains("You add the final decoration")) return true;
                }
                return false;
            }, 15000, false, false);

            if (messageFound) {
                totemsCompleted++;
                ctx.pollFramesHuman(() -> false, 600, false);
            }
            if (offeringActive) handleOfferingPhase(area);
            lastAction = "None";
            committedToTotem = false;
            persistentSpiritCache.clear();
            return true;
        }

        if (scanningAfterMove) {
            if (System.currentTimeMillis() - scanningModeStartTime > 20000) {
                log("SCAN MODE FAILED: Could not find spirits even after moving. Retrying interaction.");
                scanningAfterMove = false;
            }

            else if (persistentSpiritCache.size() >= 3) {
                log("SCAN SUCCESS: Found all 3 spirits! Proceeding to interact.");
                scanningAfterMove = false;
            }

            else {
                List<String> found = runOCRReturnList();
                for (String s : found) {
                    if (persistentSpiritCache.add(s)) {
                        log("Scanned after move: " + s + " (" + persistentSpiritCache.size() + "/3)");
                    }
                }

                ctx.pollFramesHuman(() -> false, 200, false);
                return false;
            }
        }

        RSObject totem = ctx.getObjectManager().getRSObject(obj ->
                obj.getName() != null && obj.getName().toLowerCase().contains("totem") && area.contains(obj.getWorldPosition()));

        boolean isInPosition = (totem != null && isObjectOnScreen(totem) && totem.distance(ctx.getWorldPosition()) < 8);

        if (!area.contains(ctx.getWorldPosition()) && !isInPosition) {
            committedToTotem = false;
            persistentSpiritCache.clear();
            com.osmb.api.walker.WalkConfig.Builder builder = new com.osmb.api.walker.WalkConfig.Builder().tileRandomisationRadius(1);
            builder.breakCondition(() -> {
                if (area.contains(ctx.getWorldPosition())) return true;
                return totem != null && isObjectOnScreen(totem) && totem.distance(ctx.getWorldPosition()) < 8;
            });

            if (ctx.getLastPositionChangeMillis() > 1500) {
                ctx.getWalker().walkTo(area.getRandomPosition(), builder.build());
            }
            return false;
        }

        if (!committedToTotem) {
            int logs = getInventoryCount(selectedLogID);
            if (usePreMadeItems) {
                int items = getCombinedPremadeCount();
                if (items < 4 || logs < 1) {
                    if (area.equals(AreaManager.TOTEM_05)) { committedToTotem = false; return true; }
                    log("Not resources found (Total Items: " + items + "). Returning to bank.");
                    sequenceIndex = 0;
                    return false;
                }
            } else {
                int bows = getInventoryCount(selectedUnstrungID);
                if (bows < 4) {
                    if (logs < 5) {
                        if (area.equals(AreaManager.TOTEM_05)) { committedToTotem = false; return true; }
                        log("Insufficient logs. Reset.");
                        sequenceIndex = 0;
                        return false;
                    }
                    if (FletchDecoration()) return false;
                    return false;
                } else if (logs < 1) {
                    if (area.equals(AreaManager.TOTEM_05)) { committedToTotem = false; return true; }
                    sequenceIndex = 0;
                    return false;
                }
            }
            committedToTotem = true;
            persistentSpiritCache.clear();
        }

        if (ctx.getWidgetManager().getDialogue().isVisible()) {
            handleSpiritDialoguePhase();
            lastAction = "None";
            return false;
        }

        if (totem != null) {
            if (isObjectOnScreen(totem) || totem.distance(ctx.getWorldPosition()) < 10) {
                if (!lastAction.equals("Decorate") && !lastAction.equals("Build") && !lastAction.equals("Carve")) {
                    interactWithTotem(totem);
                    return false;
                }
            } else {
                ctx.getWalker().walkTo(totem.getWorldPosition());
            }
        }

        if (lastAction.equals("Build") || lastAction.equals("Carve")) {
            boolean appeared = ctx.pollFramesUntil(() -> ctx.getWidgetManager().getDialogue().isVisible(), 4000, false, false);
            if (!appeared) lastAction = "None";
            return false;
        }
        return false;
    }

    private void interactWithTotem(RSObject totem) {
        MenuHook reactiveHook = menuEntries -> {
            for (MenuEntry entry : menuEntries) {
                String action = entry.getAction();
                if (action == null) continue;
                action = action.toLowerCase();
                if (action.contains("build")) { lastAction = "Build"; return entry; }
                if (action.contains("carve")) { lastAction = "Carve"; return entry; }
                if (action.contains("decorate")) { lastAction = "Decorate"; return entry; }
            }
            return null;
        };
        boolean interacted = totem.interact(reactiveHook);
        if (!interacted) {
            if (totem.getWorldPosition().distanceTo(ctx.getWorldPosition()) > 2) ctx.getWalker().walkTo(totem.getWorldPosition());
            else { Polygon poly = totem.getConvexHull(); if (poly != null) ctx.getFinger().tapGameScreen(poly.getResized(0.8)); }
        }
    }

    private boolean performShortcut(WorldPosition tile, String name, String action, Area targetArea) {
        if (targetArea.contains(ctx.getWorldPosition())) return true;

        RSObject obj = ctx.getObjectManager().getRSObject(o ->
                o.getName() != null && o.getName().equalsIgnoreCase(name) &&
                        o.getWorldPosition().distanceTo(tile) == 0);

        if (obj != null && (isObjectOnScreen(obj) || obj.distance(ctx.getWorldPosition()) < 10)) {
            if (obj.interact(action)) {
                boolean arrived = ctx.pollFramesUntil(() -> targetArea.contains(ctx.getWorldPosition()), 15000, false, false);
                if (arrived) {
                    ctx.pollFramesHuman(() -> false, 800, false);
                    return true;
                }
            }
        } else {
            com.osmb.api.walker.WalkConfig.Builder builder = new com.osmb.api.walker.WalkConfig.Builder().tileRandomisationRadius(1);
            builder.breakCondition(() -> {
                RSObject o = ctx.getObjectManager().getRSObject(x -> x.getName() != null && x.getName().equalsIgnoreCase(name) && x.getWorldPosition().distanceTo(tile) == 0);
                return isObjectOnScreen(o);
            });
            ctx.getWalker().walkTo(tile, builder.build());
        }
        return false;
    }

    private boolean performWallJump(WorldPosition objectTile, String name, Area destinationArea, Area walkArea) {
        if (destinationArea.contains(ctx.getWorldPosition())) return true;

        if (!walkArea.contains(ctx.getWorldPosition())) {
            com.osmb.api.walker.WalkConfig.Builder builder = new com.osmb.api.walker.WalkConfig.Builder().tileRandomisationRadius(1);
            builder.breakCondition(() -> walkArea.contains(ctx.getWorldPosition()));
            ctx.getWalker().walkTo(walkArea.getRandomPosition(), builder.build());
            return false;
        }

        RSObject wall = ctx.getObjectManager().getRSObject(o ->
                o.getName() != null && o.getName().equalsIgnoreCase(name) && o.getWorldPosition().distanceTo(objectTile) == 0);

        if (wall != null) {
            if (isObjectOnScreen(wall) || wall.distance(ctx.getWorldPosition()) < 10) {
                if (wall.interact("Climb-over")) {
                    boolean arrived = ctx.pollFramesUntil(() -> destinationArea.contains(ctx.getWorldPosition()), 15000, false, false);
                    if (arrived) {
                        ctx.pollFramesHuman(() -> false, 800, false);
                        return true;
                    }
                }
            } else {
                ctx.getWalker().walkTo(objectTile);
            }
        } else {
            ctx.getWalker().walkTo(objectTile);
        }
        return false;
    }

    private boolean performRocksDown() {
        if (AreaManager.CLIMB_AREA.contains(ctx.getWorldPosition())) return true;

        RSObject obj = ctx.getObjectManager().getRSObject(o ->
                o.getName() != null && o.getName().equalsIgnoreCase("Rocks") && o.getWorldPosition().distanceTo(TILE_ROCKS) == 0);

        if (obj != null) {
            if (isObjectOnScreen(obj) || obj.getWorldPosition().distanceTo(ctx.getWorldPosition()) < 10) {
                if (obj.interact("Climb")) {
                    return ctx.pollFramesUntil(() -> AreaManager.CLIMB_AREA.contains(ctx.getWorldPosition()), 15000, false, false);
                }
            } else {
                ctx.getWalker().walkTo(TILE_ROCKS);
            }
        } else {
            ctx.getWalker().walkTo(TILE_ROCKS);
        }
        return false;
    }

    private boolean handleBanking(Area bankArea) {
        int requiredFull = 26;
        boolean hasKnife = fletchingKnifeEquipped || getInventoryCount(KNIFE_ID) > 0;

        boolean hasBasketItem = false;
        var checkBasket = ctx.getWidgetManager().getInventory().search(BASKET_IDS);
        if (checkBasket != null) {
            for (int id : BASKET_IDS) {
                if (checkBasket.getAmount(id) > 0) {
                    hasBasketItem = true;
                    break;
                }
            }
        }
        boolean basketReady = !useLogBasket || hasBasketItem;

        if (usePreMadeItems) {
            int currentLogs = getInventoryCount(selectedLogID);
            if (hasKnife && currentLogs >= 5 && getCombinedPremadeCount() >= 20 && basketReady) {
                if (ctx.getWidgetManager().getBank().isVisible()) ctx.getWidgetManager().getBank().close();
                handleOfferingFlag(bankArea);
                return true;
            }
        }
        else if (getInventoryCount(selectedLogID) >= requiredFull && basketReady) {
            if (hasKnife) {
                if (ctx.getWidgetManager().getBank().isVisible()) ctx.getWidgetManager().getBank().close();
                handleOfferingFlag(bankArea);
                return true;
            }
        }

        String npcName = (bankArea == AreaManager.NEMUS_BANK) ? "buffalo" : "Bank booth";
        java.util.function.Predicate<RSObject> bankQuery = o ->
                o != null && o.getName() != null &&
                        o.getName().toLowerCase().contains(npcName.toLowerCase()) &&
                        bankArea.contains(o.getWorldPosition());

        RSObject bankObj = ctx.getObjectManager().getObjects(bankQuery).stream()
                .min(Comparator.comparingDouble(o -> o.getWorldPosition().distanceTo(ctx.getWorldPosition()))).orElse(null);

        if (!ctx.getWidgetManager().getBank().isVisible()) {
            if (bankObj != null && isObjectOnScreen(bankObj) && bankObj.distance(ctx.getWorldPosition()) < 14) {
                boolean clicked = bankObj.interact(menuEntries -> {
                    for (MenuEntry entry : menuEntries) {
                        String a = entry.getAction();
                        if (a != null) {
                            String lower = a.toLowerCase();
                            if (lower.contains("bank") || lower.contains("use") || lower.contains("buffalo")) return entry;
                        }
                    }
                    return null;
                });
                if (clicked) {
                    if (ctx.pollFramesUntil(() -> ctx.getWidgetManager().getBank().isVisible(), 5000, false, false)) countOfferingsNow();
                }
                return false;
            } else {
                if (bankArea.contains(ctx.getWorldPosition())) {
                    if (bankObj != null) ctx.getWalker().walkTo(bankObj.getWorldPosition());
                    else ctx.getWalker().walkTo(bankArea.getRandomPosition());
                    return false;
                }
                com.osmb.api.walker.WalkConfig.Builder builder = new com.osmb.api.walker.WalkConfig.Builder().tileRandomisationRadius(2);
                builder.breakCondition(() -> {
                    RSObject b = ctx.getObjectManager().getObjects(bankQuery).stream()
                            .min(Comparator.comparingDouble(o -> o.getWorldPosition().distanceTo(ctx.getWorldPosition()))).orElse(null);
                    return isObjectOnScreen(b) && b.distance(ctx.getWorldPosition()) < 8;
                });
                ctx.getWalker().walkTo(bankArea.getRandomPosition(), builder.build());
                return false;
            }
        }

        Set<Integer> itemsToKeep = new HashSet<>();
        itemsToKeep.add(selectedLogID);
        if (!fletchingKnifeEquipped) itemsToKeep.add(KNIFE_ID);
        if (usePreMadeItems) itemsToKeep.addAll(premadeItemIds);
        if (useLogBasket) itemsToKeep.addAll(BASKET_IDS);

        ctx.getWidgetManager().getBank().depositAll(itemsToKeep);
        ctx.pollFramesHuman(() -> false, 600, false);

        if (useLogBasket) {
            boolean basketFound = false;
            var result = ctx.getWidgetManager().getInventory().search(BASKET_IDS);
            if (result != null) {
                for (int id : BASKET_IDS) {
                    if (result.getAmount(id) > 0) {
                        basketFound = true;
                        break;
                    }
                }
            }
            if (!basketFound) {
                log("Log Basket Mode ON, but Basket is MISSING from Inventory.");
                ctx.getWidgetManager().getBank().close();
                ctx.stop();
                return true;
            }
        }

        if (!fletchingKnifeEquipped && getInventoryCount(KNIFE_ID) == 0) {
            var check = ctx.getWidgetManager().getBank().search(Set.of(KNIFE_ID));
            if (check != null && check.getAmount(KNIFE_ID) > 0) {
                ctx.getWidgetManager().getBank().withdraw(KNIFE_ID, 1);
                ctx.pollFramesHuman(() -> false, 1200, false);
            } else {
                log("No Knife in bank.");
                ctx.stop();
                return true;
            }
        }

        if (usePreMadeItems) {
            int logsTarget = 5;
            int currentLogs = getInventoryCount(selectedLogID);

            if (currentLogs < logsTarget) {
                var lCheck = ctx.getWidgetManager().getBank().search(Set.of(selectedLogID));
                if (lCheck == null || lCheck.getAmount(selectedLogID) <= 0) { log("No logs"); ctx.stop(); return true; }
                ctx.getWidgetManager().getBank().withdraw(selectedLogID, logsTarget - currentLogs);
                ctx.pollFramesUntil(() -> getInventoryCount(selectedLogID) >= logsTarget, 2000, false, false);
            } else if (currentLogs > logsTarget) {
                ctx.getWidgetManager().getBank().deposit(selectedLogID, currentLogs - logsTarget);
                ctx.pollFramesUntil(() -> getInventoryCount(selectedLogID) <= logsTarget, 2000, false, false);
            }

            for (int id : premadeItemIds) {
                if (getCombinedPremadeCount() >= 20) break;

                var bankCheck = ctx.getWidgetManager().getBank().search(Set.of(id));
                if (bankCheck != null && bankCheck.getAmount(id) > 0) {

                    log("Cascading Item ID: " + id);

                    ctx.getWidgetManager().getBank().withdraw(id, 28);

                    final int idFinal = id;
                    int prevCount = getInventoryCount(idFinal);
                    ctx.pollFramesUntil(() -> getInventoryCount(idFinal) > prevCount, 2000, false, false);
                }
            }

            boolean exitBasketCheck = !useLogBasket || (ctx.getWidgetManager().getInventory().search(BASKET_IDS) != null);

            if (exitBasketCheck && getInventoryCount(selectedLogID) >= 5 && getCombinedPremadeCount() >= 20) {
                handleOfferingFlag(bankArea);
                return true;
            } else {
                if (getCombinedPremadeCount() < 20) {
                    log("Ran out of ALL selected Pre-Made items (Have " + getCombinedPremadeCount() + "). Stopping.");
                    ctx.stop();
                    return true;
                }
            }

        } else {
            if (getInventoryCount(selectedLogID) < requiredFull) {
                var lCheck = ctx.getWidgetManager().getBank().search(Set.of(selectedLogID));
                if (lCheck == null || lCheck.getAmount(selectedLogID) <= 0) { log("No logs"); ctx.stop(); return true; }
                ctx.getWidgetManager().getBank().withdraw(selectedLogID, 27);
                ctx.pollFramesUntil(() -> getInventoryCount(selectedLogID) >= requiredFull, 1200, false, false);
            }

            if (useLogBasket) {
                if (ctx.getWidgetManager().getInventory().search(BASKET_IDS) == null) return false;
                ctx.getWidgetManager().getBank().close();
                ctx.pollFramesHuman(() -> false, 150 + random.nextInt(250), false);

                var result = ctx.getWidgetManager().getInventory().search(BASKET_IDS);
                ItemSearchResult basket = null;
                if (result != null) {
                    for (int id : BASKET_IDS) { basket = result.getItem(id); if (basket != null) break; }
                }

                if (basket != null) {
                    int logsAntes = getInventoryCount(selectedLogID);
                    if (basket.interact("Fill")) {
                        ctx.pollFramesUntil(() -> getInventoryCount(selectedLogID) < logsAntes, 2000, false, false);
                        ctx.pollFramesHuman(() -> false, 100 + random.nextInt(200), false);
                    }

                    if (getInventoryCount(selectedLogID) < requiredFull) {
                        if (bankObj != null && (bankObj.interact("Bank") || bankObj.interact("Use"))) {
                            ctx.pollFramesUntil(() -> ctx.getWidgetManager().getBank().isVisible(), 5000, false, false);
                            ctx.pollFramesUntil(() -> false, 450 + random.nextInt(1000), false);
                            ctx.getWidgetManager().getBank().withdraw(selectedLogID, 27);
                            ctx.pollFramesUntil(() -> getInventoryCount(selectedLogID) >= 26, 2000, false, false);
                            ctx.pollFramesUntil(() -> false, 250 + random.nextInt(650), false);
                            ctx.getWidgetManager().getBank().close();
                        }
                    }
                }

                boolean finalBasketConfirm = false;
                var finalCheck = ctx.getWidgetManager().getInventory().search(BASKET_IDS);
                if (finalCheck != null) {
                    for(int id : BASKET_IDS) if(finalCheck.getAmount(id) > 0) finalBasketConfirm = true;
                }

                if (finalBasketConfirm && getInventoryCount(selectedLogID) >= requiredFull) {
                    handleOfferingFlag(bankArea);
                    return true;
                } else {
                    return false;
                }
            }

            if (!useLogBasket && getInventoryCount(selectedLogID) >= requiredFull) {
                handleOfferingFlag(bankArea);
                return true;
            }
        }
        return false;
    }

    private void handleOfferingFlag(Area currentBank) {
        boolean increment = false;
        boolean Nemus = currentBank.equals(AreaManager.NEMUS_BANK);
        boolean Auburn = currentBank.equals(AreaManager.AUBURNVALE_BANK);

        if (selectedLogID != 1521) {
            if (Auburn) increment = true;
        } else {
            if (agilityLevel >= 41 && Nemus) increment = true;
            else if (agilityLevel < 41 && Auburn) increment = true;
        }

        if (increment) {
            tripsCounter++;
            log("Trip count (" + tripsCounter + "/" + tripsToNextOffering + ")");
            if (tripsCounter >= tripsToNextOffering) {
                offeringActive = true;
                tripsCounter = 0;
                tripsToNextOffering = 5 + random.nextInt(6);
                log( "Next Offering in " + tripsToNextOffering + " trips.");
            } else {
                offeringActive = false;
            }
        }
    }

    private boolean trySetupEnvironment() {
        if (!configLoaded) return false;
        try {
            if (ctx.getWidgetManager() != null && ctx.getWidgetManager().getSkillTab() != null) {
                var skill = ctx.getWidgetManager().getSkillTab().getSkillLevel(SkillType.AGILITY);
                if (skill != null && skill.getBoostedLevel() > 0) {
                    this.agilityLevel = skill.getBoostedLevel();
                    log( "Agility " + agilityLevel);
                    ensureGameTabOpen();
                    return true;
                }
            }
        } catch (Exception e) {}
        return false;
    }

    private boolean FletchDecoration() {
        ctx.getWidgetManager().getInventory().open();
        ItemSearchResult knife = ctx.getWidgetManager().getInventory().search(Set.of(KNIFE_ID)).getItem(KNIFE_ID);
        ItemSearchResult FletchItem = ctx.getWidgetManager().getInventory().search(Set.of(selectedLogID)).getItem(selectedLogID);

        if (knife != null && FletchItem != null) {
            ctx.getFinger().tap(knife.getBounds());
            ctx.pollFramesHuman(() -> false, 250, false);
            ctx.getFinger().tap(FletchItem.getBounds());
            ctx.pollFramesUntil(() -> ctx.getWidgetManager().getDialogue().isVisible(), 5000, false, false);

            if (ctx.getWidgetManager().getDialogue().selectItem(selectedUnstrungID)) {
                return ctx.pollFramesUntil(() -> getInventoryCount(selectedUnstrungID) >= 4, 20000, false, false);
            }
        }
        return false;
    }

    private boolean isSlotSelected(Rectangle bounds) {
        SearchablePixel redPixel = new SearchablePixel(new Color(180, 50, 50).getRGB(), new SingleThresholdComparator(50), ColorModel.RGB);
        return ctx.getPixelAnalyzer().findPixels(bounds, redPixel).size() > 15;
    }

    /*
     * 1. Start timer when dialogue opens.
     * 2. Run OCR to detect spirit names on screen.
     * 3. If timeout (>20s) and still missing spirits:
     * - Close inventory to clear line of sight.
     * - Calc random movement to shift position/camera.
     * - Enable "Scan Mode" for the next loop.
     * 4. If all 3 spirits found:
     * - Get slot bounds for each animal.
     * - Click the slot if not already selected.
     */
    private void handleSpiritDialoguePhase() {
        if (spiritSearchStart == 0) {
            spiritSearchStart = System.currentTimeMillis();
        }

        if (persistentSpiritCache.size() < 3) {
            List<String> frameResults = runOCRReturnList();
            for (String spirit : frameResults) {
                if (persistentSpiritCache.add(spirit)) {
                    log("New spirit detected: " + spirit + " (Cache: " + persistentSpiritCache.size() + "/3)");
                }
            }
        }

        debugSpirits = new ArrayList<>(persistentSpiritCache);

        if (persistentSpiritCache.size() < 3 && (System.currentTimeMillis() - spiritSearchStart > 20000)) {
            log("TIMEOUT: Spirits not found in 20s. Repositioning...");

            if (ctx.getWidgetManager().getInventory().isOpen()) {
                log("Closing Inventory to clear view...");
                ctx.getWidgetManager().getInventory().close();
                ctx.pollFramesHuman(() -> !ctx.getWidgetManager().getInventory().isOpen(), 1000, false);
            }

            log("Shifting position...");
            WorldPosition myPos = ctx.getWorldPosition();
            WorldPosition targetPos = null;

            for (int i = 0; i < 10; i++) {
                int dx = random.nextInt(9) - 4;
                int dy = random.nextInt(9) - 4;
                if (Math.abs(dx) < 2 && Math.abs(dy) < 2) continue;
                targetPos = new WorldPosition(myPos.getX() + dx, myPos.getY() + dy, myPos.getPlane());
                break;
            }
            if (targetPos == null) {
                targetPos = new WorldPosition(myPos.getX() + 3, myPos.getY(), myPos.getPlane());
            }

            com.osmb.api.walker.WalkConfig config = new com.osmb.api.walker.WalkConfig.Builder()
                    .breakDistance(1)
                    .build();

            ctx.getWalker().walkTo(targetPos, config);

            log("Entering SCAN MODE. Will not click totem until spirits are found.");
            scanningAfterMove = true;
            scanningModeStartTime = System.currentTimeMillis();

            spiritSearchStart = 0;
            lastAction = "None";

            ctx.pollFramesHuman(() -> false, 1500, false);
            return;
        }

        if (persistentSpiritCache.size() >= 3) {
            scanningAfterMove = false;
            spiritSearchStart = 0;

            Rectangle diagBounds = ctx.getWidgetManager().getDialogue().getBounds();
            if (diagBounds == null || diagBounds.width <= 0) {
                ctx.pollFramesHuman(() -> false, 200, false);
                return;
            }

            log("All 3 Spirits found: " + persistentSpiritCache + ". Selecting...");

            for (String spirit : persistentSpiritCache) {
                if (!ctx.getWidgetManager().getDialogue().isVisible()) break;

                Integer slotObj = SLOT_MAP.get(spirit.toLowerCase().trim());
                if (slotObj == null) {
                    log("ERROR: Spirit '" + spirit + "' not found in map.");
                    continue;
                }

                int slot = slotObj;
                Rectangle slotRect = getSlotAbsoluteBounds(slot);

                if (slotRect == null) continue;

                if (isSlotSelected(slotRect)) continue;

                if (ctx.getFinger().tap(true, slotRect)) {
                    ctx.pollFramesUntil(() -> isSlotSelected(slotRect), 1500, false, false);
                }

                ctx.pollFramesHuman(() -> false, 150 + random.nextInt(150), false);
            }
        } else {
            ctx.pollFramesHuman(() -> false, 100, false);
        }
    }

    private void countOfferingsNow() {
        var result = ctx.getWidgetManager().getInventory().search(Set.of(31054));
        if (result != null) {
            ItemSearchResult offering = result.getItem(31054);
            if (offering != null) {
                int amount = result.getAmount(offering.getId());
                if (amount > 0) {
                    offeringsCollected += amount;
                    log( "Found " + amount + " offerings. Total: " + offeringsCollected);
                }
            }
        }
    }

    /*
     * 1. Check if the basket exists in inventory.
     * 2. Check log count. If > 24, we're full enough -> return true (task done).
     * 3. If low on logs:
     * - Interact with basket ("Empty" or "Check").
     * - Handle confirmation dialogs if they pop up.
     * - Wait for inventory to fill.
     */
    private boolean handleEmptyBasket() {
        if (!useLogBasket) return true;
        var searchResult = ctx.getWidgetManager().getInventory().search(BASKET_IDS);
        if (searchResult == null) {
            log( "Script tried to empty basket, but it's MISSING from inventory.");
            ctx.stop();
            return true;
        }
        if (getInventoryCount(selectedLogID) > 24) return true;

        if (ctx.getWidgetManager().getDialogue().isVisible()) {
            var type = ctx.getWidgetManager().getDialogue().getDialogueType();
            if (type == com.osmb.api.ui.chatbox.dialogue.DialogueType.TAP_HERE_TO_CONTINUE) {
                ctx.getWidgetManager().getDialogue().continueChatDialogue();
                return false;
            }
            if (type == com.osmb.api.ui.chatbox.dialogue.DialogueType.TEXT_OPTION) {
                ctx.getWidgetManager().getDialogue().selectOption("Yes");
                ctx.pollFramesUntil(() -> getInventoryCount(selectedLogID) == 0, 3000, false, false);
                return false;
            }
        }

        ItemSearchResult basket = null;
        for (int id : BASKET_IDS) {
            basket = searchResult.getItem(id);
            if (basket != null) break;
        }

        if (basket != null) {
            if (basket.interact("Check") || basket.interact("Empty")) {
                ctx.pollFramesUntil(() -> ctx.getWidgetManager().getDialogue().isVisible(), 3000, false, false);
            }
        }
        return false;
    }

    private void checkAntiLoop() {
        int currentXp = getFletchingXpGained();
        if (currentXp > lastXpCheck) {
            lastXpCheck = currentXp;
            lastProgressTime = System.currentTimeMillis();
        }

        long timeIdle = System.currentTimeMillis() - lastProgressTime;

        if (timeIdle > 600000) {
            log("No progress detected in 10 mins. Stopping script.");
            ctx.stop();
        }
    }

    private void handleOfferingPhase(Area area) {
        log( "🔍 Looking 'Offering site'" + area.toString());
        RSObject offering = ctx.getObjectManager().getObjects(o ->
                        o != null && o.getName() != null && o.getName().equalsIgnoreCase("Offering site") && area.contains(o.getWorldPosition()))
                .stream()
                .min(Comparator.comparingDouble(o -> o.getWorldPosition().distanceTo(ctx.getWorldPosition())))
                .orElse(null);

        if (offering != null) {
            MenuHook claimHook = menuEntries -> {
                for (MenuEntry entry : menuEntries) {
                    if (entry.getAction() != null && entry.getAction().toLowerCase().contains("claim")) {
                        return entry;
                    }
                }
                return null;
            };
            if (offering.interact(claimHook)) {
                ctx.pollFramesUntil(() -> false, 1500, false, false);
            }
        } else {
            log( "No 'Offering Site' was found within the assigned area.");
        }
    }

    /*
     * 1. Find pixels with TEXT_COLOR_ID on the entire screen.
     * 2. Group close pixels into clusters/rectangles.
     * 3. Use OCR to read the text inside those areas.
     * 4. Match text against valid spirits names (jaguar, snake, etc.).
     * 5. Return the list of found spirits.
     */
    private List<String> runOCRReturnList() {
        Set<String> found = new LinkedHashSet<>();
        com.osmb.api.visual.SearchablePixel pixel = new com.osmb.api.visual.SearchablePixel(TEXT_COLOR_ID, new com.osmb.api.visual.color.tolerance.impl.SingleThresholdComparator(85), com.osmb.api.visual.color.ColorModel.RGB);
        List<Point> pts = ctx.getPixelAnalyzer().findPixels(ctx.getScreen().getBounds(), pixel);
        if (pts.isEmpty()) return new ArrayList<>();
        for (Rectangle blob : clusterPoints(pts, 12)) {
            Rectangle screen = ctx.getScreen().getBounds();
            String text = ctx.getOCR().getText(Font.STANDARD_FONT_BOLD, new Rectangle(Math.max(0, blob.x - 2), Math.max(0, blob.y - 2), Math.min(screen.width - blob.x, blob.width + 4), Math.min(screen.height - blob.y, blob.height + 4)), TEXT_COLOR_ID);
            if (text != null) {
                String clean = text.toLowerCase().trim();
                for (String t : TARGET_NAMES) if (clean.contains(t)) found.add(t);
            }
        }
        return new ArrayList<>(found);
    }

    private List<Rectangle> clusterPoints(List<Point> pts, int maxDist) {
        List<Rectangle> clusters = new ArrayList<>();
        for (Point p : pts) {
            boolean added = false;
            for (int i = 0; i < clusters.size(); i++) {
                Rectangle r = clusters.get(i);
                if (p.x >= r.x - maxDist && p.x <= r.x + r.width + maxDist && p.y >= r.y - maxDist && p.y <= r.y + r.height + maxDist) {
                    clusters.set(i, r.union(new Rectangle(p.x, p.y, 1, 1))); added = true; break;
                }
            }
            if (!added) clusters.add(new Rectangle(p.x, p.y, 1, 1));
        }
        return clusters;
    }

    private Rectangle getSlotAbsoluteBounds(int slot) {
        Rectangle diag = ctx.getWidgetManager().getDialogue().getBounds();
        if (diag == null) return null;
        return new Rectangle(diag.x + (FIRST_SLOT_BOUNDS.x + ((slot - 1) * SLOT_STEP_X)) + 25, diag.y + FIRST_SLOT_BOUNDS.y + 15, 40, 40);
    }

    private int getInventoryCount(int id) {
        var r = ctx.getWidgetManager().getInventory().search(Set.of(id)); return r != null ? r.getAmount(id) : 0;
    }

    private boolean isObjectOnScreen(RSObject obj) {
        return obj != null && obj.isInteractableOnScreen();
    }

    public boolean canHopWorlds() {
        if (ctx.getWidgetManager().getBank().isVisible()) return false;
        if (currentTask == null) return true;
        switch (currentTask) {
            case SHORTCUT_LOG_1_TO_2:
            case SHORTCUT_LOG_4_TO_5:
            case EXIT_ROCKS_DOWN:
                return false;
            case TOTEM_1: return !AreaManager.TOTEM_01.contains(ctx.getWorldPosition());
            case TOTEM_2: return !AreaManager.TOTEM_02.contains(ctx.getWorldPosition());
            case TOTEM_3: return !AreaManager.TOTEM_03.contains(ctx.getWorldPosition());
            case TOTEM_4: return !AreaManager.TOTEM_04.contains(ctx.getWorldPosition());
            case TOTEM_5: return !AreaManager.TOTEM_05.contains(ctx.getWorldPosition());
            case TOTEM_6: return !AreaManager.TOTEM_06.contains(ctx.getWorldPosition());
            case TOTEM_7: return !AreaManager.TOTEM_07.contains(ctx.getWorldPosition());
            case TOTEM_8: return !AreaManager.TOTEM_08.contains(ctx.getWorldPosition());
            default: return true;
        }
    }

    public boolean canAFK() { return canHopWorlds(); }

    public int[] regionsToPrioritise() { return new int[]{5427, 5428, 5684, 5683}; }

    public void onPaint(Canvas c) {
        int x = 15, y = 35;
        int panelW = 320;
        int panelH = 320;
        int padding = 15;
        int colValueX = x + 155;

        int COL_BG = new Color(12, 12, 18, 240).getRGB();
        int COL_BORDER = new Color(0, 210, 211, 200).getRGB();
        int COL_LABEL = new Color(190, 190, 190).getRGB();
        int COL_VALUE = new Color(255, 255, 255).getRGB();

        java.awt.Font fontTitle = new java.awt.Font("Impact", java.awt.Font.ITALIC, 22);
        java.awt.Font fontBold = new java.awt.Font("SansSerif", java.awt.Font.BOLD, 13);

        c.fillRect(x, y, panelW, panelH, COL_BG);
        c.drawRect(x, y, panelW, panelH, COL_BORDER);
        drawCenteredText(c, "VALE TOTEMS PRO", x + panelW / 2, y + 28, fontTitle, new Color(0, 210, 211).getRGB());

        java.awt.Font fontSignature = new java.awt.Font("Arial", java.awt.Font.BOLD | java.awt.Font.ITALIC, 13);

        int signatureColor = new Color(0, 210, 211).getRGB();

        int sigX = x + (panelW / 2) + 70;
        int sigY = y + 45;
        c.drawText("By JOSE", sigX, sigY, signatureColor, fontSignature);

        int cursorY = y + 55;
        long elapsed = System.currentTimeMillis() - startTime;

        drawRow(c, "Version:", "v" + scriptVersion, x + padding, colValueX, cursorY, COL_LABEL, new Color(255, 200, 0).getRGB(), fontBold);
        cursorY += 20;
        drawRow(c, "Runtime:", formatTime(elapsed), x + padding, colValueX, cursorY, COL_LABEL, COL_VALUE, fontBold);
        cursorY += 20;

        String taskName = (currentTask != null) ? currentTask.name() : "IDLE";
        if (taskName.length() > 18) taskName = taskName.substring(0, 18) + "..";
        drawRow(c, "Task:", taskName, x + padding, colValueX, cursorY, COL_LABEL, new Color(255, 180, 50).getRGB(), fontBold);
        cursorY += 20;

        String modeText = usePreMadeItems ? "Pre-Made Items" : "Fletching Items";
        int modeColor = Color.GREEN.getRGB();
        drawRow(c, "Mode:", modeText, x + padding, colValueX, cursorY, COL_LABEL, modeColor, fontBold);
        cursorY += 20;

        drawRow(c, "Log Basket:", useLogBasket ? "TRUE" : "FALSE", x + padding, colValueX, cursorY, COL_LABEL, useLogBasket ? Color.GREEN.getRGB() : Color.RED.getRGB(), fontBold);
        cursorY += 20;

        drawRow(c, "Log:", logNameDisplay, x + padding, colValueX, cursorY, COL_LABEL, Color.GREEN.getRGB(), fontBold);
        cursorY += 20;

        String displayItemName = productDisplay;
        if (displayItemName.length() > 18) displayItemName = displayItemName.substring(0, 18) + "..";
        drawRow(c, "Selected item:", displayItemName, x + padding, colValueX, cursorY, COL_LABEL, new Color(0, 210, 211).getRGB(), fontBold);
        cursorY += 20;

        double tph = (elapsed > 0) ? (totemsCompleted * 3600000.0) / elapsed : 0;
        drawRow(c, "Totems completed:", totemsCompleted + " (" + String.format("%.1f", tph) + "/h)", x + padding, colValueX, cursorY, COL_LABEL, COL_VALUE, fontBold);
        cursorY += 20;

        drawRow(c, "Offerings:", String.valueOf(offeringsCollected), x + padding, colValueX, cursorY, COL_LABEL, COL_VALUE, fontBold);
        cursorY += 20;

        int tripsRemaining = tripsToNextOffering - tripsCounter;
        String offeringStatus = offeringActive ? "NOW!" : String.valueOf(tripsRemaining);
        int offeringColor = offeringActive ? Color.GREEN.getRGB() : Color.RED.getRGB();
        drawRow(c, "Next Offering:", offeringStatus + (offeringActive ? "" : " trips"), x + padding, colValueX, cursorY, COL_LABEL, offeringColor, fontBold);
        cursorY += 25;

        XPTracker tFletch = (fletchTracker != null) ? fletchTracker.getTracker() : null;
        int fLevel = (tFletch != null) ? tFletch.getLevel() : Math.max(1, cachedFletchLevel);
        int fProg = (tFletch != null) ? tFletch.getLevelProgressPercentage() : 0;
        String fRate = (tFletch != null) ? formatK(tFletch.getXpPerHour()) + " xp/h" : "0 xp/h";
        long fGained = (tFletch != null) ? (long) tFletch.getXpGained() : 0;
        String fDetails = "+" + formatK((int)fGained) + " XP | TTL: " + ((tFletch != null) ? tFletch.timeToNextLevelString() : "-");

        String urlFletch = "https://oldschool.runescape.wiki/images/Fletching_icon.png";
        Image iconF = getIcon("FLETCH_ICON", urlFletch);
        int sectionH = 55;
        drawSkillBar(c, "FLETCHING", fLevel, fRate, fDetails, fProg, new Color(0, 180, 200).getRGB(), iconF, x + padding, cursorY, panelW - (padding * 2), sectionH);

        drawGlobalStatsPanel(c, x + 330, y);
    }

    private void drawSkillBar(Canvas c, String skillName, int level, String rightText, String bottomText, int progressPercent, int barColor, Image icon, int x, int y, int w, int h) {
        java.awt.Font fontHead = new java.awt.Font("Arial", java.awt.Font.BOLD, 12);
        java.awt.Font fontRate = new java.awt.Font("Arial", java.awt.Font.BOLD, 12);
        java.awt.Font fontBar  = new java.awt.Font("Arial", java.awt.Font.BOLD, 11);
        java.awt.Font fontBottom = new java.awt.Font("Arial", java.awt.Font.PLAIN, 10);

        int barH = 18;
        int iconSize = 30;
        int iconCenteredY = y + (h / 2) - (iconSize / 2);

        if (icon != null) {
            try { c.drawAtOn(icon, x, iconCenteredY); } catch (Exception e) {}
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

        String progStr = progressPercent + "%";
        drawCenteredText(c, progStr, contentX + (contentW / 2), barY + 13, fontBar, Color.WHITE.getRGB());
        c.drawText(bottomText, contentX, barY + barH + 12, Color.LIGHT_GRAY.getRGB(), fontBottom);
    }

    private void drawRow(Canvas c, String label, String value, int xL, int xV, int y, int colL, int colV, java.awt.Font f) {
        c.drawText(label, xL, y, colL, f);
        c.drawText(value, xV, y, colV, f);
    }

    private void drawCenteredText(Canvas c, String text, int centerX, int y, java.awt.Font font, int color) {
        FontMetrics metrics = c.getFontMetrics(font);
        int x = centerX - (metrics.stringWidth(text) / 2);
        c.drawText(text, x, y, color, font);
    }

    private String formatK(int number) {
        if (number >= 1000) return String.format("%.1fk", number / 1000.0);
        return String.valueOf(number);
    }

    private String formatTime(long ms) {
        long hours = TimeUnit.MILLISECONDS.toHours(ms);
        long minutes = TimeUnit.MILLISECONDS.toMinutes(ms) % 60;
        long seconds = TimeUnit.MILLISECONDS.toSeconds(ms) % 60;
        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }

    private Image getIcon(String name, String urlAddress) {
        if (paintImages.containsKey(name)) return paintImages.get(name);
        try {
            URL url = new URL(urlAddress);
            BufferedImage bImg = ImageIO.read(url);
            if (bImg != null) {
                int w = bImg.getWidth();
                int h = bImg.getHeight();
                int[] pixels = new int[w * h];
                bImg.getRGB(0, 0, w, h, pixels, 0, w);
                Image img = new Image(pixels, w, h);
                paintImages.put(name, img);
                return img;
            }
        } catch (Exception e) { paintImages.put(name, null); }
        return null;
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

        public XPTracker getTracker() { return xpTracker; }

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
            Rectangle bounds = getXPDropsBounds();
            if (bounds == null) return null;
            boolean isMySkill = core.getImageAnalyzer().findLocation(bounds, skillSprite) != null;
            if (!isMySkill) return null;
            String xpText = core.getOCR().getText(Font.SMALL_FONT, bounds, -1).replaceAll("[^0-9]", "");
            if (xpText.isEmpty()) return null;
            try { return Integer.parseInt(xpText); } catch (NumberFormatException e) { return null; }
        }

        private Rectangle getXPDropsBounds() {
            Rectangle bounds = xpDropsComponent.getBounds();
            if (bounds == null) return null;
            ComponentSearchResult<Integer> result = xpDropsComponent.getResult();
            if (result.getComponentImage().getGameFrameStatusType() != 1) return null;
            return new Rectangle(bounds.x - 140, bounds.y - 1, 119, 38);
        }
    }

    private void drawGlobalStatsPanel(Canvas c, int x, int y) {
        int w = 210;
        int h = 190;

        int COL_BG = new Color(12, 12, 18, 240).getRGB();
        int COL_BORDER = new Color(255, 165, 0, 200).getRGB();
        int COL_TITLE = new Color(255, 165, 0).getRGB();
        int COL_VAL = Color.WHITE.getRGB();

        c.fillRect(x, y, w, h, COL_BG);
        c.drawRect(x, y, w, h, COL_BORDER);

        java.awt.Font fontTitle = new java.awt.Font("Impact", java.awt.Font.ITALIC, 16);
        java.awt.Font fontData = new java.awt.Font("Arial", java.awt.Font.BOLD, 12);

        drawCenteredText(c, "GLOBAL STATS", x + (w/2), y + 20, fontTitle, COL_TITLE);
        c.drawLine(x + 10, y + 25, x + w - 10, y + 25, Color.GRAY.getRGB());

        int curY = y + 45;
        int gap = 22;
        int pad = 10;
        int valX = x + 115;

        c.drawText("Active accounts:", x + pad, curY, Color.GREEN.getRGB(), fontData);
        c.drawText(String.valueOf(GlobalStatsTracker.globalActiveUsers), valX, curY, COL_VAL, fontData);
        curY += gap;

        c.drawText("Totems done:", x + pad, curY, new Color(0, 210, 211).getRGB(), fontData);
        c.drawText(formatBigNumber(GlobalStatsTracker.globalTotalTotems), valX, curY, COL_VAL, fontData);
        curY += gap;

        c.drawText("Offerings:", x + pad, curY, new Color(255, 215, 0).getRGB(), fontData);
        c.drawText(formatBigNumber(GlobalStatsTracker.globalTotalOfferings), valX, curY, COL_VAL, fontData);
        curY += gap;

        c.drawText("Global XP/Hr:", x + pad, curY, new Color(255, 105, 180).getRGB(), fontData);
        c.drawText(formatBigNumber(GlobalStatsTracker.globalAvgXpHr), valX, curY, COL_VAL, fontData);
        curY += gap;

        c.drawText("Most log used:", x + pad, curY, new Color(255, 140, 0).getRGB(), fontData);

        String logName = GlobalStatsTracker.globalFavLog;
        if (logName != null && logName.length() > 14) {
            logName = logName.substring(0, 14) + ".";
        }
        c.drawText(logName, valX, curY, COL_VAL, fontData);

        curY += gap;

        c.drawText("Total XP:", x + pad, curY, new Color(138, 43, 226).getRGB(), fontData);
        c.drawText(formatBigNumber(GlobalStatsTracker.globalTotalXp), valX, curY, COL_VAL, fontData);
        curY += gap;

        c.drawText("Total Time:", x + pad, curY, new Color(0, 191, 255).getRGB(), fontData);
        c.drawText(formatGlobalTime(GlobalStatsTracker.globalTotalRuntime), valX, curY, COL_VAL, fontData);
    }

    private String formatBigNumber(long num) {
        if (num >= 1_000_000_000) return String.format("%.2fB", num / 1_000_000_000.0);
        if (num >= 1_000_000) return String.format("%.2fM", num / 1_000_000.0);
        if (num >= 1_000) return String.format("%.1fk", num / 1_000.0);
        return String.valueOf(num);
    }

    private String formatGlobalTime(long ms) {
        if (ms == 0) return "0s";
        long seconds = ms / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;
        long years = days / 365;

        if (years > 0) return years + "y " + (days % 365) + "d";
        if (days > 0) return days + "d " + (hours % 24) + "h";
        if (hours > 0) return hours + "h " + (minutes % 60) + "m";
        return (minutes % 60) + "m " + (seconds % 60) + "s";
    }

    public int getTotemsCompleted() { return this.totemsCompleted; }

    public int getOfferingsCollected() { return this.offeringsCollected; }

    public int getFletchingXpGained() {
        if (fletchTracker != null && fletchTracker.getTracker() != null) {
            return (int) fletchTracker.getTracker().getXpGained();
        }
        return 0;
    }

    public String getLogName() {
        if (this.logNameDisplay == null || this.logNameDisplay.isEmpty()) {
            return "None";
        }
        return this.logNameDisplay;
    }
}