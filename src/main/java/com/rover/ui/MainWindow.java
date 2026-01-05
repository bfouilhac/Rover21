package com.rover.ui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

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

    /**
     * Fonction principale qui initialise et crée les menus graphiques
     */
    public MainWindow() {
        initializeFrame();
        createMenuBar();
        createToolbar();
        createCenterPanel();
        createBottomPanel();

        // Initialiser les adaptateurs réseau
        initializeAdapters();

        // Centrer la fenêtre
        setLocationRelativeTo(null);
    }

    private void initializeAdapters() {
    }

    private void createBottomPanel() {
    }

    private void createCenterPanel() {
    }

    private void createToolbar() {
    }

    private void createMenuBar() {
    }

    private void initializeFrame() {
    }
}
