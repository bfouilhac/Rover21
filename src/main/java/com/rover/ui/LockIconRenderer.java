package com.rover.ui;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;

/**
 * Custom table cell renderer for displaying lock icons with different colors.
 *
 * @author Benjamin FOUILHAC, ADM21 in January-February 2026
 */
public class LockIconRenderer extends DefaultTableCellRenderer {

    // Security status constants
    public static final int LOCK_UNKNOWN = 0;   // Gray - not tested
    public static final int LOCK_SECURE = 1;    // Red - secure (default passwords don't work)
    public static final int LOCK_INSECURE = 2;  // Green - insecure (default passwords work)

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value,
                                                   boolean isSelected, boolean hasFocus,
                                                   int row, int column) {
        JLabel label = (JLabel) super.getTableCellRendererComponent(
                table, "", isSelected, hasFocus, row, column
        );

        if (value instanceof Integer status) {
            Icon lockIcon = createLockIcon(status);
            label.setIcon(lockIcon);
            label.setHorizontalAlignment(CENTER);
            label.setText("");

            // Set tooltip
            String tooltip = switch (status) {
                case LOCK_SECURE -> "Secure - Default passwords don't work";
                case LOCK_INSECURE -> "Insecure - Default passwords work!";
                default -> "Not tested";
            };
            label.setToolTipText(tooltip);
        }

        return label;
    }

    /**
     * Creates a colored lock icon based on security status.
     *
     * @param status the security status (LOCK_UNKNOWN, LOCK_SECURE, LOCK_INSECURE)
     * @return an Icon representing the lock
     */
    private Icon createLockIcon(int status) {
        Color lockColor = switch (status) {
            case LOCK_SECURE -> new Color(153, 0, 0); // Red - secure
            case LOCK_INSECURE -> new Color(0, 153, 0); // Green - insecure
            default -> new Color(128, 128, 128); // Gray - unknown
        };

        return new Icon() {
            @Override
            public void paintIcon(Component c, Graphics g, int x, int y) {
                Graphics2D g2d = (Graphics2D) g.create();
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                // Draw lock body (rectangle)
                g2d.setColor(lockColor);
                g2d.fillRoundRect(x + 4, y + 8, 12, 10, 2, 2);

                // Draw lock shackle (arc)
                g2d.setStroke(new BasicStroke(2.0f));
                g2d.drawArc(x + 6, y + 2, 8, 10, 0, 180);

                // Draw keyhole
                g2d.setColor(Color.WHITE);
                g2d.fillOval(x + 8, y + 11, 4, 4);
                g2d.fillRect(x + 9, y + 14, 2, 3);

                g2d.dispose();
            }

            @Override
            public int getIconWidth() {
                return 20;
            }

            @Override
            public int getIconHeight() {
                return 20;
            }
        };
    }
}