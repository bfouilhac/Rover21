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
    private static final Color COLOR_EDGE_BLOCKED = new Color(66, 66, 66);
    private static final Color COLOR_EDGE_UNKNOWN = new Color(200, 60, 60); // Red for stale LLDP data
    private static final Color COLOR_VERTEX_BORDER = new Color(33, 33, 33);

    // Default node sizes (for small networks)
    private static final int DEFAULT_NODE_WIDTH = 120;
    private static final int DEFAULT_NODE_HEIGHT = 50;
    private static final int MIN_NODE_WIDTH = 60;
    private static final int MIN_NODE_HEIGHT = 30;
    private static final int ARC_SIZE = 14;

    private Layout<RSTPNode, RSTPLink> layout;
    private int nodeCount = 1;
    private Dimension viewSize = new Dimension(800, 600);

    /**
     * Sets graph metrics for adaptive node sizing.
     *
     * @param nodeCount number of nodes in the graph
     * @param viewSize  size of the visualization area
     */
    public void setGraphMetrics(int nodeCount, Dimension viewSize) {
        this.nodeCount = Math.max(1, nodeCount);
        this.viewSize = viewSize != null ? viewSize : new Dimension(800, 600);
    }

    /**
     * Calculates the optimal node width based on graph size.
     */
    private int calculateNodeWidth() {
        if (nodeCount <= 5) {
            return DEFAULT_NODE_WIDTH;
        }

        // Calculate based on available space and node count
        double availableWidth = viewSize.width * 0.8;
        int optimalWidth = (int) (availableWidth / Math.sqrt(nodeCount) / 1.5);

        // Clamp between min and default
        return Math.max(MIN_NODE_WIDTH, Math.min(DEFAULT_NODE_WIDTH, optimalWidth));
    }

    /**
     * Calculates the optimal node height based on graph size.
     */
    private int calculateNodeHeight() {
        if (nodeCount <= 5) {
            return DEFAULT_NODE_HEIGHT;
        }

        double availableHeight = viewSize.height * 0.8;
        int optimalHeight = (int) (availableHeight / Math.sqrt(nodeCount) / 1.5);

        return Math.max(MIN_NODE_HEIGHT, Math.min(DEFAULT_NODE_HEIGHT, optimalHeight));
    }

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
     * Size is adaptive based on node count and view size.
     * Root bridge is slightly larger than regular switches.
     *
     * @return vertex shape function
     */
    public Function<RSTPNode, Shape> createVertexShapeTransformer() {
        return node -> {
            int baseWidth = calculateNodeWidth();
            int baseHeight = calculateNodeHeight();

            // Root bridge is 20% larger
            int width = node.isRootBridge() ? (int) (baseWidth * 1.2) : baseWidth;
            int height = node.isRootBridge() ? (int) (baseHeight * 1.2) : baseHeight;

            int arcSize = Math.min(ARC_SIZE, Math.min(width, height) / 4);

            return new RoundRectangle2D.Double(
                    -width / 2.0, -height / 2.0,
                    width, height,
                    arcSize, arcSize
            );
        };
    }

    /**
     * Creates a function that returns the label text for vertices.
     * Shows device name and optionally system location.
     * Text length adapts to node size.
     *
     * @return vertex label function
     */
    public Function<RSTPNode, String> createVertexLabelTransformer() {
        return node -> {
            String name = node.getDeviceName();
            String location = node.getDevice().getSystemLocation();

            // Calculate max length based on node width
            int nodeWidth = calculateNodeWidth();
            int maxLength = Math.max(8, nodeWidth / 8);

            // Truncate name if too long
            if (name != null && name.length() > maxLength) {
                name = name.substring(0, maxLength - 2) + "..";
            }
            if (name == null || name.isEmpty()) {
                name = "?";
            }

            // For small nodes, only show name
            if (nodeWidth < 80) {
                return String.format("<html><center>%s</center></html>", name);
            }

            // Truncate location if too long
            if (location != null && location.length() > maxLength) {
                location = location.substring(0, maxLength - 2) + "..";
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
     * <p>
     * Colors:
     * - Active links: dark gray
     * - RSTP blocked links: dark gray (differentiated by stroke style)
     * - Unknown state links (stale LLDP): red
     * </p>
     *
     * @return edge paint function
     */
    public Function<RSTPLink, Paint> createEdgePaintTransformer() {
        return link -> {
            if (link.isUnknown()) {
                return COLOR_EDGE_UNKNOWN;
            }
            return link.isActive() ? COLOR_EDGE_ACTIVE : COLOR_EDGE_BLOCKED;
        };
    }

    /**
     * Creates a function that returns the stroke style for edges.
     * <p>
     * Styles:
     * - Active links: solid line
     * - RSTP blocked links: dashed line
     * - Unknown state links (stale LLDP): solid line (color indicates the issue)
     * </p>
     *
     * @return edge stroke function
     */
    public Function<RSTPLink, Stroke> createEdgeStrokeTransformer() {
        return link -> {
            if (link.isActive() || link.isUnknown()) {
                return new BasicStroke(2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
            }
            // RSTP blocked: dashed line
            return new BasicStroke(2.0f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10.0f, new float[]{6.0f, 4.0f}, 0.0f);
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
