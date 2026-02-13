package rover.ui;

import rover.controller.MainController;
import rover.model.PlanetSwitchDevice;
import rover.model.rstp.EndDevice;
import rover.model.rstp.EndDeviceLink;
import rover.model.rstp.RSTPLink;
import rover.model.rstp.RSTPNode;
import rover.model.rstp.RSTPTopology;
import rover.model.rstp.RSTPVersion;
import rover.services.ExportService;
import rover.services.RSTPDiscoveryService;
import rover.ui.graph.RSTPGraphPanel;
import rover.utils.LanguageManager;
import rover.utils.PreferencesManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Dialog for displaying RSTP information in a table format.
 *
 * @author Benjamin FOUILHAC, ADM21 - January/February 2026
 */
public class RSTPTopologyDialog extends JDialog {

    private static final Logger logger = LoggerFactory.getLogger(RSTPTopologyDialog.class);

    private static final int DIALOG_WIDTH = 1250;
    private static final int DIALOG_HEIGHT = 700;

    // Sets minimum size: 16:9 ratio and 1066x600px
    private static final int MIN_DIALOG_WIDTH = 1066;
    private static final int MIN_DIALOG_HEIGHT = 600;

    private static final int CONTENT_PADDING = 15;

    // Button sizing: responsive from 90px (min) to 160px (comfortable max)
    private static final int BUTTON_HEIGHT = 32;
    private static final int BUTTON_MIN_WIDTH = 90;
    private static final int BUTTON_MAX_WIDTH = 160;

    private static final double BUTTON_WIDTH_RATIO = 0.14;

    private static final Font FONT_TABLE = new Font("Segoe UI", Font.PLAIN, 12);
    private static final Color COLOR_ALTERNATE_ROW = new Color(224, 224, 224);
    private static final Color COLOR_TABLE_GRID = new Color(220, 220, 220);
    private static final Color COLOR_HEADER_BACKGROUND = new Color(245, 245, 245);
    private static final Color COLOR_HEADER_TEXT = new Color(60, 60, 60);
    private static final Color COLOR_STATUS_BACKGROUND = new Color(227, 242, 253);
    private static final Color COLOR_STATUS_BORDER = new Color(144, 202, 249);
    private static final Color COLOR_STATUS_TEXT = new Color(25, 118, 210);

    // Column indices
    private static final int COL_DEVICE_NAME = 0;
    private static final int COL_MAC = 1;
    private static final int COL_IPV4 = 2;
    private static final int COL_STP = 3;
    private static final int COL_PRIORITY = 4;
    private static final int COL_HOP_COUNT = 5;
    private static final int COL_ROOT_MAC = 6;
    private static final int COL_PATH_COST = 7;
    private static final int COL_TC_TIME = 8;

    private final transient LanguageManager lang = LanguageManager.getInstance();
    private final transient RSTPDiscoveryService discoveryService;

    private JTable rstpTable;
    private DefaultTableModel tableModel;
    private transient RSTPTopology topology;
    private final transient List<PlanetSwitchDevice> originalDevices; // Stored for refresh
    private JLabel statusLabel;

    private final transient ExportService exportService = new ExportService();
    private transient SwingWorker<RSTPTopology, String> discoveryWorker;

    // Responsive buttons for dynamic resizing
    private JButton exportButton;
    private JButton refreshButton;
    private JButton closeButton;

    private RSTPGraphPanel graphPanel;

    /**
     * Creates a new RSTP Topology dialog.
     * <p>
     * Initializes the dialog with proper sizing constraints and responsive behavior.
     * The dialog uses minimum dimensions to ensure UI elements remain visible
     * during resizing.
     * </p>
     *
     * @param parent the parent frame for modal positioning
     * @param devices the list of devices to discover RSTP topology for
     */
    public RSTPTopologyDialog(JFrame parent, List<PlanetSwitchDevice> devices) {
        setTitle(lang.get("topology.window_title"));
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setModalityType(ModalityType.MODELESS);

        this.discoveryService = new RSTPDiscoveryService();
        this.originalDevices = new ArrayList<>(devices); // Store for refresh

        setSize(DIALOG_WIDTH, DIALOG_HEIGHT);
        setMinimumSize(new Dimension(MIN_DIALOG_WIDTH, MIN_DIALOG_HEIGHT));
        setLocationRelativeTo(parent);
        setLayout(new BorderLayout());

        // Add a window listener to cancel discovery and clean up resources on close
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent windowEvent) {
                cancelDiscovery();
                shutdownRSTPServices();
            }
        });

        // Add a component listener for responsive resizing
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                updateResponsiveComponents();
            }
        });

        initComponents();
        discoverAndDisplay(devices);

        // Apply initial responsive sizing
        updateResponsiveComponents();

        logger.info("RSTPTopologyDialog opened");
    }

    /**
     * Initializes UI components.
     * <p>
     * Creates the main layout with a tabbed pane containing graph and table views.
     * The graph view provides interactive network topology visualization.
     * The table view shows detailed RSTP information in tabular format.
     * </p>
     */
    private void initComponents() {
        JTabbedPane tabbedPane;
        createTable();

        // Main content panel with padding from window edges
        JPanel centerPanel = new JPanel(new BorderLayout(0, 10));
        centerPanel.setBorder(BorderFactory.createEmptyBorder(
                CONTENT_PADDING, CONTENT_PADDING, CONTENT_PADDING, CONTENT_PADDING));

        centerPanel.add(createStatusBar(), BorderLayout.NORTH);

        // Create a tabbed pane with graph and table views
        tabbedPane = new JTabbedPane();
        tabbedPane.setFont(FONT_TABLE);

        // Table View
        JScrollPane scrollPane = new JScrollPane(rstpTable);
        scrollPane.setBorder(BorderFactory.createLineBorder(new Color(200, 200, 200)));
        tabbedPane.addTab(lang.get("topology.tab_table"), scrollPane);

        // Graph View
        graphPanel = new RSTPGraphPanel();
        tabbedPane.addTab(lang.get("topology.tab_graph"), graphPanel);


        centerPanel.add(tabbedPane, BorderLayout.CENTER);

        add(centerPanel, BorderLayout.CENTER);
        add(createButtonPanel(), BorderLayout.SOUTH);
    }

    /**
     * Creates the status bar.
     */
    private JPanel createStatusBar() {
        JPanel statusBar = new JPanel(new BorderLayout(15, 0));
        statusBar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(COLOR_STATUS_BORDER, 1),
                BorderFactory.createEmptyBorder(8, 12, 8, 12)
        ));
        statusBar.setBackground(COLOR_STATUS_BACKGROUND);

        statusLabel = new JLabel(lang.get("status.ready"));
        statusLabel.setFont(FONT_TABLE);
        statusLabel.setForeground(COLOR_STATUS_TEXT);

        statusBar.add(statusLabel, BorderLayout.WEST);

        return statusBar;
    }

    /**
     * Creates the RSTP information table.
     */
    private void createTable() {
        String[] columns = {
                lang.get("table.device_name"),
                lang.get("table.mac"),
                lang.get("table.ipv4"),
                lang.get("table.stp_mode"),
                lang.get("table.priority"),
                lang.get("table.hop_count"),
                lang.get("table.root_bridge"),
                lang.get("table.path_cost"),
                lang.get("table.tc_time")
        };

        tableModel = new DefaultTableModel(columns, 0) {
            @Override
            public Class<?> getColumnClass(int columnIndex) {
                return String.class;
            }

            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        rstpTable = new JTable(tableModel) {
            @Override
            public Component prepareRenderer(TableCellRenderer renderer, int row, int column) {
                Component c = super.prepareRenderer(renderer, row, column);

                if (!c.getBackground().equals(getSelectionBackground())) {
                    c.setBackground(row % 2 == 0 ? COLOR_ALTERNATE_ROW : Color.WHITE);
                }

                return c;
            }
        };

        configureTable();
        configureTableColumns();
    }

    /**
     * Configures table properties.
     */
    private void configureTable() {
        rstpTable.setFont(FONT_TABLE);
        rstpTable.setRowHeight(30);
        rstpTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        rstpTable.getTableHeader().setReorderingAllowed(true);
        rstpTable.setShowGrid(true);
        rstpTable.setGridColor(COLOR_TABLE_GRID);
        rstpTable.setIntercellSpacing(new Dimension(1, 1));

        TableRowSorter<DefaultTableModel> tableSorter = new TableRowSorter<>(tableModel);
        rstpTable.setRowSorter(tableSorter);

        JTableHeader header = rstpTable.getTableHeader();
        header.setFont(FONT_TABLE);
        header.setBackground(COLOR_HEADER_BACKGROUND);
        header.setForeground(COLOR_HEADER_TEXT);
    }

    /**
     * Configures table column widths.
     */
    private void configureTableColumns() {
        DefaultTableCellRenderer centerRenderer = new DefaultTableCellRenderer();
        centerRenderer.setHorizontalAlignment(SwingConstants.CENTER);
        centerRenderer.setFont(FONT_TABLE);

        for (int i = 0; i < rstpTable.getColumnCount(); i++) {
            rstpTable.getColumnModel().getColumn(i).setCellRenderer(centerRenderer);
        }

        int[] columnWidths = {160, 140, 120, 100, 80, 100, 140, 100, 120};
        for (int i = 0; i < columnWidths.length; i++) {
            rstpTable.getColumnModel().getColumn(i).setPreferredWidth(columnWidths[i]);
        }
    }

    /**
     * Updates the status message (thread-safe).
     *
     * @param message the message to display
     */
    private void updateStatus(String message) {
        SwingUtilities.invokeLater(() -> statusLabel.setText(message));
    }

    /**
     * Creates the button panel with action buttons.
     * <p>
     * The panel includes export, refresh, and close buttons aligned to the right.
     * A top border separates it from the main content area, matching MainWindow styling.
     * Buttons are stored as instance variables for responsive resizing.
     * </p>
     *
     * @return the configured button panel
     */
    private JPanel createButtonPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 12));
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(224, 224, 224)),
                BorderFactory.createEmptyBorder(0, CONTENT_PADDING, 0, CONTENT_PADDING)
        ));

        // Calculate initial button size
        int dialogWidth = getWidth() > 0 ? getWidth() : DIALOG_WIDTH;
        int buttonWidth = calculateResponsiveWidth(dialogWidth, BUTTON_WIDTH_RATIO, BUTTON_MIN_WIDTH, BUTTON_MAX_WIDTH);
        Dimension buttonSize = new Dimension(buttonWidth, BUTTON_HEIGHT);

        exportButton = createResponsiveButton(lang.get("button.export"), buttonSize, _ -> export());
        refreshButton = createResponsiveButton(lang.get("button.refresh"), buttonSize, _ -> refresh());
        closeButton = createResponsiveButton(lang.get("button.close"), buttonSize, _ -> {
            shutdownRSTPServices();
            dispose();
        });

        panel.add(exportButton);
        panel.add(refreshButton);
        panel.add(closeButton);

        return panel;
    }

    /**
     * Creates a responsive button with the specified size.
     *
     * @param text     the button text
     * @param size     the initial button size
     * @param listener the action listener
     * @return the configured button
     */
    private JButton createResponsiveButton(String text, Dimension size, java.awt.event.ActionListener listener) {
        JButton button = new JButton(text);
        button.setFont(FONT_TABLE);
        button.setPreferredSize(size);
        button.setFocusPainted(false);
        button.setEnabled(true);
        button.addActionListener(listener);
        return button;
    }

    /**
     * Updates all responsive UI components based on current dialog dimensions.
     */
    private void updateResponsiveComponents() {
        int dialogWidth = getWidth();

        // Update button sizes
        int buttonWidth = calculateResponsiveWidth(dialogWidth, BUTTON_WIDTH_RATIO, BUTTON_MIN_WIDTH, BUTTON_MAX_WIDTH);
        Dimension buttonSize = new Dimension(buttonWidth, BUTTON_HEIGHT);

        updateButtonSize(exportButton, buttonSize);
        updateButtonSize(refreshButton, buttonSize);
        updateButtonSize(closeButton, buttonSize);

        // Revalidate layout
        revalidate();
        repaint();
    }

    /**
     * Calculates a responsive width based on dialog width and constraints.
     * <p>
     * The calculated width is clamped between minimum and maximum values to ensure
     * the component remains usable at all dialog sizes.
     * </p>
     *
     * @param dialogWidth the current dialog width in pixels
     * @param ratio the ratio of component width relative to dialog width (0.0 to 1.0)
     * @param minWidth the minimum allowed width in pixels
     * @param maxWidth the maximum allowed width in pixels
     * @return the calculated width, clamped between min and max
     */
    private int calculateResponsiveWidth(int dialogWidth, double ratio, int minWidth, int maxWidth) {
        int calculatedWidth = (int) (dialogWidth * ratio);
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
     * Exports the RSTP topology table to a CSV file.
     */
    private void export() {
        JFileChooser fileChooser = MainController.createFileChooser(lang);
        fileChooser.setDialogTitle(lang.get("export.choose_location"));
        fileChooser.setFileFilter(new FileNameExtensionFilter("CSV Files (*.csv)", "csv"));

        // Set the initial directory from preferences
        String lastPath = PreferencesManager.getLastExportPath();
        if (!lastPath.isEmpty()) {
            File lastDir = new File(lastPath);
            if (lastDir.exists() && lastDir.isDirectory()) {
                fileChooser.setCurrentDirectory(lastDir);
            }
        }

        // Set default filename
        fileChooser.setSelectedFile(new File("rstp_topology_export.csv"));

        int result = fileChooser.showSaveDialog(this);

        if (result == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();

            // Ensure .csv extension
            if (!file.getName().toLowerCase().endsWith(".csv")) {
                file = new File(file.getAbsolutePath() + ".csv");
            }

            if (file.exists()){
                int overwriteResult = JOptionPane.showOptionDialog(
                        this,
                        lang.get("info.overwrite_message", file.getName()),
                        lang.get("info.overwrite_title"),
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.WARNING_MESSAGE,
                        null,
                        new String[]{lang.get("button.yes"), lang.get("button.no")},
                        lang.get("button.no")
                );

                if (overwriteResult != JOptionPane.YES_OPTION) {
                    logger.info("Export cancelled, no overwrite file");
                    return;
                }
            }

            try {
                // Export the table model to CSV
                exportService.exportRSTPToCSV(tableModel, file);

                // Save the export directory for next time
                PreferencesManager.saveLastExportPath(file.getParent());

                JOptionPane.showMessageDialog(this,
                    lang.get("export.success", file.getName()),
                    lang.get("export.title"),
                    JOptionPane.INFORMATION_MESSAGE);

                logger.info("RSTP topology exported successfully to: {}", file.getAbsolutePath());

            } catch (IOException e) {
                logger.error("Failed to export RSTP topology", e);
                JOptionPane.showMessageDialog(this,
                    lang.get("export.error", e.getMessage()),
                    lang.get("error.title"),
                    JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    /**
     * Cancels the ongoing discovery process if any.
     */
    private void cancelDiscovery() {
        if (discoveryWorker != null && !discoveryWorker.isDone()) {
            logger.info("Cancelling RSTP discovery...");
            discoveryWorker.cancel(true);
            updateStatus(lang.get("status.cancelled"));
        }
    }

    /**
     * Shuts down RSTP services and cleans up all resources.
     * This closes all SSH connections and channels.
     */
    private void shutdownRSTPServices() {
        logger.info("Shutting down RSTP services...");
        try {
            discoveryService.shutdown();
            logger.info("RSTP services shutdown complete");
        } catch (Exception e) {
            logger.error("Error during RSTP services shutdown", e);
        }
    }

    /**
     * Discovers RSTP topology and displays in a table with real-time updates.
     */
    private void discoverAndDisplay(List<PlanetSwitchDevice> devices) {
        // Cancel any existing discovery
        cancelDiscovery();

        // First, display basic info for all SSH-capable devices
        updateStatus(lang.get("status.topology_loading"));
        displayBasicInfo(devices);

        updateStatus(lang.get("status.topology_discovering"));

        // Set up callback for real-time updates
        discoveryService.setProgressCallback(new RSTPDiscoveryService.DiscoveryProgressCallback() {
            @Override
            public void onNodeDiscovered(RSTPNode node) {
                SwingUtilities.invokeLater(() -> updateNodeInTable(node));
            }

            @Override
            public void onLinkDiscovered(RSTPLink link) {
                logger.debug("Link discovered: {} <-> {}",
                        link.sourceNode().getDeviceName(),
                        link.destinationNode().getDeviceName());
            }

            @Override
            public void onEndDeviceDiscovered(EndDevice endDevice, EndDeviceLink link) {
                logger.debug("End device discovered: {} on {}:{}",
                        endDevice.getMacAddress(),
                        link.switchNode().getDeviceName(),
                        link.switchPort());
            }

            @Override
            public void onProgress(String messageKey) {
                updateStatus(lang.get(messageKey));
            }
        });

        discoveryWorker = new SwingWorker<>() {
            @Override
            protected RSTPTopology doInBackground() {
                return discoveryService.discoverTopology(devices);
            }

            @Override
            protected void done() {
                if (isCancelled()) {
                    logger.info("RSTP discovery was cancelled");
                    return;
                }

                try {
                    topology = get();

                    if (topology.isValid()) {
                        // Update the graph panel with complete topology
                        if (graphPanel != null) {
                            graphPanel.setTopology(topology);
                        }

                        int linkCount = topology.getLinks().size();
                        int endDeviceCount = topology.getEndDeviceCount();
                        StringBuilder statusMsg = new StringBuilder(lang.get("status.topology_ready", topology.getNodeCount()));
                        if (linkCount > 0) {
                            statusMsg.append(" - ").append(linkCount).append(" link(s)");
                        }
                        if (endDeviceCount > 0) {
                            statusMsg.append(" - ").append(endDeviceCount).append(" end device(s)");
                        }
                        updateStatus(statusMsg.toString());
                    } else {
                        logger.warn("No RSTP-enabled devices found, showing basic info only");
                        updateStatus(lang.get("status.topology_ready_empty"));
                    }
                } catch (Exception e) {
                    logger.error("Failed to discover topology", e);
                    showError(lang.get("error.topology_discover", e.getMessage()));
                    Thread.currentThread().interrupt();
                }
            }
        };

        discoveryWorker.execute();
    }

    /**
     * Updates a single node's data in the table (called from callback).
     */
    private void updateNodeInTable(RSTPNode node) {
        int row = findDeviceRow(node.getIpAddress());
        if (row >= 0) {
            tableModel.setValueAt(formatStpMode(node.getVersion()), row, COL_STP);
            tableModel.setValueAt(extractPriority(node.getBridgeId()), row, COL_PRIORITY);
            tableModel.setValueAt(node.getHopCount() > 0 ? String.valueOf(node.getHopCount()) : "-", row, COL_HOP_COUNT);
            tableModel.setValueAt(extractMac(node.getRootBridgeId()).toUpperCase(), row, COL_ROOT_MAC);
            tableModel.setValueAt(String.valueOf(node.getRootCost()), row, COL_PATH_COST);
            tableModel.setValueAt(node.getTopologyChangeTime() != null ? node.getTopologyChangeTime() : "-", row, COL_TC_TIME);
            logger.debug("Updated table row for {} with RSTP info", node.getIpAddress());
        }
    }

    /**
     * Displays basic device information for all SSH-capable devices.
     */
    private void displayBasicInfo(List<PlanetSwitchDevice> devices) {
        tableModel.setRowCount(0);

        List<PlanetSwitchDevice> sshDevices = devices.stream()
                .filter(d -> d.isSSHUp() && d.getUsername() != null && d.getPassword() != null)
                .toList();

        logger.info("Displaying basic info for {} SSH-capable devices", sshDevices.size());

        for (PlanetSwitchDevice device : sshDevices) {
            Object[] row = new Object[9];

            row[COL_DEVICE_NAME] = device.getDeviceName();
            row[COL_MAC] = device.getMacAddress();
            row[COL_IPV4] = device.getIpAddress();
            row[COL_STP] = "-";
            row[COL_PRIORITY] = "-";
            row[COL_HOP_COUNT] = "-";
            row[COL_ROOT_MAC] = "-";
            row[COL_PATH_COST] = "-";
            row[COL_TC_TIME] = "-";

            tableModel.addRow(row);
        }
    }

    /**
     * Finds the table row for a given IP address.
     */
    private int findDeviceRow(String ipAddress) {
        for (int i = 0; i < tableModel.getRowCount(); i++) {
            String rowIp = (String) tableModel.getValueAt(i, COL_IPV4);
            if (ipAddress.equals(rowIp)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Formats STP mode for display.
     */
    private String formatStpMode(RSTPVersion version) {
        return switch (version) {
            case STP -> "STP";
            case RSTP -> "RSTP";
            case MSTP -> "MSTP";
            default -> "-";
        };
    }

    /**
     * Extracts priority from Bridge ID (format: "32768.A8-F7-E0-C5-0C-97").
     */
    private String extractPriority(String bridgeId) {
        if (bridgeId == null || !bridgeId.contains(".")) {
            return "-";
        }
        return bridgeId.split("\\.")[0];
    }

    /**
     * Extracts MAC address from Root Bridge ID (format: "32768.A8-F7-E0-C5-0C-97").
     */
    private String extractMac(String rootBridgeId) {
        if (rootBridgeId == null || !rootBridgeId.contains(".")) {
            return "-";
        }
        String mac = rootBridgeId.split("\\.")[1];
        return mac.replace("-", ":");
    }

    /**
     * Refreshes both table and graph views by rediscovering topology.
     * Uses the original device list to ensure newly connected devices are detected.
     * Closes all SSH connections first to ensure fresh data from switches.
     */
    private void refresh() {
        if (originalDevices == null || originalDevices.isEmpty()) {
            logger.warn("No devices to refresh");
            return;
        }

        logger.info("Refreshing topology with {} devices", originalDevices.size());

        // Close all SSH connections to force fresh queries
        discoveryService.shutdown();

        // Clear old topology reference
        topology = null;

        // Reset graph panel to show the loading state
        if (graphPanel != null) {
            graphPanel.setTopology(null);
        }

        discoverAndDisplay(originalDevices);
    }

    /**
     * Shows error message dialog.
     */
    private void showError(String message) {
        JOptionPane.showMessageDialog(this, message, "Error", JOptionPane.ERROR_MESSAGE);
    }
}