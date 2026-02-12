package com.osmb.script.bowstringmakerpro;

import com.osmb.api.script.Script;
import com.osmb.api.script.ScriptDefinition;
import com.osmb.api.script.SkillCategory;
import com.osmb.api.visual.drawing.Canvas;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

@ScriptDefinition(name = "Bow String Maker Pro", threadUrl = "https://wiki.osmb.co.uk/article/bow-string-maker-pro-2", version = 1.1, author = "Jose", skillCategory = SkillCategory.CRAFTING)
public class BowStringMakerPro extends Script {

    private final String scriptVersion = "1.0";
    private GameLogic logic;
    private String localUser = "Unknown";

    public BowStringMakerPro(Object scriptCore) {
        super(scriptCore);
    }

    @Override
    public void onStart() {
        logic = new GameLogic(this, scriptVersion);

        String rawUser = getDiscordUsername();
        this.localUser = (rawUser != null && !rawUser.isEmpty()) ? rawUser : "Unknown";

        if (!checkForUpdates()) {
            log("SYSTEM", "Outdated version.");
            stop();
            return;
        }

        logic.Initialize(localUser);
    }

    @Override
    public int poll() {
        if (logic != null) {
            return logic.loopPrincipal();
        }
        return 1000;
    }

    @Override
    public boolean stopped() {
        if (super.stopped()) {
            if (logic != null) {
                logic.onStop();
            }
            return true;
        }
        return false;
    }


    @Override
    public void onPaint(Canvas c) {
        if (logic != null) {
            logic.onPaint(c);
        }
    }

    @Override
    public int[] regionsToPrioritise() { return logic != null ? logic.regionsToPrioritise() : new int[]{10806}; }

    @Override
    public void onNewFrame() {
        if (logic != null) {
            logic.onNewFrame();
        }
    }

    private boolean checkForUpdates() {
        String url = ("https://raw.githubusercontent.com/joseOSMB/JOSE-OSMB-SCRIPTS/refs/heads/main/BowStringMakerPro/version.txt");
        String latest = getLatestVersion(url);

        if (latest == null) {
            log("VERSION", "Can't verify the version (Connection error?).");
            return true;
        }

        if (compareVersions(scriptVersion, latest) < 0) {
            log("VERSION", "❌ New version v" + latest + " detected!");
            log("VERSION", "Please update your script from GitHub.");
            return false;
        }

        log("VERSION", "✅ You have the latest version (v" + scriptVersion + ").");
        return true;
    }

    private String getLatestVersion(String url) {
        try {
            HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
            c.setRequestMethod("GET");
            c.setConnectTimeout(3000);
            c.setReadTimeout(3000);
            if (c.getResponseCode() != 200) return null;
            try (BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream()))) {
                String line = r.readLine();
                if (line != null && !line.isEmpty()) return line.trim();
            }
        } catch (Exception e) {}
        return null;
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
}

