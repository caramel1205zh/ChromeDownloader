import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.Priority;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;
import javafx.scene.image.Image;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javafx.application.Platform;

/**
 * Main application class for Chrome Offline Installer Downloader.
 * Provides GUI for fetching and downloading Chrome installers.
 */
public class Main extends Application {

    // UI components
    private ComboBox<String> versionChoiceBox;
    private ToggleGroup proxyToggleGroup;
    private RadioButton noProxyRadio, httpProxyRadio, httpsProxyRadio, socks5ProxyRadio;
    private TextField proxyHostField, proxyPortField, proxyUserField;
    private PasswordField proxyPassField;
    private Button fetchButton, downloadButton, cancelButton;
    private ListView<String> linkListView;
    private ProgressBar progressBar;
    private Label progressLabel, resultLabel;
    private List<String> currentDownloadLinks = new ArrayList<>();
    private DownloadFileTask currentDownloadTask = null;
    private ContextMenu linkListContextMenu;
    
    // Menu components
    private MenuBar menuBar;
    private Menu languageMenu;
    private MenuItem englishMenuItem, schineseMenuItem;
    
    // UI references for language updates
    private Label proxyHostLabel, proxyPortLabel, proxyUserLabel, proxyPassLabel;
    private VBox proxySection;

    private ExecutorService executorService;

    @Override
    public void start(Stage primaryStage) {
        // Initialize internationalization
        initializeI18n();
        
        // Setup executor service with daemon threads
        executorService = Executors.newCachedThreadPool(r -> {
            Thread thread = new Thread(r);
            thread.setDaemon(true);
            return thread;
        });
        
        createMenuBar();
        
        String osName = PlatformUtils.isWindows() ? "Windows" : (PlatformUtils.isMac() ? "macOS" : "Unknown");
        String arch = PlatformUtils.getArchForUpdateService();
        String localizedOS = I18n.getLocalizedOSName(osName);

        // Create version selection combo box
        versionChoiceBox = new ComboBox<>();
        initializeVersionChoiceBox();

        // Create proxy settings UI
        createProxySettingsUI();
        
        // Create action buttons
        fetchButton = new Button(I18n.get("button.fetch"));
        fetchButton.setOnAction(e -> fetchDownloadLinks());
        
        downloadButton = new Button(I18n.get("button.download"));
        downloadButton.setOnAction(e -> startDownload());
        downloadButton.setDisable(true);
        
        cancelButton = new Button(I18n.get("button.cancel"));
        cancelButton.setOnAction(e -> cancelDownload());
        cancelButton.setDisable(true);

        // Create download links list view
        linkListView = new ListView<>();
        linkListView.setPrefHeight(130);
        linkListView.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
        
        createLinkListContextMenu();
        
        // Setup keyboard shortcuts for copying links
        LinkListViewKeyHandler keyHandler = new LinkListViewKeyHandler(
            linkListView, currentDownloadLinks,
            this::copySelectedLinkToClipboard,
            this::copyAllLinksToClipboard
        );
        linkListView.setOnKeyPressed(keyHandler);
        
        // Enable download button when link is selected
        linkListView.getSelectionModel().selectedItemProperty()
            .addListener((obs, oldVal, newVal) -> {
                downloadButton.setDisable(newVal == null || currentDownloadTask != null);
            });

        // Create progress tracking components
        progressBar = new ProgressBar();
        progressBar.setVisible(false);
        progressBar.setProgress(0.0);
        progressBar.setPrefWidth(200); // Set fixed width for progress bar

        progressLabel = new Label(I18n.get("status.ready"));
        progressLabel.setVisible(false);
        progressLabel.setStyle("-fx-text-fill: black;");
        progressLabel.setAlignment(Pos.CENTER_LEFT); // Align left for progress text

        resultLabel = new Label();
        resultLabel.setVisible(false);
        resultLabel.setStyle("-fx-text-fill: green; -fx-font-weight: bold;");

        // Create progress bar and label area (TOP ROW - PROGRESS BAR)
        HBox progressBox = new HBox(10, progressBar, progressLabel);
        progressBox.setAlignment(Pos.CENTER_LEFT); // Align left
        
        // Create file saved message and buttons area (BOTTOM ROW)
        HBox bottomRowBox = new HBox(10);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS); // Spacer pushes buttons to the right
        
        // Add file saved message (left side) and buttons (right side) to bottom row
        bottomRowBox.getChildren().addAll(resultLabel, spacer, downloadButton, cancelButton);
        bottomRowBox.setAlignment(Pos.CENTER);
        
        // Create main container for the bottom section - PROGRESS BAR FIRST, THEN FILE MESSAGE AND BUTTONS
        VBox bottomContainer = new VBox(10); // Spacing between progress row and bottom row
        bottomContainer.setPadding(new Insets(10, 0, 10, 0)); // Top padding for separation
        bottomContainer.getChildren().addAll(
            progressBox,      // Progress bar row (TOP)
            bottomRowBox      // File message and buttons row (BOTTOM)
        );

        // Create main content layout
        Region vSpacer = new Region();
        VBox.setVgrow(vSpacer, Priority.ALWAYS); // Vertical spacer fills remaining space

        VBox content = new VBox(10);
        content.setPadding(new Insets(10));
        content.getChildren().addAll(
            new HBox(10, new Label(I18n.get("label.selectVersion")), versionChoiceBox),
            proxySection,
            fetchButton,
            new Label(I18n.get("label.availableLinks")),
            linkListView,
            vSpacer,
            bottomContainer // Progress row and file message/buttons row
        );
        
        // Use BorderPane for proper menu bar placement
        BorderPane root = new BorderPane();
        root.setTop(menuBar);
        root.setCenter(content);

        Scene scene = new Scene(root, 1000, 480);
        
        updateWindowTitle(primaryStage, localizedOS, arch);
        primaryStage.setScene(scene);
        primaryStage.setResizable(false);

        // Load application icons
        loadApplicationIcons(primaryStage);

        primaryStage.show();
    }

    /**
     * Creates the menu bar with language selection.
     */
    private void createMenuBar() {
        menuBar = new MenuBar();
        
        // File menu
        Menu fileMenu = new Menu(I18n.get("menu.file"));
        MenuItem exitMenuItem = new MenuItem(I18n.get("menu.exit"));
        exitMenuItem.setOnAction(e -> Platform.exit());
        fileMenu.getItems().add(exitMenuItem);
        
        // Language menu
        languageMenu = new Menu(I18n.get("menu.language"));
        
        englishMenuItem = new MenuItem(I18n.get("menu.english"));
        englishMenuItem.setOnAction(e -> switchLanguage(I18n.ENGLISH_US));
        
        schineseMenuItem = new MenuItem(I18n.get("menu.schinese"));
        schineseMenuItem.setOnAction(e -> switchLanguage(I18n.SIMPLIFIED_CHINESE));
        
        updateLanguageMenuCheckmarks();
        
        languageMenu.getItems().addAll(englishMenuItem, schineseMenuItem);
        
        // Help menu
        Menu helpMenu = new Menu(I18n.get("menu.help"));
        MenuItem aboutMenuItem = new MenuItem(I18n.get("menu.about"));
        aboutMenuItem.setOnAction(e -> createAboutDialog());
        helpMenu.getItems().add(aboutMenuItem);
        
        menuBar.getMenus().addAll(fileMenu, languageMenu, helpMenu);
        
        // Align menu bar to left like Windows applications
        menuBar.setUseSystemMenuBar(false);
    }
    
    /**
     * Updates checkmarks on language menu items.
     */
    private void updateLanguageMenuCheckmarks() {
        Locale current = I18n.getCurrentLocale();
        englishMenuItem.setGraphic(current.equals(I18n.ENGLISH_US) ? new Label("✓") : null);
        schineseMenuItem.setGraphic(current.equals(I18n.SIMPLIFIED_CHINESE) ? new Label("✓") : null);
    }

    /**
     * Initializes internationalization settings.
     */
    private void initializeI18n() {
        // I18n class auto-detects system locale on static initialization
        System.out.println("Current locale: " + I18n.getCurrentLocale());
        System.out.println("Bundle file: " + I18n.getBundleFileName(I18n.getCurrentLocale()));
    }
    
    /**
     * Switches application language.
     */
    private void switchLanguage(Locale locale) {
        I18n.setLocale(locale);
        updateUIForLanguage();
        updateLanguageMenuCheckmarks();
        
        // Show language change notification
        showTemporaryMessage(I18n.format("status.languageChanged", 
            I18n.getLocaleDisplayName(locale)));
    }
    
    /**
     * Updates all UI elements for the current language.
     */
    private void updateUIForLanguage() {
        String osName = PlatformUtils.isWindows() ? "Windows" : (PlatformUtils.isMac() ? "macOS" : "Unknown");
        String arch = PlatformUtils.getArchForUpdateService();
        String localizedOS = I18n.getLocalizedOSName(osName);
        
        // Update window title
        Stage stage = (Stage) fetchButton.getScene().getWindow();
        updateWindowTitle(stage, localizedOS, arch);
        
        // Update menu bar
        menuBar.getMenus().get(0).setText(I18n.get("menu.file"));
        menuBar.getMenus().get(1).setText(I18n.get("menu.language"));
        menuBar.getMenus().get(2).setText(I18n.get("menu.help"));
        
        menuBar.getMenus().get(0).getItems().get(0).setText(I18n.get("menu.exit"));
        menuBar.getMenus().get(2).getItems().get(0).setText(I18n.get("menu.about"));
        
        englishMenuItem.setText(I18n.get("menu.english"));
        schineseMenuItem.setText(I18n.get("menu.schinese"));
        
        // Update UI labels
        HBox versionBox = (HBox) versionChoiceBox.getParent();
        ((Label) versionBox.getChildren().get(0)).setText(I18n.get("label.selectVersion"));
        
        // Update version choice box
        String currentSelection = versionChoiceBox.getValue();
        versionChoiceBox.getItems().clear();
        initializeVersionChoiceBox();
        versionChoiceBox.setValue(getLocalizedVersionFromKey(getVersionKeyFromLocalized(currentSelection)));
        
        // Update proxy section
        ((Label) proxySection.getChildren().get(0)).setText(I18n.get("label.proxySettings"));
        noProxyRadio.setText(I18n.get("radio.noProxy"));
        httpProxyRadio.setText(I18n.get("radio.httpProxy"));
        httpsProxyRadio.setText(I18n.get("radio.httpsProxy"));
        socks5ProxyRadio.setText(I18n.get("radio.socks5Proxy"));
        
        proxyHostLabel.setText(I18n.get("label.proxyHost"));
        proxyHostField.setPromptText(I18n.get("placeholder.proxyHost"));
        
        proxyPortLabel.setText(I18n.get("label.proxyPort"));
        proxyPortField.setPromptText(I18n.get("placeholder.proxyPort"));
        
        proxyUserLabel.setText(I18n.get("label.proxyUser"));
        proxyUserField.setPromptText(I18n.get("placeholder.proxyUser"));
        
        proxyPassLabel.setText(I18n.get("label.proxyPass"));
        proxyPassField.setPromptText(I18n.get("placeholder.proxyPass"));
        
        // Update buttons
        fetchButton.setText(I18n.get("button.fetch"));
        downloadButton.setText(I18n.get("button.download"));
        cancelButton.setText(I18n.get("button.cancel"));
        
        // Update link list label
        VBox parentVBox = (VBox) linkListView.getParent();
        int linkListIndex = parentVBox.getChildren().indexOf(linkListView);
        ((Label) parentVBox.getChildren().get(linkListIndex - 1)).setText(I18n.get("label.availableLinks"));
        
        // Update progress label if not in temporary message mode
        String currentProgressText = progressLabel.getText();
        String pattern = I18n.get("clipboard.tempMessagePattern");
        if (!matchesTempMessagePattern(currentProgressText, pattern)) {
            progressLabel.setText(I18n.get("status.ready"));
        }
        
        // Update result label if visible
        if (resultLabel.isVisible()) {
            // Extract file path from existing text
            String currentText = resultLabel.getText();
            int startIndex = currentText.indexOf(": ");
            if (startIndex != -1) {
                String filePath = currentText.substring(startIndex + 2);
                resultLabel.setText(I18n.format("result.savedTo", filePath));
            }
        }
        
        // Update context menu
        createLinkListContextMenu();
    }
    
    /**
     * Checks if text matches temporary message pattern.
     */
    private boolean matchesTempMessagePattern(String text, String pattern) {
        if (text == null || pattern == null) return false;
        
        String[] patterns = pattern.split("\\|");
        for (String p : patterns) {
            if (text.contains(p.trim())) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Updates window title with current language.
     */
    private void updateWindowTitle(Stage stage, String osName, String arch) {
        String appTitle = I18n.format("app.title", 
            I18n.get("app.version"), osName, arch);
        stage.setTitle(appTitle);
    }

    /**
     * Initializes version choice box with localized version names.
     */
    private void initializeVersionChoiceBox() {
        String[] versions = {"Stable", "Beta", "Dev", "Canary"};
        for (String version : versions) {
            versionChoiceBox.getItems().add(I18n.getLocalizedVersion(version));
        }
        versionChoiceBox.setValue(I18n.getLocalizedVersion("Stable"));
    }
    
    /**
     * Creates proxy settings UI components.
     */
    private void createProxySettingsUI() {
        proxyToggleGroup = new ToggleGroup();
        noProxyRadio = new RadioButton(I18n.get("radio.noProxy"));
        noProxyRadio.setToggleGroup(proxyToggleGroup);
        noProxyRadio.setSelected(true);
        
        httpProxyRadio = new RadioButton(I18n.get("radio.httpProxy"));
        httpProxyRadio.setToggleGroup(proxyToggleGroup);
        
        httpsProxyRadio = new RadioButton(I18n.get("radio.httpsProxy"));
        httpsProxyRadio.setToggleGroup(proxyToggleGroup);
        
        socks5ProxyRadio = new RadioButton(I18n.get("radio.socks5Proxy"));
        socks5ProxyRadio.setToggleGroup(proxyToggleGroup);
        
        HBox proxyTypeBox = new HBox(10, noProxyRadio, httpProxyRadio, httpsProxyRadio, socks5ProxyRadio);
        
        proxyHostLabel = new Label(I18n.get("label.proxyHost"));
        proxyHostField = new TextField();
        proxyHostField.setPromptText(I18n.get("placeholder.proxyHost"));
        proxyHostField.setDisable(true);
        
        proxyPortLabel = new Label(I18n.get("label.proxyPort"));
        proxyPortField = new TextField();
        proxyPortField.setPromptText(I18n.get("placeholder.proxyPort"));
        proxyPortField.setDisable(true);
        
        proxyUserLabel = new Label(I18n.get("label.proxyUser"));
        proxyUserField = new TextField();
        proxyUserField.setPromptText(I18n.get("placeholder.proxyUser"));
        proxyUserField.setDisable(true);
        
        proxyPassLabel = new Label(I18n.get("label.proxyPass"));
        proxyPassField = new PasswordField();
        proxyPassField.setPromptText(I18n.get("placeholder.proxyPass"));
        proxyPassField.setDisable(true);
        
        GridPane proxyGrid = new GridPane();
        proxyGrid.setHgap(10);
        proxyGrid.setVgap(10);
        proxyGrid.add(proxyHostLabel, 0, 0);
        proxyGrid.add(proxyHostField, 1, 0);
        proxyGrid.add(proxyPortLabel, 2, 0);
        proxyGrid.add(proxyPortField, 3, 0);
        proxyGrid.add(proxyUserLabel, 0, 1);
        proxyGrid.add(proxyUserField, 1, 1);
        proxyGrid.add(proxyPassLabel, 2, 1);
        proxyGrid.add(proxyPassField, 3, 1);
        
        ProxyToggleChangeListener proxyToggleListener = new ProxyToggleChangeListener(
            noProxyRadio, proxyHostField, proxyPortField, proxyUserField, proxyPassField);
        proxyToggleGroup.selectedToggleProperty().addListener(proxyToggleListener);
        
        proxySection = new VBox(10);
        proxySection.setPadding(new Insets(10));
        proxySection.setStyle("-fx-border-color: lightgray; -fx-border-radius: 5; -fx-background-color: #f9f9f9;");
        proxySection.getChildren().addAll(
            new Label(I18n.get("label.proxySettings")),
            proxyTypeBox,
            proxyGrid
        );
    }
    
    /**
     * Gets the localized version name from English key.
     */
    private String getLocalizedVersionFromKey(String englishKey) {
        return I18n.getLocalizedVersion(englishKey);
    }

    /**
     * Gets the English version key from localized version name.
     */
    private String getVersionKeyFromLocalized(String localizedVersion) {
        String[] versions = {"Stable", "Beta", "Dev", "Canary"};
        for (String version : versions) {
            if (localizedVersion.equals(I18n.getLocalizedVersion(version))) {
                return version;
            }
        }
        return "Stable"; // Default fallback
    }

    /**
     * Creates and shows the about dialog.
     */
    private void createAboutDialog() {
        Alert aboutDialog = new Alert(Alert.AlertType.INFORMATION);
        aboutDialog.setTitle(I18n.format("about.title", I18n.get("app.name")));
        aboutDialog.setHeaderText(I18n.format("about.header", 
            I18n.get("app.name"), I18n.get("app.version")));
        
        String osName = PlatformUtils.getOSDisplayName();
        String localizedOS = I18n.getLocalizedOSName(osName);
        String arch = PlatformUtils.getArchForUpdateService();
        
        aboutDialog.setContentText(I18n.format("about.content",
            I18n.get("app.name"), I18n.get("app.version"), 
            localizedOS, arch));
        
        Stage stage = (Stage) aboutDialog.getDialogPane().getScene().getWindow();
        Stage mainStage = (Stage) fetchButton.getScene().getWindow();
        stage.getIcons().addAll(mainStage.getIcons());
        
        aboutDialog.showAndWait();
    }

    @Override
    public void stop() {
        if (executorService != null) {
            executorService.shutdownNow();
        }
        
        if (currentDownloadTask != null && currentDownloadTask.isRunning()) {
            currentDownloadTask.cancel();
        }
        
        if (linkListContextMenu != null) {
            linkListContextMenu.hide();
            linkListView.setContextMenu(null);
            linkListContextMenu.getItems().clear();
        }
    }
    
    /**
     * Creates context menu for link list view.
     */
    private void createLinkListContextMenu() {
        linkListContextMenu = new ContextMenu();
        
        MenuItem copyLinkItem = new MenuItem(I18n.get("contextMenu.copySelected"));
        copyLinkItem.setOnAction(e -> copySelectedLinkToClipboard());
        
        MenuItem copyAllLinksItem = new MenuItem(I18n.get("contextMenu.copyAll"));
        copyAllLinksItem.setOnAction(e -> copyAllLinksToClipboard());
        
        linkListContextMenu.getItems().addAll(copyLinkItem, copyAllLinksItem);
        
        linkListView.setContextMenu(linkListContextMenu);
    }
    
    /**
     * Copies selected link to clipboard.
     */
    private void copySelectedLinkToClipboard() {
        int selectedIndex = linkListView.getSelectionModel().getSelectedIndex();
        if (selectedIndex >= 0 && selectedIndex < currentDownloadLinks.size()) {
            String url = currentDownloadLinks.get(selectedIndex);
            copyToClipboard(url, I18n.get("clipboard.copiedSelected"));
        } else {
            showAlert(I18n.get("alert.noSelection.title"), 
                     I18n.get("alert.noSelection.content"));
        }
    }
    
    /**
     * Copies all links to clipboard.
     */
    private void copyAllLinksToClipboard() {
        if (currentDownloadLinks.isEmpty()) {
            showAlert(I18n.get("alert.noLinks.title"), 
                     I18n.get("alert.noLinks.content"));
            return;
        }
        
        StringBuilder allLinks = new StringBuilder();
        for (String link : currentDownloadLinks) {
            allLinks.append(link).append("\n");
        }
        
        copyToClipboard(allLinks.toString().trim(), I18n.get("clipboard.copiedAll"));
    }
    
    /**
     * Copies text to clipboard and shows success message.
     */
    private void copyToClipboard(String text, String successMessage) {
        try {
            StringSelection selection = new StringSelection(text);
            java.awt.datatransfer.Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            clipboard.setContents(selection, null);
            
            showTemporaryMessage(successMessage);
            
        } catch (Exception ex) {
            showAlert(I18n.get("alert.clipboardError.title"),
                     I18n.format("alert.clipboardError.content", ex.getMessage()));
        }
    }
    
    /**
     * Shows a temporary message in the progress label.
     */
    private void showTemporaryMessage(String message) {
        String originalText = progressLabel.getText();
        String originalStyle = progressLabel.getStyle();
        boolean wasVisible = progressLabel.isVisible();
        
        Platform.runLater(() -> {
            progressLabel.setVisible(true);
            progressLabel.setText(message);
            progressLabel.setStyle("-fx-text-fill: blue;");
        });
        
        new Thread(() -> {
            try {
                Thread.sleep(3000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            
            Platform.runLater(() -> {
                if (progressLabel.getText().equals(message)) {
                    progressLabel.setText(originalText);
                    progressLabel.setStyle(originalStyle);
                    progressLabel.setVisible(wasVisible);
                }
            });
        }).start();
    }

    /**
     * Loads application icons.
     */
    private void loadApplicationIcons(Stage stage) {
        String[] iconPaths = {"/images/512.png", "/images/256.png", "/images/128.png", 
                             "/images/96.png", "/images/72.png", "/images/64.png"};
        
        for (String path : iconPaths) {
            try (java.io.InputStream is = Main.class.getResourceAsStream(path)) {
                if (is != null) {
                    Image icon = new Image(is);
                    if (!icon.isError()) {
                        stage.getIcons().add(icon);
                    }
                }
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * Creates ProxySettings from UI inputs.
     */
    private ProxySettings createProxySettings() {
        RadioButton selectedRadio = (RadioButton) proxyToggleGroup.getSelectedToggle();
        
        if (selectedRadio == noProxyRadio) {
            return new ProxySettings();
        }
        
        String host = proxyHostField.getText().trim();
        String portText = proxyPortField.getText().trim();
        String username = proxyUserField.getText().trim();
        String password = proxyPassField.getText();
        
        if (host.isEmpty() || portText.isEmpty()) {
            showAlert(I18n.get("alert.proxyError.title"),
                     I18n.get("alert.proxyError.hostPort"));
            return null;
        }
        
        try {
            int port = Integer.parseInt(portText);
            if (port < 1 || port > 65535) {
                showAlert(I18n.get("alert.proxyError.title"),
                         I18n.get("alert.proxyError.portRange"));
                return null;
            }
            
            ProxyType proxyType;
            if (selectedRadio == httpProxyRadio) {
                proxyType = ProxyType.HTTP;
            } else if (selectedRadio == httpsProxyRadio) {
                proxyType = ProxyType.HTTPS;
            } else if (selectedRadio == socks5ProxyRadio) {
                proxyType = ProxyType.SOCKS5;
            } else {
                return new ProxySettings();
            }
            
            if (!username.isEmpty()) {
                return new ProxySettings(proxyType, host, port, username, password);
            } else {
                return new ProxySettings(proxyType, host, port);
            }
            
        } catch (NumberFormatException e) {
            showAlert(I18n.get("alert.proxyError.title"),
                     I18n.get("alert.proxyError.portNumber"));
            return null;
        }
    }
    
    /**
     * Shows an alert dialog.
     */
    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    /**
     * Fetches download links for selected version.
     */
    private void fetchDownloadLinks() {
        resultLabel.setVisible(false);
        progressLabel.setVisible(false);
        progressBar.setVisible(false);
        
        ProxySettings proxySettings = createProxySettings();
        if (proxySettings == null) {
            return;
        }
        
        fetchButton.setDisable(true);

        String localizedVersion = versionChoiceBox.getValue();
        String versionKey = getVersionKeyFromLocalized(localizedVersion);
        
        FetchLinksTask task = new FetchLinksTask(versionKey, proxySettings);

        task.setOnRunning(TaskStateHandlers.createFetchLinksRunningHandler(progressLabel, proxySettings));
        
        task.setOnSucceeded(TaskStateHandlers.createFetchLinksSucceededHandler(
            progressLabel, linkListView, currentDownloadLinks, fetchButton));
        
        task.setOnFailed(TaskStateHandlers.createFetchLinksFailedHandler(
            progressLabel, linkListView, fetchButton));

        task.messageProperty().addListener((obs, old, msg) -> {
            if (msg != null) {
                progressLabel.setText(msg);
            }
        });

        executorService.submit(task);
    }

    /**
     * Starts downloading selected file.
     */
    private void startDownload() {
        int selectedIndex = linkListView.getSelectionModel().getSelectedIndex();
        if (selectedIndex < 0 || selectedIndex >= currentDownloadLinks.size()) return;

        String url = currentDownloadLinks.get(selectedIndex);
        String fileName = url.substring(url.lastIndexOf('/') + 1);
        
        String userHome = System.getProperty("user.home");
        File downloadsDir = new File(userHome, "Downloads");
        if (!downloadsDir.exists()) {
            downloadsDir.mkdirs();
        }
        if (progressBar.getProgress() >= 1.0) {
            progressBar.setProgress(0.0);
        }
        
        File outputFile = new File(downloadsDir, fileName);

        if (outputFile.exists()) {
            Alert confirmAlert = new Alert(Alert.AlertType.CONFIRMATION);
            confirmAlert.setTitle(I18n.get("alert.fileExists.title"));
            confirmAlert.setHeaderText(I18n.get("alert.fileExists.header"));
            confirmAlert.setContentText(I18n.format("alert.fileExists.content", fileName));
            Stage alertStage = (Stage) confirmAlert.getDialogPane().getScene().getWindow();
            Stage mainStage = (Stage) fetchButton.getScene().getWindow();
            alertStage.getIcons().addAll(mainStage.getIcons());
            if (confirmAlert.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
                return;
            }
        }
        
        ProxySettings proxySettings = createProxySettings();
        if (proxySettings == null) {
            return;
        }

        resultLabel.setVisible(false);
        progressBar.setVisible(true);
        progressLabel.setVisible(true);
        progressBar.setProgress(0);
        
        String proxyInfo = proxySettings.isUseProxy() ? 
            I18n.format("status.startingVia", 
                I18n.getLocalizedProxyType(proxySettings.getProxyTypeString())) : 
            I18n.get("status.starting");
        progressLabel.setText(proxyInfo);
        progressLabel.setStyle("-fx-text-fill: black;");
        
        fetchButton.setDisable(true);
        downloadButton.setDisable(true);
        cancelButton.setDisable(false);
        linkListView.setDisable(true);

        currentDownloadTask = new DownloadFileTask(url, outputFile, proxySettings);
        
        DownloadTaskHandlers.setupDownloadHandlers(
            currentDownloadTask,
            progressBar,
            progressLabel,
            resultLabel,
            outputFile,
            this::resetUIAfterDownload
        );

        executorService.submit(currentDownloadTask);
    }

    /**
     * Cancels the current download.
     */
    private void cancelDownload() {
        if (currentDownloadTask != null && currentDownloadTask.isRunning()) {
            Alert confirmAlert = new Alert(Alert.AlertType.CONFIRMATION);
            confirmAlert.setTitle(I18n.get("alert.cancelConfirm.title"));
            confirmAlert.setHeaderText(I18n.get("alert.cancelConfirm.header"));
            confirmAlert.setContentText(I18n.get("alert.cancelConfirm.content"));
            
            if (confirmAlert.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
                currentDownloadTask.cancel();
            }
        }
    }

    /**
     * Resets UI after download completion/cancellation/failure.
     */
    private void resetUIAfterDownload() {
        Platform.runLater(() -> {
            currentDownloadTask = null;
            fetchButton.setDisable(false);
            downloadButton.setDisable(linkListView.getSelectionModel().getSelectedItem() == null);
            cancelButton.setDisable(true);
            linkListView.setDisable(false);
        });
    }

    public static void main(String[] args) {
        launch(args);
    }
}