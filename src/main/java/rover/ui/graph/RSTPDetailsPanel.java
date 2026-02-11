package rover.ui.graph;

import rover.model.rstp.RSTPNode;
import rover.model.rstp.RSTPPort;
import rover.utils.LanguageManager;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;

/**
 * Panel displaying detailed information about a selected RSTP node.
 * Shows device properties and port states table.
 *
 * @author Benjamin FOUILHAC, ADM21 - January/February 2026
 */
public class RSTPDetailsPanel extends JPanel {

    private static final Font FONT_TITLE = new Font("Segoe UI", Font.PLAIN, 14);
    private static final Font FONT_LABEL = new Font("Segoe UI", Font.PLAIN, 12);
    private static final Font FONT_VALUE = new Font("Segoe UI", Font.PLAIN, 12);
    private static final Color COLOR_BORDER = new Color(224, 224, 224);
    private static final Color COLOR_BACKGROUND = new Color(250, 250, 250);
    private static final int PREFERRED_WIDTH = 280;

    private final transient LanguageManager lang = LanguageManager.getInstance();

    private JLabel deviceNameValue;
    private JLabel ipAddressValue;
    private JLabel macAddressValue;
    private JLabel bridgeIdValue;
    private JLabel rootBridgeIdValue;
    private JLabel rootCostValue;
    private JLabel hopCountValue;
    private JLabel stpVersionValue;
    private DefaultTableModel portsTableModel;

    public RSTPDetailsPanel() {
        initComponents();
        showEmptyState();
    }

    private void initComponents() {
        setLayout(new BorderLayout());
        setPreferredSize(new Dimension(PREFERRED_WIDTH, 0));
        setBackground(COLOR_BACKGROUND);
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 1, 0, 0, COLOR_BORDER),
                BorderFactory.createEmptyBorder(12, 12, 12, 12)
        ));

        add(createHeaderPanel(), BorderLayout.NORTH);
        add(createPortsPanel(), BorderLayout.CENTER);
    }

    private JPanel createHeaderPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setOpaque(false);

        JLabel titleLabel = new JLabel(lang.get("topology.details_title"));
        titleLabel.setFont(FONT_TITLE);
        titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(titleLabel);
        panel.add(Box.createVerticalStrut(12));

        panel.add(createInfoRow(lang.get("topology.details_device_name")));
        deviceNameValue = (JLabel) ((JPanel) panel.getComponent(panel.getComponentCount() - 1)).getComponent(1);

        panel.add(createInfoRow(lang.get("topology.details_ip_address")));
        ipAddressValue = (JLabel) ((JPanel) panel.getComponent(panel.getComponentCount() - 1)).getComponent(1);

        panel.add(createInfoRow("MAC:"));
        macAddressValue = (JLabel) ((JPanel) panel.getComponent(panel.getComponentCount() - 1)).getComponent(1);

        panel.add(createInfoRow(lang.get("topology.details_bridge_id")));
        bridgeIdValue = (JLabel) ((JPanel) panel.getComponent(panel.getComponentCount() - 1)).getComponent(1);

        panel.add(createInfoRow(lang.get("topology.details_root_bridge")));
        rootBridgeIdValue = (JLabel) ((JPanel) panel.getComponent(panel.getComponentCount() - 1)).getComponent(1);

        panel.add(createInfoRow(lang.get("topology.details_root_cost")));
        rootCostValue = (JLabel) ((JPanel) panel.getComponent(panel.getComponentCount() - 1)).getComponent(1);

        panel.add(createInfoRow(lang.get("topology.details_hop_count")));
        hopCountValue = (JLabel) ((JPanel) panel.getComponent(panel.getComponentCount() - 1)).getComponent(1);

        panel.add(createInfoRow(lang.get("topology.details_stp_version")));
        stpVersionValue = (JLabel) ((JPanel) panel.getComponent(panel.getComponentCount() - 1)).getComponent(1);

        panel.add(Box.createVerticalStrut(16));

        JLabel portsTitle = new JLabel(lang.get("topology.details_ports"));
        portsTitle.setFont(FONT_TITLE);
        portsTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(portsTitle);
        panel.add(Box.createVerticalStrut(8));

        return panel;
    }

    private JPanel createInfoRow(String labelText) {
        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 22));

        JLabel label = new JLabel(labelText);
        label.setFont(FONT_LABEL);
        label.setForeground(new Color(100, 100, 100));
        row.add(label, BorderLayout.WEST);

        JLabel value = new JLabel("-");
        value.setFont(FONT_VALUE);
        row.add(value, BorderLayout.CENTER);

        return row;
    }

    private JPanel createPortsPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setOpaque(false);

        String[] columns = {"Port", "Role", "State"};
        portsTableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        JTable portsTable = new JTable(portsTableModel);
        portsTable.setFont(FONT_LABEL);
        portsTable.setRowHeight(24);
        portsTable.getTableHeader().setFont(FONT_LABEL);
        portsTable.setShowGrid(true);
        portsTable.setGridColor(COLOR_BORDER);

        DefaultTableCellRenderer centerRenderer = new DefaultTableCellRenderer();
        centerRenderer.setHorizontalAlignment(SwingConstants.CENTER);
        for (int i = 0; i < portsTable.getColumnCount(); i++) {
            portsTable.getColumnModel().getColumn(i).setCellRenderer(centerRenderer);
        }

        JScrollPane scrollPane = new JScrollPane(portsTable);
        scrollPane.setBorder(BorderFactory.createLineBorder(COLOR_BORDER));
        panel.add(scrollPane, BorderLayout.CENTER);

        return panel;
    }

    /**
     * Updates the panel with details of the selected node.
     *
     * @param node the selected RSTP node, or null to clear
     */
    public void setSelectedNode(RSTPNode node) {
        if (node == null) {
            showEmptyState();
            return;
        }

        deviceNameValue.setText(node.getDeviceName() != null ? node.getDeviceName() : "-");
        ipAddressValue.setText(node.getIpAddress() != null ? node.getIpAddress() : "-");
        macAddressValue.setText(node.getDevice() != null ? node.getDevice().getMacAddress() : "-");
        bridgeIdValue.setText(node.getBridgeId() != null ? formatBridgeId(node.getBridgeId()) : "-");
        rootBridgeIdValue.setText(node.getRootBridgeId() != null ? formatBridgeId(node.getRootBridgeId()) : "-");
        rootCostValue.setText(String.valueOf(node.getRootCost()));
        hopCountValue.setText(String.valueOf(node.getHopCount()));
        stpVersionValue.setText(node.getVersion() != null ? node.getVersion().toString() : "-");

        updatePortsTable(node);
    }

    private String formatBridgeId(String bridgeId) {
        if (bridgeId == null) return "-";
        if (bridgeId.length() > 20) {
            return bridgeId.substring(0, 8) + "..." + bridgeId.substring(bridgeId.length() - 8);
        }
        return bridgeId;
    }

    private void updatePortsTable(RSTPNode node) {
        portsTableModel.setRowCount(0);

        if (node.getPorts() == null || node.getPorts().isEmpty()) {
            return;
        }

        for (RSTPPort port : node.getPorts()) {
            Object[] row = {
                    port.getPortNumber(),
                    port.getRole() != null ? port.getRole().toString() : "-",
                    port.getState() != null ? port.getState().toString() : "-"
            };
            portsTableModel.addRow(row);
        }
    }

    /**
     * Shows an empty state when no node is selected.
     */
    public void showEmptyState() {
        deviceNameValue.setText(lang.get("topology.details_select_node"));
        ipAddressValue.setText("-");
        macAddressValue.setText("-");
        bridgeIdValue.setText("-");
        rootBridgeIdValue.setText("-");
        rootCostValue.setText("-");
        hopCountValue.setText("-");
        stpVersionValue.setText("-");
        portsTableModel.setRowCount(0);
    }
}
