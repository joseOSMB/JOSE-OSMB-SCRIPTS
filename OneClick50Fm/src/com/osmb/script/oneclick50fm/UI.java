package com.osmb.script.oneclick50fm;


import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.awt.Desktop;
import java.io.File;
import java.net.URI;
import java.util.Properties;
import java.io.*;

public class UI extends VBox {
    private final OneClick50Fm ctx;
    private final String SETTINGS_PATH = System.getProperty("user.home") + File.separator + "OneClick50FM_Settings.properties";
    private CheckBox stopAt50Check;

    public UI(OneClick50Fm script) {
        this.ctx = script;
        this.setStyle("-fx-background-color: #2f3640; -fx-padding: 20; -fx-spacing: 15;");
        this.setPrefWidth(400.0);

        VBox settingsSection = this.createSection("General Settings", this.buildMainSettings());

        Button confirmButton = new Button("START ONE CLICK 50FM");
        confirmButton.setStyle("-fx-background-color: #e67e22; -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 14;");
        confirmButton.setPrefWidth(Double.MAX_VALUE);

        confirmButton.setOnAction((e) -> {
            this.saveSettings();
            this.ctx.initConfiguration(this);
            ((Stage) this.getScene().getWindow()).close();
        });

        Hyperlink shopLink = new Hyperlink("Visit JoseScripts Shop");
        shopLink.setStyle("-fx-text-fill: #3498db; -fx-font-size: 11;");
        shopLink.setAlignment(Pos.CENTER);
        shopLink.setOnAction((e) -> {
            try { Desktop.getDesktop().browse(new URI("https://linktr.ee/JoseScripts")); } catch (Exception ex) { ex.printStackTrace(); }
        });

        this.getChildren().addAll(settingsSection, confirmButton, shopLink);
        this.loadSettings();
    }

    private Node buildMainSettings() {
        VBox container = new VBox(10);

        this.stopAt50Check = new CheckBox("Stop at Level 50 Firemaking?");
        this.stopAt50Check.setStyle("-fx-text-fill: white; -fx-font-size: 12;");

        Tooltip tp = new Tooltip("If checked, the script will logout/stop when FM level reaches 50.");
        this.stopAt50Check.setTooltip(tp);

        container.getChildren().add(this.stopAt50Check);
        return container;
    }

    private VBox createSection(String title, Node content) {
        VBox section = new VBox(5);
        Label lblTitle = new Label(title);
        lblTitle.setStyle("-fx-text-fill: #f5f6fa; -fx-font-weight: bold; -fx-font-size: 13;");
        VBox inner = new VBox(content);
        inner.setStyle("-fx-background-color: #353b48; -fx-padding: 10; -fx-background-radius: 5;");
        section.getChildren().addAll(lblTitle, inner);
        return section;
    }

    public boolean isStopAt50Enabled() {
        return this.stopAt50Check.isSelected();
    }

    private void saveSettings() {
        try (OutputStream output = new FileOutputStream(SETTINGS_PATH)) {
            Properties prop = new Properties();

            prop.setProperty("stopAt50", String.valueOf(this.stopAt50Check.isSelected()));

            prop.store(output, "OneClick50FM Configuration");

        } catch (IOException io) {
            io.printStackTrace();
            System.out.println("Error saving settings: " + io.getMessage());
        }
    }

    private void loadSettings() {
        File file = new File(SETTINGS_PATH);

        if (!file.exists()) return;

        try (InputStream input = new FileInputStream(file)) {
            Properties prop = new Properties();
            prop.load(input);

            if (prop.getProperty("stopAt50") != null) {
                boolean isSelected = Boolean.parseBoolean(prop.getProperty("stopAt50"));
                this.stopAt50Check.setSelected(isSelected);
            }

        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }
}