package com.rover.ui;

import com.rover.controller.MainController;
import com.rover.model.PlanetSwitchDevice;
import com.rover.model.rstp.RSTPNode;
import com.rover.model.rstp.RSTPTopology;
import com.rover.model.rstp.RSTPVersion;
import com.rover.services.ExportService;
import com.rover.services.RSTPDiscoveryService;
import com.rover.utils.LanguageManager;
import com.rover.utils.PreferencesManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.*;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * Dialog for displaying RSTP information in a table format.
 *
 * @author Benjamin FOUILHAC, ADM21 - January 2026
 */
public class RSTPTopologyDialog extends JDialog {

    private static final Logger logger = LoggerFactory.getLogger(RSTPTopologyDialog.class);

    private static final int DIALOG_WIDTH = 1000;
    private static final int DIALOG_HEIGHT = 600;

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
    private JLabel statusLabel;

    private final transient ExportService exportService = new ExportService();
    private transient SwingWorker<RSTPTopology, String> discoveryWorker;

    public RSTPTopologyDialog(JFrame parent, List<PlanetSwitchDevice> devices) {
        setTitle(lang.get("topology.window_title"));
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setModalityType(ModalityType.APPLICATION_MODAL);

        this.discoveryService = new RSTPDiscoveryService();

        setSize(DIALOG_WIDTH, DIALOG_HEIGHT);
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

        initComponents();
        discoverAndDisplay(devices);

        logger.info("RSTPTopologyDialog opened");
    }

    /**
     * Initializes UI components.
     */
    private void initComponents() {
        createTable();

        JPanel centerPanel = new JPanel(new BorderLayout());
        centerPanel.add(createStatusBar(), BorderLayout.NORTH);

        JScrollPane scrollPane = new JScrollPane(rstpTable);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        centerPanel.add(scrollPane, BorderLayout.CENTER);

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

    private JPanel createButtonPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 10));

        JButton exportButton = MainWindow.createActionButton(lang.get("button.export"), _ -> export());

        JButton refreshButton = MainWindow.createActionButton(lang.get("button.refresh"), _ -> refresh());

        JButton closeButton = MainWindow.createActionButton(lang.get("button.close"), _ -> {
            shutdownRSTPServices();
            dispose();
        });

        exportButton.setEnabled(true);
        refreshButton.setEnabled(true);
        closeButton.setEnabled(true);

        panel.add(exportButton);
        panel.add(refreshButton);
        panel.add(closeButton);

        return panel;
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
     * Discovers RSTP topology and displays in a table.
     */
    private void discoverAndDisplay(List<PlanetSwitchDevice> devices) {
        // Cancel any existing discovery
        cancelDiscovery();

        // First, display basic info for all SSH-capable devices
        updateStatus(lang.get("status.topology_loading"));
        displayBasicInfo(devices);

        updateStatus(lang.get("status.topology_discovering"));

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
                        updateStatus(lang.get("status.topology_update_rstp_information"));
                        updateWithRstpInfo();
                        updateStatus(lang.get("status.topology_ready", topology.getNodeCount()));
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
     * Updates table with RSTP information from discovered topology.
     */
    private void updateWithRstpInfo() {
        if (topology == null || !topology.isValid()) {
            return;
        }

        for (RSTPNode node : topology.getNodes()) {
            // Find the row for this device
            int row = findDeviceRow(node.getIpAddress());

            if (row >= 0) {
                tableModel.setValueAt(formatStpMode(node.getVersion()), row, COL_STP);
                tableModel.setValueAt(extractPriority(node.getBridgeId()), row, COL_PRIORITY);
                tableModel.setValueAt(node.getHopCount() > 0 ? String.valueOf(node.getHopCount()) : "-", row, COL_HOP_COUNT);
                tableModel.setValueAt(extractMac(node.getRootBridgeId()).toUpperCase(), row, COL_ROOT_MAC);
                tableModel.setValueAt(String.valueOf(node.getRootCost()), row, COL_PATH_COST);
                tableModel.setValueAt(node.getTopologyChangeTime() != null ? node.getTopologyChangeTime() : "-", row, COL_TC_TIME);
            }
        }

        logger.info("Updated {} rows with RSTP information", topology.getNodeCount());
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
     * Refreshes the table by rediscovering topology.
     */
    private void refresh() {
        if (topology == null || topology.getNodes().isEmpty()) {
            return;
        }

        List<PlanetSwitchDevice> devices = topology.getNodes().stream()
                .map(RSTPNode::getDevice)
                .toList();

        discoverAndDisplay(devices);
    }

    /**
     * Shows error message dialog.
     */
    private void showError(String message) {
        JOptionPane.showMessageDialog(this, message, "Error", JOptionPane.ERROR_MESSAGE);
    }
}