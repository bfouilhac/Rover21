package com.rover.ui;

import com.rover.model.PlanetSwitchDevice;
import com.rover.model.rstp.*;
import com.rover.services.RSTPDiscoveryService;
import com.rover.utils.LanguageManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import javax.swing.table.*;
import java.awt.*;
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

    // Column indices
    private static final int COL_DEVICE_NAME = 0;
    private static final int COL_MAC = 1;
    private static final int COL_IPV4 = 2;
    private static final int COL_STP = 3;
    private static final int COL_PRIORITY = 4;
    private static final int COL_HOP_COUNT = 5;
    private static final int COL_ROOT_MAC = 6;
    private static final int COL_PATH_COST = 7;

    private final LanguageManager lang = LanguageManager.getInstance();
    private final RSTPDiscoveryService discoveryService;

    private JTable rstpTable;
    private DefaultTableModel tableModel;
    private RSTPTopology topology;

    public RSTPTopologyDialog(JFrame parent, List<PlanetSwitchDevice> devices) {
        super(parent, "RSTP Topology Information", false);
        this.discoveryService = new RSTPDiscoveryService();

        setSize(DIALOG_WIDTH, DIALOG_HEIGHT);
        setLocationRelativeTo(parent);
        setLayout(new BorderLayout());

        initComponents();
        discoverAndDisplay(devices);

        logger.info("RstpTopologyDialog opened");
    }

    /**
     * Initializes UI components.
     */
    private void initComponents() {
        createTable();

        JScrollPane scrollPane = new JScrollPane(rstpTable);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());

        add(scrollPane, BorderLayout.CENTER);
        add(createButtonPanel(), BorderLayout.SOUTH);
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
                lang.get("table.path_cost")
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

        int[] columnWidths = {160, 140, 120, 100, 80, 100, 140, 100};
        for (int i = 0; i < columnWidths.length; i++) {
            rstpTable.getColumnModel().getColumn(i).setPreferredWidth(columnWidths[i]);
        }
    }

    /**
     * Creates button panel at bottom.
     */
    private JPanel createButtonPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 10));

        JButton refreshButton = MainWindow.createActionButton(lang.get("button.refresh"), e -> refresh());
        JButton closeButton = MainWindow.createActionButton(lang.get("button.close"), e -> dispose());

        panel.add(refreshButton);
        panel.add(closeButton);

        return panel;
    }

    /**
     * Discovers RSTP topology and displays in a table.
     */
    private void discoverAndDisplay(List<PlanetSwitchDevice> devices) {
        displayBasicInfo(devices);

        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));

        SwingWorker<RSTPTopology, Void> worker = new SwingWorker<>() {
            @Override
            protected RSTPTopology doInBackground() {
                return discoveryService.discoverTopology(devices);
            }

            @Override
            protected void done() {
                try {
                    topology = get();

                    if (topology.isValid()) {
                        updateWithRstpInfo();
                    } else {
                        logger.warn("No RSTP-enabled devices found, showing basic info only");
                    }
                } catch (Exception e) {
                    logger.error("Failed to discover topology", e);
                    showError("Failed to discover RSTP topology: " + e.getMessage());
                } finally {
                    setCursor(Cursor.getDefaultCursor());
                }
            }
        };

        worker.execute();
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
            Object[] row = new Object[8];

            row[COL_DEVICE_NAME] = device.getDeviceName();
            row[COL_MAC] = device.getMacAddress();
            row[COL_IPV4] = device.getIpAddress();
            row[COL_STP] = "-";
            row[COL_PRIORITY] = "-";
            row[COL_HOP_COUNT] = "-";
            row[COL_ROOT_MAC] = "-";
            row[COL_PATH_COST] = "-";

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
                tableModel.setValueAt(extractMac(node.getRootBridgeId()), row, COL_ROOT_MAC);
                tableModel.setValueAt(String.valueOf(node.getRootCost()), row, COL_PATH_COST);
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