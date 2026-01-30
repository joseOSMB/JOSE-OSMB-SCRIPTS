package com.osmb.script.oneclick50fm;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ThreadFactory;

public class GlobalStatsTracker {

    // --- CAMBIA ESTO POR TU URL REAL ---
    private static final String EDGE_FUNCTION_URL = "https://mwgwddtwigrgrbgiqsfg.supabase.co/functions/v1/OneClick50fm-edge";

    private static final String SESSION_ID = java.util.UUID.randomUUID().toString();

    // Variables globales
    public static int globalActiveUsers = 0;
    public static long globalTotalRuntime = 0;
    public static long globalHits50 = 0;
    public static long globalWcXp = 0;
    public static long globalFmXp = 0;
    public static long globalAvgXpHr = 0;
    public static String globalFavLocation = "Loading...";

    private static ScheduledExecutorService scheduler;
    private static long sessionStartTime = 0;
    private static volatile boolean isRunning = false;
    private static long lastRequestTime = 0;

    public static synchronized void startTracking(String username, OneClick50Fm script) {
        if (isRunning) return;

        // Matar cualquier hilo viejo a la fuerza
        if (scheduler != null && !scheduler.isShutdown()) {
            try { scheduler.shutdownNow(); } catch (Exception e) {}
        }

        isRunning = true;
        if (sessionStartTime <= 0) sessionStartTime = System.currentTimeMillis();

        // 1. SOLUCIÓN ZOMBIS: Hilo Daemon (Muere al cerrar el script)
        scheduler = Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r);
                t.setDaemon(true);
                t.setName("GlobalStats-FM-Tracker");
                return t;
            }
        });

        // Ejecutar cada 5 minutos
        scheduler.scheduleAtFixedRate(() -> syncWithEdgeFunction(username, script, false), 0, 1, TimeUnit.MINUTES);
    }

    public static synchronized void stopTracking(String user, OneClick50Fm script) {
        if (scheduler != null) {
            scheduler.shutdownNow();
            scheduler = null;
        }
        isRunning = false;

        // Enviar señal final en hilo Daemon
        Thread t = new Thread(() -> syncWithEdgeFunction(user, script, true));
        t.setDaemon(true);
        t.start();
    }

    private static void syncWithEdgeFunction(String username, OneClick50Fm script, boolean isClosing) {
        // 2. ANTI-SPAM: Si no estamos cerrando, esperar mínimo 5s entre peticiones
        if (!isClosing && System.currentTimeMillis() - lastRequestTime < 5000) {
            return;
        }
        lastRequestTime = System.currentTimeMillis();

        try {
            if (sessionStartTime <= 0) sessionStartTime = System.currentTimeMillis();
            long myRuntime = System.currentTimeMillis() - sessionStartTime;

            int hasHit50 = (script != null) ? script.getHasReachedLevel50() : 0;
            String myLoc = (script != null) ? script.getLogName() : "Unknown";
            int wcXp = (script != null) ? script.GetWcXpGained() : 0;
            int fmXp = (script != null) ? script.GetFmXpGained() : 0;

            String status = isClosing ? "CLOSING" : "ACTIVE";

            // 3. SOLUCIÓN ERROR 400: ¡AQUÍ ESTÁ LA MAGIA!
            // Agregué comillas escapadas (\") a los strings
            String jsonBody = "{"
                    + "\"session_id\": \"" + username + "_" + SESSION_ID + "\","
                    + "\"username\": \"" + username + "\","
                    + "\"hit_50\": " + hasHit50 + ","
                    + "\"location\": \"" + myLoc + "\","  // <--- ¡AHORA TIENE COMILLAS!
                    + "\"wc_xp\": " + wcXp + ","
                    + "\"fm_xp\": " + fmXp + ","
                    + "\"runtime\": " + myRuntime + ","
                    + "\"status\": \"" + status + "\""    // <--- ¡AHORA TIENE COMILLAS!
                    + "}";

            String responseJson = sendRequest(jsonBody);

            if (responseJson != null && !responseJson.isEmpty()) {
                parseResponse(responseJson);
            }

        } catch (Exception e) {
            // Error silencioso para no spamear tu log
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
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);

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
        } catch (Exception e) { }
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
                        case "gHit50": globalHits50 = (long) Double.parseDouble(valStr); break;
                        case "gWcXp": globalWcXp = (long) Double.parseDouble(valStr); break;
                        case "gFmXp": globalFmXp = (long) Double.parseDouble(valStr); break;
                        case "avgXpHr": globalAvgXpHr = (long) Double.parseDouble(valStr); break;
                        case "favLocation": globalFavLocation = valStr; break;
                    }
                } catch (Exception ex) {}
            }
        } catch (Exception e) {}
    }
}