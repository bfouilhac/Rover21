package com.rover;

import com.formdev.flatlaf.FlatLightLaf;
import com.rover.ui.MainWindow;

import javax.swing.*;
import java.net.SocketException;

/**
 * Point d'entrée de l'utilitaire
 */
public class Main {

    public static void main(String[] args) {

        try {
            UIManager.setLookAndFeel(new FlatLightLaf()); // Appliquer le thème FlatLaf moderne
        } catch (Exception e) {
            System.err.println("ERR - Initialisation du thème manquée");
            e.printStackTrace();
        }

        SwingUtilities.invokeLater(() -> { // Lancer l'interface sur un thread séparé
            MainWindow window = null;
            try {
                window = new MainWindow();
            } catch (SocketException e) {
                throw new RuntimeException(e);
            }
            window.setVisible(true);
        });
    }
}