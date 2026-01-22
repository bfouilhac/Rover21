package com.rover.ui;

import com.rover.model.NetworkInterfaceCustom;
import com.rover.model.PlanetSwitchDevice;
import com.rover.utils.LanguageManager;
import com.rover.utils.PreferencesManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.*;
import java.awt.*;

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

    private static final int BUTTON_WIDTH = 130;
    private static final int BUTTON_HEIGHT = 32;

    private static final Color COLOR_ALTERNATE_ROW = new Color(224, 224, 224);
    private static final Color COLOR_STATUS_BACKGROUND = new Color(227, 242, 253);
    private static final Color COLOR_STATUS_BORDER = new Color(144, 202, 249);
    private static final Color COLOR_STATUS_TEXT = new Color(25, 118, 210);
    private static final Color COLOR_TABLE_GRID = new Color(220, 220, 220);
    private static final Color COLOR_HEADER_BACKGROUND = new Color(245, 245, 245);
    private static final Color COLOR_HEADER_TEXT = new Color(60, 60, 60);

    private static final int DEFAULT_WINDOW_WIDTH = 1200;
    private static final int DEFAULT_WINDOW_HEIGHT = 700;

    private static final int COMBO_BOX_WIDTH = 400;
    private static final int COMBO_BOX_HEIGHT = 30;

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

    private final LanguageManager lang = LanguageManager.getInstance();

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
    private JButton updateDeviceButton;
    private JButton updateMultiButton;
    private JButton updateAllButton;
    private JButton connectButton;
    private JButton discoverButton;
    private JButton refreshAdaptersButton;

    // Data lists
    private List<PlanetSwitchDevice> discoveredDevices;
    private List<NetworkInterfaceCustom> availableAdapters;

    private UIEventListener eventListener; // Listener for UI events (controller).

    private int lastDiscoveredCount = 0;
    private String lastDuplicateMacs = "";

    private APP_STATE appState; // Current application state.

    /**
     * Interface for callbacks to the MainController.
     * <p>
     * This interface defines all user interaction events that the controller
     * needs to handle. The main window calls these methods when the user
     * performs actions like clicking buttons or menu items.
     * </p>
     *
     * @see com.rover.controller.MainController
     */
    public interface UIEventListener {

        /**
         * Called when the discovery button is clicked.
         *
         * @param selectedAdapter the network adapter selected for discovery
         */
        void onDiscoverClicked(NetworkInterfaceCustom selectedAdapter);

        /**
         * Called when the update device button is clicked.
         *
         * @param device the device to update
         */
        void onUpdateDeviceClicked(PlanetSwitchDevice device);

        /**
         * Called when the update multi button is clicked.
         *
         * @param devices the list of selected devices to update
         */
        void onUpdateMultiClicked(List<PlanetSwitchDevice> devices);

        /**
         * Called when the update-all button is clicked.
         *
         * @param device all discovered devices
         */
        void onGetStartupConfigClicked(PlanetSwitchDevice device);

        /**
         * Called when the connection button is clicked.
         *
         * @param device the device to connect to
         */
        void onConnectToDeviceClicked(PlanetSwitchDevice device);

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

            protocolStatusCache.computeIfAbsent(macAddress, k -> new HashMap<>()).put(protocol.toLowerCase(), isAvailable);
        }
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
     */
    private void initializeFrame() {
        setTitle(lang.get("window.title"));
        setSize(PreferencesManager.getWindowWidth(DEFAULT_WINDOW_WIDTH),
                PreferencesManager.getWindowHeight(DEFAULT_WINDOW_HEIGHT));
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());
    }

    /**
     * Sets up the window close listener to save state.
     */
    private void setupWindowListener() {
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent windowEvent) {
                saveWindowState();
            }
        });
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
        quitItem.addActionListener(e -> {
            saveWindowState();
            System.exit(0);
        });

        JMenuItem csvItem = new JMenuItem("CSV");
        csvItem.addActionListener(e -> handleExport("CSV"));

        JMenuItem xlsxItem = new JMenuItem("XLSX");
        xlsxItem.addActionListener(e -> handleExport("XLSX"));

        fileMenu.add(exportItem);
        fileMenu.addSeparator();
        fileMenu.add(quitItem);
        exportItem.add(csvItem);
        exportItem.add(xlsxItem);

        return fileMenu;
    }

    /**
     * Creates the Edit menu with preferences and language submenu.
     */
    private JMenu createEditMenu() {
        JMenu editMenu = new JMenu(lang.get("menu.edit"));

        JMenuItem preferencesItem = new JMenuItem(lang.get("menu.preferences"));
        preferencesItem.addActionListener(e -> showPreferences());

        JMenuItem clearItem = new JMenuItem(lang.get("menu.clear"));
        clearItem.addActionListener(e -> handleClearResults());

        editMenu.add(preferencesItem);
        editMenu.add(clearItem);
        editMenu.addSeparator();
        editMenu.add(createLanguageMenu());

        return editMenu;
    }

    /**
     * Creates the language submenu.
     */
    private JMenu createLanguageMenu() {
        JMenu languageMenu = new JMenu(lang.get("menu.language"));

        JMenuItem englishItem = new JMenuItem("English");
        englishItem.addActionListener(e -> changeLanguage("en"));

        JMenuItem frenchItem = new JMenuItem("Français");
        frenchItem.addActionListener(e -> changeLanguage("fr"));

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

        return viewMenu;
    }

    /**
     * Creates the Help menu.
     */
    private JMenu createHelpMenu() {
        JMenu helpMenu = new JMenu(lang.get("menu.help"));

        JMenuItem docItem = new JMenuItem(lang.get("menu.documentation"));
        docItem.addActionListener(e -> showDocumentation());

        JMenuItem aboutItem = new JMenuItem(lang.get("menu.about"));
        aboutItem.addActionListener(e -> showAbout());

        helpMenu.add(docItem);
        helpMenu.add(aboutItem);

        return helpMenu;
    }

    /**
     * Creates the toolbar with adapter selection and discover button.
     */
    private void createToolbar() {
        JPanel toolbar = new JPanel(new BorderLayout());
        toolbar.setBorder(new EmptyBorder(12, 15, 12, 15));

        toolbar.add(createToolbarLeftPanel(), BorderLayout.WEST);
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
     * Creates the adapter combo box with a custom renderer.
     */
    private JComboBox<NetworkInterfaceCustom> createAdapterComboBox() {
        JComboBox<NetworkInterfaceCustom> comboBox = new JComboBox<>();
        comboBox.setFont(FONT_MODERN);
        comboBox.setPreferredSize(new Dimension(COMBO_BOX_WIDTH, COMBO_BOX_HEIGHT));
        comboBox.setToolTipText(lang.get("toolbar.network_interface"));

        comboBox.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value,
                                                          int index, boolean isSelected, boolean cellHasFocus) {
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
     * Creates the refresh adapters button.
     */
    private JButton createRefreshButton() {
        JButton button = new JButton(lang.get("toolbar.refresh_adapters"));
        button.setPreferredSize(new Dimension(BUTTON_WIDTH, BUTTON_HEIGHT));
        button.setFont(FONT_MODERN);
        button.setFocusPainted(false);
        button.setToolTipText(lang.get("toolbar.refresh_adapters_tip"));
        button.addActionListener(e -> handleRefreshAdapters());
        return button;
    }

    /**
     * Creates the right panel of the toolbar (progress bar and discover button).
     */
    private JPanel createToolbarRightPanel() {
        JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));

        progressBar = new JProgressBar();
        progressBar.setIndeterminate(true);
        progressBar.setPreferredSize(new Dimension(25, 25));
        progressBar.setVisible(false);

        discoverButton = new JButton(lang.get("toolbar.discover"));
        discoverButton.setFont(FONT_MODERN);
        discoverButton.setPreferredSize(new Dimension(BUTTON_WIDTH, BUTTON_HEIGHT));
        discoverButton.setFocusPainted(false);
        discoverButton.addActionListener(e -> handleDiscoverClicked());

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
                "HTTPS"
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

        JTableHeader header = devicesTable.getTableHeader();
        header.setFont(FONT_TABLE);
        header.setBackground(COLOR_HEADER_BACKGROUND);
        header.setForeground(COLOR_HEADER_TEXT);
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

        int[] columnWidths = {100, 160, 140, 120, 120, 120, 120, 200, 150, 180, 70, 70, 70, 70};
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

        updateDeviceButton = createActionButton(lang.get("button.update_device"), e -> handleUpdateDevice());
        updateMultiButton = createActionButton(lang.get("button.update_multi"), e -> handleUpdateMulti());
        updateAllButton = createActionButton(lang.get("button.get_startup_config"), e -> handleGetStartupConfig());
        connectButton = createActionButton(lang.get("button.connect"), e -> handleConnectToDevice());

        bottomPanel.add(updateDeviceButton);
        bottomPanel.add(updateMultiButton);
        bottomPanel.add(updateAllButton);
        bottomPanel.add(connectButton);

        add(bottomPanel, BorderLayout.SOUTH);
    }

    /**
     * Creates a styled action button.
     */
    private JButton createActionButton(String text, java.awt.event.ActionListener listener) {
        JButton button = new JButton(text);
        button.setFont(FONT_MODERN);
        button.setPreferredSize(new Dimension(BUTTON_WIDTH, BUTTON_HEIGHT));
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
     * Handles the update device button click.
     */
    private void handleUpdateDevice() {
        int selectedRow = devicesTable.getSelectedRow();

        if (selectedRow >= 0 && selectedRow < discoveredDevices.size()) {
            PlanetSwitchDevice device = discoveredDevices.get(devicesTable.convertRowIndexToModel(selectedRow));
            if (eventListener != null) {
                eventListener.onUpdateDeviceClicked(device);
            }
        }
    }

    /**
     * Handles the update multi button click.
     */
    private void handleUpdateMulti() {
        List<PlanetSwitchDevice> selectedDevices = getSelectedDevices();

        if (!selectedDevices.isEmpty() && eventListener != null) {
            eventListener.onUpdateMultiClicked(selectedDevices);
        }
    }

    /**
     * Handles the update all button click.
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
     * Handles the connect to device button click.
     */
    private void handleConnectToDevice() {
        int selectedRow = devicesTable.getSelectedRow();

        if (selectedRow >= 0 && selectedRow < discoveredDevices.size()) {
            PlanetSwitchDevice device = discoveredDevices.get(devicesTable.convertRowIndexToModel(selectedRow));
            if (eventListener != null) {
                eventListener.onConnectToDeviceClicked(device);
            }
        }
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
                String statusMsg = lang.get("status.completed_found", deviceCount);
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
                false, false, false, false
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
     * Returns the list of selected devices.
     */
    private List<PlanetSwitchDevice> getSelectedDevices() {
        List<PlanetSwitchDevice> selected = new ArrayList<>();

        for (int row : devicesTable.getSelectedRows()) {
            if (row >= 0 && row < discoveredDevices.size()) {
                selected.add(discoveredDevices.get(devicesTable.convertRowIndexToModel(row)));
            }
        }

        return selected;
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
    private void updateButtonStates() {
        int selectedCount = devicesTable.getSelectedRowCount();
        boolean hasSelection = selectedCount > 0;
        boolean multiSelection = selectedCount > 1;
        boolean hasDevices = !discoveredDevices.isEmpty();

        updateDeviceButton.setEnabled(hasSelection && selectedCount == 1);
        updateMultiButton.setEnabled(multiSelection);
        updateAllButton.setEnabled(hasDevices);
        connectButton.setEnabled(hasSelection && selectedCount == 1);
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
}