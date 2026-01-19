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

    // Custom password testing components
    private JTextField customUsernameField;
    private JTextField customPasswordField;

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
        setSize(550, 650);
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
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);

        add(scrollPane, BorderLayout.CENTER);
        add(createButtonPanel(), BorderLayout.SOUTH);
    }

    /**
     * Creates the Discovery Configuration block.
     *
     * @return a JPanel containing the discovery timeout and port settings
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

        // Discovery timeout
        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0;
        panel.add(new JLabel(lang.get("preferences.discovery_timeout")), gbc);

        gbc.gridx = 1; gbc.weightx = 0;
        discoveryTimeoutSpinner = new JSpinner(new SpinnerNumberModel(PreferencesManager.getDiscoveryTimeout(), 1, 30, 1));
        discoveryTimeoutSpinner.setPreferredSize(new Dimension(80, 25));
        panel.add(discoveryTimeoutSpinner, gbc);

        gbc.gridx = 2; gbc.weightx = 1.0;
        panel.add(new JLabel(lang.get("preferences.seconds")), gbc);

        // Send port
        gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0;
        panel.add(new JLabel(lang.get("preferences.discovery_send_port")), gbc);

        gbc.gridx = 1; gbc.weightx = 0;
        discoverySendPortSpinner = new JTextField(String.valueOf(PreferencesManager.getDiscoverySendPort()));
        discoverySendPortSpinner.setPreferredSize(new Dimension(80, 25));
        panel.add(discoverySendPortSpinner, gbc);

        gbc.gridx = 2; gbc.weightx = 1.0;
        panel.add(new JLabel(""), gbc);

        // Listen port
        gbc.gridx = 0; gbc.gridy = 2; gbc.weightx = 0;
        panel.add(new JLabel(lang.get("preferences.discovery_listen_port")), gbc);

        gbc.gridx = 1; gbc.weightx = 0;
        discoveryListenPortSpinner = new JTextField(String.valueOf(PreferencesManager.getDiscoveryListenPort()));
        discoveryListenPortSpinner.setPreferredSize(new Dimension(80, 25));
        panel.add(discoveryListenPortSpinner, gbc);

        gbc.gridx = 2; gbc.weightx = 1.0;
        panel.add(new JLabel(""), gbc);

        return panel;
    }

    /**
     * Creates the SNMP Configuration block.
     *
     * @return a JPanel containing SNMP community, timeout, version, and credential settings
     * @see PreferencesManager#getSnmpCommunity()
     * @see PreferencesManager#getSnmpTimeout()
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

        // SNMP version
        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0;
        panel.add(new JLabel(lang.get("preferences.snmp_version")), gbc);

        gbc.gridx = 1; gbc.weightx = 0;
        snmpVersionComboBox = new JComboBox<>(new String[]{"v1", "v2c", "v3"});
        snmpVersionComboBox.setPreferredSize(new Dimension(100, 25));
        snmpVersionComboBox.addActionListener(e -> toggleSNMPv3Fields());
        panel.add(snmpVersionComboBox, gbc);

        gbc.gridx = 2; gbc.weightx = 1.0;
        panel.add(new JLabel(""), gbc);

        // SNMP community
        gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0;
        panel.add(new JLabel(lang.get("preferences.snmp_community")), gbc);

        gbc.gridx = 1; gbc.gridwidth = 2; gbc.weightx = 1.0;
        snmpCommunityField = new JTextField(PreferencesManager.getSnmpCommunity());
        snmpCommunityField.setPreferredSize(new Dimension(200, 25));
        panel.add(snmpCommunityField, gbc);

        // SNMP timeout
        gbc.gridx = 0; gbc.gridy = 2; gbc.gridwidth = 1; gbc.weightx = 0;
        panel.add(new JLabel(lang.get("preferences.snmp_timeout")), gbc);

        gbc.gridx = 1; gbc.weightx = 0;
        snmpTimeoutSpinner = new JSpinner(new SpinnerNumberModel(PreferencesManager.getSnmpTimeout(), 250, 10000, 250));
        snmpTimeoutSpinner.setPreferredSize(new Dimension(80, 25));
        panel.add(snmpTimeoutSpinner, gbc);

        gbc.gridx = 2; gbc.weightx = 1.0;
        panel.add(new JLabel(lang.get("preferences.milliseconds")), gbc);

        // SNMPv3 username (hidden by default)
        gbc.gridx = 0; gbc.gridy = 3; gbc.weightx = 0;
        snmpV3UsernameLabel = new JLabel(lang.get("preferences.snmpv3_username"));
        snmpV3UsernameLabel.setVisible(false);
        panel.add(snmpV3UsernameLabel, gbc);

        gbc.gridx = 1; gbc.gridwidth = 2; gbc.weightx = 1.0;
        snmpV3UsernameField = new JTextField();
        snmpV3UsernameField.setPreferredSize(new Dimension(200, 25));
        snmpV3UsernameField.setVisible(false);
        panel.add(snmpV3UsernameField, gbc);

        // SNMPv3 password (hidden by default)
        gbc.gridx = 0; gbc.gridy = 4; gbc.gridwidth = 1; gbc.weightx = 0;
        snmpV3PasswordLabel = new JLabel(lang.get("preferences.snmpv3_password"));
        snmpV3PasswordLabel.setVisible(false);
        panel.add(snmpV3PasswordLabel, gbc);

        gbc.gridx = 1; gbc.gridwidth = 2; gbc.weightx = 1.0;
        snmpV3PasswordField = new JPasswordField();
        snmpV3PasswordField.setPreferredSize(new Dimension(200, 25));
        snmpV3PasswordField.setVisible(false);
        panel.add(snmpV3PasswordField, gbc);

        // Auto-enrich checkbox
        gbc.gridx = 0; gbc.gridy = 5; gbc.gridwidth = 3; gbc.weightx = 1.0;
        gbc.insets = new Insets(10, 5, 5, 5);
        autoEnrichSnmpCheckbox = new JCheckBox(lang.get("preferences.auto_enrich_snmp"));
        panel.add(autoEnrichSnmpCheckbox, gbc);

        return panel;
    }

    /**
     * Creates the Protocol Configuration block.
     * <p>
     * This block contains SSH and Telnet port settings, as well as
     * custom password management for credential testing.
     * </p>
     *
     * @return a JPanel containing the protocol port settings and custom password management
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

        // Separator
        gbc.gridx = 0; gbc.gridy = 2; gbc.gridwidth = 3;
        gbc.insets = new Insets(15, 5, 10, 5);
        JSeparator separator = new JSeparator(SwingConstants.HORIZONTAL);
        panel.add(separator, gbc);

        // Custom credentials label
        gbc.gridx = 0; gbc.gridy = 3; gbc.gridwidth = 3;
        gbc.insets = new Insets(5, 5, 5, 5);
        JLabel customCredentialsLabel = new JLabel(lang.get("preferences.title_pwd"));
        panel.add(customCredentialsLabel, gbc);

        // Custom username input field
        gbc.gridx = 0; gbc.gridy = 4; gbc.gridwidth = 1; gbc.weightx = 1.0;
        customUsernameField = new JTextField();
        customUsernameField.setPreferredSize(new Dimension(250, 25));
        customUsernameField.setToolTipText(lang.get("preferences.username_tooltip"));
        panel.add(customUsernameField, gbc);

        // Custom password input field
        gbc.gridx = 0; gbc.gridy = 5; gbc.gridwidth = 1; gbc.weightx = 1.0;
        customPasswordField = new JTextField();
        customPasswordField.setPreferredSize(new Dimension(250, 25));
        customPasswordField.setToolTipText(lang.get("preferences.pwd_tooltip"));
        panel.add(customPasswordField, gbc);

        // Add button
        gbc.gridx = 1; gbc.gridy = 5; gbc.weightx = 0;
        JButton addCustomAccountButton = new JButton(lang.get("button.add"));
        addCustomAccountButton.setPreferredSize(new Dimension(70, 25));
        addCustomAccountButton.addActionListener(e -> addCustomAccount());
        panel.add(addCustomAccountButton, gbc);

        // View button
        gbc.gridx = 2; gbc.gridy = 5; gbc.weightx = 0;
        JButton viewPasswordsButton = new JButton(lang.get("button.view"));
        viewPasswordsButton.setPreferredSize(new Dimension(70, 25));
        viewPasswordsButton.addActionListener(e -> viewCustomPasswords());
        panel.add(viewPasswordsButton, gbc);

        return panel;
    }

    /**
     * Adds the entered custom credentials to the internal credential list.
     * <p>
     * This method retrieves the username and password from the input fields,
     * validates that the password is not empty, adds the credentials to the
     * custom password list, and clears both input fields for the next entry.
     * Duplicate credential pairs are prevented from being added.
     * </p>
     */
    private void addCustomAccount() {
        String username = customUsernameField.getText().trim();
        String password = customPasswordField.getText().trim();

        if (password.isEmpty()) {
            JOptionPane.showMessageDialog(
                    this,
                    lang.get("preferences.empty_password_message"),
                    lang.get("preferences.empty_password_title"),
                    JOptionPane.WARNING_MESSAGE
            );
            return;
        }

        // Create credential string in format "username:password"
        String credential = username.isEmpty() ? password : username + ":" + password;

        if (PreferencesManager.getCustomAccountMap().contains(credential)) {
            JOptionPane.showMessageDialog(
                    this,
                    lang.get("preferences.wrong_account_message"),
                    lang.get("preferences.wrong_account_title"),
                    JOptionPane.WARNING_MESSAGE
            );
            return;
        }

        PreferencesManager.getCustomAccountMap().add(credential);
        customUsernameField.setText("");
        customPasswordField.setText("");
    }

    /**
     * Displays the list of custom credentials in a formatted dialog.
     * <p>
     * This method creates a message dialog showing all custom credentials
     * currently in the list. If the list is empty, an appropriate message
     * is displayed. Credentials are presented in a bullet-point format for
     * easy readability, showing either "username:password" or just "password"
     * if no username was provided.
     * </p>
     */
    private void viewCustomPasswords() {
        if (PreferencesManager.getCustomAccountMap().isEmpty()) {
            JOptionPane.showMessageDialog(
                    this,
                    lang.get("preferences.error_accountlist_empty_message"),
                    lang.get("preferences.error_accountlist_empty_title"),
                    JOptionPane.INFORMATION_MESSAGE
            );
            return;
        }

        StringBuilder message = new StringBuilder(lang.get("preferences.account_list") + "\n \n");
        for (String credential : PreferencesManager.getCustomAccountMap()) {
            message.append(credential).append("\n");
        }

        JOptionPane.showMessageDialog(
                this,
                message.toString(),
                lang.get("preferences.account_view_title"),
                JOptionPane.INFORMATION_MESSAGE
        );
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

        // Revalidate and repaint to update layout
        revalidate();
        repaint();
    }

    /**
     * Creates the bottom button panel with OK and Cancel buttons.
     *
     * @return a JPanel containing the action buttons
     */
    private JPanel createButtonPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 10));

        JButton okButton = new JButton(lang.get("preferences.ok"));
        JButton cancelButton = new JButton(lang.get("preferences.cancel"));

        okButton.setPreferredSize(new Dimension(100, 30));
        cancelButton.setPreferredSize(new Dimension(100, 30));

        okButton.addActionListener(e -> {
            savePreferences();
            wasApplied = true;
            dispose();
        });

        cancelButton.addActionListener(e -> {
            wasApplied = false;
            dispose();
        });

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