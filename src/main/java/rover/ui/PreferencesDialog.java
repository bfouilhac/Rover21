package rover.ui;

import rover.utils.LanguageManager;
import rover.utils.PreferencesManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
 * @author Benjamin FOUILHAC, ADM21 - January/February 2026
 * @see PreferencesManager
 * @see LanguageManager
 */
public class PreferencesDialog extends JDialog {

    private static final Logger logger = LoggerFactory.getLogger(PreferencesDialog.class);

    private static final Font FONT_TITLE = new Font("Segoe UI", Font.PLAIN, 13);
    private static final int DIALOG_WIDTH = 550;
    private static final int DIALOG_HEIGHT = 650;
    private static final int FIELD_HEIGHT = 25;
    private static final int SPINNER_WIDTH = 80;
    private static final int TEXT_FIELD_WIDTH = 200;
    private static final int BUTTON_WIDTH = 100;
    private static final int BUTTON_HEIGHT = 30;
    private static final int SMALL_BUTTON_WIDTH = 70;
    private static final int COMPONENT_SPACING = 5;
    private static final int BLOCK_SPACING = 15;
    private static final int PANEL_PADDING = 15;

    private static final int MIN_DISCOVERY_TIMEOUT = 1;
    private static final int MAX_DISCOVERY_TIMEOUT = 30;
    private static final int MIN_SNMP_TIMEOUT = 250;
    private static final int MAX_SNMP_TIMEOUT = 10000;
    private static final int SNMP_TIMEOUT_STEP = 250;
    private static final int MIN_PORT = 1;
    private static final int MAX_PORT = 65535;

    private final transient LanguageManager lang = LanguageManager.getInstance();

    private JSpinner discoveryTimeoutSpinner;
    private JTextField discoverySendPortField;
    private JTextField discoveryListenPortField;

    private JTextField snmpCommunityField;
    private JSpinner snmpTimeoutSpinner;
    private JCheckBox autoEnrichSnmpCheckbox;
    private JComboBox<String> snmpVersionComboBox;
    private JTextField snmpV3UsernameField;
    private JPasswordField snmpV3PasswordField;
    private JLabel snmpV3UsernameLabel;
    private JLabel snmpV3PasswordLabel;

    private JSpinner sshPortSpinner;
    private JSpinner telnetPortSpinner;
    private JSpinner protocolTimeoutSpinner;

    private JTextField defaultConfigPathField;

    private JTextField customUsernameField;
    private JTextField customPasswordField;

    private boolean applied = false;

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
        setSize(DIALOG_WIDTH, DIALOG_HEIGHT);
        setLocationRelativeTo(parent);
        setResizable(false);

        initComponents();
        loadPreferences();

        logger.info("PreferencesDialog opened");
    }

    /**
     * Returns whether the user applied changes before closing the dialog.
     * <p>
     * The caller can use this method to determine if any actions
     * need to be taken after the dialog closes, such as refreshing data
     * or notifying the user that changes were saved.
     * </p>
     *
     * @return true if the user clicked OK and applied changes, false otherwise
     */
    public boolean wasApplied() {
        return applied;
    }

    /**
     * Initializes all UI components and layouts.
     * <p>
     * This method creates the main panel structure with vertically stacked
     * configuration blocks, each contained in a titled border. A scroll pane
     * is used to allow for future expansion with additional settings blocks.
     * </p>
     */
    private void initComponents() {
        setLayout(new BorderLayout(10, 10));

        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(PANEL_PADDING, PANEL_PADDING, PANEL_PADDING, PANEL_PADDING));

        mainPanel.add(createDiscoveryBlock());
        mainPanel.add(Box.createVerticalStrut(BLOCK_SPACING));
        mainPanel.add(createSnmpBlock());
        mainPanel.add(Box.createVerticalStrut(BLOCK_SPACING));
        mainPanel.add(createProtocolBlock());
        mainPanel.add(Box.createVerticalStrut(BLOCK_SPACING));
        mainPanel.add(createConfigFileBlock());

        JScrollPane scrollPane = new JScrollPane(mainPanel);
        scrollPane.setBorder(null);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);

        add(scrollPane, BorderLayout.CENTER);
        add(createButtonPanel(), BorderLayout.SOUTH);
    }

    /**
     * Creates the Discovery Configuration block.
     * <p>
     * This block contains settings for the discovery timeout and the UDP ports
     * used for sending and receiving discovery packets.
     * </p>
     *
     * @return a JPanel containing the discovery timeout and port settings
     */
    private JPanel createDiscoveryBlock() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(createTitledBorder(lang.get("preferences.block.discovery")));

        GridBagConstraints gbc = createDefaultConstraints();

        addDiscoveryTimeoutRow(panel, gbc);
        addDiscoverySendPortRow(panel, gbc);
        addDiscoveryListenPortRow(panel, gbc);

        return panel;
    }

    private void addDiscoveryTimeoutRow(JPanel panel, GridBagConstraints gbc) {
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 0;
        panel.add(new JLabel(lang.get("preferences.discovery_timeout")), gbc);

        gbc.gridx = 1;
        discoveryTimeoutSpinner = createSpinner(PreferencesManager.getDiscoveryTimeout(),
                MIN_DISCOVERY_TIMEOUT, MAX_DISCOVERY_TIMEOUT, 1);
        panel.add(discoveryTimeoutSpinner, gbc);

        gbc.gridx = 2;
        gbc.weightx = 1.0;
        panel.add(new JLabel(lang.get("preferences.seconds")), gbc);
    }

    private void addDiscoverySendPortRow(JPanel panel, GridBagConstraints gbc) {
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.weightx = 0;
        panel.add(new JLabel(lang.get("preferences.discovery_send_port")), gbc);

        gbc.gridx = 1;
        discoverySendPortField = createPortTextField(PreferencesManager.getDiscoverySendPort());
        panel.add(discoverySendPortField, gbc);

        gbc.gridx = 2;
        gbc.weightx = 1.0;
        panel.add(new JLabel(""), gbc);
    }

    private void addDiscoveryListenPortRow(JPanel panel, GridBagConstraints gbc) {
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.weightx = 0;
        panel.add(new JLabel(lang.get("preferences.discovery_listen_port")), gbc);

        gbc.gridx = 1;
        discoveryListenPortField = createPortTextField(PreferencesManager.getDiscoveryListenPort());
        panel.add(discoveryListenPortField, gbc);

        gbc.gridx = 2;
        gbc.weightx = 1.0;
        panel.add(new JLabel(""), gbc);
    }

    /**
     * Creates the SNMP Configuration block.
     * <p>
     * This block contains settings for SNMP version selection, community string,
     * timeout, SNMPv3 credentials (shown only when v3 is selected), and the
     * auto-enrich option.
     * </p>
     *
     * @return a JPanel containing SNMP community, timeout, version, and credential settings
     */
    private JPanel createSnmpBlock() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(createTitledBorder(lang.get("preferences.block.snmp")));

        GridBagConstraints gbc = createDefaultConstraints();

        addSnmpVersionRow(panel, gbc);
        addSnmpCommunityRow(panel, gbc);
        addSnmpTimeoutRow(panel, gbc);
        addSnmpV3UsernameRow(panel, gbc);
        addSnmpV3PasswordRow(panel, gbc);
        addAutoEnrichRow(panel, gbc);

        return panel;
    }

    private void addSnmpVersionRow(JPanel panel, GridBagConstraints gbc) {
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 0;
        panel.add(new JLabel(lang.get("preferences.snmp_version")), gbc);

        gbc.gridx = 1;
        snmpVersionComboBox = new JComboBox<>(new String[]{"v1", "v2c", "v3"});
        snmpVersionComboBox.setPreferredSize(new Dimension(100, FIELD_HEIGHT));
        snmpVersionComboBox.addActionListener(_ -> toggleSnmpV3Fields());
        panel.add(snmpVersionComboBox, gbc);

        gbc.gridx = 2;
        gbc.weightx = 1.0;
        panel.add(new JLabel(""), gbc);
    }

    private void addSnmpCommunityRow(JPanel panel, GridBagConstraints gbc) {
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.weightx = 0;
        gbc.gridwidth = 1;
        panel.add(new JLabel(lang.get("preferences.snmp_community")), gbc);

        gbc.gridx = 1;
        gbc.gridwidth = 2;
        gbc.weightx = 1.0;
        snmpCommunityField = new JTextField(PreferencesManager.getSnmpCommunity());
        snmpCommunityField.setPreferredSize(new Dimension(TEXT_FIELD_WIDTH, FIELD_HEIGHT));
        panel.add(snmpCommunityField, gbc);
    }

    private void addSnmpTimeoutRow(JPanel panel, GridBagConstraints gbc) {
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.gridwidth = 1;
        gbc.weightx = 0;
        panel.add(new JLabel(lang.get("preferences.snmp_timeout")), gbc);

        gbc.gridx = 1;
        snmpTimeoutSpinner = createSpinner(PreferencesManager.getSnmpTimeout(),
                MIN_SNMP_TIMEOUT, MAX_SNMP_TIMEOUT, SNMP_TIMEOUT_STEP);
        panel.add(snmpTimeoutSpinner, gbc);

        gbc.gridx = 2;
        gbc.weightx = 1.0;
        panel.add(new JLabel(lang.get("preferences.milliseconds")), gbc);
    }

    private void addSnmpV3UsernameRow(JPanel panel, GridBagConstraints gbc) {
        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.weightx = 0;
        gbc.gridwidth = 1;
        snmpV3UsernameLabel = new JLabel(lang.get("preferences.snmpv3_username"));
        snmpV3UsernameLabel.setVisible(false);
        panel.add(snmpV3UsernameLabel, gbc);

        gbc.gridx = 1;
        gbc.gridwidth = 2;
        gbc.weightx = 1.0;
        snmpV3UsernameField = new JTextField();
        snmpV3UsernameField.setPreferredSize(new Dimension(TEXT_FIELD_WIDTH, FIELD_HEIGHT));
        snmpV3UsernameField.setVisible(false);
        panel.add(snmpV3UsernameField, gbc);
    }

    private void addSnmpV3PasswordRow(JPanel panel, GridBagConstraints gbc) {
        gbc.gridx = 0;
        gbc.gridy = 4;
        gbc.gridwidth = 1;
        gbc.weightx = 0;
        snmpV3PasswordLabel = new JLabel(lang.get("preferences.snmpv3_password"));
        snmpV3PasswordLabel.setVisible(false);
        panel.add(snmpV3PasswordLabel, gbc);

        gbc.gridx = 1;
        gbc.gridwidth = 2;
        gbc.weightx = 1.0;
        snmpV3PasswordField = new JPasswordField();
        snmpV3PasswordField.setPreferredSize(new Dimension(TEXT_FIELD_WIDTH, FIELD_HEIGHT));
        snmpV3PasswordField.setVisible(false);
        panel.add(snmpV3PasswordField, gbc);
    }

    private void addAutoEnrichRow(JPanel panel, GridBagConstraints gbc) {
        gbc.gridx = 0;
        gbc.gridy = 5;
        gbc.gridwidth = 3;
        gbc.weightx = 1.0;
        gbc.insets = new Insets(10, COMPONENT_SPACING, COMPONENT_SPACING, COMPONENT_SPACING);
        autoEnrichSnmpCheckbox = new JCheckBox(lang.get("preferences.auto_enrich_snmp"));
        panel.add(autoEnrichSnmpCheckbox, gbc);
    }

    /**
     * Creates the Protocol Configuration block.
     * <p>
     * This block contains SSH and Telnet port settings, as well as
     * custom credential management for security testing.
     * </p>
     *
     * @return a JPanel containing the protocol port settings and custom credential management
     */
    private JPanel createProtocolBlock() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(createTitledBorder(lang.get("preferences.block.protocols")));

        GridBagConstraints gbc = createDefaultConstraints();

        addSshPortRow(panel, gbc);
        addTelnetPortRow(panel, gbc);
        addProtocolTimeoutRow(panel, gbc);
        addCredentialsSeparator(panel, gbc);
        addCredentialsLabel(panel, gbc);
        addCredentialsInputFields(panel, gbc);

        return panel;
    }

    private void addSshPortRow(JPanel panel, GridBagConstraints gbc) {
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 0;
        panel.add(new JLabel(lang.get("preferences.ssh_port")), gbc);

        gbc.gridx = 1;
        sshPortSpinner = createSpinner(PreferencesManager.getSshPort(), MIN_PORT, MAX_PORT, 1);
        panel.add(sshPortSpinner, gbc);

        gbc.gridx = 2;
        gbc.weightx = 1.0;
        panel.add(new JLabel(""), gbc);
    }

    private void addTelnetPortRow(JPanel panel, GridBagConstraints gbc) {
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.weightx = 0;
        panel.add(new JLabel(lang.get("preferences.telnet_port")), gbc);

        gbc.gridx = 1;
        telnetPortSpinner = createSpinner(PreferencesManager.getTelnetPort(), MIN_PORT, MAX_PORT, 1);
        panel.add(telnetPortSpinner, gbc);

        gbc.gridx = 2;
        gbc.weightx = 1.0;
        panel.add(new JLabel(""), gbc);
    }

    private void addProtocolTimeoutRow(JPanel panel, GridBagConstraints gbc) {
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.weightx = 0;
        panel.add(new JLabel(lang.get("preferences.protocol_timeout")), gbc);

        gbc.gridx = 1;
        protocolTimeoutSpinner = createSpinner(PreferencesManager.getProtocolTestTimeout(), 500, 10000, 100);
        panel.add(protocolTimeoutSpinner, gbc);

        gbc.gridx = 2;
        gbc.weightx = 1.0;
        panel.add(new JLabel("ms"), gbc);
    }

    private void addCredentialsSeparator(JPanel panel, GridBagConstraints gbc) {
        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.gridwidth = 3;
        gbc.insets = new Insets(BLOCK_SPACING, COMPONENT_SPACING, 10, COMPONENT_SPACING);
        panel.add(new JSeparator(SwingConstants.HORIZONTAL), gbc);
    }

    private void addCredentialsLabel(JPanel panel, GridBagConstraints gbc) {
        gbc.gridx = 0;
        gbc.gridy = 4;
        gbc.gridwidth = 3;
        gbc.insets = new Insets(COMPONENT_SPACING, COMPONENT_SPACING, COMPONENT_SPACING, COMPONENT_SPACING);
        panel.add(new JLabel(lang.get("preferences.title_pwd")), gbc);
    }

    private void addCredentialsInputFields(JPanel panel, GridBagConstraints gbc) {
        gbc.gridx = 0;
        gbc.gridy = 5;
        gbc.gridwidth = 1;
        gbc.weightx = 1.0;
        customUsernameField = new JTextField();
        customUsernameField.setPreferredSize(new Dimension(250, FIELD_HEIGHT));
        customUsernameField.setToolTipText(lang.get("preferences.username_tooltip"));
        panel.add(customUsernameField, gbc);

        gbc.gridy = 6;
        customPasswordField = new JTextField();
        customPasswordField.setPreferredSize(new Dimension(250, FIELD_HEIGHT));
        customPasswordField.setToolTipText(lang.get("preferences.pwd_tooltip"));
        panel.add(customPasswordField, gbc);

        gbc.gridx = 1;
        gbc.weightx = 0;
        JButton addButton = new JButton(lang.get("button.add"));
        addButton.setPreferredSize(new Dimension(SMALL_BUTTON_WIDTH, FIELD_HEIGHT));
        addButton.addActionListener(_ -> addCustomAccount());
        panel.add(addButton, gbc);

        gbc.gridx = 2;
        JButton viewButton = new JButton(lang.get("button.view"));
        viewButton.setPreferredSize(new Dimension(SMALL_BUTTON_WIDTH, FIELD_HEIGHT));
        viewButton.addActionListener(_ -> viewCustomCredentials());
        panel.add(viewButton, gbc);
    }


    /**
     * Creates the Configuration File block.
     * <p>
     * This block contains settings for the default directory path where
     * configuration files are saved.
     * </p>
     *
     * @return a JPanel containing the config file path settings
     */
    private JPanel createConfigFileBlock() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(createTitledBorder(lang.get("preferences.block.config_file")));

        GridBagConstraints gbc = createDefaultConstraints();

        addConfigPathRow(panel, gbc);

        return panel;
    }

    private void addConfigPathRow(JPanel panel, GridBagConstraints gbc) {
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 0;
        panel.add(new JLabel(lang.get("preferences.config_path")), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        defaultConfigPathField = new JTextField();
        defaultConfigPathField.setPreferredSize(new Dimension(TEXT_FIELD_WIDTH, FIELD_HEIGHT));
        defaultConfigPathField.setEditable(false);
        panel.add(defaultConfigPathField, gbc);

        gbc.gridx = 2;
        gbc.weightx = 0;
        JButton browseButton = new JButton(lang.get("preferences.browse"));
        browseButton.setPreferredSize(new Dimension(BUTTON_WIDTH, FIELD_HEIGHT));
        browseButton.addActionListener(_ -> browseForConfigPath());
        panel.add(browseButton, gbc);
    }

    /**
     * Opens a directory chooser dialog to select the default config path.
     */
    private void browseForConfigPath() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setDialogTitle(lang.get("preferences.select_config_directory"));

        String currentPath = defaultConfigPathField.getText();
        if (!currentPath.isEmpty()) {
            chooser.setCurrentDirectory(new java.io.File(currentPath));
        }

        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            defaultConfigPathField.setText(chooser.getSelectedFile().getAbsolutePath());
        }
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

        okButton.setPreferredSize(new Dimension(BUTTON_WIDTH, BUTTON_HEIGHT));
        cancelButton.setPreferredSize(new Dimension(BUTTON_WIDTH, BUTTON_HEIGHT));

        okButton.addActionListener(_ -> {
            savePreferences();
            applied = true;
            logger.info("Preferences saved and dialog closed");
            dispose();
        });

        cancelButton.addActionListener(_ -> {
            applied = false;
            logger.info("Preferences dialog cancelled");
            dispose();
        });

        panel.add(okButton);
        panel.add(cancelButton);

        return panel;
    }

    /**
     * Creates a titled border with consistent styling.
     *
     * @param title the title to display in the border
     * @return a compound border with title and padding
     */
    private javax.swing.border.Border createTitledBorder(String title) {
        TitledBorder border = BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(), title);
        border.setTitleFont(FONT_TITLE);
        return BorderFactory.createCompoundBorder(border,
                BorderFactory.createEmptyBorder(10, 10, 10, 10));
    }

    /**
     * Creates default GridBagConstraints for consistent layout.
     *
     * @return configured GridBagConstraints
     */
    private GridBagConstraints createDefaultConstraints() {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(COMPONENT_SPACING, COMPONENT_SPACING, COMPONENT_SPACING, COMPONENT_SPACING);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        return gbc;
    }

    /**
     * Creates a spinner with consistent styling.
     *
     * @param value the initial value
     * @param minimum the minimum allowed value
     * @param maximum the maximum allowed value
     * @param step the step increment
     * @return a configured JSpinner
     */
    private JSpinner createSpinner(int value, int minimum, int maximum, int step) {
        JSpinner spinner = new JSpinner(new SpinnerNumberModel(value, minimum, maximum, step));
        spinner.setPreferredSize(new Dimension(SPINNER_WIDTH, FIELD_HEIGHT));
        return spinner;
    }

    /**
     * Creates a text field for port input with consistent styling.
     *
     * @param port the initial port value
     * @return a configured JTextField
     */
    private JTextField createPortTextField(int port) {
        JTextField field = new JTextField(String.valueOf(port));
        field.setPreferredSize(new Dimension(SPINNER_WIDTH, FIELD_HEIGHT));
        return field;
    }

    /**
     * Toggles visibility of SNMPv3 credential fields based on a selected SNMP version.
     */
    private void toggleSnmpV3Fields() {
        String selectedVersion = (String) snmpVersionComboBox.getSelectedItem();
        boolean isV3 = "v3".equals(selectedVersion);

        snmpV3UsernameLabel.setVisible(isV3);
        snmpV3UsernameField.setVisible(isV3);
        snmpV3PasswordLabel.setVisible(isV3);
        snmpV3PasswordField.setVisible(isV3);

        revalidate();
        repaint();
    }

    /**
     * Adds the entered custom credentials to the internal credential list.
     * <p>
     * This method retrieves the username and password from the input fields,
     * validates that the password is not empty, adds the credentials to the
     * custom credential list, and clears both input fields for the next entry.
     * Duplicate credential pairs are prevented from being added.
     * </p>
     */
    private void addCustomAccount() {
        String username = customUsernameField.getText().trim();
        String password = customPasswordField.getText().trim();

        if (password.isEmpty()) {
            showWarning(lang.get("preferences.empty_password_title"),
                    lang.get("preferences.empty_password_message"));
            return;
        }

        String credential = username.isEmpty() ? password : username + ":" + password;

        if (PreferencesManager.getCustomAccountMap().contains(credential)) {
            showWarning(lang.get("preferences.wrong_account_title"),
                    lang.get("preferences.wrong_account_message"));
            return;
        }

        PreferencesManager.getCustomAccountMap().add(credential);
        customUsernameField.setText("");
        customPasswordField.setText("");

        showInfo(lang.get("preferences.add_account_success_title"), lang.get("preferences.add_account_success_message"));
        logger.info("Custom credential added: {}", username.isEmpty() ? "[password only]" : username);
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
    private void viewCustomCredentials() {
        if (PreferencesManager.getCustomAccountMap().isEmpty()) {
            showInfo(lang.get("preferences.error_accountlist_empty_title"),
                    lang.get("preferences.error_accountlist_empty_message"));
            return;
        }

        StringBuilder message = new StringBuilder(lang.get("preferences.account_list") + "\n \n");
        for (String credential : PreferencesManager.getCustomAccountMap()) {
            message.append(credential).append("\n");
        }

        showInfo(lang.get("preferences.account_view_title"), message.toString());
    }

    /**
     * Displays a warning dialog.
     *
     * @param title the dialog title
     * @param message the warning message
     */
    private void showWarning(String title, String message) {
        JOptionPane.showMessageDialog(this, message, title, JOptionPane.WARNING_MESSAGE);
    }

    /**
     * Displays an information dialog.
     *
     * @param title the dialog title
     * @param message the information message
     */
    private void showInfo(String title, String message) {
        JOptionPane.showMessageDialog(this, message, title, JOptionPane.INFORMATION_MESSAGE);
    }

    /**
     * Loads current preference values into the UI components.
     * <p>
     * This method is called during dialog initialization to populate all
     * input fields with their current saved values from the PreferencesManager.
     * </p>
     */
    private void loadPreferences() {
        snmpCommunityField.setText(PreferencesManager.getSnmpCommunity());
        snmpTimeoutSpinner.setValue(PreferencesManager.getSnmpTimeout());
        discoveryTimeoutSpinner.setValue(PreferencesManager.getDiscoveryTimeout());
        discoverySendPortField.setText(String.valueOf(PreferencesManager.getDiscoverySendPort()));
        discoveryListenPortField.setText(String.valueOf(PreferencesManager.getDiscoveryListenPort()));
        autoEnrichSnmpCheckbox.setSelected(PreferencesManager.getAutoEnrichSnmp());

        snmpVersionComboBox.setSelectedItem(PreferencesManager.getSnmpVersion());
        snmpV3UsernameField.setText(PreferencesManager.getSnmpV3Username());
        snmpV3PasswordField.setText(PreferencesManager.getSnmpV3Password());

        sshPortSpinner.setValue(PreferencesManager.getSshPort());
        telnetPortSpinner.setValue(PreferencesManager.getTelnetPort());
        protocolTimeoutSpinner.setValue(PreferencesManager.getProtocolTestTimeout());

        defaultConfigPathField.setText(PreferencesManager.getDefaultConfigPath());

        toggleSnmpV3Fields();

        logger.debug("Preferences loaded into dialog");
    }

    /**
     * Saves all preference values from the UI components to persistent storage.
     * <p>
     * This method reads values from all input fields and spinners, then
     * persists them using PreferencesManager. The preferences are saved to
     * the system's user preference store and will be loaded on the next application startup.
     * </p>
     */
    private void savePreferences() {
        PreferencesManager.saveSnmpCommunity(snmpCommunityField.getText());
        PreferencesManager.saveSnmpTimeout((Integer) snmpTimeoutSpinner.getValue());
        PreferencesManager.saveDiscoveryTimeout((Integer) discoveryTimeoutSpinner.getValue());
        PreferencesManager.saveDiscoverySendPort(parsePort(discoverySendPortField.getText()));
        PreferencesManager.saveDiscoveryListenPort(parsePort(discoveryListenPortField.getText()));
        PreferencesManager.saveAutoEnrichSnmp(autoEnrichSnmpCheckbox.isSelected());

        PreferencesManager.saveSnmpVersion((String) snmpVersionComboBox.getSelectedItem());
        PreferencesManager.saveSnmpV3Username(snmpV3UsernameField.getText());
        PreferencesManager.saveSnmpV3Password(new String(snmpV3PasswordField.getPassword()));

        PreferencesManager.saveSshPort((Integer) sshPortSpinner.getValue());
        PreferencesManager.saveTelnetPort((Integer) telnetPortSpinner.getValue());
        PreferencesManager.saveProtocolTestTimeout((Integer) protocolTimeoutSpinner.getValue());

        PreferencesManager.saveDefaultConfigPath(defaultConfigPathField.getText());

        logger.info("Preferences saved successfully");
    }

    /**
     * Parses a port number from a string, with validation.
     *
     * @param text the text to parse
     * @return the parsed port number, or a default if parsing fails
     */
    private int parsePort(String text) {
        try {
            int port = Integer.parseInt(text.trim());
            if (port >= MIN_PORT && port <= MAX_PORT) {
                return port;
            }
        } catch (NumberFormatException e) {
            logger.warn("Invalid port number: {}", text);
        }
        return MIN_PORT;
    }
}