package com.rover.ui;

import com.rover.model.NetworkInterfaceCustom;
import com.rover.model.PlanetSwitchDevice;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Main window of Rover21 using Java Swing and FlatLightLaf.
 * @see <a href="https://docs.oracle.com/javase/8/docs/api/javax/swing/package-summary.html"> Java Swing </a>
 * @see <a href="https://www.formdev.com/flatlaf/"> FlatLaf</a>
 * @author Benjamin FOUILHAC, ADM21 in January-February 2026.
 */
public class MainWindow extends JFrame {

    // Modern font for the app using the Segoe UI
    private static final Font MODERN_FONT = new Font("Segoe UI", Font.PLAIN, 13);
    private static final Font TABLE_FONT = new Font("Segoe UI", Font.PLAIN, 12);
    private static final Logger logger = LoggerFactory.getLogger(MainWindow.class);

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

    // List of network interfaces
    private List<PlanetSwitchDevice> discoveredDevices;

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
    }

    /**
     * Constructor, initiates all the graphics elements and sets the font.
     */
    public MainWindow() {
        this.discoveredDevices = new ArrayList<>();

        setUIFont();

        initializeFrame();
        createMenuBar();
        createToolbar();
        createCenterPanel();
        createBottomPanel();

        // Centrer la fenêtre
        setLocationRelativeTo(null);
    }

    /**
     * Sets the font for all the components.
     */
    private void setUIFont() {
        UIManager.put("Button.font", MainWindow.MODERN_FONT);
        UIManager.put("Label.font", MainWindow.MODERN_FONT);
        UIManager.put("ComboBox.font", MainWindow.MODERN_FONT);
        UIManager.put("Table.font", TABLE_FONT);
        UIManager.put("TableHeader.font", TABLE_FONT);
        UIManager.put("Menu.font", MainWindow.MODERN_FONT);
        UIManager.put("MenuItem.font", MainWindow.MODERN_FONT);
        UIManager.put("CheckBoxMenuItem.font", MainWindow.MODERN_FONT);
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
     * Create the main frame, sets up the size and the behavior on close.
     */
    private void initializeFrame() {
        setTitle("Rover21 - PLANET Switch Discovery");
        setSize(1200, 700);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());
    }

    /**
     * Creates the menu bar and all the graphic elements that comprise it: “File,” “Edit,” “View,” “Help”.
     */
    private void createMenuBar() {
        JMenuBar menuBar = new JMenuBar();

        // Menu File
        JMenu fileMenu = new JMenu("File");
        JMenuItem exportItem = new JMenuItem("Export to CSV...");
        exportItem.addActionListener(e -> handleExport());
        JMenuItem quitItem = new JMenuItem("Quit");
        quitItem.addActionListener(e -> System.exit(0));

        fileMenu.add(exportItem);
        fileMenu.addSeparator();
        fileMenu.add(quitItem);

        // Menu Edit
        JMenu editMenu = new JMenu("Edit");
        JMenuItem preferencesItem = new JMenuItem("Preferences...");
        preferencesItem.addActionListener(e -> showPreferences());
        JMenuItem clearItem = new JMenuItem("Clear Results");
        clearItem.addActionListener(e -> handleClearResults());

        editMenu.add(preferencesItem);
        editMenu.add(clearItem);

        // Menu View
        JMenu viewMenu = new JMenu("View");
        JCheckBoxMenuItem detailsItem = new JCheckBoxMenuItem("Show Details Panel", true);
        viewMenu.add(detailsItem);

        // Menu Help
        JMenu helpMenu = new JMenu("Help");
        JMenuItem docItem = new JMenuItem("Documentation");
        docItem.addActionListener(e -> showDocumentation());
        JMenuItem aboutItem = new JMenuItem("About Rover21");
        aboutItem.addActionListener(e -> showAbout());

        helpMenu.add(docItem);
        helpMenu.add(aboutItem);

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

        JPanel leftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 0));

        JLabel adapterLabel = new JLabel("Network Interface :");
        adapterLabel.setFont(MODERN_FONT);

        adapterComboBox = new JComboBox<>();
        adapterComboBox.setFont(MODERN_FONT);
        adapterComboBox.setPreferredSize(new Dimension(400, 30));
        adapterComboBox.setToolTipText("Select the network adapter to scan");
        adapterComboBox.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                setFont(MODERN_FONT);
                if (value instanceof NetworkInterfaceCustom adapter) {
                    setText(adapter.toDisplayString());
                }
                return this;
            }
        });

        leftPanel.add(adapterLabel);
        leftPanel.add(adapterComboBox);

        JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));

        progressBar = new JProgressBar();
        progressBar.setIndeterminate(true);
        progressBar.setPreferredSize(new Dimension(25, 25));
        progressBar.setVisible(false);

        discoverButton = new JButton("Discover");
        discoverButton.setFont(MODERN_FONT);
        discoverButton.setPreferredSize(new Dimension(130, 32));
        discoverButton.setFocusPainted(false);
        discoverButton.addActionListener(e -> handleDiscoverClicked());

        rightPanel.add(progressBar);
        rightPanel.add(discoverButton);

        toolbar.add(leftPanel, BorderLayout.WEST);
        toolbar.add(rightPanel, BorderLayout.EAST);

        add(toolbar, BorderLayout.NORTH);
    }

    /**
     * Create the central panel with the table.
     * Sets all the properties of the JTable.
     * @see JTable
     */
    private void createCenterPanel() {
        JPanel centerPanel = new JPanel(new BorderLayout(0, 10));
        centerPanel.setBorder(new EmptyBorder(15, 15, 15, 15));

        JPanel statusBar = new JPanel(new BorderLayout(15, 0));
        statusBar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(144, 202, 249), 1),
                new EmptyBorder(8, 12, 8, 12)
        ));
        statusBar.setBackground(new Color(227, 242, 253));

        statusLabel = new JLabel("Ready");
        statusLabel.setFont(MODERN_FONT);
        statusLabel.setForeground(new Color(25, 118, 210));

        deviceCountLabel = new JLabel("Devices found: 0");
        deviceCountLabel.setFont(MODERN_FONT);
        deviceCountLabel.setForeground(new Color(25, 118, 210));

        statusBar.add(statusLabel, BorderLayout.WEST);
        statusBar.add(deviceCountLabel, BorderLayout.EAST);

        // Devices table
        String[] columns = {
                "MAC Address", "Device Name", "Firmware version", "Device IP",
                "IPv4 Address", "NetMask", "Gateway", "Description"
        };

        tableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        devicesTable = new JTable(tableModel);
        devicesTable.setFont(TABLE_FONT);
        devicesTable.setRowHeight(30);
        devicesTable.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        devicesTable.getTableHeader().setReorderingAllowed(true);

        devicesTable.setShowGrid(true); // Shows the lines between the cells = better visibility
        devicesTable.setAutoCreateRowSorter(true); // Allows us to sort the table by columns, asc & desc
        devicesTable.setGridColor(new Color(220, 220, 220));
        devicesTable.setIntercellSpacing(new Dimension(1, 1));

        DefaultTableCellRenderer centerRenderer = new DefaultTableCellRenderer();
        centerRenderer.setHorizontalAlignment(SwingConstants.CENTER);
        centerRenderer.setFont(TABLE_FONT);

        for (int i = 0; i < devicesTable.getColumnCount(); i++) {
            devicesTable.getColumnModel().getColumn(i).setCellRenderer(centerRenderer); // Appliquer le renderer centré à toutes les colonnes
        }

        // Header style
        JTableHeader header = devicesTable.getTableHeader();
        header.setFont(TABLE_FONT);
        header.setBackground(new Color(245, 245, 245));
        header.setForeground(new Color(60, 60, 60));

        // Columns width, larger for device description but thicker for IPv4 address...
        devicesTable.getColumnModel().getColumn(0).setPreferredWidth(140);
        devicesTable.getColumnModel().getColumn(1).setPreferredWidth(180);
        devicesTable.getColumnModel().getColumn(2).setPreferredWidth(120);
        devicesTable.getColumnModel().getColumn(3).setPreferredWidth(120);
        devicesTable.getColumnModel().getColumn(4).setPreferredWidth(120);
        devicesTable.getColumnModel().getColumn(5).setPreferredWidth(120);
        devicesTable.getColumnModel().getColumn(6).setPreferredWidth(120);
        devicesTable.getColumnModel().getColumn(7).setPreferredWidth(200);

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
     * Create bottom panel with actions buttons.
     */
    private void createBottomPanel() {
        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 12));
        bottomPanel.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(224, 224, 224)));

        updateDeviceBtn = new JButton("Update Device");
        updateMultiBtn = new JButton("Update Multi");
        updateAllBtn = new JButton("Update All");
        connectBtn = new JButton("Connect to Device");

        // Style et configuration des boutons
        JButton[] buttons = {updateDeviceBtn, updateMultiBtn, updateAllBtn, connectBtn};
        for (JButton btn : buttons) {
            btn.setFont(MODERN_FONT);
            btn.setPreferredSize(new Dimension(140, 32));
            btn.setFocusPainted(false);
            btn.setEnabled(false);
        }

        // Actions des boutons
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
                    device.getDeviceIp(),
                    device.getIpAddress(),
                    device.getNetMask(),
                    device.getGateway(),
                    device.getDescription()
            });
        }
        updateDeviceCount();
        updateButtonStates();
    }

    /**
     * Update the status message in the header.
     * @param message the message you want to print, has to be a String.
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
            statusLabel.setText("Scanning network...");
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
        logger.error(message);
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
     * Clears the table results, returns the utility to “Ready” mode.
     */
    public void clearResults() {
        discoveredDevices.clear();
        tableModel.setRowCount(0);
        updateDeviceCount();
        updateButtonStates();
        statusLabel.setText("Ready.");
    }

    // Contains various handlers to trigger and effectively manage interactions with buttons and other elements.
    private void handleDiscoverClicked() {
        NetworkInterfaceCustom selectedAdapter = (NetworkInterfaceCustom) adapterComboBox.getSelectedItem();
        if (selectedAdapter == null) {
            showError("No Adapter Selected", "Please select a network adapter before discovering.");
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
            showInfo("No Data", "No devices to export. Please run a discovery first.");
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
     * Retrieves data from the selected columns.
     * @return List of objects from the selected rows.
     */
    private List<PlanetSwitchDevice> getSelectedDevices() {
        List<PlanetSwitchDevice> selected = new ArrayList<>();
        int[] selectedRows = devicesTable.getSelectedRows();

        for (int row : selectedRows) {
            if (row >= 0 && row < discoveredDevices.size()) {
                selected.add(discoveredDevices.get(devicesTable.convertRowIndexToModel(row)));
            }
        }
        return selected;
    }

    private void updateDeviceCount() {
        deviceCountLabel.setText("Devices found: " + discoveredDevices.size());
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
        showInfo("Preferences", "Preferences panel coming soon !");
    }

    private void showDocumentation() {
        showInfo("Documentation", "Documentation coming soon !");
    }

    private void showAbout() {
        JOptionPane.showMessageDialog(
                this,
                "Rover21 - PLANET Switch Discovery Tool\n" +
                        "Version 2.1.0\n\n" +
                        "Modern network discovery tool for PLANET switches.\n\n" +
                        "© 2026",
                "About Rover21",
                JOptionPane.INFORMATION_MESSAGE
        );
    }
}