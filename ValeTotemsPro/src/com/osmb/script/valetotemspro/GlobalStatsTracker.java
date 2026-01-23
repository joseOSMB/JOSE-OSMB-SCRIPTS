package com.osmb.script.valetotemspro;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class GlobalStatsTracker {

    private static final String EDGE_FUNCTION_URL = "https://rlfebpjrdzjwalaxkubk.supabase.co/functions/v1/valetotems-edge";

    private static final String SESSION_ID = java.util.UUID.randomUUID().toString();

    public static int globalActiveUsers = 0;
    public static long globalTotalRuntime = 0;
    public static long globalTotalTotems = 0;
    public static long globalTotalOfferings = 0;
    public static long globalTotalXp = 0;
    public static String globalFavLog = "Loading...";
    public static long globalAvgXpHr = 0;

    private static ScheduledExecutorService scheduler;
    private static long sessionStartTime;

    private static volatile boolean isRunning = false;

    public static synchronized void startTracking(String username, GameLogic logic) {
        if (isRunning) return;


        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdownNow();
        }

        isRunning = true;
        sessionStartTime = System.currentTimeMillis();
        scheduler = Executors.newSingleThreadScheduledExecutor();

        scheduler.scheduleAtFixedRate(() -> syncWithEdgeFunction(username, logic, false), 0, 10, TimeUnit.MINUTES);
    }

    public static synchronized void stopTracking(String user, GameLogic logic) {
        if (scheduler != null) {
            scheduler.shutdownNow();
            scheduler = null;
        }
        isRunning = false;

        new Thread(() -> syncWithEdgeFunction(user, logic, true)).start();
    }

    private static void syncWithEdgeFunction(String username, GameLogic logic, boolean isClosing) {
        try {
            if (sessionStartTime <= 0) sessionStartTime = System.currentTimeMillis();

            long myRuntime = System.currentTimeMillis() - sessionStartTime;

            int myTotems = (logic != null) ? logic.getTotemsCompleted() : 0;
            int myOfferings = (logic != null) ? logic.getOfferingsCollected() : 0;
            int myXp = (logic != null) ? logic.getFletchingXpGained() : 0;
            String myLog = (logic != null) ? logic.getLogName() : "Unknown";

            String status = isClosing ? "CLOSING" : "ACTIVE";

            String jsonBody = "{"
                    + "\"session_id\": \"" + username + "_" + SESSION_ID + "\","
                    + "\"username\": \"" + username + "\","
                    + "\"log_type\": \"" + myLog + "\","
                    + "\"totems\": " + myTotems + ","
                    + "\"offerings\": " + myOfferings + ","
                    + "\"xp\": " + myXp + ","
                    + "\"runtime\": " + myRuntime + ","
                    + "\"status\": \"" + status + "\""
                    + "}";

            String responseJson = sendRequest(jsonBody);

            if (responseJson != null && !responseJson.isEmpty()) {
                parseResponse(responseJson);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static String sendRequest(String jsonBody) {
        try {
            URL url = new URL(EDGE_FUNCTION_URL);

            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");

            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("User-Agent", "Java-Client");

            conn.setDoOutput(true);
            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = jsonBody.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            int code = conn.getResponseCode();
            if (code >= 200 && code < 300) {
                BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) response.append(line);
                return response.toString();
            }
        } catch (Exception e) {

        }
        return null;
    }

    private static void parseResponse(String json) {
        if (json == null || !json.contains("{")) return;
        try {
            json = json.replace("{", "").replace("}", "").replace("\"", "");
            String[] parts = json.split(",");

            for (String part : parts) {
                String[] kv = part.split(":");
                if (kv.length < 2) continue;
                String key = kv[0].trim();
                String valStr = kv[1].trim();

                try {
                    switch (key) {
                        case "active": globalActiveUsers = (int) Double.parseDouble(valStr); break;
                        case "gRuntime": globalTotalRuntime = (long) Double.parseDouble(valStr); break;
                        case "gTotems": globalTotalTotems = (long) Double.parseDouble(valStr); break;
                        case "gOfferings": globalTotalOfferings = (long) Double.parseDouble(valStr); break;
                        case "gXp": globalTotalXp = (long) Double.parseDouble(valStr); break;
                        case "avgXpHr": globalAvgXpHr = (long) Double.parseDouble(valStr); break;
                        case "favLog": globalFavLog = valStr; break;
                    }
                } catch (Exception ex) {}
            }
        } catch (Exception e) {}
    }
}