package com.rover.ui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;

public class MainWindow extends JFrame { // Fenêtre unique et principale

    // Éléments graphiques
    private JComboBox<String> networkInterfaces; // Liste des interfaces réseau disponibles
    private JButton discoverButton; // Bouton qui permet de lancer la découverte des appareils
    private JLabel statusLabel; // Statut du logiciel, permet de débugger
    private JLabel deviceCountLabel; // Permet d'afficher simplement combien d'appareils ont été trouvés
    private JTable devicesTable; // Tableau contenant toutes les informations liées aux switchs trouvés
    private DefaultTableModel tableModel;

    // Boutons d'action (inutile pour le moment)
    private JButton updateDeviceBtn;
    private JButton updateMultiBtn;
    private JButton updateAllBtn;
    private JButton connectBtn;

    private final String currentNetInterface = "";

    /**
     * Constructeur qui initialise et crée les menus graphiques
     */
    public MainWindow() throws SocketException {
        initializeFrame();
        createMenuBar();
        createToolbar();
        createCenterPanel();

        initializeAdapters(); // Initialiser la liste des adaptateurs réseau

        setLocationRelativeTo(null); // Centrer la fenêtre
    }

    /**
     * Initialise la fenêtre principale, paramètre la taille, la fermeture et le layout
     */
    private void initializeFrame() {
        setTitle("Rover21 - Utilitaire de découverte de switchs PLANET");
        setSize(1200, 700);
        setMinimumSize(new Dimension(1000, 600)); // Taille minimale, pas de taille fixe
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
        // exportItem.addActionListener(e -> exportToCSV());
        JMenuItem quitItem = new JMenuItem("Quit");
        quitItem.addActionListener(e -> System.exit(0));

        fileMenu.add(exportItem);
        fileMenu.addSeparator();
        fileMenu.add(quitItem);

        // Menu Options
        JMenu optionsMenu = new JMenu("Options");
        JCheckBoxMenuItem detailsItem = new JCheckBoxMenuItem("Edit software options", true);
        optionsMenu.add(detailsItem);

        // Menu Help
        JMenu helpMenu = new JMenu("Help");
        JMenuItem docItem = new JMenuItem("PLANET Website");
        // docItem.addActionListener(e -> showWebsite());
        JMenuItem aboutItem = new JMenuItem("Software version");
        // aboutItem.addActionListener(e -> showAbout());
        helpMenu.add(docItem);
        helpMenu.add(aboutItem);

        // Ajouter tous les menus
        menuBar.add(fileMenu);
        menuBar.add(optionsMenu);
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

        JLabel adapterLabel = new JLabel("Network Adapter:");
        adapterLabel.setFont(adapterLabel.getFont().deriveFont(13f));

        networkInterfaces = new JComboBox<>();
        networkInterfaces.setPreferredSize(new Dimension(350, 30));
        networkInterfaces.setToolTipText("Select the network adapter to scan");

        leftPanel.add(adapterLabel);
        leftPanel.add(networkInterfaces);

        // Panel droit avec progress bar et bouton discover
        JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));

        discoverButton = new JButton("Discover");
        discoverButton.setPreferredSize(new Dimension(130, 32));
        discoverButton.setFont(discoverButton.getFont().deriveFont(13f));
        discoverButton.setFocusPainted(false);
        // discoverButton.addActionListener(e -> startDiscovery());

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

        JPanel statusBar = new JPanel(new BorderLayout(15, 0)); // Barre de statut en haut pour indiquer l'état
        statusBar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(144, 202, 249), 1),
                new EmptyBorder(8, 12, 8, 12)
        ));
        statusBar.setBackground(new Color(227, 242, 253));

        statusLabel = new JLabel("Ready");
        statusLabel.setFont(statusLabel.getFont().deriveFont(12f)); // Police de 12 pixels pour la barre de statut, comme les infos du tableau

        deviceCountLabel = new JLabel("Devices found: 0");
        deviceCountLabel.setFont(deviceCountLabel.getFont().deriveFont(12f));
        deviceCountLabel.setForeground(new Color(25, 118, 210));

        statusBar.add(statusLabel, BorderLayout.WEST); // Le statut sera à gauche de la bar de statut
        statusBar.add(deviceCountLabel, BorderLayout.EAST); // Alors que le nombre d'appareils trouvés sera à droite

        // Liste des colonnes du tableau (à modifier pour en ajouter plus)
        String[] columns = {
                "MAC Address", "Device Name", "Version", "Device IP",
                "IP Address", "NetMask", "Gateway", "Description"
        };

        tableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false; // Rendre le tableau non-éditable
            }
        };

        // Création et paramétrage du tableau d'appareils
        devicesTable = new JTable(tableModel);
        devicesTable.setFont(devicesTable.getFont().deriveFont(12f)); // Police 12 pour les informations
        devicesTable.setRowHeight(28); // Hauteur des cellules : 28 pixels
        devicesTable.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        devicesTable.getTableHeader().setReorderingAllowed(true);

        // Largeurs de colonnes préférées
        devicesTable.getColumnModel().getColumn(0).setPreferredWidth(140); // MAC Address
        devicesTable.getColumnModel().getColumn(1).setPreferredWidth(180); // Device name
        devicesTable.getColumnModel().getColumn(2).setPreferredWidth(100); // Version
        devicesTable.getColumnModel().getColumn(3).setPreferredWidth(120); // Device IP
        devicesTable.getColumnModel().getColumn(4).setPreferredWidth(120); // IP Address
        devicesTable.getColumnModel().getColumn(5).setPreferredWidth(120); // Net Mask
        devicesTable.getColumnModel().getColumn(6).setPreferredWidth(120); // Gateaway
        devicesTable.getColumnModel().getColumn(7).setPreferredWidth(200); // Description

        JScrollPane scrollPane = new JScrollPane(devicesTable);
        scrollPane.setBorder(BorderFactory.createLineBorder(new Color(224, 224, 224)));

        centerPanel.add(statusBar, BorderLayout.NORTH);
        centerPanel.add(scrollPane, BorderLayout.CENTER);

        add(centerPanel, BorderLayout.CENTER);
    }

    /**
     * Initialise la liste des adaptateurs réseau en scannant les différentes interfaces disponibles
     * Récupère les interfaces qui ont une adresse IP et l'ajoute dans le menu déroulant
     */
    private void initializeAdapters() throws SocketException {
        Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
        while (interfaces.hasMoreElements()) {
            NetworkInterface netint = interfaces.nextElement();
            if (netint.isLoopback() || !netint.isUp()) continue;
            Enumeration<InetAddress> addresses = netint.getInetAddresses();
            while (addresses.hasMoreElements()) {
                InetAddress addr = addresses.nextElement();
                if (addr instanceof java.net.Inet4Address && !addr.isLoopbackAddress()) {
                    networkInterfaces.addItem(netint.getDisplayName() + " - " + addr.getHostAddress());
                }
            }
        }
    }

}
