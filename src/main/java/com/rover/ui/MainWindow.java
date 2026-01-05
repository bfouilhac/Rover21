package com.rover.ui;

import com.rover.controller.MainController;
import com.rover.model.NetworkInterfaceCustom;
import com.rover.model.PlanetSwitchDevice;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Fenêtre principale (et unique) de Rover21
 * Interfaçage avec Java Swing + FlatLaf
 */
public class MainWindow extends JFrame {

    // Composants UI
    private JComboBox<NetworkInterfaceCustom> adapterComboBox; // Combo box qui contient la liste des interfaces réseau découvertes
    private JButton discoverButton; // Bouton qui enclenche la découverte des appareils sur le réseau de l'interface choisie
    private JLabel statusLabel; // Statut en cours qui permet de débugger / suivre l'avancée des opérations
    private JLabel deviceCountLabel; // Petit compteur pour savoir rapidement combien d'appareils ont été trouvés
    private JTable devicesTable; // Tableau permettant de voir toutes les informations récupérées des switchs trouvés
    private DefaultTableModel tableModel; // Le modèle de tableau

    // Boutons d'action
    private JButton updateDeviceBtn;
    private JButton updateMultiBtn;
    private JButton updateAllBtn;
    private JButton connectBtn;

    // Données
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
     * Constructeur de la page principale : MainWindow.
     * Crée une liste d'appareils et d'interfaces réseau.
     * Initialise et crée les éléments visuels à travers les fonctions associées.
     */
    public MainWindow() {
        this.discoveredDevices = new ArrayList<>();
        this.availableAdapters = new ArrayList<>();

        initializeFrame();
        createMenuBar();
        createToolbar();
        createCenterPanel();
        createBottomPanel();

        // Centrer la fenêtre
        setLocationRelativeTo(null);
    }

    /**
     * Définit le listener pour les événements UI (généralement le Controller)
     */
    public void setEventListener(UIEventListener listener) {
        this.eventListener = listener;
    }

    /**
     * Initialise la fenêtre principale
     * Limite la taille minimale de la fenêtre à 1000x600 pixels
     * Configure le comportement sur la fermeture de la page : EXIT_ON_CLOSE
     */
    private void initializeFrame() {
        setTitle("Rover21 - PLANET Switch Discovery");
        setSize(1200, 700);
        setMinimumSize(new Dimension(1000, 600));
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());
    }

    /**
     * Crée la barre de menu
     */
    private void createMenuBar() {
        JMenuBar menuBar = new JMenuBar();

        // Menu File
        JMenu fileMenu = new JMenu("File");
        JMenuItem exportItem = new JMenuItem("Export to CSV");
        exportItem.addActionListener(e -> handleExport());
        JMenuItem quitItem = new JMenuItem("Quit");
        quitItem.addActionListener(e -> System.exit(0));

        fileMenu.add(exportItem);
        fileMenu.addSeparator();
        fileMenu.add(quitItem);

        // Menu Edit
        JMenu editMenu = new JMenu("Edit");
        JMenuItem preferencesItem = new JMenuItem("Preferences");
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

        JLabel adapterLabel = new JLabel("Network Interface : ");
        adapterLabel.setFont(adapterLabel.getFont().deriveFont(13f));

        adapterComboBox = new JComboBox<>();
        adapterComboBox.setPreferredSize(new Dimension(400, 30));
        adapterComboBox.setToolTipText("Select the network adapter to scan");
        adapterComboBox.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value,
                                                          int index, boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof NetworkInterfaceCustom adapter) {
                    setText(adapter.toDisplayString());
                }
                return this;
            }
        });

        leftPanel.add(adapterLabel);
        leftPanel.add(adapterComboBox);

        // Panel droit avec bouton discover en haut à droite de la fenêtre
        JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));

        discoverButton = new JButton("Discover");
        discoverButton.setPreferredSize(new Dimension(130, 32));
        discoverButton.setFont(discoverButton.getFont().deriveFont(13f));
        discoverButton.setFocusPainted(false);
        discoverButton.addActionListener(e -> handleDiscoverClicked());

        rightPanel.add(discoverButton);

        toolbar.add(leftPanel, BorderLayout.WEST);
        toolbar.add(rightPanel, BorderLayout.EAST);

        add(toolbar, BorderLayout.NORTH);
    }

    /**
     * Crée le panneau central avec le tableau récapitulatif
     * Crée les éléments graphiques et les positionne dans le panneau
     * Crée le tableau dans un JScrollPane pour pouvoir défiler les informations
     */
    private void createCenterPanel() {
        JPanel centerPanel = new JPanel(new BorderLayout(0, 10));
        centerPanel.setBorder(new EmptyBorder(15, 15, 15, 15));

        // Barre de statut en haut, au dessus du tableau de données
        JPanel statusBar = new JPanel(new BorderLayout(15, 0));
        statusBar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(144, 202, 249), 1),
                new EmptyBorder(8, 12, 8, 12)
        ));
        statusBar.setBackground(new Color(227, 242, 253));

        statusLabel = new JLabel("Ready");
        statusLabel.setFont(statusLabel.getFont().deriveFont(12f));
        statusLabel.setForeground(new Color(25, 118, 210));

        deviceCountLabel = new JLabel("Devices found : 0");
        deviceCountLabel.setFont(deviceCountLabel.getFont().deriveFont(12f));
        deviceCountLabel.setForeground(new Color(25, 118, 210));

        statusBar.add(statusLabel, BorderLayout.WEST);
        statusBar.add(deviceCountLabel, BorderLayout.EAST);

        // Tableau des devices
        String[] columns = {
                "MAC Address", "Device Name", "Firmware version", "Device IP",
                "IPv4 Address", "NetMask", "Gateaway", "Description"
        };

        tableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false; // Rendre le tableau non-éditable (à changer dans le futur, notamment pour faire les mises à jour des produits)
            }
        };

        devicesTable = new JTable(tableModel);
        devicesTable.setFont(devicesTable.getFont().deriveFont(12f));
        devicesTable.setRowHeight(28);
        devicesTable.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        devicesTable.getTableHeader().setReorderingAllowed(true);

        // Largeurs de colonnes préférées
        devicesTable.getColumnModel().getColumn(0).setPreferredWidth(140);
        devicesTable.getColumnModel().getColumn(1).setPreferredWidth(180);
        devicesTable.getColumnModel().getColumn(2).setPreferredWidth(100);
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
        scrollPane.setBorder(BorderFactory.createLineBorder(new Color(224, 224, 224)));

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
     * Insère la liste des adaptateurs réseau dans la ComboBox.
     * Nécessite le chargement des adaptateurs disponibles
     * @see MainController#loadNetworkAdapters()
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
     * Ajoute ligne par ligne les informations suivantes : adresse MAC, nom d'appareil, version du firmware, adresse IP, masque réseau, passerelle réseau et une description du produit
     */
    public void displayDevices(List<PlanetSwitchDevice> devices) {
        this.discoveredDevices = new ArrayList<>(devices);
        tableModel.setRowCount(0); // Nettoyage des données avant insertion (éviter une partie des doublons si re-découverte des produits)
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
        updateDeviceCount(); // Mettre à jour le statut de l'utilitaire
        updateButtonStates(); // Réactivation des autres boutons
    }

    /**
     * Met à jour le message de statut
     * @param message le message à afficher dans le statut
     */
    public void setStatusMessage(String message) {
        statusLabel.setText(message);
    }

    /**
     * Active/désactive le mode scanning
     * @param scanning booléen (true/false)
     */
    public void setScanningMode(boolean scanning) {
        discoverButton.setEnabled(!scanning);
        if (scanning) {
            statusLabel.setText("Scanning network...");
        }
    }

    /**
     * Affiche un message d'erreur (en cas de bug...)
     * @param title le titre du message (ex: bug sur le paramétrage...).
     * @param message : le message d'erreur qui convient bien (doit correspondre au titre et donner des informations permettant le débuggage / compréhension du problème)
     */
    public void showError(String title, String message) {
        JOptionPane.showMessageDialog(
                this,
                message,
                title,
                JOptionPane.ERROR_MESSAGE
        );
    }

    /**
     * Affiche un message d'information avec un titre et un message correspondant
     * @param title le titre du message (ex: aucun appareil trouvé).
     * @param message : le message d'informations qui convient bien (doit correspondre au titre et donner des informations permettant la compréhension)
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
     * Efface les résultats du tableau
     * Réinitialise le compteur d'appareil à 0
     * Met le statut en "Ready"
     */
    public void clearResults() {
        discoveredDevices.clear();
        tableModel.setRowCount(0);
        updateDeviceCount();
        updateButtonStates();
        statusLabel.setText("Ready");
    }

    /**
     * Gère le clic sur le bouton Discover
     * Vérifie que l'eventlistener n'est pas nul avant de lancer le traitement de l'interaction
     * Vérfie aussi que l'interface réseau choisie n'est pas nulle, évitant ainsi plusieurs erreurs inutiles
     */
    private void handleDiscoverClicked() {
        NetworkInterfaceCustom selectedAdapter = (NetworkInterfaceCustom) adapterComboBox.getSelectedItem();
        if (selectedAdapter == null) {
            showError("No adapter selected", "Please select a network adapter before discovering.");
            return;
        }
        if (eventListener != null) {
            eventListener.onDiscoverClicked(selectedAdapter);
        }
    }

    /**
     * Gère l'interaction avec le bouton "Update Device"
     * Sera implémentée plus tard
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
     * Gère l'interaction avec le bouton "Update Multi"
     * Sera implémentée plus tard
     */
    private void handleUpdateMulti() {
        List<PlanetSwitchDevice> selectedDevices = getSelectedDevices();
        if (!selectedDevices.isEmpty() && eventListener != null) {
            eventListener.onUpdateMultiClicked(selectedDevices);
        }
    }

    /**
     * Gère l'interaction avec le bouton "Update All"
     * Sera implémentée plus tard
     */
    private void handleUpdateAll() {
        if (!discoveredDevices.isEmpty() && eventListener != null) {
            eventListener.onUpdateAllClicked(new ArrayList<>(discoveredDevices));
        }
    }

    /**
     * Gère l'interaction avec le bouton "Connect to Device"
     * Sera implémentée plus tard
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
     * Gère l'export CSV (feature à implémenter plus tard)
     * Sera implémentée plus tard
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
     * Gère le clear results
     */
    private void handleClearResults() {
        if (eventListener != null) {
            eventListener.onClearResultsClicked();
        } else {
            clearResults();
        }
    }

    /**
     * Récupère les switchs sélectionnés dans le tableau
     */
    private List<PlanetSwitchDevice> getSelectedDevices() {
        List<PlanetSwitchDevice> selected = new ArrayList<>();
        int[] selectedRows = devicesTable.getSelectedRows();

        for (int row : selectedRows) {
            if (row >= 0 && row < discoveredDevices.size()) {
                selected.add(discoveredDevices.get(row));
            }
        }
        return selected;
    }

    /**
     * Met à jour l'affichage du compteur d'appareils trouvés
     */
    private void updateDeviceCount() {
        deviceCountLabel.setText("Devices found : " + discoveredDevices.size());
    }

    /**
     * Met à jour l'état des boutons selon la sélection
     */
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
    /**
     * Affiche les préférences
     * Sera implémentée plus tard
     */
    private void showPreferences() {
        showInfo("Preferences", "Preferences panel coming soon!");
    }

    /**
     * Affiche la documentation
     * Sera implémentée plus tard
     */
    private void showDocumentation() {
        showInfo("Documentation", "Documentation coming soon!");
    }

    /**
     * Affiche la boîte About
     * Affiche quelques informations à propos de l'utilitaire : version, petite description etc...
     */
    private void showAbout() {
        JOptionPane.showMessageDialog(
                this,
                "Rover21 - PLANET Switch Discovery Tool\n" +
                        "Version 1.0.0\n" +
                        "Modern network discovery tool for PLANET switches.\n",
                "About Rover21",
                JOptionPane.INFORMATION_MESSAGE
        );
    }
}