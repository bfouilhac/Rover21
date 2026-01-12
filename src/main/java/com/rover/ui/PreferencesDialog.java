package com.rover.ui;

import com.rover.utils.LanguageManager;
import com.rover.utils.PreferencesManager;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;

/**
 * Dialog window for configuring application preferences and settings.
 * <p>
 * The dialog follows a clean, block-based layout inspired by professional network
 * tools like PuTTY, with titled borders separating different configuration sections.
 * Settings are persisted using the {@link PreferencesManager} and take effect
 * immediately upon clicking "OK".
 * </p>
 *
 * @author Benjamin FOUILHAC, ADM21 in January-February 2026
 * @see PreferencesManager
 * @see LanguageManager
 */
public class PreferencesDialog extends JDialog {

    private final LanguageManager lang = LanguageManager.getInstance();

    // Discovery settings components
    private JSpinner discoveryTimeoutSpinner;
    private JTextField discoverySendPortSpinner;
    private JTextField discoveryListenPortSpinner;

    // SNMP settings components
    private JTextField snmpCommunityField;
    private JSpinner snmpTimeoutSpinner;
    private JCheckBox autoEnrichSnmpCheckbox;
    private JComboBox<String> snmpVersionComboBox;
    private JTextField snmpV3UsernameField;
    private JPasswordField snmpV3PasswordField;
    private JLabel snmpV3UsernameLabel;
    private JLabel snmpV3PasswordLabel;

    // Protocol settings components
    private JSpinner sshPortSpinner;
    private JSpinner telnetPortSpinner;

    private boolean wasApplied = false;

    /**
     * Constructs a new PreferencesDialog.
     * <p>
     * The dialog is created as a modal window, meaning the parent window
     * cannot be interacted with until this dialog is closed. The dialog
     * is centered relative to its parent and is not resizable to maintain
     * a consistent layout.
     * </p>
     *
     * @param parent the parent JFrame that owns this dialog
     */
    public PreferencesDialog(JFrame parent) {
        super(parent, "", true);
        setTitle(lang.get("preferences.title"));
        setSize(550, 600);
        setLocationRelativeTo(parent);
        setResizable(false);

        initComponents();
        loadPreferences();
    }

    /**
     * Initializes all UI components and layouts.
     * <p>
     * This method creates the main panel structure with vertically stacked
     * configuration blocks, each contained in a titled border. A scroll pane
     * is used to allow for future expansion with additional settings blocks.
     * </p>
     *
     * @see #createDiscoveryBlock()
     * @see #createSnmpBlock()
     * @see #createProtocolBlock()
     * @see #createButtonPanel()
     */
    private void initComponents() {
        setLayout(new BorderLayout(10, 10));

        // Main panel with vertical layout
        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        // Add configuration blocks
        mainPanel.add(createDiscoveryBlock());
        mainPanel.add(Box.createVerticalStrut(15));
        mainPanel.add(createSnmpBlock());
        mainPanel.add(Box.createVerticalStrut(15));
        mainPanel.add(createProtocolBlock());

        // Wrap in scroll pane for future expansion
        JScrollPane scrollPane = new JScrollPane(mainPanel);
        scrollPane.setBorder(null);
        add(scrollPane, BorderLayout.CENTER);

        // Bottom button panel
        add(createButtonPanel(), BorderLayout.SOUTH);
    }

    /**
     * Creates the Network Discovery configuration block.
     *
     * @return a JPanel containing the discovery settings
     * @see PreferencesManager#getDiscoveryTimeout()
     * @see PreferencesManager#getDiscoverySendPort()
     * @see PreferencesManager#getDiscoveryListenPort()
     */
    private JPanel createDiscoveryBlock() {
        JPanel panel = new JPanel(new GridBagLayout());

        // Title border
        TitledBorder border = BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(),
                lang.get("preferences.block.discovery")
        );
        border.setTitleFont(new Font("Segoe UI", Font.PLAIN, 13));
        panel.setBorder(BorderFactory.createCompoundBorder(
                border,
                BorderFactory.createEmptyBorder(10, 10, 10, 10)
        ));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Discovery timeout setting
        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0;
        panel.add(new JLabel(lang.get("preferences.discovery_timeout")), gbc);

        gbc.gridx = 1; gbc.weightx = 0;
        discoveryTimeoutSpinner = new JSpinner(new SpinnerNumberModel(PreferencesManager.getDiscoveryTimeout(), 1, 60000, 1));
        discoveryTimeoutSpinner.setPreferredSize(new Dimension(80, 25));
        panel.add(discoveryTimeoutSpinner, gbc);

        gbc.gridx = 2; gbc.weightx = 1.0;
        panel.add(new JLabel(lang.get("preferences.milliseconds")), gbc);

        // Send port setting
        gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0;
        panel.add(new JLabel(lang.get("preferences.discovery_send_port")), gbc);

        gbc.gridx = 1; gbc.weightx = 0;
        discoverySendPortSpinner = new JTextField();
        discoverySendPortSpinner.setText(String.valueOf(PreferencesManager.getDiscoverySendPort()));
        discoverySendPortSpinner.setPreferredSize(new Dimension(80, 25));
        panel.add(discoverySendPortSpinner, gbc);

        gbc.gridx = 2; gbc.weightx = 1.0;
        panel.add(new JLabel(""), gbc);

        // Listen port setting
        gbc.gridx = 0; gbc.gridy = 2; gbc.weightx = 0;
        panel.add(new JLabel(lang.get("preferences.discovery_listen_port")), gbc);

        gbc.gridx = 1; gbc.weightx = 0;
        discoveryListenPortSpinner = new JTextField();
        discoveryListenPortSpinner.setText(String.valueOf(PreferencesManager.getDiscoveryListenPort()));
        discoveryListenPortSpinner.setPreferredSize(new Dimension(80, 25));
        panel.add(discoveryListenPortSpinner, gbc);

        gbc.gridx = 2; gbc.weightx = 1.0;
        panel.add(new JLabel(""), gbc);

        return panel;
    }

    /**
     * Creates the SNMP Configuration block.
     *
     * @return a JPanel containing the SNMP settings
     * @see PreferencesManager#getSnmpCommunity()
     * @see PreferencesManager#getSnmpTimeout()
     * @see PreferencesManager#getAutoEnrichSnmp()
     * @see PreferencesManager#getSnmpVersion()
     */
    private JPanel createSnmpBlock() {
        JPanel panel = new JPanel(new GridBagLayout());

        // Title border
        TitledBorder border = BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(),
                lang.get("preferences.block.snmp")
        );
        border.setTitleFont(new Font("Segoe UI", Font.PLAIN, 13));
        panel.setBorder(BorderFactory.createCompoundBorder(
                border,
                BorderFactory.createEmptyBorder(10, 10, 10, 10)
        ));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // SNMP Version
        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0;
        panel.add(new JLabel(lang.get("preferences.snmp_version")), gbc);

        gbc.gridx = 1; gbc.gridwidth = 2; gbc.weightx = 1.0;
        snmpVersionComboBox = new JComboBox<>(new String[]{"v1", "v2c", "v3"});
        snmpVersionComboBox.setPreferredSize(new Dimension(80, 25));
        snmpVersionComboBox.addActionListener(e -> toggleSNMPv3Fields());
        panel.add(snmpVersionComboBox, gbc);

        // SNMP Community string (for v1 and v2c)
        gbc.gridx = 0; gbc.gridy = 1; gbc.gridwidth = 1; gbc.weightx = 0;
        panel.add(new JLabel(lang.get("preferences.snmp_community")), gbc);

        gbc.gridx = 1; gbc.gridwidth = 2; gbc.weightx = 1.0;
        snmpCommunityField = new JTextField(15);
        panel.add(snmpCommunityField, gbc);

        // SNMPv3 Username (visible only for v3)
        gbc.gridx = 0; gbc.gridy = 2; gbc.gridwidth = 1; gbc.weightx = 0;
        snmpV3UsernameLabel = new JLabel(lang.get("preferences.snmpv3_username"));
        panel.add(snmpV3UsernameLabel, gbc);

        gbc.gridx = 1; gbc.gridwidth = 2; gbc.weightx = 1.0;
        snmpV3UsernameField = new JTextField(15);
        panel.add(snmpV3UsernameField, gbc);

        // SNMPv3 Password (visible only for v3)
        gbc.gridx = 0; gbc.gridy = 3; gbc.gridwidth = 1; gbc.weightx = 0;
        snmpV3PasswordLabel = new JLabel(lang.get("preferences.snmpv3_password"));
        panel.add(snmpV3PasswordLabel, gbc);

        gbc.gridx = 1; gbc.gridwidth = 2; gbc.weightx = 1.0;
        snmpV3PasswordField = new JPasswordField(15);
        panel.add(snmpV3PasswordField, gbc);

        // SNMP Timeout
        gbc.gridx = 0; gbc.gridy = 4; gbc.gridwidth = 1; gbc.weightx = 0;
        panel.add(new JLabel(lang.get("preferences.snmp_timeout")), gbc);

        gbc.gridx = 1; gbc.weightx = 0;
        snmpTimeoutSpinner = new JSpinner(new SpinnerNumberModel(500, 250, 2000, 25));
        snmpTimeoutSpinner.setPreferredSize(new Dimension(80, 25));
        panel.add(snmpTimeoutSpinner, gbc);

        gbc.gridx = 2; gbc.weightx = 1.0;
        panel.add(new JLabel(lang.get("preferences.milliseconds")), gbc);

        // Auto-enrich checkbox
        gbc.gridx = 0; gbc.gridy = 5; gbc.gridwidth = 3; gbc.weightx = 1.0;
        gbc.insets = new Insets(10, 5, 5, 5);
        autoEnrichSnmpCheckbox = new JCheckBox(lang.get("preferences.auto_enrich_snmp"));
        panel.add(autoEnrichSnmpCheckbox, gbc);

        return panel;
    }

    /**
     * Creates the Protocol Configuration block.
     *
     * @return a JPanel containing the protocol port settings
     * @see PreferencesManager#getSshPort()
     * @see PreferencesManager#getTelnetPort()
     */
    private JPanel createProtocolBlock() {
        JPanel panel = new JPanel(new GridBagLayout());

        // Title border
        TitledBorder border = BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(),
                lang.get("preferences.block.protocols")
        );
        border.setTitleFont(new Font("Segoe UI", Font.PLAIN, 13));
        panel.setBorder(BorderFactory.createCompoundBorder(
                border,
                BorderFactory.createEmptyBorder(10, 10, 10, 10)
        ));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // SSH Port
        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0;
        panel.add(new JLabel(lang.get("preferences.ssh_port")), gbc);

        gbc.gridx = 1; gbc.weightx = 0;
        sshPortSpinner = new JSpinner(new SpinnerNumberModel(PreferencesManager.getSshPort(), 1, 65535, 1));
        sshPortSpinner.setPreferredSize(new Dimension(80, 25));
        panel.add(sshPortSpinner, gbc);

        gbc.gridx = 2; gbc.weightx = 1.0;
        panel.add(new JLabel(""), gbc);

        // Telnet Port
        gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0;
        panel.add(new JLabel(lang.get("preferences.telnet_port")), gbc);

        gbc.gridx = 1; gbc.weightx = 0;
        telnetPortSpinner = new JSpinner(new SpinnerNumberModel(PreferencesManager.getTelnetPort(), 1, 65535, 1));
        telnetPortSpinner.setPreferredSize(new Dimension(80, 25));
        panel.add(telnetPortSpinner, gbc);

        gbc.gridx = 2; gbc.weightx = 1.0;
        panel.add(new JLabel(""), gbc);

        return panel;
    }

    /**
     * Toggles visibility of SNMPv3 credential fields based on selected SNMP version.
     */
    private void toggleSNMPv3Fields() {
        String selectedVersion = (String) snmpVersionComboBox.getSelectedItem();
        boolean isV3 = "v3".equals(selectedVersion);

        snmpV3UsernameLabel.setVisible(isV3);
        snmpV3UsernameField.setVisible(isV3);
        snmpV3PasswordLabel.setVisible(isV3);
        snmpV3PasswordField.setVisible(isV3);

        // Disable community field for v3
        snmpCommunityField.setEnabled(!isV3);
    }

    /**
     * Creates the button panel at the bottom of the dialog.
     *
     * @return a JPanel containing the action buttons
     * @see #savePreferences()
     */
    private JPanel createButtonPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 10));

        JButton okButton = new JButton(lang.get("preferences.ok"));
        okButton.setPreferredSize(new Dimension(80, 30));
        okButton.addActionListener(e -> {
            wasApplied = true;
            savePreferences();
            dispose();
        });

        JButton cancelButton = new JButton(lang.get("preferences.cancel"));
        cancelButton.setPreferredSize(new Dimension(80, 30));
        cancelButton.addActionListener(e -> dispose());

        panel.add(okButton);
        panel.add(cancelButton);

        return panel;
    }

    /**
     * Loads current preference values into the UI components.
     * <p>
     * This method is called during dialog initialization to populate all
     * input fields with their current saved values from PreferencesManager.
     * </p>
     *
     * @see PreferencesManager
     */
    private void loadPreferences() {
        snmpCommunityField.setText(PreferencesManager.getSnmpCommunity());
        snmpTimeoutSpinner.setValue(PreferencesManager.getSnmpTimeout());
        discoveryTimeoutSpinner.setValue(PreferencesManager.getDiscoveryTimeout());
        discoverySendPortSpinner.setText(String.valueOf(PreferencesManager.getDiscoverySendPort()));
        discoveryListenPortSpinner.setText(String.valueOf(PreferencesManager.getDiscoveryListenPort()));
        autoEnrichSnmpCheckbox.setSelected(PreferencesManager.getAutoEnrichSnmp());

        // SNMP version
        snmpVersionComboBox.setSelectedItem(PreferencesManager.getSnmpVersion());

        // SNMPv3 credentials
        snmpV3UsernameField.setText(PreferencesManager.getSnmpV3Username());
        snmpV3PasswordField.setText(PreferencesManager.getSnmpV3Password());

        // Protocol ports
        sshPortSpinner.setValue(PreferencesManager.getSshPort());
        telnetPortSpinner.setValue(PreferencesManager.getTelnetPort());

        // Toggle v3 fields visibility
        toggleSNMPv3Fields();
    }

    /**
     * Saves all preference values from the UI components to persistent storage.
     * <p>
     * This method reads values from all input fields and spinners, then
     * persists them using PreferencesManager. The preferences are saved to
     * the system's user preference store and will be loaded on the next application startup.
     * </p>
     *
     * @see PreferencesManager
     */
    private void savePreferences() {
        PreferencesManager.saveSnmpCommunity(snmpCommunityField.getText());
        PreferencesManager.saveSnmpTimeout((Integer) snmpTimeoutSpinner.getValue());
        PreferencesManager.saveDiscoveryTimeout((Integer) discoveryTimeoutSpinner.getValue());
        PreferencesManager.saveDiscoverySendPort(Integer.parseInt(discoverySendPortSpinner.getText()));
        PreferencesManager.saveDiscoveryListenPort(Integer.parseInt(discoveryListenPortSpinner.getText()));
        PreferencesManager.saveAutoEnrichSnmp(autoEnrichSnmpCheckbox.isSelected());

        // SNMP version
        PreferencesManager.saveSnmpVersion((String) snmpVersionComboBox.getSelectedItem());

        // SNMPv3 credentials
        PreferencesManager.saveSnmpV3Username(snmpV3UsernameField.getText());
        PreferencesManager.saveSnmpV3Password(new String(snmpV3PasswordField.getPassword()));

        // Protocol ports
        PreferencesManager.saveSshPort((Integer) sshPortSpinner.getValue());
        PreferencesManager.saveTelnetPort((Integer) telnetPortSpinner.getValue());
    }

    /**
     * Returns whether the user applied changes before closing the dialog.
     * <p>
     * This method can be used by the caller to determine if any actions
     * need to be taken after the dialog closes, such as refreshing data
     * or notifying the user that changes were saved.
     * </p>
     *
     * @return true if the user clicked OK and applied changes, false otherwise
     */
    public boolean wasApplied() {
        return wasApplied;
    }
}