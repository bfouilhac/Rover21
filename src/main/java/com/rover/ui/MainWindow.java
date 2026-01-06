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
import java.util.Arrays;
import java.util.List;

/**
 * Fenêtre principale (et unique) de Rover21
 * Interfaçage avec Java Swing + FlatLaf
 *
 * Chemin: src/main/java/com/rover/ui/MainWindow.java
 */
public class MainWindow extends JFrame {

    // Police moderne pour toute l'application utilisant la police Segoe UI
    private static final Font MODERN_FONT = new Font("Segoe UI", Font.PLAIN, 13);
    private static final Font MODERN_FONT_BOLD = new Font("Segoe UI", Font.PLAIN, 13);
    private static final Font TABLE_FONT = new Font("Segoe UI", Font.PLAIN, 12);
    private static final Font HEADER_FONT = new Font("Segoe UI", Font.PLAIN, 12);
    private static final Logger logger = LoggerFactory.getLogger(MainWindow.class);

    // Composants graphiques
    private JComboBox<NetworkInterfaceCustom> adapterComboBox;
    private JButton discoverButton;
    private JProgressBar progressBar;
    private JLabel statusLabel;
    private JLabel deviceCountLabel;
    private JTable devicesTable;
    private DefaultTableModel tableModel;

    // Boutons d'action
    private JButton updateDeviceBtn;
    private JButton updateMultiBtn;
    private JButton updateAllBtn;
    private JButton connectBtn;

    // Liste des appareils / interfaces réseau
    private List<PlanetSwitchDevice> discoveredDevices;
    private List<NetworkInterfaceCustom> availableAdapters;

    // Listener pour les événements UI (sera le Controller)
    private UIEventListener eventListener;

    /**
     * Interface pour les callbacks vers le Controller
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
     * Constructeur de la page principale : MainWindow
     */
    public MainWindow() {
        this.discoveredDevices = new ArrayList<>();
        this.availableAdapters = new ArrayList<>();

        // Appliquer la police moderne à tous les composants UI Manager
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
     * Applique une police personnalisée à tous les composants Swing
     */
    private void setUIFont() {
        UIManager.put("Button.font", MainWindow.MODERN_FONT);
        UIManager.put("Label.font", MainWindow.MODERN_FONT);
        UIManager.put("ComboBox.font", MainWindow.MODERN_FONT);
        UIManager.put("Table.font", TABLE_FONT);
        UIManager.put("TableHeader.font", HEADER_FONT);
        UIManager.put("Menu.font", MainWindow.MODERN_FONT);
        UIManager.put("MenuItem.font", MainWindow.MODERN_FONT);
        UIManager.put("CheckBoxMenuItem.font", MainWindow.MODERN_FONT);
    }

    /**
     * Définit le listener pour les événements UI
     */
    public void setEventListener(UIEventListener listener) {
        this.eventListener = listener;
    }

    /**
     * Initialise la fenêtre principale.
     * Configure la taille et le comportement sur fermeture
     */
    private void initializeFrame() {
        setTitle("Rover21 - PLANET Switch Discovery");
        setSize(1200, 700);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());
    }

    /**
     * Crée la barre de menu et tous les éléments graphiques qui le compose : "File, "Edit", "View", "Help"
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

        // Ajouter tous les menus
        menuBar.add(fileMenu);
        menuBar.add(editMenu);
        menuBar.add(viewMenu);
        menuBar.add(helpMenu);

        setJMenuBar(menuBar);
    }

    /**
     * Crée la toolbar avec les contrôles principaux
     */
    private void createToolbar() {
        JPanel toolbar = new JPanel();
        toolbar.setLayout(new BorderLayout());
        toolbar.setBorder(new EmptyBorder(12, 15, 12, 15));

        // Panel gauche avec le label et le combobox
        JPanel leftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 0));

        JLabel adapterLabel = new JLabel("Network Interface :");
        adapterLabel.setFont(MODERN_FONT_BOLD);

        adapterComboBox = new JComboBox<>();
        adapterComboBox.setFont(MODERN_FONT);
        adapterComboBox.setPreferredSize(new Dimension(400, 30));
        adapterComboBox.setToolTipText("Select the network adapter to scan");
        adapterComboBox.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value,
                                                          int index, boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                setFont(MODERN_FONT);
                if (value instanceof NetworkInterfaceCustom) {
                    NetworkInterfaceCustom adapter = (NetworkInterfaceCustom) value;
                    setText(adapter.toDisplayString());
                }
                return this;
            }
        });

        leftPanel.add(adapterLabel);
        leftPanel.add(adapterComboBox);

        // Panel droit avec progress bar et bouton discover
        JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));

        progressBar = new JProgressBar();
        progressBar.setIndeterminate(true);
        progressBar.setPreferredSize(new Dimension(25, 25));
        progressBar.setVisible(false);

        discoverButton = new JButton("Discover");
        discoverButton.setFont(MODERN_FONT_BOLD);
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
     * Crée le panneau central avec le tableau
     */
    private void createCenterPanel() {
        JPanel centerPanel = new JPanel(new BorderLayout(0, 10));
        centerPanel.setBorder(new EmptyBorder(15, 15, 15, 15));

        // Barre de statut en haut
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
        deviceCountLabel.setFont(MODERN_FONT_BOLD);
        deviceCountLabel.setForeground(new Color(25, 118, 210));

        statusBar.add(statusLabel, BorderLayout.WEST);
        statusBar.add(deviceCountLabel, BorderLayout.EAST);

        // Tableau des devices
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

        devicesTable.setShowGrid(true); // Permet d'afficher les lignes entre les cellules (meilleure visibilité)
        devicesTable.setAutoCreateRowSorter(true); // Permet de trier les éléments à partir des colonnes (header)
        devicesTable.setGridColor(new Color(220, 220, 220));
        devicesTable.setIntercellSpacing(new Dimension(1, 1));

        DefaultTableCellRenderer centerRenderer = new DefaultTableCellRenderer();
        centerRenderer.setHorizontalAlignment(SwingConstants.CENTER);
        centerRenderer.setFont(TABLE_FONT);

        for (int i = 0; i < devicesTable.getColumnCount(); i++) {
            devicesTable.getColumnModel().getColumn(i).setCellRenderer(centerRenderer); // Appliquer le renderer centré à toutes les colonnes
        }

        // Style du header
        JTableHeader header = devicesTable.getTableHeader();
        header.setFont(HEADER_FONT);
        header.setBackground(new Color(245, 245, 245));
        header.setForeground(new Color(60, 60, 60));

        // Largeurs de colonnes préférées
        devicesTable.getColumnModel().getColumn(0).setPreferredWidth(140);
        devicesTable.getColumnModel().getColumn(1).setPreferredWidth(180);
        devicesTable.getColumnModel().getColumn(2).setPreferredWidth(120);
        devicesTable.getColumnModel().getColumn(3).setPreferredWidth(120);
        devicesTable.getColumnModel().getColumn(4).setPreferredWidth(120);
        devicesTable.getColumnModel().getColumn(5).setPreferredWidth(120);
        devicesTable.getColumnModel().getColumn(6).setPreferredWidth(120);
        devicesTable.getColumnModel().getColumn(7).setPreferredWidth(200);

        // Activer/désactiver les boutons selon la sélection
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
     * Crée le panneau du bas avec les boutons d'action
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
     * Charge la liste des adaptateurs réseau dans la ComboBox
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
     * Affiche les devices découverts dans le tableau
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
     * Met à jour le message de statut
     */
    public void setStatusMessage(String message) {
        statusLabel.setText(message);
    }

    /**
     * Active/désactive le mode scanning
     */
    public void setScanningMode(boolean scanning) {
        discoverButton.setEnabled(!scanning);
        progressBar.setVisible(scanning);

        if (scanning) {
            statusLabel.setText("Scanning network...");
        }
    }

    /**
     * Affiche un message d'erreur quelconque
     * @param title le titre de l'erreur, en clair
     * @param message le corps du message d'erreur, doit être clair et compréhensible = doit permettre l'identification du problème / source rapidement
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
     * Affiche un message d'information quelconque
     * @param title le titre du message, en clair
     * @param message le corps du message d'information, doit être clair et compréhensible (pas de log d'erreur ...)
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
     * Efface les résultats du tableau, remet l'utilitaire en mode "Ready"
     */
    public void clearResults() {
        discoveredDevices.clear();
        tableModel.setRowCount(0);
        updateDeviceCount();
        updateButtonStates();
        statusLabel.setText("Ready");
    }

    // Contient les différents handlers pour trigger et gérer efficacement les interactions avec les boutons et autres éléments
    private void handleDiscoverClicked() {
        NetworkInterfaceCustom selectedAdapter = (NetworkInterfaceCustom) adapterComboBox.getSelectedItem();
        System.out.println("Selected adapter: " + selectedAdapter);
        if (selectedAdapter == null) {
            showError("No Adapter Selected", "Please select a network adapter before discovering.");
            return;
        }

        if (eventListener != null) {
            eventListener.onDiscoverClicked(selectedAdapter);
        }
    }

    /**
     * Permet de gérer l'intéraction avec le bouton "Update Device"
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
     * Permet de gérer l'intéraction avec le bouton "Update Multi"
     */
    private void handleUpdateMulti() {
        List<PlanetSwitchDevice> selectedDevices = getSelectedDevices();
        if (!selectedDevices.isEmpty() && eventListener != null) {
            eventListener.onUpdateMultiClicked(selectedDevices);
        }
    }

    /**
     * Permet de gérer l'intéraction avec le bouton "Update All"
     */
    private void handleUpdateAll() {
        if (!discoveredDevices.isEmpty() && eventListener != null) {
            eventListener.onUpdateAllClicked(new ArrayList<>(discoveredDevices));
        }
    }

    /**
     * Permet de gérer l'intéraction avec le bouton "Connect to Device"
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
     * Permet de détecter et de rediriger l'export des données trouvés vers un CSV
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
     * Méthode trigger qui permet de nettoyer le tableau contenant les informations des switchs trouvés
     */
    private void handleClearResults() {
        if (eventListener != null) {
            eventListener.onClearResultsClicked();
        } else {
            clearResults();
        }
    }

    /**
     * Permet de récupérer les données des colonnes sélectionnées
     * @return la liste des objets des lignes sélectionnées
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
        showInfo("Preferences", "Preferences panel coming soon!");
    }

    private void showDocumentation() {
        showInfo("Documentation", "Documentation coming soon!");
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