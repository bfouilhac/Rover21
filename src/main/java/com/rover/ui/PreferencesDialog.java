package com.rover.ui;

import com.rover.utils.LanguageManager;
import com.rover.utils.PreferencesManager;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;

public class PreferencesDialog extends JDialog {

    private final LanguageManager lang = LanguageManager.getInstance();

    // UI Components
    private JTextField snmpCommunityField;
    private JSpinner snmpTimeoutSpinner;
    private JSpinner discoveryTimeoutSpinner;
    private JCheckBox autoEnrichSnmpCheckbox;

    private boolean wasApplied = false;

    public PreferencesDialog(JFrame parent) {
        super(parent, "", true);
        setTitle(lang.get("preferences.title"));
        setSize(500, 450);
        setLocationRelativeTo(parent);
        setResizable(false);

        initComponents();
        loadPreferences();
    }

    private void initComponents() {
        setLayout(new BorderLayout(10, 10));

        // Main panel with vertical layout
        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        // Add blocks
        mainPanel.add(createDiscoveryBlock());
        mainPanel.add(Box.createVerticalStrut(15));
        mainPanel.add(createSnmpBlock());

        // Add to scroll pane (for future expansion)
        JScrollPane scrollPane = new JScrollPane(mainPanel);
        scrollPane.setBorder(null);
        add(scrollPane, BorderLayout.CENTER);

        // Bottom buttons
        add(createButtonPanel(), BorderLayout.SOUTH);
    }

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
        discoveryTimeoutSpinner = new JSpinner(new SpinnerNumberModel(PreferencesManager.getDiscoveryTimeout(), 1000, 60000, 100));
        discoveryTimeoutSpinner.setPreferredSize(new Dimension(80, 25));
        panel.add(discoveryTimeoutSpinner, gbc);

        gbc.gridx = 2; gbc.weightx = 1.0;
        panel.add(new JLabel(lang.get("preferences.milliseconds")), gbc);

        return panel;
    }

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

        // SNMP Community
        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0;
        panel.add(new JLabel(lang.get("preferences.snmp_community")), gbc);

        gbc.gridx = 1; gbc.gridwidth = 2; gbc.weightx = 1.0;
        snmpCommunityField = new JTextField(15);
        panel.add(snmpCommunityField, gbc);

        // SNMP Timeout
        gbc.gridx = 0; gbc.gridy = 1; gbc.gridwidth = 1; gbc.weightx = 0;
        panel.add(new JLabel(lang.get("preferences.snmp_timeout")), gbc);

        gbc.gridx = 1; gbc.weightx = 0;
        snmpTimeoutSpinner = new JSpinner(new SpinnerNumberModel(500, 250, 2000, 25));
        snmpTimeoutSpinner.setPreferredSize(new Dimension(80, 25));
        panel.add(snmpTimeoutSpinner, gbc);

        gbc.gridx = 2; gbc.weightx = 1.0;
        panel.add(new JLabel(lang.get("preferences.milliseconds")), gbc);

        // Auto-enrich with SNMP
        gbc.gridx = 0; gbc.gridy = 2; gbc.gridwidth = 3; gbc.weightx = 1.0;
        gbc.insets = new Insets(10, 5, 5, 5);
        autoEnrichSnmpCheckbox = new JCheckBox(lang.get("preferences.auto_enrich_snmp"));
        panel.add(autoEnrichSnmpCheckbox, gbc);

        return panel;
    }

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

    private void loadPreferences() {
        discoveryTimeoutSpinner.setValue(PreferencesManager.getDiscoveryTimeout());
        snmpCommunityField.setText(PreferencesManager.getSnmpCommunity());
        snmpTimeoutSpinner.setValue(PreferencesManager.getSnmpTimeout());
        autoEnrichSnmpCheckbox.setSelected(PreferencesManager.getAutoEnrichSnmp());
    }

    private void savePreferences() {
        PreferencesManager.saveDiscoveryTimeout((Integer) discoveryTimeoutSpinner.getValue());
        PreferencesManager.saveSnmpCommunity(snmpCommunityField.getText());
        PreferencesManager.saveSnmpTimeout((Integer) snmpTimeoutSpinner.getValue());
        PreferencesManager.saveAutoEnrichSnmp(autoEnrichSnmpCheckbox.isSelected());
    }

    public boolean wasApplied() {
        return wasApplied;
    }
}