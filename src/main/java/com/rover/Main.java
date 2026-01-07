package com.rover;

import com.formdev.flatlaf.FlatLightLaf;
import com.rover.controller.MainController;
import com.rover.ui.MainWindow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;

/**
 * Entrypoint of the program
 */
public class Main {

    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(new FlatLightLaf());  // Appliquer le thème FlatLaf pour l'UI
        } catch (Exception e) {
            logger.error("Error setting Laf", e);
        }

        SwingUtilities.invokeLater(() -> { // Lancer l'interface sur un thread séparé
            MainWindow window = new MainWindow(); // Créer la View
            MainController controller = new MainController(window); // Créer le Controller et l'associer à la View
            controller.initialize(); // Initialiser l'application (charge les adaptateurs)
            window.setVisible(true); // Afficher la fenêtre
        });
    }
}