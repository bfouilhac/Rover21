package com.rover;

import com.formdev.flatlaf.FlatLightLaf;
import com.rover.controller.MainController;
import com.rover.ui.MainWindow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;

/**
 * Entry point of the Rover21 PLANET switch discovery application.
 * This class initializes the user interface look and feel, creates the main window,
 * and sets up the MVC architecture by linking the controller and view.
 *
 * @author Benjamin FOUILHAC, ADM21 in January-February 2026
 * @see MainWindow
 * @see MainController
 */
public class Main {

    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    /**
     * Main method that starts the application.
     *
     * @param args command line arguments (currently not used)
     */
    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(new FlatLightLaf()); // apply FlatLaf theme for modern UI
        } catch (Exception e) {
            logger.error("Error setting Look and Feel", e);
        }

        SwingUtilities.invokeLater(() -> { // launch interface on Event Dispatch Thread
            MainWindow window = new MainWindow(); // create the View
            MainController controller = new MainController(window); // create the Controller and associate it with the View
            controller.initialize(); // initialize the application (loads network adapters)
            window.setVisible(true); // display the window
        });
    }
}