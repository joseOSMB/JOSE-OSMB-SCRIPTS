package com.osmb.script.valetotemspro;

import com.osmb.api.ScriptCore;
import com.osmb.api.javafx.JavaFXUtils;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.awt.Desktop;
import java.net.URI;
import java.util.*;
import java.util.stream.Collectors;

public class UI extends VBox {
    private final ValeTotemsPro ctx;

    private ComboBox<String> logSelector;
    private ComboBox<String> productSelector;
    private ImageView itemImage;
    private CheckBox basketCheckBox;

    private CheckBox preMadeCheckBox;
    private CheckBox fletchKnifeCheckBox;

    private VBox premadeItemsContainer;
    private final Map<Integer, CheckBox> premadeCheckboxesMap = new LinkedHashMap<>();

    private final Map<String, Integer> logMap = new LinkedHashMap<>();
    private final Map<String, int[]> productIdsMap = new HashMap<>();
    private final Map<String, Map<String, Integer>> preMadeDataMap = new HashMap<>();

    public UI(ValeTotemsPro script) {
        this.ctx = script;
        initData();

        this.setStyle("-fx-background-color: #1e272e; -fx-padding: 25; -fx-spacing: 20; -fx-border-color: #485460; -fx-border-width: 2;");
        this.setPrefWidth(450.0);
        this.setPrefHeight(650.0);

        Label title = new Label("VALE TOTEMS PRO");
        title.setStyle("-fx-text-fill: #00d2d3; -fx-font-size: 22; -fx-font-weight: bold; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.6), 5, 0, 0, 1);");
        title.setAlignment(Pos.CENTER);
        title.setMaxWidth(Double.MAX_VALUE);

        VBox settingsBox = createSection("Configuration", buildSettings(script));

        Button startBtn = new Button("START SCRIPT");
        startBtn.setStyle("-fx-background-color: #0be881; -fx-text-fill: #1e272e; -fx-font-weight: bold; -fx-font-size: 14; -fx-cursor: hand; -fx-background-radius: 5;");
        startBtn.setPrefWidth(Double.MAX_VALUE);
        startBtn.setPrefHeight(45);

        startBtn.setOnAction(e -> {
            saveSettings();
            ctx.StartConfiguration(this);
            ((Stage) this.getScene().getWindow()).close();
        });

        Hyperlink shopLink = new Hyperlink("Visit JoseScripts Shop");
        shopLink.setStyle("-fx-text-fill: #3498db; -fx-underline: true; -fx-font-size: 11;");
        shopLink.setOnAction(e -> {
            try { Desktop.getDesktop().browse(new URI("https://linktr.ee/JoseScripts")); } catch (Exception ex) { ex.printStackTrace(); }
        });

        this.getChildren().addAll(title, settingsBox, startBtn, shopLink);
        this.setAlignment(Pos.TOP_CENTER);

        loadSettings();
    }

    private void initData() {
        logMap.put("Oak Logs", 1521);
        logMap.put("Willow Logs", 1519);
        logMap.put("Maple Logs", 1517);
        logMap.put("Yew Logs", 1515);
        logMap.put("Magic Logs", 1513);
        logMap.put("Redwood Logs", 19669);

        productIdsMap.put("Oak Logs", new int[]{54, 56});
        productIdsMap.put("Willow Logs", new int[]{60, 58});
        productIdsMap.put("Maple Logs", new int[]{64, 62});
        productIdsMap.put("Yew Logs", new int[]{68, 66});
        productIdsMap.put("Magic Logs", new int[]{72, 70});
        productIdsMap.put("Redwood Logs", new int[]{31049});

        setupPreMadeData("Oak Logs", 54, 843, 56, 845, 22251, 9442);
        setupPreMadeData("Willow Logs", 60, 849, 58, 847, 22254, 9444);
        setupPreMadeData("Maple Logs", 64, 853, 62, 851, 22257, 9448);
        setupPreMadeData("Yew Logs", 68, 857, 66, 855, 22260, 9452);
        setupPreMadeData("Magic Logs", 72, 861, 70, 859, 22263, 21952);

        Map<String, Integer> redwoodItems = new LinkedHashMap<>();
        redwoodItems.put("Redwood Shield", 25622);
        redwoodItems.put("Redwood Hiking Staff", 31049);
        preMadeDataMap.put("Redwood Logs", redwoodItems);
    }

    private void setupPreMadeData(String logKey, int s_u, int s, int l_u, int l, int shield, int stock) {
        Map<String, Integer> items = new LinkedHashMap<>();
        String prefix = logKey.replace(" Logs", "");
        items.put(prefix + " shortbow (u)", s_u);
        items.put(prefix + " longbow (u)", l_u);
        items.put(prefix + " shortbow", s);
        items.put(prefix + " longbow", l);
        items.put(prefix + " shield", shield);
        items.put(prefix + " stock", stock);
        preMadeDataMap.put(logKey, items);
    }

    private Node buildSettings(ScriptCore core) {
        VBox container = new VBox(15);

        VBox logBox = new VBox(5);
        Label lblLog = new Label("Select Log Type:");
        lblLog.setStyle("-fx-text-fill: #d2dae2; -fx-font-size: 12;");
        logSelector = new ComboBox<>();
        logSelector.getItems().addAll(logMap.keySet());
        logSelector.setValue("Oak Logs");
        logSelector.setMaxWidth(Double.MAX_VALUE);
        logSelector.setStyle("-fx-base: #485460; -fx-text-fill: white; -fx-font-size: 13;");

        itemImage = new ImageView();
        itemImage.setFitHeight(32);
        itemImage.setFitWidth(32);
        updateImage(core, logMap.get("Oak Logs"));

        HBox logRow = new HBox(10, logSelector, itemImage);
        logRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(logSelector, Priority.ALWAYS);
        logBox.getChildren().addAll(lblLog, logRow);

        VBox prodBox = new VBox(5);
        Label lblProd = new Label("Crafting Output (Normal Mode):");
        lblProd.setStyle("-fx-text-fill: #d2dae2; -fx-font-size: 12;");
        productSelector = new ComboBox<>();
        productSelector.setMaxWidth(Double.MAX_VALUE);
        productSelector.setStyle("-fx-base: #485460; -fx-text-fill: white; -fx-font-size: 13;");
        prodBox.getChildren().addAll(lblProd, productSelector);

        VBox preMadeBox = new VBox(10);
        preMadeBox.setStyle("-fx-padding: 10; -fx-border-color: #ff9f43; -fx-border-width: 1; -fx-border-radius: 5; -fx-background-color: rgba(255, 159, 67, 0.05);");

        preMadeCheckBox = new CheckBox("Use Pre-Made Items");
        preMadeCheckBox.setStyle("-fx-text-fill: #ff9f43; -fx-font-size: 12; -fx-font-weight: bold;");

        fletchKnifeCheckBox = new CheckBox("Fletching Knife Equipped?");
        fletchKnifeCheckBox.setStyle("-fx-text-fill: #54a0ff; -fx-font-size: 11; -fx-padding: 0 0 0 20;");
        fletchKnifeCheckBox.visibleProperty().bind(preMadeCheckBox.selectedProperty());
        fletchKnifeCheckBox.managedProperty().bind(preMadeCheckBox.selectedProperty());

        Label lblPreMadeItem = new Label("Select Items (Priority Top-Down):");
        lblPreMadeItem.setStyle("-fx-text-fill: #ff9f43; -fx-font-size: 11;");
        lblPreMadeItem.visibleProperty().bind(preMadeCheckBox.selectedProperty());
        lblPreMadeItem.managedProperty().bind(preMadeCheckBox.selectedProperty());

        premadeItemsContainer = new VBox(5);
        ScrollPane scrollPane = new ScrollPane(premadeItemsContainer);
        scrollPane.setFitToWidth(true);
        scrollPane.setPrefHeight(200);
        scrollPane.setStyle("-fx-background: #353b48; -fx-background-color: transparent;");
        scrollPane.visibleProperty().bind(preMadeCheckBox.selectedProperty());
        scrollPane.managedProperty().bind(preMadeCheckBox.selectedProperty());

        preMadeBox.getChildren().addAll(preMadeCheckBox, fletchKnifeCheckBox, lblPreMadeItem, scrollPane);

        basketCheckBox = new CheckBox("Use Log Basket (Forestry)");
        basketCheckBox.setStyle("-fx-text-fill: #d2dae2; -fx-font-size: 12;");

        basketCheckBox.disableProperty().bind(
                logSelector.valueProperty().isEqualTo("Oak Logs")
                        .or(preMadeCheckBox.selectedProperty())
        );

        preMadeCheckBox.selectedProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal) {
                basketCheckBox.setSelected(false);
                productSelector.setDisable(true);
            } else {
                fletchKnifeCheckBox.setSelected(false);
                productSelector.setDisable(logSelector.getValue().equals("Redwood Logs"));
            }
        });

        logSelector.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                updateImage(core, logMap.get(newVal));
                updateProductOptions(newVal);
                updatePreMadeSelector(newVal);
                if ("Oak Logs".equals(newVal)) basketCheckBox.setSelected(false);
            }
        });

        updateProductOptions("Oak Logs");
        updatePreMadeSelector("Oak Logs");

        container.getChildren().addAll(logBox, prodBox, new Separator(), preMadeBox, basketCheckBox);
        return container;
    }

    private void updatePreMadeSelector(String logName) {
        premadeItemsContainer.getChildren().clear();
        premadeCheckboxesMap.clear();

        Map<String, Integer> items = preMadeDataMap.get(logName);
        if (items != null) {
            for (Map.Entry<String, Integer> entry : items.entrySet()) {
                CheckBox cb = new CheckBox(entry.getKey());
                cb.setStyle("-fx-text-fill: white;");

                if (premadeCheckboxesMap.isEmpty()) cb.setSelected(true);
                premadeCheckboxesMap.put(entry.getValue(), cb);
                premadeItemsContainer.getChildren().add(cb);
            }
        }
    }

    public List<Integer> getSelectedPreMadeItemIDs() {
        List<Integer> selected = new ArrayList<>();
        if (!preMadeCheckBox.isSelected()) return selected;

        for (Map.Entry<Integer, CheckBox> entry : premadeCheckboxesMap.entrySet()) {
            if (entry.getValue().isSelected()) {
                selected.add(entry.getKey());
            }
        }
        return selected;
    }

    public boolean isFletchingKnifeEquipped() {
        return preMadeCheckBox.isSelected() && fletchKnifeCheckBox.isSelected();
    }

    private void updateProductOptions(String logName) {
        productSelector.getItems().clear();
        if (logName.equals("Redwood Logs")) {
            productSelector.getItems().add("Redwood Hiking Staff");
            productSelector.getSelectionModel().select(0);
            productSelector.setDisable(true);
        } else {
            productSelector.getItems().addAll("Shortbow (u)", "Longbow (u)");
            productSelector.getSelectionModel().select("Longbow (u)");
            productSelector.setDisable(preMadeCheckBox.isSelected());
        }
    }
    private void updateImage(ScriptCore core, int id) { try { itemImage.setImage(JavaFXUtils.getItemImageView(core, id).getImage()); } catch (Exception e) {} }

    private VBox createSection(String title, Node content) {
        VBox section = new VBox(10);
        Label lblTitle = new Label(title);
        lblTitle.setStyle("-fx-text-fill: #00d2d3; -fx-font-weight: bold; -fx-font-size: 12; -fx-padding: 0 0 5 0; -fx-border-color: transparent transparent #00d2d3 transparent;");
        lblTitle.setMaxWidth(Double.MAX_VALUE);
        section.getChildren().addAll(lblTitle, content);
        section.setStyle("-fx-background-color: #2f3640; -fx-padding: 15; -fx-background-radius: 8; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.3), 5, 0, 0, 1);");
        return section;
    }

    public int getSelectedLogID() { return logMap.getOrDefault(logSelector.getValue(), 1521); }
    public String getSelectedLogName() { return logSelector.getValue(); }
    public String getSelectedProductName() { return productSelector.getValue(); }
    public boolean isUseLogBasket() { return basketCheckBox.isSelected(); }
    public boolean isUsePreMadeItems() { return preMadeCheckBox.isSelected(); }
    public int getSelectedProductID() {
        String log = logSelector.getValue();
        String prod = productSelector.getValue();
        int[] options = productIdsMap.get(log);
        if (options == null) return 56;
        if (log.equals("Redwood Logs")) return options[0];
        if (prod != null && prod.contains("Shortbow")) return options[0];
        return options[1];
    }

    private void saveSettings() {
        Properties props = new Properties();
        props.setProperty("logType", logSelector.getValue());
        if (!productSelector.isDisabled()) props.setProperty("productType", productSelector.getValue());
        props.setProperty("usePreMade", String.valueOf(preMadeCheckBox.isSelected()));
        props.setProperty("knifeEquipped", String.valueOf(fletchKnifeCheckBox.isSelected()));
        props.setProperty("useBasket", String.valueOf(basketCheckBox.isSelected()));

        List<Integer> ids = getSelectedPreMadeItemIDs();
        if (!ids.isEmpty()) {
            String joined = ids.stream().map(String::valueOf).collect(Collectors.joining(","));
            props.setProperty("preMadeItemsList", joined);
        }

        SettingsHandler.save(props);
    }

    public String getSelectedPreMadeItemName() {
        if (!preMadeCheckBox.isSelected()) return "None";
        List<String> names = new ArrayList<>();
        for (Map.Entry<Integer, CheckBox> entry : premadeCheckboxesMap.entrySet()) {
            if (entry.getValue().isSelected()) names.add(entry.getValue().getText());
        }
        return names.isEmpty() ? "None" : names.get(0) + (names.size() > 1 ? " (+" + (names.size()-1) + ")" : "");
    }

    private void loadSettings() {
        Properties props = SettingsHandler.load();
        if (!props.isEmpty()) {
            try {
                String log = props.getProperty("logType");
                String prod = props.getProperty("productType");
                String preMade = props.getProperty("usePreMade");
                String knifeEq = props.getProperty("knifeEquipped");
                String useBasket = props.getProperty("useBasket");
                String preMadeItemsList = props.getProperty("preMadeItemsList");

                if (log != null && logMap.containsKey(log)) {
                    logSelector.setValue(log);

                    if (preMade != null) preMadeCheckBox.setSelected(Boolean.parseBoolean(preMade));
                    if (knifeEq != null) fletchKnifeCheckBox.setSelected(Boolean.parseBoolean(knifeEq));

                    if (useBasket != null && !log.equals("Oak Logs")) {
                        basketCheckBox.setSelected(Boolean.parseBoolean(useBasket));
                    }

                    if (prod != null && !log.equals("Redwood Logs")) productSelector.setValue(prod);

                    if (preMadeItemsList != null && !preMadeItemsList.isEmpty()) {
                        String[] parts = preMadeItemsList.split(",");
                        Set<Integer> loadedIds = new HashSet<>();
                        for (String p : parts) loadedIds.add(Integer.parseInt(p));

                        for (Map.Entry<Integer, CheckBox> entry : premadeCheckboxesMap.entrySet()) {
                            entry.getValue().setSelected(loadedIds.contains(entry.getKey()));
                        }
                    }
                }
            } catch (Exception e) {}
        }
    }

}


