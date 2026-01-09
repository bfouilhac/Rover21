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
import java.util.List;

import static com.rover.utils.PreferencesManager.*;

/**
 * The main window of Rover21 using Java Swing and FlatLightLaf.
 * Now supports internationalization (i18n) with English and French languages.
 * @see <a href="https://docs.oracle.com/javase/8/docs/api/javax/swing/package-summary.html"> Java Swing </a>
 * @see <a href="https://www.formdev.com/flatlaf/"> FlatLaf</a>
 * @author Benjamin FOUILHAC, ADM21 in January-February 2026.
 */
public class MainWindow extends JFrame {

    // Modern font for the app using Segoe UI
    private static final Font MODERN_FONT = new Font("Segoe UI", Font.PLAIN, 13);
    private static final Font TABLE_FONT = new Font("Segoe UI", Font.PLAIN, 12);
    private static final int BUTTON_WIDTH = 130;
    private static final int BUTTON_HEIGHT = 32;
    private static final Color ALTERNATE_TABLE_COLOR = new Color(238, 226, 215);

    private static final Logger logger = LoggerFactory.getLogger(MainWindow.class);

    // Language manager for translations
    private final LanguageManager lang = LanguageManager.getInstance();

    // UI elements
    private JComboBox<NetworkInterfaceCustom> adapterComboBox;
    private JProgressBar progressBar;
    private JLabel statusLabel;
    private JLabel deviceCountLabel;
    private JTable devicesTable;
    private DefaultTableModel tableModel;

    // Actions buttons
    private JButton updateDeviceBtn;
    private JButton updateMultiBtn;
    private JButton updateAllBtn;
    private JButton connectBtn;
    private JButton discoverButton;
    private JButton refreshNetIntsButton;

    // List of network interfaces
    private List<PlanetSwitchDevice> discoveredDevices;
    private List<NetworkInterfaceCustom> availableAdapters;

    // Listener for UI events
    private UIEventListener eventListener;

    /**
     * Interface for callbacks to the MainController.
     * @see com.rover.controller.MainController
     */
    public interface UIEventListener {
        void onDiscoverClicked(NetworkInterfaceCustom selectedAdapter);
        void onUpdateDeviceClicked(PlanetSwitchDevice device);
        void onUpdateMultiClicked(List<PlanetSwitchDevice> devices);
        void onUpdateAllClicked(List<PlanetSwitchDevice> devices);
        void onConnectToDeviceClicked(PlanetSwitchDevice device);
        void onExportClicked(List<PlanetSwitchDevice> devices);
        void onClearResultsClicked();
        void onRefreshAdaptersClicked();
    }

    /**
     * Constructor initiates all the graphics elements and sets the font.
     */
    public MainWindow() {
        this.discoveredDevices = new ArrayList<>();
        this.availableAdapters = new ArrayList<>();

        setUIFont();
        initializeFrame();
        createMenuBar();
        createToolbar();
        createCenterPanel();
        createBottomPanel();

        // Center the window
        setLocationRelativeTo(null);
        toFront(); // Brings the window to the front so it can be visible easily
        setupWindowListener(); // Setting up the listener so we can know when the window is closed

        restoreWindowState();

        logger.info("MainWindow initialized with language: {}", lang.getCurrentLanguageCode());
    }

    /**
     * Sets up the window listener to save state on close. (mostly to save the dimensions)
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
     * Saves the current window size in the preferences.
     * @see PreferencesManager
     */
    private void saveWindowState() {
        PreferencesManager.saveWindowSize(getWidth(), getHeight());
        logger.info("Window state saved: {}x{} at ({}, {})",
                getWidth(), getHeight(), getX(), getY());
    }

    /**
     * Restores the saved window size.
     * @see PreferencesManager
     */
    private void restoreWindowState() {
        int width = PreferencesManager.getWindowWidth(1200);
        int height = PreferencesManager.getWindowHeight(700);
        setSize(width, height);
        logger.info("Window state restored : {}x{} at", width, height);
    }

    /**
     * Sets the font for all the components.
     * @see UIManager
     */
    private void setUIFont() {
        UIManager.put("Button.font", MODERN_FONT);
        UIManager.put("Label.font", MODERN_FONT);
        UIManager.put("ComboBox.font", MODERN_FONT);
        UIManager.put("Table.font", TABLE_FONT);
        UIManager.put("TableHeader.font", TABLE_FONT);
        UIManager.put("Menu.font", MODERN_FONT);
        UIManager.put("MenuItem.font", MODERN_FONT);
        UIManager.put("CheckBoxMenuItem.font", MODERN_FONT);
    }

    /**
     * Defines the listener used for all the user interactions.
     * @param listener the UIEventListener you want to set up.
     * @see UIEventListener
     */
    public void setEventListener(UIEventListener listener) {
        this.eventListener = listener;
    }

    /**
     * Initializes the main window frame with a translated title.
     */
    private void initializeFrame() {
        setTitle(lang.get("window.title"));
        setSize(getWindowWidth(1200), getWindowHeight(700));
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());
    }

    /**
     * Creates the menu bar with translated menu items.
     */
    private void createMenuBar() {
        JMenuBar menuBar = new JMenuBar();

        // Menu File
        JMenu fileMenu = new JMenu(lang.get("menu.file"));
        JMenuItem exportItem = new JMenuItem(lang.get("menu.export"));
        exportItem.addActionListener(e -> handleExport());
        JMenuItem quitItem = new JMenuItem(lang.get("menu.quit"));
        quitItem.addActionListener(e -> {
            saveWindowSize(this.getWidth(),  this.getHeight());
            System.exit(0);
        });

        fileMenu.add(exportItem);
        fileMenu.addSeparator();
        fileMenu.add(quitItem);

        // Menu Edit
        JMenu editMenu = new JMenu(lang.get("menu.edit"));
        JMenuItem preferencesItem = new JMenuItem(lang.get("menu.preferences"));
        preferencesItem.addActionListener(e -> showPreferences());
        JMenuItem clearItem = new JMenuItem(lang.get("menu.clear"));
        clearItem.addActionListener(e -> handleClearResults());

        editMenu.add(preferencesItem);
        editMenu.add(clearItem);

        // Add Language submenu
        JMenu languageMenu = new JMenu(lang.get("menu.language"));

        JMenuItem englishItem = new JMenuItem("English");
        englishItem.addActionListener(e -> changeLanguage("en"));

        JMenuItem frenchItem = new JMenuItem("Français");
        frenchItem.addActionListener(e -> changeLanguage("fr"));

        languageMenu.add(englishItem);
        languageMenu.add(frenchItem);

        editMenu.addSeparator();
        editMenu.add(languageMenu);

        // Menu View
        JMenu viewMenu = new JMenu(lang.get("menu.view"));
        JCheckBoxMenuItem detailsItem = new JCheckBoxMenuItem(lang.get("menu.show_details"), true);
        viewMenu.add(detailsItem);

        // Menu Help
        JMenu helpMenu = new JMenu(lang.get("menu.help"));
        JMenuItem docItem = new JMenuItem(lang.get("menu.documentation"));
        docItem.addActionListener(e -> showDocumentation());
        JMenuItem aboutItem = new JMenuItem(lang.get("menu.about"));
        aboutItem.addActionListener(e -> showAbout());

        helpMenu.add(docItem);
        helpMenu.add(aboutItem);

        // Add all menus
        menuBar.add(fileMenu);
        menuBar.add(editMenu);
        menuBar.add(viewMenu);
        menuBar.add(helpMenu);

        setJMenuBar(menuBar);
    }

    /**
     * Creates the toolbar with the main controls.
     */
    private void createToolbar() {
        JPanel toolbar = new JPanel();
        toolbar.setLayout(new BorderLayout());
        toolbar.setBorder(new EmptyBorder(12, 15, 12, 15));

        // Left panel with the label, combobox, and refresh button
        JPanel leftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));

        JLabel adapterLabel = new JLabel(lang.get("toolbar.network_interface"));
        adapterLabel.setFont(MODERN_FONT);

        adapterComboBox = new JComboBox<>();
        adapterComboBox.setFont(MODERN_FONT);
        adapterComboBox.setPreferredSize(new Dimension(400, 30));
        adapterComboBox.setToolTipText(lang.get("toolbar.network_interface"));
        adapterComboBox.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value,
                                                          int index, boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                setFont(MODERN_FONT);
                if (value instanceof NetworkInterfaceCustom adapter) {
                    setText(adapter.toDisplayString());
                }
                return this;
            }
        });

        // Refresh button
        refreshNetIntsButton = new JButton(lang.get("toolbar.refresh_adapters"));
        refreshNetIntsButton.setPreferredSize(new Dimension(BUTTON_WIDTH, BUTTON_HEIGHT));
        refreshNetIntsButton.setFont(MODERN_FONT);
        refreshNetIntsButton.setFocusPainted(false);
        refreshNetIntsButton.setToolTipText(lang.get("toolbar.refresh_adapters_tip"));
        refreshNetIntsButton.addActionListener(e -> handleRefreshAdapters());

        leftPanel.add(adapterLabel);
        leftPanel.add(adapterComboBox);
        leftPanel.add(refreshNetIntsButton);

        // Right panel with progress bar and discover button
        JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));

        progressBar = new JProgressBar();
        progressBar.setIndeterminate(true);
        progressBar.setPreferredSize(new Dimension(25, 25));
        progressBar.setVisible(false);

        discoverButton = new JButton(lang.get("toolbar.discover"));
        discoverButton.setFont(MODERN_FONT);
        discoverButton.setPreferredSize(new Dimension(BUTTON_WIDTH, BUTTON_HEIGHT));
        discoverButton.setFocusPainted(false);
        discoverButton.addActionListener(e -> handleDiscoverClicked());

        rightPanel.add(progressBar);
        rightPanel.add(discoverButton);

        toolbar.add(leftPanel, BorderLayout.WEST);
        toolbar.add(rightPanel, BorderLayout.EAST);

        add(toolbar, BorderLayout.NORTH);
    }

    /**
     * Creates the center panel with the table.
     */
    private void createCenterPanel() {
        JPanel centerPanel = new JPanel(new BorderLayout(0, 10));
        centerPanel.setBorder(new EmptyBorder(15, 15, 15, 15));

        // Status bar at the top
        JPanel statusBar = new JPanel(new BorderLayout(15, 0));
        statusBar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(144, 202, 249), 1),
                new EmptyBorder(8, 12, 8, 12)
        ));
        statusBar.setBackground(new Color(227, 242, 253));

        statusLabel = new JLabel(lang.get("status.ready"));
        statusLabel.setFont(MODERN_FONT);
        statusLabel.setForeground(new Color(25, 118, 210));

        deviceCountLabel = new JLabel(lang.get("table.devices_found", 0));
        deviceCountLabel.setFont(MODERN_FONT);
        deviceCountLabel.setForeground(new Color(25, 118, 210));

        statusBar.add(statusLabel, BorderLayout.WEST);
        statusBar.add(deviceCountLabel, BorderLayout.EAST);

        // Devices table
        String[] columns = {
                lang.get("table.device_name"),
                lang.get("table.mac"),
                lang.get("table.firmware"),
                lang.get("table.ipv4"),
                lang.get("table.netmask"),
                lang.get("table.gateway"),
                lang.get("table.description"),
                lang.get("table.location"),
                lang.get("table.uptime")
        };

        tableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        devicesTable = new JTable(tableModel) {
            public Component prepareRenderer(TableCellRenderer renderer, int row, int column) {
                Component c = super.prepareRenderer(renderer, row, column);
                if (!c.getBackground().equals(getSelectionBackground())){
                    c.setBackground((row % 2 == 0 ? ALTERNATE_TABLE_COLOR : Color.WHITE));
                }
                return c;
            }
        };
        devicesTable.setFont(TABLE_FONT);
        devicesTable.setRowHeight(30);
        devicesTable.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        devicesTable.getTableHeader().setReorderingAllowed(true);

        // Show grid borders
        devicesTable.setShowGrid(true);
        devicesTable.setGridColor(new Color(220, 220, 220));
        devicesTable.setIntercellSpacing(new Dimension(1, 1));

        // Enable column sorting
        TableRowSorter<DefaultTableModel> tableSorter = new TableRowSorter<>(tableModel);
        devicesTable.setRowSorter(tableSorter);

        // Center text in cells
        DefaultTableCellRenderer centerRenderer = new DefaultTableCellRenderer();
        centerRenderer.setHorizontalAlignment(SwingConstants.CENTER);
        centerRenderer.setFont(TABLE_FONT);

        // Apply centered renderer to all columns
        for (int i = 0; i < devicesTable.getColumnCount(); i++) {
            devicesTable.getColumnModel().getColumn(i).setCellRenderer(centerRenderer);
        }

        // Header style
        JTableHeader header = devicesTable.getTableHeader();
        header.setFont(TABLE_FONT);
        header.setBackground(new Color(245, 245, 245));
        header.setForeground(new Color(60, 60, 60));

        // Preferred column widths
        devicesTable.getColumnModel().getColumn(0).setPreferredWidth(140);
        devicesTable.getColumnModel().getColumn(1).setPreferredWidth(180);
        devicesTable.getColumnModel().getColumn(2).setPreferredWidth(120);
        devicesTable.getColumnModel().getColumn(3).setPreferredWidth(120);
        devicesTable.getColumnModel().getColumn(4).setPreferredWidth(120);
        devicesTable.getColumnModel().getColumn(5).setPreferredWidth(120);
        devicesTable.getColumnModel().getColumn(6).setPreferredWidth(200);
        devicesTable.getColumnModel().getColumn(7).setPreferredWidth(200);
        devicesTable.getColumnModel().getColumn(8).setPreferredWidth(200);


        // Enable/disable buttons based on selection
        devicesTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                updateButtonStates();
            }
        });

        JScrollPane scrollPane = new JScrollPane(devicesTable);
        scrollPane.setBorder(BorderFactory.createLineBorder(new Color(200, 200, 200)));

        centerPanel.add(statusBar, BorderLayout.NORTH);
        centerPanel.add(scrollPane, BorderLayout.CENTER);

        add(centerPanel, BorderLayout.CENTER);
    }

    /**
     * Creates the bottom panel with action buttons.
     */
    private void createBottomPanel() {
        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 12));
        bottomPanel.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(224, 224, 224)));

        updateDeviceBtn = new JButton(lang.get("button.update_device"));
        updateMultiBtn = new JButton(lang.get("button.update_multi"));
        updateAllBtn = new JButton(lang.get("button.update_all"));
        connectBtn = new JButton(lang.get("button.connect"));

        // Style and configuration of buttons
        JButton[] buttons = {updateDeviceBtn, updateMultiBtn, updateAllBtn, connectBtn};
        for (JButton btn : buttons) {
            btn.setFont(MODERN_FONT);
            btn.setPreferredSize(new Dimension(BUTTON_WIDTH, BUTTON_HEIGHT));
            btn.setFocusPainted(false);
            btn.setEnabled(false);
        }

        // Button actions
        updateDeviceBtn.addActionListener(e -> handleUpdateDevice());
        updateMultiBtn.addActionListener(e -> handleUpdateMulti());
        updateAllBtn.addActionListener(e -> handleUpdateAll());
        connectBtn.addActionListener(e -> handleConnectToDevice());

        bottomPanel.add(updateDeviceBtn);
        bottomPanel.add(updateMultiBtn);
        bottomPanel.add(updateAllBtn);
        bottomPanel.add(connectBtn);

        add(bottomPanel, BorderLayout.SOUTH);
    }

    /**
     * Loads the adapters into the ComboBox.
     * @param adapters all the network interfaces found that will be displayed.
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
     * Shows all the PLANET devices found after a discovery.
     * @see com.rover.services.PlanetDiscoveryService#discoverDevices(String)
     */
    public void displayDevices(List<PlanetSwitchDevice> devices) {
        this.discoveredDevices = new ArrayList<>(devices);
        tableModel.setRowCount(0);
        for (PlanetSwitchDevice device : devices) {
            tableModel.addRow(new Object[]{
                    device.getMacAddress(),
                    device.getDeviceName(),
                    device.getVersion(),
                    device.getIpAddress(),
                    device.getNetMask(),
                    device.getGateway(),
                    device.getDescription(),
                    device.getSystemLocation() != null ? device.getSystemLocation() : "N/A",
                    device.getSystemUptime() != null ? device.getSystemUptime() : "N/A"
            });
        }
        updateDeviceCount();
        updateButtonStates();
    }

    /**
     * Update the status message in the header.
     * @param message the message you want to print has to be a String.
     */
    public void setStatusMessage(String message) {
        statusLabel.setText(message);
    }

    /**
     * Enable/Disable scanning mode : disabling buttons etc ...
     */
    public void setScanningMode(boolean scanning) {
        discoverButton.setEnabled(!scanning);
        progressBar.setVisible(scanning);

        if (scanning) {
            statusLabel.setText(lang.get("status.scanning", ""));
        }
    }

    /**
     * Prints an error message.
     * @param title the error in a simplified way.
     * @param message contains the error message. must be clear and understandable = must allow the problem/source to be identified quickly.
     */
    public void showError(String title, String message) {
        JOptionPane.showMessageDialog(
                this,
                message,
                title,
                JOptionPane.ERROR_MESSAGE
        );
    }

    /**
     * Displays any informational message.
     * @param title The title of the message, in plain text.
     * @param message The body of the informational message, which must be clear and understandable (no error logs, etc.).
     */
    public void showInfo(String title, String message) {
        JOptionPane.showMessageDialog(
                this,
                message,
                title,
                JOptionPane.INFORMATION_MESSAGE
        );
    }

    /**
     * Clears the results from the table.
     */
    public void clearResults() {
        discoveredDevices.clear();
        tableModel.setRowCount(0);
        updateDeviceCount();
        updateButtonStates();
        statusLabel.setText(lang.get("status.ready"));
    }

    /**
     * Clears the combo box of adapters
     */
    public void clearAdapters() {
        availableAdapters.clear();
        adapterComboBox.removeAllItems();
    }

    /**
     * Handles the refresh adapters button click.
     * Requests the controller to reload network adapters.
     */
    private void handleRefreshAdapters() {
        logger.info("Refresh adapters clicked");
        refreshNetIntsButton.setEnabled(false); // Disable the button temporarily
        if (eventListener != null) {
            NetworkInterfaceCustom currentSelection = (NetworkInterfaceCustom) adapterComboBox.getSelectedItem();
            eventListener.onRefreshAdaptersClicked();
            if (currentSelection != null) { // Restore selection
                for (int i = 0; i < adapterComboBox.getItemCount(); i++) {
                    if (adapterComboBox.getItemAt(i).equals(currentSelection)) {
                        adapterComboBox.setSelectedIndex(i);
                        break;
                    }
                }
            }
        }
        SwingUtilities.invokeLater(() -> refreshNetIntsButton.setEnabled(true)); // Re-enable button after refresh
    }

    private void handleDiscoverClicked() {
        NetworkInterfaceCustom selectedAdapter = (NetworkInterfaceCustom) adapterComboBox.getSelectedItem();
        if (selectedAdapter == null) {
            showError(
                    lang.get("error.no_adapter_title"),
                    lang.get("error.no_adapter_message")
            );
            return;
        }
        if (eventListener != null) {
            eventListener.onDiscoverClicked(selectedAdapter);
        }
    }

    /**
     * Manages interaction with the “Update Device” button.
     */
    private void handleUpdateDevice() {
        int selectedRow = devicesTable.getSelectedRow();
        if (selectedRow >= 0 && selectedRow < discoveredDevices.size()) {
            PlanetSwitchDevice device = discoveredDevices.get(selectedRow);
            if (eventListener != null) {
                eventListener.onUpdateDeviceClicked(device);
            }
        }
    }

    /**
     * Manages interaction with the “Update Multi” button.
     */
    private void handleUpdateMulti() {
        List<PlanetSwitchDevice> selectedDevices = getSelectedDevices();
        if (!selectedDevices.isEmpty() && eventListener != null) {
            eventListener.onUpdateMultiClicked(selectedDevices);
        }
    }

    /**
     * Manages interaction with the “Update All” button.
     */
    private void handleUpdateAll() {
        if (!discoveredDevices.isEmpty() && eventListener != null) {
            eventListener.onUpdateAllClicked(new ArrayList<>(discoveredDevices));
        }
    }

    /**
     * Manages interaction with the “Connect to Device” button.
     */
    private void handleConnectToDevice() {
        int selectedRow = devicesTable.getSelectedRow();
        if (selectedRow >= 0 && selectedRow < discoveredDevices.size()) {
            PlanetSwitchDevice device = discoveredDevices.get(selectedRow);
            if (eventListener != null) {
                eventListener.onConnectToDeviceClicked(device);
            }
        }
    }

    /**
     * Detects and redirects the export of found data to a CSV file.
     */
    private void handleExport() {
        if (discoveredDevices.isEmpty()) {
            showInfo(
                    lang.get("info.no_data_title"),
                    lang.get("info.no_data_message")
            );
            return;
        }

        if (eventListener != null) {
            eventListener.onExportClicked(new ArrayList<>(discoveredDevices));
        }
    }

    /**
     * Trigger method that cleans up the array containing information about the switches found.
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
     * @param languageCode the ISO 639-1 language code ("en" or "fr")
     */
    private void changeLanguage(String languageCode) {
        logger.info("Changing language to : {}", languageCode);
        lang.setLanguage(languageCode); // Change language
        refreshUI(); // Refresh the UI
        logger.info("Language changed successfully to : {}", languageCode);
    }

    private void refreshUI() {
        NetworkInterfaceCustom selectedAdapter = (NetworkInterfaceCustom) adapterComboBox.getSelectedItem();
        List<PlanetSwitchDevice> currentDevices = new ArrayList<>(discoveredDevices);


        getContentPane().removeAll(); // Clear and rebuild

        // Recreate all components
        createMenuBar();
        createToolbar();
        createCenterPanel();
        createBottomPanel();

        // Restore state
        loadNetworkInterfaceCustoms(availableAdapters);
        if (selectedAdapter != null) {
            adapterComboBox.setSelectedItem(selectedAdapter);
        }
        displayDevices(currentDevices);

        // Update title
        setTitle(lang.get("window.title"));

        // Refresh display
        revalidate();
        repaint();
    }

    /**
     * Retrieves data from the selected columns.
     * @return List of objects from the selected rows.
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

    private void updateDeviceCount() {
        deviceCountLabel.setText(lang.get("table.devices_found", discoveredDevices.size()));
    }

    private void updateButtonStates() {
        int selectedCount = devicesTable.getSelectedRowCount();
        boolean hasSelection = selectedCount > 0;
        boolean multiSelection = selectedCount > 1;
        boolean hasDevices = !discoveredDevices.isEmpty();

        updateDeviceBtn.setEnabled(hasSelection && selectedCount == 1);
        updateMultiBtn.setEnabled(multiSelection);
        updateAllBtn.setEnabled(hasDevices);
        connectBtn.setEnabled(hasSelection && selectedCount == 1);
    }

    private void showPreferences() {
        PreferencesDialog dialog = new PreferencesDialog(this);
        dialog.setVisible(true);
        dialog.toFront();
        if (dialog.wasApplied()) { // Si l'utilisateur a appliqué des changements
            showInfo(
                    lang.get("preferences.applied_title"),
                    lang.get("preferences.applied_message")
            );
        }
    }

    private void showDocumentation() {
        showInfo(
                lang.get("info.documentation_title"),
                lang.get("info.documentation_message")
        );
    }

    private void showAbout() {
        JOptionPane.showMessageDialog(
                this,
                lang.get("about.content"),
                lang.get("dialog.about_title"),
                JOptionPane.INFORMATION_MESSAGE
        );
    }
}