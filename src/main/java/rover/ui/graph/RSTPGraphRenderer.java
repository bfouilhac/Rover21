package rover.ui.graph;

import com.google.common.base.Function;
import rover.model.rstp.EndDevice;
import rover.model.rstp.RSTPLink;
import rover.model.rstp.RSTPNode;
import rover.model.rstp.TopologyLink;
import rover.model.rstp.TopologyNode;
import edu.uci.ics.jung.algorithms.layout.Layout;

import java.awt.*;
import java.awt.font.FontRenderContext;
import java.awt.geom.Point2D;
import java.awt.geom.RoundRectangle2D;

/**
 * Provides visual transformers for JUNG graph rendering.
 * Defines colors, shapes, strokes, and labels for RSTP topology visualization.
 * Edge labels dynamically adjust based on node positions for visual consistency.
 *
 * @author Benjamin FOUILHAC, ADM21 - January/February 2026
 */
public class RSTPGraphRenderer {

    private static final Color COLOR_ROOT_BRIDGE = new Color(204, 255, 153);
    private static final Color COLOR_SWITCH = new Color(153, 204, 255);
    private static final Color COLOR_END_DEVICE = new Color(255, 224, 178); // Orange/peach for end devices
    private static final Color COLOR_EDGE_ACTIVE = new Color(66, 66, 66);
    private static final Color COLOR_EDGE_BLOCKED = new Color(66, 66, 66);
    private static final Color COLOR_EDGE_UNKNOWN = new Color(200, 60, 60);
    private static final Color COLOR_EDGE_END_DEVICE = new Color(150, 150, 150); // Gray for end device links
    private static final Color COLOR_VERTEX_BORDER = new Color(33, 33, 33);

    private static final int MIN_NODE_WIDTH = 80;
    private static final int MIN_NODE_HEIGHT = 36;
    private static final int MAX_NODE_WIDTH = 200;
    private static final int MAX_NODE_HEIGHT = 70;
    private static final int PADDING_HORIZONTAL = 20;
    private static final int PADDING_VERTICAL = 12;
    private static final int ARC_SIZE = 14;

    private static final Font LABEL_FONT = new Font("Segoe UI", Font.PLAIN, 12);
    private static final Font SUBLABEL_FONT = new Font("Segoe UI", Font.PLAIN, 10);
    private static final FontRenderContext FRC = new FontRenderContext(null, true, true);

    private Layout<TopologyNode, TopologyLink> layout;
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
     * Calculates node dimensions based on actual text content.
     *
     * @param node the node to measure
     * @return dimension [width, height] for the node
     */
    private Dimension calculateNodeDimensions(TopologyNode node) {
        String[] lines = getNodeTextLines(node);

        int maxTextWidth = 0;
        for (String line : lines) {
            Font font = line.equals(lines[0]) ? LABEL_FONT : SUBLABEL_FONT;
            int textWidth = (int) font.getStringBounds(line, FRC).getWidth();
            maxTextWidth = Math.max(maxTextWidth, textWidth);
        }

        int lineHeight = lines.length > 1 ? 28 : 18;
        int textHeight = lineHeight + (lines.length > 1 ? 14 : 0);

        int width = Math.clamp(maxTextWidth + PADDING_HORIZONTAL, MIN_NODE_WIDTH, MAX_NODE_WIDTH);
        int height = Math.clamp(textHeight + PADDING_VERTICAL, MIN_NODE_HEIGHT, MAX_NODE_HEIGHT);

        return new Dimension(width, height);
    }

    /**
     * Extracts text lines from a node for measurement.
     *
     * @param node the node to extract text from
     * @return array of text lines (main label, optional sublabel)
     */
    private String[] getNodeTextLines(TopologyNode node) {
        if (node instanceof EndDevice endDevice) {
            return new String[]{endDevice.getName(), endDevice.getMacAddress()};
        } else if (node instanceof RSTPNode rstpNode) {
            String name = rstpNode.getDeviceName();
            if (name == null || name.isEmpty()) {
                name = "?";
            }
            String location = rstpNode.getDevice().getSystemLocation();
            if (location != null && !location.isEmpty()) {
                return new String[]{name, location};
            }
            return new String[]{name};
        }
        return new String[]{"?"};
    }

    /**
     * Creates a function that returns the fill color for vertices.
     * Root bridge is green, switches are blue, end devices are orange.
     *
     * @return vertex paint function
     */
    public Function<TopologyNode, Paint> createVertexPaintTransformer() {
        return node -> {
            if (!node.isSwitch()) {
                return COLOR_END_DEVICE;
            }
            return node.isRootBridge() ? COLOR_ROOT_BRIDGE : COLOR_SWITCH;
        };
    }

    /**
     * Creates a function that returns the shape for vertices.
     * Size is adaptive based on actual text content to ensure text is fully visible.
     * Root bridge has a thicker border (handled by stroke), end devices are slightly smaller.
     *
     * @return vertex shape function
     */
    public Function<TopologyNode, Shape> createVertexShapeTransformer() {
        return node -> {
            Dimension baseDim = calculateNodeDimensions(node);
            int width = baseDim.width;
            int height = baseDim.height;

            // Slight size adjustments for visual hierarchy
            if (!node.isSwitch()) {
                width = (int) (width * 0.95);
                height = (int) (height * 0.95);
            } else if (node.isRootBridge()) {
                width = (int) (width * 1.1);
                height = (int) (height * 1.1);
            }

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
     * Switches show the device name and location, end devices show their name and MAC address.
     *
     * @return vertex label function
     */
    public Function<TopologyNode, String> createVertexLabelTransformer() {
        return node -> {
            if (node instanceof EndDevice endDevice) {
                return createEndDeviceLabel(endDevice);
            } else if (node instanceof RSTPNode rstpNode) {
                return createSwitchLabel(rstpNode);
            }
            return "?";
        };
    }

    /**
     * Creates the label for an end device.
     */
    private String createEndDeviceLabel(EndDevice device) {
        return String.format("<html><center>%s<br/><font size='2'>%s</font></center></html>", device.getName(), device.getMacAddress());
    }

    /**
     * Creates the label for a switch node.
     * Text is not truncated as node size adapts to content.
     */
    private String createSwitchLabel(RSTPNode node) {
        String name = node.getDeviceName();
        String location = node.getDevice().getSystemLocation();

        if (name == null || name.isEmpty()) {
            name = "?";
        }

        StringBuilder html = new StringBuilder("<html><center>");
        html.append(name);

        if (location != null && !location.isEmpty()) {
            html.append("<br/><font size='2' color='#555555'>");
            html.append(location);
            html.append("</font>");
        }

        html.append("</center></html>");
        return html.toString();
    }

    /**
     * Creates a function that returns the border stroke for vertices.
     *
     * @return vertex stroke function
     */
    public Function<TopologyNode, Stroke> createVertexStrokeTransformer() {
        return node -> {
            if (!node.isSwitch()) {
                return new BasicStroke(1.5f);
            }
            return node.isRootBridge() ? new BasicStroke(3.0f) : new BasicStroke(2.0f);
        };
    }

    /**
     * Creates a function that returns the border color for vertices.
     *
     * @return vertex draw paint function
     */
    public Function<TopologyNode, Paint> createVertexDrawPaintTransformer() {
        return _ -> COLOR_VERTEX_BORDER;
    }

    /**
     * Creates a function that returns the paint color for edges.
     * <p>
     * Colors:
     * - Active links: dark gray
     * - RSTP blocked links: dark gray (differentiated by stroke style)
     * - Unknown state links (stale LLDP): red
     * - End device links: light gray
     * </p>
     *
     * @return edge paint function
     */
    public Function<TopologyLink, Paint> createEdgePaintTransformer() {
        return link -> {
            if (!link.isSwitchLink()) {
                return COLOR_EDGE_END_DEVICE;
            }
            if (link instanceof RSTPLink rstpLink) {
                if (rstpLink.isUnknown()) {
                    return COLOR_EDGE_UNKNOWN;
                }
                return rstpLink.isActive() ? COLOR_EDGE_ACTIVE : COLOR_EDGE_BLOCKED;
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
     * - End device links: thinner solid line
     * </p>
     *
     * @return edge stroke function
     */
    public Function<TopologyLink, Stroke> createEdgeStrokeTransformer() {
        return link -> {
            if (!link.isSwitchLink()) {
                return new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
            }
            if (link instanceof RSTPLink rstpLink && (rstpLink.isActive() || rstpLink.isUnknown()) ) {
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
    public void setLayout(Layout<TopologyNode, TopologyLink> layout) {
        this.layout = layout;
    }

    /**
     * Creates a function that returns the label text for edges.
     * For switch-to-switch links: shows port numbers ordered by visual position.
     * For end device links: shows only the switch port number.
     *
     * @return edge label function
     */
    public Function<TopologyLink, String> createEdgeLabelTransformer() {
        return link -> {
            if (!link.isSwitchLink()) {
                // End device link: show only the switch port
                return String.valueOf(link.getSourcePort());
            }

            int srcPort = link.getSourcePort();
            int dstPort = link.getDestinationPort();

            if (layout == null) {
                // Fallback: consistent ordering by unique ID
                String srcId = link.getSourceNode().getUniqueId();
                String dstId = link.getDestinationNode().getUniqueId();
                if (srcId != null && dstId != null && srcId.compareTo(dstId) > 0) {
                    return String.format("%d - %d", dstPort, srcPort);
                }
                return String.format("%d - %d", srcPort, dstPort);
            }

            // Get current positions from layout
            Point2D srcPos = layout.apply(link.getSourceNode());
            Point2D dstPos = layout.apply(link.getDestinationNode());

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
