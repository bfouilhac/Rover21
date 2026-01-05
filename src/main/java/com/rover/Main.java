package com.rover;

import com.formdev.flatlaf.FlatLightLaf;
import com.rover.controller.MainController;
import com.rover.ui.MainWindow;

import javax.swing.*;

/**
 * Point d'entrée de l'application
 */
public class Main {

    public static void main(String[] args) {

        try {
            UIManager.setLookAndFeel(new FlatLightLaf());  // Appliquer le thème FlatLaf pour l'UI
        } catch (Exception e) {
            System.err.println("Failed to initialize FlatLaf theme");
            e.printStackTrace();
        }

        SwingUtilities.invokeLater(() -> { // Lancer l'interface sur un thread séparé
            MainWindow window = new MainWindow(); // Créer la View
            MainController controller = new MainController(window); // Créer le Controller et l'associer à la View
            controller.initialize(); // Initialiser l'application (charge les adaptateurs)
            window.setVisible(true); // Afficher la fenêtre
        });
    }
}