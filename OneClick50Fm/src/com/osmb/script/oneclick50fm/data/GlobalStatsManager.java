package com.osmb.script.oneclick50fm.data;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class GlobalStatsManager {

    private static final String API_URL = "https://vyglkgvndfjlpxhoawjx.supabase.co/functions/v1/track-stats";

    private final String sessionID = UUID.randomUUID().toString();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    public static String activeUsers = "Loading...";
    public static String globalRuntime = "0h 0m";
    public static String globalWc = "0";
    public static String globalFm = "0";
    public static String favZone = "-";
    public static String reached50 = "0";

    private long currentRuntime = 0;
    private long currentWcXp = 0;
    private long currentFmXp = 0;
    private int currentFmLevel = 1;
    private String currentLocationName = "Unknown";

    public void start() {
        scheduler.scheduleAtFixedRate(() -> syncData(false), 10, 300, TimeUnit.SECONDS);
    }

    public void stop() {
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdownNow();
        }
        new Thread(() -> {
            try {
                Thread.sleep(100);
                syncData(true);
            } catch (Exception e) {}
        }).start();
    }

    public void updateLocalData(long runtime, long wcXp, long fmXp, int fmLevel, String locName) {
        this.currentRuntime = runtime;
        this.currentWcXp = wcXp;
        this.currentFmXp = fmXp;
        this.currentFmLevel = fmLevel;
        this.currentLocationName = locName;
    }

    private void syncData(boolean isClosing) {
        try {
            boolean reached50Bool = (currentFmLevel >= 50);

                String jsonPost = String.format(
                        "{" +
                                "\"session_id\":\"%s\", " +
                                "\"runtime\":%d, " +
                                "\"wc_xp\":%d, " +
                                "\"fm_xp\":%d, " +
                                "\"location\":\"%s\", " +
                                "\"hit_50\":%b, " +
                                "\"ended\":%b" +
                                "}",
                        sessionID, currentRuntime, currentWcXp, currentFmXp, currentLocationName, reached50Bool, isClosing
                );
                sendRequest("POST", jsonPost);

            if (!isClosing) {
                String response = sendRequest("GET", null);
                if (response != null) parseResponse(response);
            }
        } catch (Exception e) {}
    }

    private String sendRequest(String method, String jsonBody) throws Exception {
        URL url = new URL(API_URL);
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setRequestMethod(method);
        con.setRequestProperty("Content-Type", "application/json");
        con.setConnectTimeout(3000);
        con.setReadTimeout(3000);

        if (jsonBody != null) {
            con.setDoOutput(true);
            try (OutputStream os = con.getOutputStream()) {
                os.write(jsonBody.getBytes(StandardCharsets.UTF_8));
            }
        }

        if (con.getResponseCode() == 200) {
            BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream(), StandardCharsets.UTF_8));
            StringBuilder content = new StringBuilder();
            String line;
            while ((line = in.readLine()) != null) content.append(line);
            in.close();
            return content.toString();
        }
        return null;
    }

    private void parseResponse(String json) {
        try {
            json = json.replace("{", "").replace("}", "").replace("\"", "");
            String[] parts = json.split(",");
            for (String part : parts) {
                String[] kv = part.split(":");
                if (kv.length < 2) continue;
                String key = kv[0].trim();
                String val = kv[1].trim();
                switch (key) {
                    case "active_users": activeUsers = val; break;
                    case "global_runtime": try { globalRuntime = formatGlobalTime(Long.parseLong(val)); } catch(Exception e){} break;
                    case "global_wc_xp": try { globalWc = formatK(Long.parseLong(val)); } catch(Exception e){} break;
                    case "global_fm_xp": try { globalFm = formatK(Long.parseLong(val)); } catch(Exception e){} break;
                    case "lvl_50_count": reached50 = val; break;
                    case "favorite_zone": favZone = val; break;
                }
            }
        } catch (Exception e) {}
    }

    private String formatK(long number) {
        if (number >= 1_000_000_000) return String.format("%.2fB", number / 1_000_000_000.0);
        if (number >= 1_000_000) return String.format("%.2fM", number / 1_000_000.0);
        if (number >= 1_000) return String.format("%.1fk", number / 1_000.0);
        return String.valueOf(number);
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
        return (minutes % 60) + "m";
    }
}