package com.osmb.script.bowstringmakerpro;

import com.osmb.api.ScriptCore;
import com.osmb.api.location.area.impl.RectangleArea;
import com.osmb.api.scene.RSObject;
import com.osmb.api.script.Script;
import com.osmb.api.trackers.experience.XPTracker;
import com.osmb.api.ui.component.ComponentSearchResult;
import com.osmb.api.ui.component.minimap.xpcounter.XPDropsComponent;
import com.osmb.api.utils.RandomUtils;
import com.osmb.api.visual.color.ColorModel;
import com.osmb.api.visual.color.tolerance.impl.SingleThresholdComparator;
import com.osmb.api.visual.drawing.Canvas;
import com.osmb.api.visual.image.Image;
import com.osmb.api.visual.image.SearchableImage;
import com.osmb.api.visual.ocr.fonts.Font;
import com.osmb.api.shape.Rectangle;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class GameLogic {

    private final Object lock = new Object();

    private boolean reportSent = false;
    private String localUser = "Free User";

    private final Script ctx;
    private final String scriptVersion;
    private String status = "Initializing...";
    private long startTime;

    // --- IDs ---
    private static final int BALE_OF_FLAX_ID = 31045;
    private static final int BOW_STRING_SPOOL_ID = 31052;
    private static final int FLAX_ID = 1779;
    private static final int BOW_STRING_PRODUCT_ID = 1777;

    private static final RectangleArea WORK_AREA = new RectangleArea(2710, 3470, 6, 3, 1);

    private boolean isCrafting = false;
    private long lastAnimationTime = 0;

    private long lastXpCheckTime = System.currentTimeMillis();
    private double lastXpValue = -1;

    private final SkillTracker craftingTracker;
    private static final int CRAFTING_SPRITE_ID = 207;

    private final Map<String, Image> paintImages = new HashMap<>();

    public GameLogic(Script ctx, String version) {
        this.ctx = ctx;
        this.scriptVersion = version;
        this.startTime = System.currentTimeMillis();
        this.craftingTracker = new SkillTracker(ctx, CRAFTING_SPRITE_ID);
    }

    public void Initialize(String user) {
        synchronized (lock) { reportSent = false; }

        this.localUser = (user != null && !user.isEmpty()) ? user : "Free User";

        GlobalStatsTracker.startTracking(this.localUser, this);
        log("System", "Configuration Loaded. Script Started.");
    }

    public void onStop() {
        synchronized (lock) {
            if (reportSent) return;
            GlobalStatsTracker.stopTracking(this.localUser, this);
            reportSent = true;
        }
    }


    public void onNewFrame() {
        if (craftingTracker != null) craftingTracker.checkXP();
    }

    public int loopPrincipal() {
        if (ctx.getWorldPosition() == null) return 1000;


        if (checkSecurityStop()) {
            return 1000;
        }

        if (!hasRequiredItems()) {
            status = "Missing required items!";
            log("Error", "Missing Bale or Spool. Stopping.");
            ctx.stop();
            return 1000;
        }

        CurrentTask task = decideTask();
        status = (task != null) ? task.name() : "IDLE";

        if (task != null) {
            executeTask(task);
        }

        return RandomUtils.uniformRandom(200, 400);
    }

    private boolean checkSecurityStop() {
        if (ctx.getWorldPosition() == null) return false;

        if (!WORK_AREA.contains(ctx.getWorldPosition())) {
            log("Error", "Player outside Work Area. Stopping...");
            status = "Stopped (Out of Area)";
            ctx.stop();
            return true;
        }

        if (craftingTracker != null && craftingTracker.getTracker() != null) {
            double currentXp = craftingTracker.getTracker().getXpGained();

            if (currentXp > lastXpValue) {
                lastXpValue = currentXp;
                lastXpCheckTime = System.currentTimeMillis();
            } else {
                long timeSinceXp = System.currentTimeMillis() - lastXpCheckTime;
                if (timeSinceXp > 900000) {
                    log("Error", "TIMEOUT: No XP gained in 15 minutes. Stopping script.");
                    status = "Stopped (XP Timeout)";
                    ctx.stop();
                    return true;
                }
            }
        }

        var inv = ctx.getWidgetManager().getInventory();
        if (inv == null || !inv.isVisible()) return false;

        if (getEmptySlots() < 25) {
            int totalOccupied = 28 - getEmptySlots();
            int flaxCount = getInventoryCount(FLAX_ID);
            int nonFlaxItems = totalOccupied - flaxCount;

            if (nonFlaxItems > 3) {
                log("Error", "Found > 3 items that are NOT Flax. Stopping.");
                status = "Stopped (Non-Flax Items)";
                ctx.stop();
                return true;
            }
        }

        return false;
    }

    private boolean hasRequiredItems() {
        var inv = ctx.getWidgetManager().getInventory();
        if (inv == null || !inv.isVisible()) return true;

        var spool = inv.search(Set.of(BOW_STRING_SPOOL_ID));
        var bale = inv.search(Set.of(BALE_OF_FLAX_ID));

        boolean spoolMissing = (spool != null && spool.getAmount(BOW_STRING_SPOOL_ID) == 0);
        boolean baleMissing = (bale != null && bale.getAmount(BALE_OF_FLAX_ID) == 0);

        if (spool != null && spoolMissing) return false;
        if (bale != null && baleMissing) return false;

        return true;
    }

    private int getInventoryCount(int id) {
        var inv = ctx.getWidgetManager().getInventory();
        if (inv == null || !inv.isVisible()) return 0;
        var item = inv.search(Set.of(id));
        return (item != null) ? item.getAmount(id) : 0;
    }

    private int getEmptySlots() {
        var inv = ctx.getWidgetManager().getInventory();
        if (inv == null || !inv.isVisible()) return 0;
        var allItems = inv.search(Set.of());
        int occupied = (allItems != null) ? allItems.getOccupiedSlotCount() : 0;
        return 28 - occupied;
    }

    private CurrentTask decideTask() {
        int flaxCount = getInventoryCount(FLAX_ID);
        int emptySlots = getEmptySlots();

        if (flaxCount > 0) {
            if (isCrafting) {
                return CurrentTask.WAITING_FOR_FINISH;
            }

            if (ctx.getWidgetManager().getDialogue().isVisible()) {
                return CurrentTask.SELECT_INTERFACE;
            }

            return CurrentTask.SPIN_FLAX;
        }

        if (flaxCount == 0) {
            isCrafting = false;
            if (emptySlots >= 25) {
                return CurrentTask.UNPACK_BALE;
            }
        }

        return null;
    }

    private void executeTask(CurrentTask task) {
        switch (task) {
            case UNPACK_BALE -> handleUnpack();
            case SPIN_FLAX -> handleSpinning();
            case SELECT_INTERFACE -> handleInterfaceSelection();
            case WAITING_FOR_FINISH -> handleBlockingWait();
        }
    }

    private void handleBlockingWait() {
        log("System", "Crafting started. Monitoring animation...");

        while (true) {
            int flaxCount = getInventoryCount(FLAX_ID);

            if (flaxCount == 0) {
                log("System", "Flax depleted. Stopping monitor.");
                ctx.pollFramesHuman(() -> false, 400, false);
                break;
            }

            boolean isAnimating = ctx.getPixelAnalyzer().isPlayerAnimating(0.1);
            if (isAnimating) {
                lastAnimationTime = System.currentTimeMillis();
            }

            long timeSinceAnim = System.currentTimeMillis() - lastAnimationTime;

            if (timeSinceAnim > 7000) {
                log("System", "Timeout (4s no anim). Stopping monitor.");
                break;
            }

            status = "Crafting... (Idle: " + timeSinceAnim + "ms)";

            ctx.pollFramesUntil(() -> false, 350, false);
        }
        isCrafting = false;
    }

    private void handleUnpack() {
        log("Task", "Unpacking Flax...");
        var inv = ctx.getWidgetManager().getInventory();
        if (!inv.isVisible()) inv.open();

        var result = inv.search(Set.of(BALE_OF_FLAX_ID));

        if (result != null) {
            var baleItem = result.getItem(BALE_OF_FLAX_ID);
            if (baleItem != null) {
                ctx.getFinger().tap(baleItem.getBounds());
                ctx.pollFramesHuman(() -> false, RandomUtils.uniformRandom(400, 600), false);
                ctx.pollFramesHuman(() -> getInventoryCount(FLAX_ID) > 0, 2000, false);
            }
        }
    }

    private void handleSpinning() {
        log("Task", "Clicking Spinning Wheel...");

        RSObject wheel = ctx.getObjectManager().getRSObject(obj ->
                obj.getName() != null && obj.getName().equalsIgnoreCase("Spinning Wheel")
        );

        if (wheel != null) {
            if (wheel.interact("Spin")) {
                ctx.pollFramesHuman(() -> false, RandomUtils.uniformRandom(500, 1000), false);

                ctx.pollFramesUntil(() -> ctx.getWidgetManager().getDialogue().isVisible(), 5000, false, false);
            }
        } else {
            log("Error", "Spinning Wheel not found!");
        }
    }

    private void handleInterfaceSelection() {
        log("Task", "Selecting Bow String...");

        if (ctx.getWidgetManager().getDialogue().selectItem(BOW_STRING_PRODUCT_ID)) {
            lastAnimationTime = System.currentTimeMillis();

            isCrafting = true;

            log("System", "Selection made.");

            ctx.pollFramesHuman(() -> false, RandomUtils.uniformRandom(400, 700), false);
        }
    }

    private void log(String tag, String msg) {
        ctx.log(tag, msg);
    }

    public int[] regionsToPrioritise() { return new int[]{10806}; }

    private enum CurrentTask {
        UNPACK_BALE, SPIN_FLAX, SELECT_INTERFACE, WAITING_FOR_FINISH
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

    public void onPaint(Canvas c) {
        int x = 15, y = 35;
        int panelW = 320;
        int panelH = 225;
        int padding = 15;
        int colValueX = x + 155;

        int COL_BG = new Color(12, 12, 18, 240).getRGB();
        int COL_BORDER = new Color(0, 210, 211, 200).getRGB();
        int COL_LABEL = new Color(190, 190, 190).getRGB();
        int COL_VALUE = new Color(255, 255, 255).getRGB();
        int COL_TITLE = new Color(0, 210, 211).getRGB();

        java.awt.Font fontTitle = new java.awt.Font("Impact", java.awt.Font.ITALIC, 22);
        java.awt.Font fontBold = new java.awt.Font("SansSerif", java.awt.Font.BOLD, 13);
        java.awt.Font fontSignature = new java.awt.Font("Arial", java.awt.Font.BOLD | java.awt.Font.ITALIC, 13);

        c.fillRect(x, y, panelW, panelH, COL_BG);
        c.drawRect(x, y, panelW, panelH, COL_BORDER);

        drawCenteredText(c, "BOW STRING MAKER PRO", x + panelW / 2, y + 28, fontTitle, COL_TITLE);
        c.drawText("By JOSE", x + (panelW / 2) + 95, y + 45, COL_TITLE, fontSignature);

        int cursorY = y + 55;
        long elapsed = System.currentTimeMillis() - startTime;

        XPTracker tCraft = (craftingTracker != null) ? craftingTracker.getTracker() : null;

        long xpGained = (tCraft != null) ? (long) tCraft.getXpGained() : 0;
        int bowStringsMade = (int) (xpGained / 15.0);

        long xpPerHour = (tCraft != null) ? (long) tCraft.getXpPerHour() : 0;
        long bowsPerHour = (elapsed > 0) ? (long) (bowStringsMade * 3600000.0 / elapsed) : 0;

        drawRow(c, "Version:", "v" + scriptVersion, x + padding, colValueX, cursorY, COL_LABEL, new Color(255, 200, 0).getRGB(), fontBold);
        cursorY += 20;

        drawRow(c, "Runtime:", formatTime(elapsed), x + padding, colValueX, cursorY, COL_LABEL, COL_VALUE, fontBold);
        cursorY += 20;

        String taskName = (status.startsWith("Crafting")) ? "Crafting..." : status;
        if (taskName.length() > 20) taskName = taskName.substring(0, 20);
        drawRow(c, "Task:", taskName, x + padding, colValueX, cursorY, COL_LABEL, new Color(255, 180, 50).getRGB(), fontBold);
        cursorY += 20;

        long timeSinceLastXp = System.currentTimeMillis() - lastXpCheckTime;

        String xpTimerText = formatTime(timeSinceLastXp);
        int timerColor = (timeSinceLastXp > 300000) ? new Color(255, 100, 100).getRGB() : COL_VALUE;

        drawRow(c, "XP Timeout:", xpTimerText + " / 15:00", x + padding, colValueX, cursorY, COL_LABEL, timerColor, fontBold);
        cursorY += 20;

        drawRow(c, "Bow String Made:", bowStringsMade + " (" + formatK(bowsPerHour) + "/h)", x + padding, colValueX, cursorY, COL_LABEL, COL_TITLE, fontBold);
        cursorY += 25;

        int currentLevel = (tCraft != null) ? tCraft.getLevel() : 1;
        int progress = (tCraft != null) ? tCraft.getLevelProgressPercentage() : 0;
        String ttl = (tCraft != null) ? tCraft.timeToNextLevelString() : "--:--:--";

        String details = "+" + formatK((int)xpGained) + " XP | TTL: " + ttl;
        String rateText = formatK(xpPerHour) + " xp/h";

        String urlCrafting = "https://oldschool.runescape.wiki/images/Crafting_icon.png";
        Image iconCraft = getIcon("CRAFT_ICON", urlCrafting);
        int colCraft = new Color(139, 69, 19).getRGB();

        drawSkillBar(c, "CRAFTING", currentLevel, rateText, details, progress, colCraft, iconCraft, x + padding, cursorY, panelW - (padding * 2), 55);
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
            c.drawAtOn(icon, x, iconCenteredY);
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

    private void drawGlobalStatsPanel(Canvas c, int x, int y) {
        int w = 210;
        int h = 140;

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

        c.drawText("BowString made:", x + pad, curY, new Color(0, 210, 211).getRGB(), fontData);
        c.drawText(formatBigNumber(GlobalStatsTracker.globalBowStringMade), valX, curY, COL_VAL, fontData);
        curY += gap;

        c.drawText("Global XP/Hr:", x + pad, curY, new Color(255, 105, 180).getRGB(), fontData);
        c.drawText(formatBigNumber(GlobalStatsTracker.globalAvgXpHr), valX, curY, COL_VAL, fontData);
        curY += gap;

        c.drawText("Total XP:", x + pad, curY, new Color(138, 43, 226).getRGB(), fontData);
        c.drawText(formatBigNumber(GlobalStatsTracker.globalTotalXp), valX, curY, COL_VAL, fontData);
        curY += gap;

        c.drawText("Total Time:", x + pad, curY, new Color(0, 191, 255).getRGB(), fontData);
        c.drawText(formatGlobalTime(GlobalStatsTracker.globalTotalRuntime), valX, curY, COL_VAL, fontData);
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

    private String formatK(long number) {
        if (number >= 1000) return String.format("%.1fk", number / 1000.0);
        return String.valueOf(number);
    }

    private String formatTime(long ms) {
        if (ms <= 0) return "--:--:--";
        long hours = TimeUnit.MILLISECONDS.toHours(ms);
        long minutes = TimeUnit.MILLISECONDS.toMinutes(ms) % 60;
        long seconds = TimeUnit.MILLISECONDS.toSeconds(ms) % 60;
        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
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

    public int getBowStringMade() {
        if (craftingTracker != null && craftingTracker.getTracker() != null) {
            long xpGained = (long) craftingTracker.getTracker().getXpGained();
            return (int) (xpGained / 15.0);
        }
        return 0;
    }

    public int getCraftingXpGained() {
        if (craftingTracker != null && craftingTracker.getTracker() != null) {
            return (int) craftingTracker.getTracker().getXpGained();
        }
        return 0;
    }
}