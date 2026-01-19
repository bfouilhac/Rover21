package com.rover.ui;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;

/**
 * Custom table cell renderer for displaying security lock icons with different colors.
 * <p>
 * This renderer displays a padlock icon in the table cell with a color indicating
 * the security status of the device.
 * </p>
 *
 * @author Benjamin FOUILHAC, ADM21 - January/February 2026
 * @see com.rover.services.ProtocolTestService
 */
public class LockIconRenderer extends DefaultTableCellRenderer {

    public static final int LOCK_UNKNOWN = 0;
    public static final int LOCK_SECURE = 1;
    public static final int LOCK_INSECURE = 2;

    private static final Color COLOR_SECURE = new Color(153, 0, 0);
    private static final Color COLOR_INSECURE = new Color(0, 153, 0);
    private static final Color COLOR_UNKNOWN = new Color(128, 128, 128);

    private static final int ICON_WIDTH = 20;
    private static final int ICON_HEIGHT = 20;

    private static final String TOOLTIP_SECURE = "Secure - Default passwords don't work";
    private static final String TOOLTIP_INSECURE = "Insecure - Default passwords work!";
    private static final String TOOLTIP_UNKNOWN = "Not tested";

    /**
     * Returns the component used for drawing the cell.
     * <p>
     * This method creates a JLabel with a colored lock icon based on the security
     * status value in the cell. The icon color indicates whether the device is
     * secure, insecure, or not yet tested.
     * </p>
     *
     * @param table the JTable that is asking the renderer to draw
     * @param value the value of the cell to be rendered (expected Integer)
     * @param isSelected true if the cell is selected
     * @param hasFocus true if the cell has focus
     * @param row the row index of the cell being drawn
     * @param column the column index of the cell being drawn
     * @return the component used for drawing the cell
     */
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
            label.setToolTipText(getTooltipForStatus(status));
        }

        return label;
    }

    /**
     * Returns the appropriate tooltip text for a security status.
     *
     * @param status the security status (LOCK_UNKNOWN, LOCK_SECURE, or LOCK_INSECURE)
     * @return the tooltip text describing the security status
     */
    private String getTooltipForStatus(int status) {
        return switch (status) {
            case LOCK_SECURE -> TOOLTIP_SECURE;
            case LOCK_INSECURE -> TOOLTIP_INSECURE;
            default -> TOOLTIP_UNKNOWN;
        };
    }

    /**
     * Returns the appropriate color for a security status.
     *
     * @param status the security status (LOCK_UNKNOWN, LOCK_SECURE, or LOCK_INSECURE)
     * @return the color representing the security status
     */
    private Color getColorForStatus(int status) {
        return switch (status) {
            case LOCK_SECURE -> COLOR_SECURE;
            case LOCK_INSECURE -> COLOR_INSECURE;
            default -> COLOR_UNKNOWN;
        };
    }

    /**
     * Creates a colored lock icon based on security status.
     *
     * @param status the security status (LOCK_UNKNOWN, LOCK_SECURE, or LOCK_INSECURE)
     * @return an Icon representing the lock with the appropriate color
     */
    private Icon createLockIcon(int status) {
        Color lockColor = getColorForStatus(status);

        return new Icon() {
            @Override
            public void paintIcon(Component component, Graphics graphics, int x, int y) {
                Graphics2D g2d = (Graphics2D) graphics.create();
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                drawLockBody(g2d, lockColor, x, y);
                drawLockShackle(g2d, lockColor, x, y);
                drawKeyhole(g2d, x, y);

                g2d.dispose();
            }

            @Override
            public int getIconWidth() {
                return ICON_WIDTH;
            }

            @Override
            public int getIconHeight() {
                return ICON_HEIGHT;
            }
        };
    }

    /**
     * Draws the lock body (rounded rectangle).
     *
     * @param g2d the Graphics2D context
     * @param color the fill color
     * @param x the x coordinate
     * @param y the y coordinate
     */
    private void drawLockBody(Graphics2D g2d, Color color, int x, int y) {
        g2d.setColor(color);
        g2d.fillRoundRect(x + 4, y + 8, 12, 10, 2, 2);
    }

    /**
     * Draws the lock shackle (the arc at the top).
     *
     * @param g2d the Graphics2D context
     * @param color the stroke color
     * @param x the x coordinate
     * @param y the y coordinate
     */
    private void drawLockShackle(Graphics2D g2d, Color color, int x, int y) {
        g2d.setColor(color);
        g2d.setStroke(new BasicStroke(2.0f));
        g2d.drawArc(x + 6, y + 2, 8, 10, 0, 180);
    }

    /**
     * Draws the keyhole (white circle with the rectangle below).
     *
     * @param g2d the Graphics2D context
     * @param x the x coordinate
     * @param y the y coordinate
     */
    private void drawKeyhole(Graphics2D g2d, int x, int y) {
        g2d.setColor(Color.WHITE);
        g2d.fillOval(x + 8, y + 11, 4, 4);
        g2d.fillRect(x + 9, y + 14, 2, 3);
    }
}