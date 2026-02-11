package rover.ui;

import rover.model.NetworkInterfaceCustom;
import rover.model.PlanetSwitchDevice;
import rover.utils.LanguageManager;
import rover.utils.PreferencesManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rover.controller.MainController;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The main window of the Rover21 application using Java Swing and FlatLightLaf.
 * <p>
 * This class represents the View component of the MVC architecture. It provides
 * the user interface for network adapter selection, device discovery, and results
 * display. The window supports internationalization (i18n) with English and French.
 * </p>
 *
 * @author Benjamin FOUILHAC, ADM21 - January/February 2026
 * @see <a href="https://docs.oracle.com/javase/8/docs/api/javax/swing/package-summary.html">Java Swing</a>
 * @see <a href="https://www.formdev.com/flatlaf/">FlatLaf</a>
 */
public class MainWindow extends JFrame {

    private static final Logger logger = LoggerFactory.getLogger(MainWindow.class);

    private static final Font FONT_MODERN = new Font("Segoe UI", Font.PLAIN, 13);
    private static final Font FONT_TABLE = new Font("Segoe UI", Font.PLAIN, 12);

    /*
     * ===================================================================================
     * RESPONSIVE SIZING CONSTANTS
     * ===================================================================================
     * These constants define the responsive behavior of UI components.
     * All ratios are calculated relative to the minimum window width (1250px) to ensure
     * proper display at all supported resolutions.
     *
     * Layout composition at 1250px (minimum):
     * - Left panel  (~40%): Label + ComboBox + Refresh button
     * - Center panel (~25%): Two company logos
     * - Right panel (~35%): Progress bar + Discover button + margins
     * ===================================================================================
     */

    // Button sizing: responsive from 100px (min) to 180px (comfortable max)
    private static final int BUTTON_HEIGHT = 32;
    private static final int BUTTON_MIN_WIDTH = 100;
    private static final int BUTTON_MAX_WIDTH = 180;
    /** Button width = 12% of window width (150px at 1250px, 180px capped at 1500px+). */
    private static final double BUTTON_WIDTH_RATIO = 0.12;

    private static final Color COLOR_ALTERNATE_ROW = new Color(224, 224, 224);
    private static final Color COLOR_STATUS_BACKGROUND = new Color(227, 242, 253);
    private static final Color COLOR_STATUS_BORDER = new Color(144, 202, 249);
    private static final Color COLOR_STATUS_TEXT = new Color(25, 118, 210);
    private static final Color COLOR_TABLE_GRID = new Color(220, 220, 220);
    private static final Color COLOR_HEADER_BACKGROUND = new Color(245, 245, 245);
    private static final Color COLOR_HEADER_TEXT = new Color(60, 60, 60);

    private static final int DEFAULT_WINDOW_WIDTH = 1550;
    private static final int DEFAULT_WINDOW_HEIGHT = 650;

    // Minimum window dimensions to ensure all UI elements remain visible and usable.
    private static final int MIN_WINDOW_WIDTH = 1250;
    private static final int MIN_WINDOW_HEIGHT = 400;

    // ComboBox sizing: responsive from 180px (min) to 350px (comfortable max)
    private static final int COMBO_BOX_HEIGHT = 30;
    private static final int COMBO_BOX_MIN_WIDTH = 180;
    private static final int COMBO_BOX_MAX_WIDTH = 350;

    private static final double COMBO_BOX_WIDTH_RATIO = 0.22;

    // Logo sizing: responsive with a preserved aspect ratio (original 310x80)
    private static final int LOGO_MIN_WIDTH = 140;
    private static final int LOGO_MAX_WIDTH = 260;
    private static final int LOGO_MIN_HEIGHT = 36;
    private static final int LOGO_MAX_HEIGHT = 67;

    private static final double LOGO_WIDTH_RATIO = 0.13; // Logo width = 13% of window width (162px at 1250px, 201px at 1550px (standard size), 249px at 1920px).

    private static final double LOGO_ASPECT_RATIO = 310.0 / 80.0; // Logo aspect ratio (width/height) from the original 310x80 dimensions.

    // Table column indices
    private static final int COL_LOCK = 0;
    private static final int COL_DEVICE_NAME = 1;
    private static final int COL_MAC = 2;
    private static final int COL_FIRMWARE = 3;
    private static final int COL_IPV4 = 4;
    private static final int COL_NETMASK = 5;
    private static final int COL_GATEWAY = 6;
    private static final int COL_DESCRIPTION = 7;
    private static final int COL_LOCATION = 8;
    private static final int COL_UPTIME = 9;
    private static final int COL_TELNET = 10;
    private static final int COL_SSH = 11;
    private static final int COL_HTTP = 12;
    private static final int COL_HTTPS = 13;
    private static final int COL_PASSWORD = 14;

    private final transient LanguageManager lang = LanguageManager.getInstance();

    private transient AWTEventListener listener;

    private final Map<String, Map<String, Boolean>> protocolStatusCache = new HashMap<>();
    private final Map<String, Integer> securityStatusCache = new HashMap<>();

    // UI Components
    private JComboBox<NetworkInterfaceCustom> adapterComboBox;
    private JProgressBar progressBar;
    private JLabel statusLabel;
    private JLabel deviceCountLabel;
    private JTable devicesTable;
    private DefaultTableModel tableModel;

    // Action buttons
    private JButton downloadSCButton;
    private JButton getAllSCButton;
    private JButton discoverButton;
    private JButton refreshAdaptersButton;
    private JButton showRSTPTopologyButton;

    // Responsive UI components (logos with original images for scaling)
    private JLabel planetLogoLabel;
    private JLabel adm21LogoLabel;
    private transient Image planetLogoOriginal;
    private transient Image adm21LogoOriginal;

    // Data lists
    private transient List<PlanetSwitchDevice> discoveredDevices;
    private transient List<NetworkInterfaceCustom> availableAdapters;

    private transient UIEventListener eventListener; // Listener for UI events (controller).

    private int lastDiscoveredCount = 0;
    private String lastDuplicateMacs = "";

    private APP_STATE appState; // Current application state.

    private transient LogDialog logDialog; // Log viewer dialog

    /**
     * Interface for callbacks to the MainController.
     * <p>
     * This interface defines all user interaction events that the controller
     * needs to handle. The main window calls these methods when the user
     * performs actions like clicking buttons or menu items.
     * </p>
     *
     * @see MainController
     */
    public interface UIEventListener {

        /**
         * Called when the discovery button is clicked.
         *
         * @param selectedAdapter the network adapter selected for discovery
         */
        void onDiscoverClicked(NetworkInterfaceCustom selectedAdapter);

        /**
         * Called when the get config button is clicked.
         *
         * @param device the device to get startup config for
         */
        void onGetStartupConfigClicked(PlanetSwitchDevice device);

        /**
         * Called when the get all configs button is clicked.
         *
         * @param devices the list of devices to get startup configs for
         */
        void onGetAllConfigsClicked(List<PlanetSwitchDevice> devices);

        /**
         * Called when the export menu item is clicked.
         *
         * @param devices the devices to export
         */
        void onExportClicked(List<PlanetSwitchDevice> devices, String fileType);

        /**
         * Called when clear results are requested.
         */
        void onClearResultsClicked();

        /**
         * Called when refresh adapters are requested.
         */
        void onRefreshAdaptersClicked();
    }

    /**
     * Constructs the main window and initializes all UI components.
     * <p>
     * The constructor sets up the window frame, creates all panels and components,
     * centers the window on screen, and restores the saved window size.
     * </p>
     */
    public MainWindow() {
        this.discoveredDevices = new ArrayList<>();
        this.availableAdapters = new ArrayList<>();

        initializeFonts();
        initializeFrame();
        createMenuBar();
        createToolbar();
        createCenterPanel();
        createBottomPanel();

        setLocationRelativeTo(null);
        toFront();
        setupWindowListener();
        restoreWindowState();

        // Apply initial responsive sizing based on restored window dimensions
        updateResponsiveComponents();

        appState = APP_STATE.INIT;

        logger.info("MainWindow initialized with language: {}", lang.getCurrentLanguageCode());
    }

    /**
     * Sets the current application state.
     *
     * @param appState the new application state
     */
    public void setAppState(APP_STATE appState) {
        this.appState = appState;
    }

    /**
     * Sets the event listener for UI events.
     *
     * @param listener the UIEventListener to handle events
     */
    public void setEventListener(UIEventListener listener) {
        this.eventListener = listener;
    }

    /**
     * Loads network adapters into the combo box.
     * <p>
     * Only adapters that are valid for scanning (up, non-loopback, with valid IP)
     * are added to the combo box.
     * </p>
     *
     * @param adapters the list of network adapters to display
     */
    public void loadNetworkInterfaceCustoms(List<NetworkInterfaceCustom> adapters) {
        this.availableAdapters = new ArrayList<>(adapters);
        adapterComboBox.removeAllItems();

        for (NetworkInterfaceCustom adapter : adapters) {
            if (adapter.isValidForScanning()) {
                adapterComboBox.addItem(adapter);
            }
        }

        if (adapterComboBox.getItemCount() > 0) {
            adapterComboBox.setSelectedIndex(0);
        }
    }

    /**
     * Displays discovered devices on the table.
     *
     * @param devices the list of discovered devices
     */
    public void displayDevices(List<PlanetSwitchDevice> devices) {
        this.discoveredDevices = new ArrayList<>(devices);
        tableModel.setRowCount(0);

        for (PlanetSwitchDevice device : devices) {
            addDeviceToTable(device);
        }

        updateDeviceCount();
        updateButtonStates();
    }

    /**
     * Adds or updates a device in the table.
     * <p>
     * If the device already exists (by MAC address), the row is updated.
     * Otherwise, a new row is added.
     * </p>
     *
     * @param device the device to add or update
     */
    public void addOrUpdateDevice(PlanetSwitchDevice device) {
        int existingRow = findDeviceRowByMac(device.getMacAddress());

        if (existingRow >= 0) {
            updateDeviceRow(existingRow, device);
        } else {
            addDeviceRow(device);
        }
    }

    /**
     * Updates the status message in the status bar.
     *
     * @param message the message to display
     */
    public void setStatusMessage(String message) {
        statusLabel.setText(message);
    }

    /**
     * Enables or disables scanning mode.
     * <p>
     * In scanning mode, the discovery button is disabled and the progress
     * bar is visible.
     * </p>
     *
     * @param scanning true to enable scanning mode, false to disable
     */
    public void setScanningMode(boolean scanning) {
        discoverButton.setEnabled(!scanning);
        progressBar.setVisible(scanning);

        if (scanning) {
            statusLabel.setText(lang.get("status.scanning", ""));
        }
    }

    /**
     * Updates the protocol status checkbox for a device.
     *
     * @param macAddress the device MAC address
     * @param protocol the protocol name ("telnet", "ssh", "http", "https")
     * @param isAvailable true if the protocol is available
     */
    public void setProtocolStatus(String macAddress, String protocol, boolean isAvailable) {
        int row = findDeviceRowByMac(macAddress);

        if (row >= 0) {
            int columnIndex = getProtocolColumnIndex(protocol);

            if (columnIndex >= 0) {
                tableModel.setValueAt(isAvailable, row, columnIndex);
                logger.debug("Set {} status to {} for device at row {}", protocol, isAvailable, row);
            }

            protocolStatusCache.computeIfAbsent(macAddress, _ -> new HashMap<>()).put(protocol.toLowerCase(), isAvailable);
        }
    }

    /**
     * Updates the password text a device.
     *
     * @param device the device to update
     */
    public void setDevicePassword(PlanetSwitchDevice device){
        int row = findDeviceRowByMac(device.getMacAddress());
        if (row >= 0) {
            tableModel.setValueAt(device.getPassword(), row, COL_PASSWORD);
        }
        logger.debug("Set device password to {} for device at row {}", device.getPassword(), row);
    }

    /**
     * Updates the security lock icon for a device.
     *
     * @param macAddress the device MAC address
     * @param status the security status (LOCK_UNKNOWN, LOCK_SECURE, LOCK_INSECURE)
     */
    public void setSecurityStatus(String macAddress, int status) {
        int row = findDeviceRowByMac(macAddress);

        if (row >= 0) {
            tableModel.setValueAt(status, row, COL_LOCK);
            securityStatusCache.put(macAddress, status);
            logger.debug("Set security status to {} for device {}", status, macAddress);
        }
    }

    /**
     * Sets the discovery completion state for status preservation during language changes.
     *
     * @param deviceCount the number of devices discovered
     * @param duplicateMacs comma-separated MAC addresses of duplicate devices
     */
    public void setDiscoveryState(int deviceCount, String duplicateMacs) {
        this.lastDiscoveredCount = deviceCount;
        this.lastDuplicateMacs = duplicateMacs;
        logger.debug("Discovery state saved: {} devices, duplicates: {}", deviceCount, duplicateMacs);
    }

    /**
     * Clears all results from the table.
     */
    public void clearResults() {
        discoveredDevices.clear();
        tableModel.setRowCount(0);
        protocolStatusCache.clear();
        securityStatusCache.clear();
        clearDiscoveryState();
        updateDeviceCount();
        updateButtonStates();
        statusLabel.setText(lang.get("status.ready"));
    }

    /**
     * Clears the adapter combo box.
     */
    public void clearAdapters() {
        availableAdapters.clear();
        adapterComboBox.removeAllItems();
    }

    /**
     * Displays an error dialog.
     *
     * @param title the dialog title
     * @param message the error message
     */
    public void showError(String title, String message) {
        JOptionPane.showMessageDialog(this, message, title, JOptionPane.ERROR_MESSAGE);
    }

    /**
     * Displays an information dialog.
     *
     * @param title the dialog title
     * @param message the information message
     */
    public void showInfo(String title, String message) {
        JOptionPane.showMessageDialog(this, message, title, JOptionPane.INFORMATION_MESSAGE);
    }

    /**
     * Formats a list of duplicate devices into a comma-separated MAC address string.
     *
     * @param duplicateDevices list of devices with duplicate IPs
     * @return comma-separated MAC addresses (e.g., "A8:F7:E0:C5:10:5D, A8:F7:E0:DE:0F:96")
     */
    public String formatDuplicateMACs(List<PlanetSwitchDevice> duplicateDevices) {
        if (duplicateDevices.isEmpty()) {
            return "";
        }

        return duplicateDevices.stream()
                .map(PlanetSwitchDevice::getMacAddress)
                .collect(Collectors.joining(", "));
    }

    /**
     * Initializes fonts for all UI components.
     */
    private void initializeFonts() {
        UIManager.put("Button.font", FONT_MODERN);
        UIManager.put("Label.font", FONT_MODERN);
        UIManager.put("ComboBox.font", FONT_MODERN);
        UIManager.put("Table.font", FONT_TABLE);
        UIManager.put("TableHeader.font", FONT_TABLE);
        UIManager.put("Menu.font", FONT_MODERN);
        UIManager.put("MenuItem.font", FONT_MODERN);
        UIManager.put("CheckBoxMenuItem.font", FONT_MODERN);
    }

    /**
     * Initializes the main window frame properties.
     * <p>
     * Sets the window title, default size, minimum size to ensure UI elements
     * remain visible during resizing, and configures the layout manager.
     * </p>
     */
    private void initializeFrame() {
        setTitle(lang.get("window.title"));
        setSize(PreferencesManager.getWindowWidth(DEFAULT_WINDOW_WIDTH), PreferencesManager.getWindowHeight(DEFAULT_WINDOW_HEIGHT));
        setMinimumSize(new Dimension(MIN_WINDOW_WIDTH, MIN_WINDOW_HEIGHT));
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());
    }

    /**
     * Sets up window listeners for close events and responsive resizing.
     * <p>
     * Configures both a WindowListener for cleanup on close and a ComponentListener
     * for dynamic UI component resizing when the window dimensions change.
     * </p>
     */
    private void setupWindowListener() {
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent windowEvent) {
                saveWindowState();
                cleanup();
            }
        });

        // Add a component listener for responsive resizing
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                updateResponsiveComponents();
            }
        });
    }

    /**
     * Updates all responsive UI components based on current window dimensions.
     * <p>
     * Recalculates and applies appropriate sizes for buttons, combo boxes, and logos
     * based on the current window width, respecting minimum and maximum constraints.
     * </p>
     */
    private void updateResponsiveComponents() {
        int windowWidth = getWidth();

        // Update button sizes
        int buttonWidth = calculateResponsiveWidth(windowWidth, BUTTON_WIDTH_RATIO, BUTTON_MIN_WIDTH, BUTTON_MAX_WIDTH);
        Dimension buttonSize = new Dimension(buttonWidth, BUTTON_HEIGHT);
        updateButtonSize(discoverButton, buttonSize);
        updateButtonSize(refreshAdaptersButton, buttonSize);
        updateButtonSize(downloadSCButton, buttonSize);
        updateButtonSize(getAllSCButton, buttonSize);
        updateButtonSize(showRSTPTopologyButton, buttonSize);

        // Update combo box size
        int comboWidth = calculateResponsiveWidth(windowWidth, COMBO_BOX_WIDTH_RATIO, COMBO_BOX_MIN_WIDTH, COMBO_BOX_MAX_WIDTH);
        if (adapterComboBox != null) {
            Dimension comboSize = new Dimension(comboWidth, COMBO_BOX_HEIGHT);
            adapterComboBox.setPreferredSize(comboSize);
            adapterComboBox.setMaximumSize(comboSize);
        }

        // Update logo sizes
        updateLogoSizes(windowWidth);

        // Revalidate layout
        revalidate();
        repaint();
    }

    /**
     * Calculates a responsive width based on window width and constraints.
     * <p>
     * The calculated width is clamped between minimum and maximum values to ensure
     * the component remains usable at all window sizes.
     * </p>
     *
     * @param windowWidth the current window width in pixels
     * @param ratio       the ratio of component width relative to window width (0.0 to 1.0)
     * @param minWidth    the minimum allowed width in pixels
     * @param maxWidth    the maximum allowed width in pixels
     * @return the calculated width, clamped between min and max
     */
    private int calculateResponsiveWidth(int windowWidth, double ratio, int minWidth, int maxWidth) {
        int calculatedWidth = (int) (windowWidth * ratio);
        // Math.clamp(value, min, max) - clamps value within [min, max] range
        return Math.clamp(calculatedWidth, minWidth, maxWidth);
    }

    /**
     * Updates a button's preferred and maximum size for responsive behavior.
     *
     * @param button the button to update (maybe null)
     * @param size the new size to apply
     */
    private void updateButtonSize(JButton button, Dimension size) {
        if (button != null) {
            button.setPreferredSize(size);
            button.setMaximumSize(size);
        }
    }

    /**
     * Updates logo sizes based on window width while maintaining an aspect ratio.
     *
     * @param windowWidth the current window width
     */
    private void updateLogoSizes(int windowWidth) {
        int logoWidth = calculateResponsiveWidth(windowWidth, LOGO_WIDTH_RATIO, LOGO_MIN_WIDTH, LOGO_MAX_WIDTH);
        int logoHeight = (int) (logoWidth / LOGO_ASPECT_RATIO);

        // Clamp height to constraints
        logoHeight = Math.clamp(logoHeight, LOGO_MIN_HEIGHT, LOGO_MAX_HEIGHT);

        // Recalculate width based on clamped height to maintain an aspect ratio
        logoWidth = (int) (logoHeight * LOGO_ASPECT_RATIO);

        if (planetLogoLabel != null && planetLogoOriginal != null) {
            Image scaledImage = planetLogoOriginal.getScaledInstance(logoWidth, logoHeight, Image.SCALE_SMOOTH);
            planetLogoLabel.setIcon(new ImageIcon(scaledImage));
        }

        if (adm21LogoLabel != null && adm21LogoOriginal != null) {
            Image scaledImage = adm21LogoOriginal.getScaledInstance(logoWidth, logoHeight, Image.SCALE_SMOOTH);
            adm21LogoLabel.setIcon(new ImageIcon(scaledImage));
        }
    }

    /**
     * Cleans up resources to prevent memory leaks.
     * <p>
     * Removes the AWTEventListener to ensure proper garbage collection
     * of this window and its components.
     * </p>
     */
    private void cleanup() {
        if (listener != null) {
            Toolkit.getDefaultToolkit().removeAWTEventListener(listener);
            listener = null;
            logger.debug("AWTEventListener removed");
        }
        if (logDialog != null) {
            logDialog.dispose();
            logDialog = null;
        }
    }

    /**
     * Saves the current window size to preferences.
     */
    private void saveWindowState() {
        PreferencesManager.saveWindowSize(getWidth(), getHeight());
        logger.info("Window state saved: {}x{}", getWidth(), getHeight());
    }

    /**
     * Restores the saved window size from preferences.
     */
    private void restoreWindowState() {
        int width = PreferencesManager.getWindowWidth(DEFAULT_WINDOW_WIDTH);
        int height = PreferencesManager.getWindowHeight(DEFAULT_WINDOW_HEIGHT);
        setSize(width, height);
        logger.info("Window state restored: {}x{}", width, height);
    }

    /**
     * Creates the menu bar with all menus.
     */
    private void createMenuBar() {
        JMenuBar menuBar = new JMenuBar();

        menuBar.add(createFileMenu());
        menuBar.add(createEditMenu());
        menuBar.add(createOptionsMenu());
        menuBar.add(createViewMenu());
        menuBar.add(createHelpMenu());

        setJMenuBar(menuBar);
    }

    /**
     * Creates the File menu.
     */
    private JMenu createFileMenu() {
        JMenu fileMenu = new JMenu(lang.get("menu.file"));

        JMenu exportItem = new JMenu(lang.get("menu.export"));

        JMenuItem quitItem = new JMenuItem(lang.get("menu.quit"));
        quitItem.addActionListener(_ -> {
            saveWindowState();
            System.exit(0);
        });

        JMenuItem csvItem = new JMenuItem("CSV");
        csvItem.addActionListener(_ -> handleExport("CSV"));

        JMenuItem xlsxItem = new JMenuItem("XLSX");
        xlsxItem.addActionListener(_ -> handleExport("XLSX"));

        fileMenu.add(exportItem);
        fileMenu.addSeparator();
        fileMenu.add(quitItem);
        exportItem.add(csvItem);
        exportItem.add(xlsxItem);

        return fileMenu;
    }

    /**
     * Creates the Edit menu.
     */
    private JMenu createEditMenu() {
        JMenu editMenu = new JMenu(lang.get("menu.edit"));

        JMenuItem clearItem = new JMenuItem(lang.get("menu.clear"));
        clearItem.addActionListener(_ -> handleClearResults());

        editMenu.add(clearItem);

        return editMenu;
    }

    /**
     * Creates the Options menu with preferences and language submenu.
     */
    private JMenu createOptionsMenu() {
        JMenu optionsMenu = new JMenu(lang.get("menu.options"));

        JMenuItem preferencesItem = new JMenuItem(lang.get("menu.preferences"));
        preferencesItem.addActionListener(_ -> showPreferences());

        optionsMenu.add(preferencesItem);
        optionsMenu.addSeparator();
        optionsMenu.add(createLanguageMenu());

        return optionsMenu;
    }

    /**
     * Creates the language submenu.
     */
    private JMenu createLanguageMenu() {
        JMenu languageMenu = new JMenu(lang.get("menu.language"));

        JMenuItem englishItem = new JMenuItem("English");
        englishItem.addActionListener(_ -> changeLanguage("en"));

        JMenuItem frenchItem = new JMenuItem("Français");
        frenchItem.addActionListener(_ -> changeLanguage("fr"));

        languageMenu.add(englishItem);
        languageMenu.add(frenchItem);

        return languageMenu;
    }

    /**
     * Creates the View menu.
     */
    private JMenu createViewMenu() {
        JMenu viewMenu = new JMenu(lang.get("menu.view"));

        JCheckBoxMenuItem detailsItem = new JCheckBoxMenuItem(lang.get("menu.show_details"), true);
        viewMenu.add(detailsItem);

        viewMenu.addSeparator();

        JMenuItem logsItem = new JMenuItem(lang.get("menu.logs"));
        logsItem.addActionListener(_ -> showLogsDialog());
        viewMenu.add(logsItem);

        return viewMenu;
    }

    /**
     * Creates the Help menu.
     */
    private JMenu createHelpMenu() {
        JMenu helpMenu = new JMenu(lang.get("menu.help"));

        JMenuItem docItem = new JMenuItem(lang.get("menu.documentation"));
        docItem.addActionListener(_ -> showDocumentation());

        JMenuItem aboutItem = new JMenuItem(lang.get("menu.about"));
        aboutItem.addActionListener(_ -> showAbout());

        helpMenu.add(docItem);
        helpMenu.add(aboutItem);

        return helpMenu;
    }

    /**
     * Creates the toolbar with adapter selection, company logos, and discover button.
     */
    private void createToolbar() {
        JPanel toolbar = new JPanel(new BorderLayout());
        toolbar.setBorder(new EmptyBorder(12, 15, 12, 15));

        toolbar.add(createToolbarLeftPanel(), BorderLayout.WEST);
        toolbar.add(createToolbarCenterPanel(), BorderLayout.CENTER);
        toolbar.add(createToolbarRightPanel(), BorderLayout.EAST);

        add(toolbar, BorderLayout.NORTH);
    }

    /**
     * Creates the left panel of the toolbar (adapter selection).
     */
    private JPanel createToolbarLeftPanel() {
        JPanel leftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));

        JLabel adapterLabel = new JLabel(lang.get("toolbar.network_interface"));
        adapterLabel.setFont(FONT_MODERN);

        adapterComboBox = createAdapterComboBox();

        refreshAdaptersButton = createRefreshButton();

        leftPanel.add(adapterLabel);
        leftPanel.add(adapterComboBox);
        leftPanel.add(refreshAdaptersButton);

        return leftPanel;
    }

    /**
     * Creates the adapter combo box with a custom renderer and responsive sizing.
     * <p>
     * The combo box uses minimum and maximum size constraints to ensure a proper
     * display during window resizing while maintaining usability.
     * </p>
     *
     * @return the configured combo box
     */
    private JComboBox<NetworkInterfaceCustom> createAdapterComboBox() {
        JComboBox<NetworkInterfaceCustom> comboBox = new JComboBox<>();
        comboBox.setFont(FONT_MODERN);
        comboBox.setMinimumSize(new Dimension(COMBO_BOX_MIN_WIDTH, COMBO_BOX_HEIGHT));
        comboBox.setToolTipText(lang.get("toolbar.network_interface"));

        // Set the initial responsive size
        int windowWidth = getWidth() > 0 ? getWidth() : DEFAULT_WINDOW_WIDTH;
        int comboWidth = calculateResponsiveWidth(windowWidth, COMBO_BOX_WIDTH_RATIO, COMBO_BOX_MIN_WIDTH, COMBO_BOX_MAX_WIDTH);
        comboBox.setPreferredSize(new Dimension(comboWidth, COMBO_BOX_HEIGHT));

        comboBox.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                setFont(FONT_MODERN);
                if (value instanceof NetworkInterfaceCustom adapter) {
                    setText(adapter.toDisplayString());
                }
                return this;
            }
        });

        return comboBox;
    }

    /**
     * Creates the refresh adapters button with responsive sizing.
     *
     * @return the configured refresh button
     */
    private JButton createRefreshButton() {
        JButton button = new JButton(lang.get("toolbar.refresh_adapters"));
        button.setFont(FONT_MODERN);
        button.setFocusPainted(false);
        button.setToolTipText(lang.get("toolbar.refresh_adapters_tip"));
        button.addActionListener(_ -> handleRefreshAdapters());

        // Set the initial responsive size
        int windowWidth = getWidth() > 0 ? getWidth() : DEFAULT_WINDOW_WIDTH;
        int buttonWidth = calculateResponsiveWidth(windowWidth, BUTTON_WIDTH_RATIO, BUTTON_MIN_WIDTH, BUTTON_MAX_WIDTH);
        button.setPreferredSize(new Dimension(buttonWidth, BUTTON_HEIGHT));

        return button;
    }

    /**
     * Creates the center panel of the toolbar with company logos.
     * <p>
     * Logos are stored as instance variables to allow dynamic resizing
     * when the window dimensions change.
     * </p>
     */
    private JPanel createToolbarCenterPanel() {
        JPanel centerPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 20, 0));

        // Load and add PLANET logo (stored for responsive resizing)
        planetLogoLabel = createLogoLabel("/images/planet_logo_png.png", "PLANET Logo", true);
        if (planetLogoLabel != null) {
            centerPanel.add(planetLogoLabel);
        }

        // Load and add ADM21 logo (stored for responsive resizing)
        adm21LogoLabel = createLogoLabel("/images/adm21_logo_png.png", "ADM21 Logo", false);
        if (adm21LogoLabel != null) {
            centerPanel.add(adm21LogoLabel);
        }

        return centerPanel;
    }

    /**
     * Creates a JLabel with a logo image loaded from resources.
     * <p>
     * The original image is stored for later rescaling during window resize events.
     * The initial size is calculated based on current window dimensions.
     * </p>
     *
     * @param resourcePath  the path to the image resource (e.g., "/images/logo.png")
     * @param altText       alternative text for the image
     * @param isPlanetLogo  true if this is the PLANET logo (determines storage location)
     * @return JLabel containing the scaled image, or null if loading fails
     */
    private JLabel createLogoLabel(String resourcePath, String altText, boolean isPlanetLogo) {
        try {
            java.net.URL imageUrl = getClass().getResource(resourcePath);
            if (imageUrl == null) {
                logger.warn("Logo image not found: {}", resourcePath);
                return null;
            }

            ImageIcon originalIcon = new ImageIcon(imageUrl);
            Image originalImage = originalIcon.getImage();

            // Store the original image for responsive resizing
            if (isPlanetLogo) {
                planetLogoOriginal = originalImage;
            } else {
                adm21LogoOriginal = originalImage;
            }

            // Calculate the initial size based on the current window width
            int windowWidth = getWidth() > 0 ? getWidth() : DEFAULT_WINDOW_WIDTH;
            int logoWidth = calculateResponsiveWidth(windowWidth, LOGO_WIDTH_RATIO, LOGO_MIN_WIDTH, LOGO_MAX_WIDTH);
            int logoHeight = (int) (logoWidth / LOGO_ASPECT_RATIO);
            logoHeight = Math.clamp(logoHeight, LOGO_MIN_HEIGHT, LOGO_MAX_HEIGHT);
            logoWidth = (int) (logoHeight * LOGO_ASPECT_RATIO);

            Image scaledImage = originalImage.getScaledInstance(logoWidth, logoHeight, Image.SCALE_SMOOTH);

            JLabel label = new JLabel(new ImageIcon(scaledImage));
            label.setToolTipText(altText);

            return label;

        } catch (Exception e) {
            logger.error("Failed to load logo image {}: {}", resourcePath, e.getMessage());
            return null;
        }
    }

    /**
     * Creates the right panel of the toolbar (progress bar and discover button).
     * <p>
     * The discovery button uses responsive sizing that adapts to window width.
     * </p>
     *
     * @return the configured right panel
     */
    private JPanel createToolbarRightPanel() {
        JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));

        progressBar = new JProgressBar();
        progressBar.setIndeterminate(true);
        progressBar.setPreferredSize(new Dimension(25, 25));
        progressBar.setVisible(false);

        discoverButton = new JButton(lang.get("toolbar.discover"));
        discoverButton.setFont(FONT_MODERN);
        discoverButton.setFocusPainted(false);
        discoverButton.addActionListener(_ -> handleDiscoverClicked());

        // Set the initial responsive size
        int windowWidth = getWidth() > 0 ? getWidth() : DEFAULT_WINDOW_WIDTH;
        int buttonWidth = calculateResponsiveWidth(windowWidth, BUTTON_WIDTH_RATIO, BUTTON_MIN_WIDTH, BUTTON_MAX_WIDTH);
        discoverButton.setPreferredSize(new Dimension(buttonWidth, BUTTON_HEIGHT));

        rightPanel.add(progressBar);
        rightPanel.add(discoverButton);

        return rightPanel;
    }

    /**
     * Creates the center panel with a status bar and device table.
     */
    private void createCenterPanel() {
        JPanel centerPanel = new JPanel(new BorderLayout(0, 10));
        centerPanel.setBorder(new EmptyBorder(15, 15, 15, 15));

        centerPanel.add(createStatusBar(), BorderLayout.NORTH);
        centerPanel.add(createTableScrollPane(), BorderLayout.CENTER);

        add(centerPanel, BorderLayout.CENTER);
    }

    /**
     * Creates the status bar at the top of the center panel.
     */
    private JPanel createStatusBar() {
        JPanel statusBar = new JPanel(new BorderLayout(15, 0));
        statusBar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(COLOR_STATUS_BORDER, 1),
                new EmptyBorder(8, 12, 8, 12)
        ));
        statusBar.setBackground(COLOR_STATUS_BACKGROUND);

        if (statusLabel == null) {
            statusLabel = new JLabel(lang.get("status.ready"));
            statusLabel.setFont(FONT_MODERN);
            statusLabel.setForeground(COLOR_STATUS_TEXT);
        }

        deviceCountLabel = new JLabel(lang.get("table.devices_found", 0));
        deviceCountLabel.setFont(FONT_MODERN);
        deviceCountLabel.setForeground(COLOR_STATUS_TEXT);

        statusBar.add(statusLabel, BorderLayout.WEST);
        statusBar.add(deviceCountLabel, BorderLayout.EAST);

        return statusBar;
    }

    /**
     * Creates the scroll pane containing the device table.
     */
    private JScrollPane createTableScrollPane() {
        createDevicesTable();

        JScrollPane scrollPane = new JScrollPane(devicesTable);
        scrollPane.setBorder(BorderFactory.createLineBorder(new Color(200, 200, 200)));

        return scrollPane;
    }

    /**
     * Creates and configures the devices table.
     */
    private void createDevicesTable() {
        String[] columns = getTableColumns();

        tableModel = createTableModel(columns);
        devicesTable = new JTable(tableModel) {
            @Override
            public Component prepareRenderer(TableCellRenderer renderer, int row, int column) {
                Component c = super.prepareRenderer(renderer, row, column);

                if (!c.getBackground().equals(getSelectionBackground())) {
                    c.setBackground(row % 2 == 0 ? COLOR_ALTERNATE_ROW : Color.WHITE);
                }

                if (c instanceof JCheckBox checkbox) {
                    checkbox.setEnabled(false);
                }

                return c;
            }
        };

        configureTable();
        configureTableColumns();
    }

    /**
     * Returns the column names for the table.
     */
    private String[] getTableColumns() {
        return new String[]{
                lang.get("table.security_lock"),
                lang.get("table.device_name"),
                lang.get("table.mac"),
                lang.get("table.firmware"),
                lang.get("table.ipv4"),
                lang.get("table.netmask"),
                lang.get("table.gateway"),
                lang.get("table.description"),
                lang.get("table.location"),
                lang.get("table.uptime"),
                "Telnet",
                "SSH",
                "HTTP",
                "HTTPS",
                lang.get("table.password")
        };
    }

    /**
     * Creates the table model with custom column classes.
     */
    private DefaultTableModel createTableModel(String[] columns) {
        return new DefaultTableModel(columns, 0) {
            @Override
            public Class<?> getColumnClass(int columnIndex) {
                if (columnIndex == COL_LOCK) {
                    return Integer.class;
                }
                if (columnIndex >= COL_TELNET && columnIndex <= COL_HTTPS) {
                    return Boolean.class;
                }
                return String.class;
            }

            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
    }

    /**
     * Configures the table properties.
     */
    private void configureTable() {
        devicesTable.setFont(FONT_TABLE);
        devicesTable.setRowHeight(30);
        devicesTable.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        devicesTable.getTableHeader().setReorderingAllowed(true);
        devicesTable.setShowGrid(true);
        devicesTable.setGridColor(COLOR_TABLE_GRID);
        devicesTable.setIntercellSpacing(new Dimension(1, 1));

        TableRowSorter<DefaultTableModel> tableSorter = new TableRowSorter<>(tableModel);
        devicesTable.setRowSorter(tableSorter);

        devicesTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                updateButtonStates();
            }
        });

        devicesTable.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e ){
                devicesTable.clearSelection();
                devicesTable.getSelectionModel().clearSelection();
            }
        });

        JTableHeader header = devicesTable.getTableHeader();
        header.setFont(FONT_TABLE);
        header.setBackground(COLOR_HEADER_BACKGROUND);
        header.setForeground(COLOR_HEADER_TEXT);

        listener = event -> {
            if (event instanceof MouseEvent && event.getID() == MouseEvent.MOUSE_CLICKED) {
                Component source = (Component) event.getSource();
                if (!SwingUtilities.isDescendingFrom(source, devicesTable)) {
                    devicesTable.clearSelection();
                }
            }
        };

        Toolkit.getDefaultToolkit().addAWTEventListener(listener, AWTEvent.MOUSE_EVENT_MASK);
    }

    /**
     * Configures table column widths and renderers.
     */
    private void configureTableColumns() {
        DefaultTableCellRenderer centerRenderer = new DefaultTableCellRenderer();
        centerRenderer.setHorizontalAlignment(SwingConstants.CENTER);
        centerRenderer.setFont(FONT_TABLE);

        for (int i = 0; i < devicesTable.getColumnCount(); i++) {
            if (i < COL_TELNET) {
                devicesTable.getColumnModel().getColumn(i).setCellRenderer(centerRenderer);
            }
        }

        devicesTable.getColumnModel().getColumn(COL_LOCK).setCellRenderer(new LockIconRenderer());
        devicesTable.getColumnModel().getColumn(COL_PASSWORD).setCellRenderer(new PasswordCellRenderer());

        int[] columnWidths = {100, 160, 140, 120, 120, 120, 120, 200, 150, 180, 70, 70, 70, 70, 150};
        for (int i = 0; i < columnWidths.length; i++) {
            devicesTable.getColumnModel().getColumn(i).setPreferredWidth(columnWidths[i]);
        }
    }

    /**
     * Creates the bottom panel with action buttons.
     */
    private void createBottomPanel() {
        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 12));
        bottomPanel.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(224, 224, 224)));

        downloadSCButton = createActionButton(lang.get("button.get_startup_config"), _ -> handleGetStartupConfig());
        getAllSCButton = createActionButton(lang.get("button.get_all_config"), _ -> handleGetAllConfigs());
        showRSTPTopologyButton = createActionButton("RSTP", _ -> handleGetRSTP());

        bottomPanel.add(downloadSCButton);
        bottomPanel.add(getAllSCButton);
        bottomPanel.add(showRSTPTopologyButton);

        add(bottomPanel, BorderLayout.SOUTH);
    }

    /**
     * Creates a styled action button with the default maximum width.
     * <p>
     * The button is created with the maximum width as the default size.
     * Responsive resizing is handled separately by the ComponentListener.
     * </p>
     *
     * @param text     the button text
     * @param listener the action listener for click events
     * @return the configured button
     */
    public static JButton createActionButton(String text, java.awt.event.ActionListener listener) {
        JButton button = new JButton(text);
        button.setFont(FONT_MODERN);
        button.setPreferredSize(new Dimension(BUTTON_MAX_WIDTH, BUTTON_HEIGHT));
        button.setFocusPainted(false);
        button.setEnabled(false);
        button.addActionListener(listener);
        return button;
    }

    /**
     * Handles the refresh adapters button click.
     */
    private void handleRefreshAdapters() {
        logger.info("Refresh adapters clicked");
        refreshAdaptersButton.setEnabled(false);

        if (eventListener != null) {
            NetworkInterfaceCustom currentSelection = (NetworkInterfaceCustom) adapterComboBox.getSelectedItem();
            eventListener.onRefreshAdaptersClicked();

            if (currentSelection != null) {
                restoreAdapterSelection(currentSelection);
            }
        }

        SwingUtilities.invokeLater(() -> refreshAdaptersButton.setEnabled(true));
    }

    /**
     * Restores the adapter selection after refresh.
     */
    private void restoreAdapterSelection(NetworkInterfaceCustom previousSelection) {
        for (int i = 0; i < adapterComboBox.getItemCount(); i++) {
            if (adapterComboBox.getItemAt(i).equals(previousSelection)) {
                adapterComboBox.setSelectedIndex(i);
                break;
            }
        }
    }

    /**
     * Handles the discovery button click.
     */
    private void handleDiscoverClicked() {
        NetworkInterfaceCustom selectedAdapter = (NetworkInterfaceCustom) adapterComboBox.getSelectedItem();

        if (selectedAdapter == null) {
            showError(lang.get("error.no_adapter_title"), lang.get("error.no_adapter_message"));
            return;
        }

        if (eventListener != null) {
            eventListener.onDiscoverClicked(selectedAdapter);
        }
    }

    /**
     * Handles the get startup-config button.
     */
    private void handleGetStartupConfig() {
        int selectedRow = devicesTable.getSelectedRow();

        if (selectedRow >= 0) {
            PlanetSwitchDevice device = discoveredDevices.get(devicesTable.convertRowIndexToModel(selectedRow));
            if (eventListener != null) {
                eventListener.onGetStartupConfigClicked(device);
            }
        }
    }

    /**
     * Handles the get all startup-config button.
     */
    private void handleGetAllConfigs(){
        if (eventListener != null) {
            eventListener.onGetAllConfigsClicked(discoveredDevices);
        }
    }

    /**
     * Handles the get RSTP button.
     */
    private void handleGetRSTP(){
        new RSTPTopologyDialog(this, discoveredDevices).setVisible(true);
    }

    /**
     * Handles the export menu action.
     */
    private void handleExport(String fileType) {
        if (discoveredDevices.isEmpty()) {
            showInfo(lang.get("info.no_data_title"), lang.get("info.no_data_message"));
            return;
        }

        if (eventListener != null) {
            eventListener.onExportClicked(new ArrayList<>(discoveredDevices), fileType);
        }
    }

    /**
     * Handles the clear results action.
     */
    private void handleClearResults() {
        if (eventListener != null) {
            eventListener.onClearResultsClicked();
        } else {
            clearResults();
        }
    }

    /**
     * Changes the application language and refreshes the UI.
     *
     * @param languageCode the ISO 639-1 language code ("en" or "fr")
     */
    private void changeLanguage(String languageCode) {
        logger.info("Changing language to: {}", languageCode);
        lang.setLanguage(languageCode);
        refreshUI();
        logger.info("Language changed successfully to: {}", languageCode);
    }

    /**
     * Refreshes the entire UI after a language change.
     * <p>
     * Preserves the current state (selected adapter, discovered devices, etc.)
     * and reapplies responsive sizing after recreating UI components.
     * </p>
     */
    private void refreshUI() {
        NetworkInterfaceCustom selectedAdapter = (NetworkInterfaceCustom) adapterComboBox.getSelectedItem();
        List<PlanetSwitchDevice> currentDevices = new ArrayList<>(discoveredDevices);
        int currentDiscoveredCount = lastDiscoveredCount;
        String currentDuplicateMacs = lastDuplicateMacs;

        getContentPane().removeAll();

        createMenuBar();
        createToolbar();
        createCenterPanel();
        createBottomPanel();

        loadNetworkInterfaceCustoms(availableAdapters);
        if (selectedAdapter != null) {
            adapterComboBox.setSelectedItem(selectedAdapter);
        }

        displayDevices(currentDevices);
        restoreProtocolStatus();

        updateStatusAfterRefresh(currentDiscoveredCount, currentDuplicateMacs);

        // Reapply responsive sizing after UI recreation
        updateResponsiveComponents();

        setTitle(lang.get("window.title"));
        revalidate();
        repaint();
    }

    /**
     * Updates the status message after UI refresh.
     */
    private void updateStatusAfterRefresh(int deviceCount, String duplicateMacs) {
        if (appState == APP_STATE.FINAL_STATE) {
            if (deviceCount == 0) {
                statusLabel.setText(lang.get("status.completed_none"));
            } else {
                String statusMsg = lang.get("status.enrichment_complete", deviceCount);
                if (!duplicateMacs.isEmpty()) {
                    statusMsg += " " + lang.get("error.duplicate_message", duplicateMacs);
                }
                statusLabel.setText(statusMsg);
            }
        } else if (appState == APP_STATE.ADAPTERS_FOUND) {
            statusLabel.setText(lang.get("status.ready_adapters", availableAdapters.size()));
        }
    }

    /**
     * Adds a device to the table with checkbox initialization.
     */
    private void addDeviceToTable(PlanetSwitchDevice device) {
        tableModel.addRow(new Object[]{
                LockIconRenderer.LOCK_UNKNOWN,
                device.getDeviceName(),
                device.getMacAddress(),
                device.getVersion(),
                device.getIpAddress(),
                device.getNetMask(),
                device.getGateway(),
                device.getDescription(),
                translateValue(device.getSystemLocation()),
                translateValue(device.getSystemUptime()),
                false, false, false, false,
                device.getPassword()
        });
    }

    /**
     * Adds a new device row to the table.
     */
    private void addDeviceRow(PlanetSwitchDevice device) {
        discoveredDevices.add(device);
        addDeviceToTable(device);
        updateDeviceCount();
        logger.debug("Added device row: {}", device.getDeviceName());
    }

    /**
     * Updates an existing device row in the table.
     */
    private void updateDeviceRow(int row, PlanetSwitchDevice device) {
        discoveredDevices.set(row, device);

        tableModel.setValueAt(device.getDeviceName(), row, COL_DEVICE_NAME);
        tableModel.setValueAt(device.getMacAddress(), row, COL_MAC);
        tableModel.setValueAt(device.getVersion(), row, COL_FIRMWARE);
        tableModel.setValueAt(device.getIpAddress(), row, COL_IPV4);
        tableModel.setValueAt(device.getNetMask(), row, COL_NETMASK);
        tableModel.setValueAt(device.getGateway(), row, COL_GATEWAY);
        tableModel.setValueAt(device.getDescription(), row, COL_DESCRIPTION);
        tableModel.setValueAt(translateValue(device.getSystemLocation()), row, COL_LOCATION);
        tableModel.setValueAt(translateValue(device.getSystemUptime()), row, COL_UPTIME);

        logger.debug("Updated device row: {}", device.getDeviceName());
    }

    /**
     * Finds a device row by MAC address.
     *
     * @param macAddress the MAC address to search for
     * @return the row index, or -1 if not found
     */
    private int findDeviceRowByMac(String macAddress) {
        for (int i = 0; i < discoveredDevices.size(); i++) {
            if (discoveredDevices.get(i).getMacAddress().equals(macAddress)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Returns the column index for a protocol name.
     */
    private int getProtocolColumnIndex(String protocol) {
        return switch (protocol.toLowerCase()) {
            case "telnet" -> COL_TELNET;
            case "ssh" -> COL_SSH;
            case "http" -> COL_HTTP;
            case "https" -> COL_HTTPS;
            default -> -1;
        };
    }

    /**
     * Restores protocol checkbox status from the cache after UI refresh.
     */
    private void restoreProtocolStatus() {
        protocolStatusCache.forEach((macAddress, protocols) ->
                protocols.forEach((protocol, status) -> {
                    int row = findDeviceRowByMac(macAddress);
                    if (row >= 0) {
                        int columnIndex = getProtocolColumnIndex(protocol);
                        if (columnIndex >= 0) {
                            tableModel.setValueAt(status, row, columnIndex);
                        }
                    }
                }));

        securityStatusCache.forEach((macAddress, status) -> {
            int row = findDeviceRowByMac(macAddress);
            if (row >= 0) {
                tableModel.setValueAt(status, row, COL_LOCK);
            }
        });

        logger.debug("Restored protocol status for {} devices", protocolStatusCache.size());
    }

    /**
     * Translates special values to the current language.
     */
    private String translateValue(String value) {
        if (value == null || value.isEmpty()) {
            return lang.get("table.na");
        }

        String normalizedValue = value.trim().toUpperCase();

        return switch (normalizedValue) {
            case "TIMEOUT" -> lang.get("table.timeout");
            case "ERROR" -> lang.get("table.error");
            case "N/A", "N/D" -> lang.get("table.na");
            case "SNMP NOT AVAILABLE" -> lang.get("table.snmp_not_available");
            case "LOADING..." -> lang.get("table.loading");
            case "DUPLICATE", "DUPLICATES", "DOUBLON", "DOUBLONS" -> lang.get("table.duplicate");
            default -> value;
        };
    }

    /**
     * Updates the device count label.
     */
    private void updateDeviceCount() {
        deviceCountLabel.setText(lang.get("table.devices_found", discoveredDevices.size()));
    }

    /**
     * Updates the enabled state of action buttons based on selection.
     */
    public void updateButtonStates() {
        SwingUtilities.invokeLater(() -> {
            downloadSCButton.setEnabled(devicesTable.getSelectedRowCount() == 1); // Enabling if ONE device is selected
            getAllSCButton.setEnabled(!discoveredDevices.isEmpty() && devicesTable.getSelectedRowCount() == 0);
            showRSTPTopologyButton.setEnabled(!discoveredDevices.isEmpty());
        });
    }

    /**
     * Clears the discovery state.
     */
    private void clearDiscoveryState() {
        this.lastDiscoveredCount = 0;
        this.lastDuplicateMacs = "";
        logger.debug("Discovery state cleared");
    }

    /**
     * Shows the preferences dialog.
     */
    private void showPreferences() {
        PreferencesDialog dialog = new PreferencesDialog(this);
        dialog.setVisible(true);
        dialog.toFront();

        if (dialog.wasApplied()) {
            showInfo(lang.get("preferences.applied_title"), lang.get("preferences.applied_message"));
        }
    }

    /**
     * Shows the documentation dialog.
     */
    private void showDocumentation() {
        showInfo(lang.get("info.documentation_title"), lang.get("info.documentation_message"));
    }

    /**
     * Shows the about dialog.
     */
    private void showAbout() {
        JOptionPane.showMessageDialog(
                this,
                lang.get("about.content"),
                lang.get("dialog.about_title"),
                JOptionPane.INFORMATION_MESSAGE
        );
    }

    /**
     * Shows the application logs dialog.
     */
    private void showLogsDialog() {
        if (logDialog == null || !logDialog.isDisplayable()) {
            logDialog = new LogDialog(this);
        }
        logDialog.setVisible(true);
        logDialog.toFront();
    }

    /**
     * Overrides dispose to ensure proper cleanup of resources.
     * <p>
     * This method ensures the AWTEventListener is removed even if
     * dispose() is called directly without triggering windowClosing.
     * </p>
     */
    @Override
    public void dispose() {
        cleanup();
        super.dispose();
    }

    /**
     * Custom cell renderer for the password column.
     * Displays asterisks by default and reveals password on mouse hover.
     */
    private class PasswordCellRenderer extends DefaultTableCellRenderer {
        private int hoveredRow = -1;

        PasswordCellRenderer() {
            setHorizontalAlignment(SwingConstants.CENTER);
            setFont(FONT_TABLE);

            devicesTable.addMouseMotionListener(new MouseMotionAdapter() {
                @Override
                public void mouseMoved(MouseEvent e) {
                    int row = devicesTable.rowAtPoint(e.getPoint());
                    int col = devicesTable.columnAtPoint(e.getPoint());

                    if (col == COL_PASSWORD && row != hoveredRow) {
                        hoveredRow = row;
                        devicesTable.repaint();
                    } else if (col != COL_PASSWORD && hoveredRow != -1) {
                        hoveredRow = -1;
                        devicesTable.repaint();
                    }
                }
            });

            devicesTable.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseExited(MouseEvent e) {
                    if (hoveredRow != -1) {
                        hoveredRow = -1;
                        devicesTable.repaint();
                    }
                }
            });
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                                                       boolean isSelected, boolean hasFocus, int row, int column) {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

            if (!getText().equals(devicesTable.getSelectionBackground().toString())) {
                setBackground(row % 2 == 0 ? COLOR_ALTERNATE_ROW : Color.WHITE);
            }

            if (value == null || value.toString().isEmpty()) {
                setText(lang.get("table.na"));
            } else if (row == hoveredRow) {
                setText(value.toString());
            } else {
                setText("*".repeat(8));
            }

            return this;
        }
    }
}