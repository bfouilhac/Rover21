package com.rover.ui.graph;

import com.google.common.base.Function;
import com.rover.model.rstp.RSTPLink;
import com.rover.model.rstp.RSTPNode;

import java.awt.*;
import java.awt.geom.RoundRectangle2D;

/**
 * Provides visual transformers for JUNG graph rendering.
 * Defines colors, shapes, strokes, and labels for RSTP topology visualization.
 *
 * @author Benjamin FOUILHAC, ADM21 - February 2026
 */
public class RSTPGraphRenderer {

    private static final Color COLOR_ROOT_BRIDGE = new Color(204, 255, 153);
    private static final Color COLOR_SWITCH = new Color(153, 204, 255);
    private static final Color COLOR_EDGE_ACTIVE = new Color(66, 66, 66);
    private static final Color COLOR_EDGE_BLOCKED = new Color(220, 53, 69);  // Red for blocked links
    private static final Color COLOR_VERTEX_BORDER = new Color(33, 33, 33);

    // Node sizes increased to accommodate two lines of text
    private static final int NODE_WIDTH = 120;
    private static final int NODE_HEIGHT = 50;
    private static final int ROOT_NODE_WIDTH = 140;
    private static final int ROOT_NODE_HEIGHT = 60;
    private static final int ARC_SIZE = 14;

    /**
     * Creates a function that returns the fill color for vertices.
     * Root bridge is green, other switches are blue.
     *
     * @return vertex paint function
     */
    public Function<RSTPNode, Paint> createVertexPaintTransformer() {
        return node -> node.isRootBridge() ? COLOR_ROOT_BRIDGE : COLOR_SWITCH;
    }

    /**
     * Creates a function that returns the shape for vertices.
     * Root bridge is slightly larger than regular switches.
     *
     * @return vertex shape function
     */
    public Function<RSTPNode, Shape> createVertexShapeTransformer() {
        return node -> {
            int width = node.isRootBridge() ? ROOT_NODE_WIDTH : NODE_WIDTH;
            int height = node.isRootBridge() ? ROOT_NODE_HEIGHT : NODE_HEIGHT;
            return new RoundRectangle2D.Double(
                    -width / 2.0, -height / 2.0,
                    width, height,
                    ARC_SIZE, ARC_SIZE
            );
        };
    }

    /**
     * Creates a function that returns the label text for vertices.
     * Shows device name and system location on two lines using HTML.
     *
     * @return vertex label function
     */
    public Function<RSTPNode, String> createVertexLabelTransformer() {
        return node -> {
            String name = node.getDeviceName();
            String location = node.getDevice().getSystemLocation();

            // Truncate name if too long
            if (name != null && name.length() > 16) {
                name = name.substring(0, 14) + "...";
            }
            if (name == null || name.isEmpty()) {
                name = "Unknown";
            }

            // Truncate location if too long
            if (location != null && location.length() > 16) {
                location = location.substring(0, 14) + "...";
            }

            // Build HTML label with two lines
            StringBuilder html = new StringBuilder("<html><center>");
            html.append(name);

            if (location != null && !location.isEmpty()) {
                html.append("<br/><font size='2' color='#555555'>");
                html.append(location);
                html.append("</font>");
            }

            html.append("</center></html>");
            return html.toString();
        };
    }

    /**
     * Creates a function that returns the border stroke for vertices.
     *
     * @return vertex stroke function
     */
    public Function<RSTPNode, Stroke> createVertexStrokeTransformer() {
        return node -> node.isRootBridge()
                ? new BasicStroke(3.0f)
                : new BasicStroke(2.0f);
    }

    /**
     * Creates a function that returns the border color for vertices.
     *
     * @return vertex draw paint function
     */
    public Function<RSTPNode, Paint> createVertexDrawPaintTransformer() {
        return _ -> COLOR_VERTEX_BORDER;
    }

    /**
     * Creates a function that returns the paint color for edges.
     * Active links are dark-gray, blocked links are red.
     *
     * @return edge paint function
     */
    public Function<RSTPLink, Paint> createEdgePaintTransformer() {
        return link -> link.isActive() ? COLOR_EDGE_ACTIVE : COLOR_EDGE_BLOCKED;
    }

    /**
     * Creates a function that returns the stroke style for edges.
     * Active links are solid, blocked links are dashed.
     *
     * @return edge stroke function
     */
    public Function<RSTPLink, Stroke> createEdgeStrokeTransformer() {
        return link -> {
            if (link.isActive()) {
                return new BasicStroke(2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
            } else {
                return new BasicStroke(2.0f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10.0f, new float[]{6.0f, 4.0f}, 0.0f);
            }
        };
    }

    /**
     * Creates a function that returns the label text for edges.
     * Shows "DeviceName:port — DeviceName:port" format so ports stay attached to their devices.
     *
     * @return edge label function
     */
    public Function<RSTPLink, String> createEdgeLabelTransformer() {
        return link -> String.format("%s:%d - %s:%d",
                link.getSourceShortName(), link.sourcePort(),
                link.getDestinationShortName(), link.destinationPort());
    }

    /**
     * Returns the font to use for vertex labels.
     *
     * @return label font
     */
    public Font getVertexLabelFont() {
        return new Font("Segoe UI", Font.PLAIN, 12);
    }

    /**
     * Returns the font to use for edge labels.
     *
     * @return edge label font
     */
    public Font getEdgeLabelFont() {
        return new Font("Segoe UI", Font.PLAIN, 13);
    }
}
