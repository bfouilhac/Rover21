package rover;

import com.formdev.flatlaf.FlatLightLaf;
import rover.controller.MainController;
import rover.ui.MainWindow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;

/**
 * Entry point of the Rover21 PLANET switch discovery application.
 *
 * @author Benjamin FOUILHAC, ADM21 - January/February 2026
 * @see MainWindow
 * @see MainController
 */
public class Main {

    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    /**
     * Private constructor
     */
    private Main(){}

    /**
     * Main method that starts the Rover21 application.
     * <p>
     * This method initializes the look and feel, then launches the UI on the
     * Swing Event Dispatch Thread (EDT) to ensure thread safety. The EDT is
     * required for all Swing component operations.
     * </p>
     *
     */
    static void main() {
        initializeLookAndFeel();
        launchApplication();
    }

    /**
     * Initializes the FlatLaf look and feel for a modern UI appearance.
     * <p>
     * FlatLaf provides a flat, modern design that looks consistent across
     * different operating systems. If initialization fails, the application
     * will fall back to the default system look and feel.
     * </p>
     */
    private static void initializeLookAndFeel() {
        try {
            UIManager.setLookAndFeel(new FlatLightLaf());
            logger.info("FlatLaf look and feel initialized successfully");
        } catch (Exception e) {
            logger.error("Failed to set FlatLaf look and feel, using system default", e);
        }
    }

    /**
     * Launches the application UI on the Event Dispatch Thread.
     * <p>
     * This method creates the View (MainWindow), links it to the Controller
     * (MainController), initializes the application, and makes the window visible.
     * All operations are performed on the EDT using SwingUtilities.invokeLater().
     * </p>
     */
    private static void launchApplication() {
        SwingUtilities.invokeLater(() -> {
            logger.info("Launching Rover21 application...");

            MainWindow window = new MainWindow();
            MainController controller = new MainController(window);

            controller.initialize();
            window.setVisible(true);

            logger.info("Rover21 application started successfully");
        });
    }
}