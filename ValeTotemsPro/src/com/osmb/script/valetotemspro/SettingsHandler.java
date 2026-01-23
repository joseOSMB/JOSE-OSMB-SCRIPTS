package com.osmb.script.valetotemspro;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

public class SettingsHandler {

    private static final String FILE_NAME = "ValeTotemsPro.properties";

    private static final Path SETTINGS_DIR = Paths.get(System.getProperty("user.home"), "OSMB", "Settings");
    private static final Path SETTINGS_FILE = SETTINGS_DIR.resolve(FILE_NAME);

    public static void save(Properties props) {
        try {
            if (!Files.exists(SETTINGS_DIR)) {
                Files.createDirectories(SETTINGS_DIR);
            }
            try (OutputStream output = new FileOutputStream(SETTINGS_FILE.toFile())) {
                props.store(output, "Vale Totems Pro Configuration");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static Properties load() {
        Properties props = new Properties();
        if (Files.exists(SETTINGS_FILE)) {
            try (InputStream input = new FileInputStream(SETTINGS_FILE.toFile())) {
                props.load(input);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return props;
    }
}