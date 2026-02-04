package com.rover.ui.graph;

import com.google.common.base.Function;
import com.rover.model.rstp.RSTPLink;
import com.rover.model.rstp.RSTPNode;
import edu.uci.ics.jung.algorithms.layout.Layout;

import java.awt.*;
import java.awt.geom.Point2D;
import java.awt.geom.RoundRectangle2D;

/**
 * Provides visual transformers for JUNG graph rendering.
 * Defines colors, shapes, strokes, and labels for RSTP topology visualization.
 * Edge labels dynamically adjust based on node positions for visual consistency.
 *
 * @author Benjamin FOUILHAC, ADM21 - February 2026
 */
public class RSTPGraphRenderer {

    private static final Color COLOR_ROOT_BRIDGE = new Color(204, 255, 153);
    private static final Color COLOR_SWITCH = new Color(153, 204, 255);
    private static final Color COLOR_EDGE_ACTIVE = new Color(66, 66, 66);
    private static final Color COLOR_EDGE_BLOCKED = new Color(220, 53, 69);
    private static final Color COLOR_VERTEX_BORDER = new Color(33, 33, 33);

    private static final int NODE_WIDTH = 120;
    private static final int NODE_HEIGHT = 50;
    private static final int ROOT_NODE_WIDTH = 140;
    private static final int ROOT_NODE_HEIGHT = 60;
    private static final int ARC_SIZE = 14;

    private Layout<RSTPNode, RSTPLink> layout;

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
     * Sets the layout used for position-aware edge labels.
     * Must be called before creating the edge label transformer.
     *
     * @param layout the graph layout
     */
    public void setLayout(Layout<RSTPNode, RSTPLink> layout) {
        this.layout = layout;
    }

    /**
     * Creates a function that returns the label text for edges.
     * The label shows port numbers ordered by visual position:
     * left port first, right port second (or top/bottom for vertical edges).
     * This ensures the label always matches the visual representation.
     *
     * @return edge label function
     */
    public Function<RSTPLink, String> createEdgeLabelTransformer() {
        return link -> {
            int srcPort = link.sourcePort();
            int dstPort = link.destinationPort();

            if (layout == null) {
                // Fallback: consistent ordering by node name
                String srcName = link.sourceNode().getDeviceName();
                String dstName = link.destinationNode().getDeviceName();
                if (srcName != null && dstName != null && srcName.compareTo(dstName) > 0) {
                    return String.format("%d - %d", dstPort, srcPort);
                }
                return String.format("%d - %d", srcPort, dstPort);
            }

            // Get current positions from layout
            Point2D srcPos = layout.apply(link.sourceNode());
            Point2D dstPos = layout.apply(link.destinationNode());

            // Determine visual order based on position
            boolean srcIsFirst = determineVisualOrder(srcPos, dstPos);

            if (srcIsFirst) {
                return String.format("%d - %d", srcPort, dstPort);
            } else {
                return String.format("%d - %d", dstPort, srcPort);
            }
        };
    }

    /**
     * Determines which node should appear first in the label based on visual position.
     * Uses angle-based calculation for smooth transitions without gaps.
     * <p>
     * The label reads left-to-right, so the "first" port is the one on the left.
     * For vertical edges, "first" is the one on top.
     * </p>
     *
     * @param srcPos position of source node
     * @param dstPos position of destination node
     * @return true if a source should appear first (left or top), false otherwise
     */
    private boolean determineVisualOrder(Point2D srcPos, Point2D dstPos) {
        double dx = dstPos.getX() - srcPos.getX();
        double dy = dstPos.getY() - srcPos.getY();

        // Handle case where nodes are at the exact same position
        if (Math.abs(dx) < 1 && Math.abs(dy) < 1) {
            return true;
        }

        // Calculate an angle from source to destination
        // atan2 returns an angle in radians: -PI to +PI
        // 0 = right, PI/2 = down, PI/-PI = left, -PI/2 = up
        double angle = Math.atan2(dy, dx);

        // If the vector points to the right half (angle between -PI/2 and +PI/2),
        // then a source is on the left side -> a source comes first. 
        // Otherwise, destination is on the left -> destination comes first
        return angle > -Math.PI / 2 && angle <= Math.PI / 2;
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
