package rover.ui;

import rover.utils.LanguageManager;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

/**
 * Non-modal dialog containing the log panel.
 * <p>
 * Can be accessed from the View menu. Displays application logs in real-time
 * with filtering and export capabilities.
 * </p>
 *
 * @author Benjamin FOUILHAC, ADM21
 */
public class LogDialog extends JDialog {

    private static final int DIALOG_WIDTH = 900;
    private static final int DIALOG_HEIGHT = 500;

    private final LogPanel logPanel;

    /**
     * Creates a new log dialog.
     *
     * @param owner the parent frame
     */
    public LogDialog(Frame owner) {
        super(owner, LanguageManager.getInstance().get("logs.title"), false);

        setSize(DIALOG_WIDTH, DIALOG_HEIGHT);
        setLocationRelativeTo(owner);
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

        logPanel = new LogPanel();
        add(logPanel, BorderLayout.CENTER);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                logPanel.dispose();
            }
        });
    }

    /**
     * Disposes of this dialog and releases resources.
     */
    @Override
    public void dispose() {
        logPanel.dispose();
        super.dispose();
    }
}
