package rover.ui.graph;

import com.google.common.base.Function;
import rover.model.rstp.EndDevice;
import rover.model.rstp.EndDeviceLink;
import rover.model.rstp.RSTPLink;
import rover.model.rstp.RSTPNode;
import rover.model.rstp.RSTPTopology;
import rover.model.rstp.TopologyLink;
import rover.model.rstp.TopologyNode;
import rover.utils.LanguageManager;
import edu.uci.ics.jung.algorithms.layout.Layout;
import edu.uci.ics.jung.algorithms.layout.StaticLayout;
import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.UndirectedSparseGraph;
import edu.uci.ics.jung.visualization.VisualizationViewer;
import edu.uci.ics.jung.visualization.control.CrossoverScalingControl;
import edu.uci.ics.jung.visualization.control.DefaultModalGraphMouse;
import edu.uci.ics.jung.visualization.control.ModalGraphMouse;
import edu.uci.ics.jung.visualization.control.ScalingControl;
import edu.uci.ics.jung.visualization.decorators.EdgeShape;
import edu.uci.ics.jung.visualization.picking.PickedState;
import edu.uci.ics.jung.visualization.renderers.Renderer;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The main panel for RSTP topology graph visualization using the JUNG library.
 * Provides an interactive graph with zoom, pan, drag, and node selection.
 *
 * @author Benjamin FOUILHAC, ADM21 - January/February 2026
 */
public class RSTPGraphPanel extends JPanel {

    private static final Color COLOR_BACKGROUND = Color.WHITE;
    private static final Color COLOR_EMPTY_STATE = new Color(150, 150, 150);
    private static final int TOOLBAR_HEIGHT = 36;

    private final transient LanguageManager lang = LanguageManager.getInstance();
    private final transient RSTPGraphRenderer renderer = new RSTPGraphRenderer();
    private final transient ScalingControl scaler = new CrossoverScalingControl();

    private transient RSTPTopology topology;
    private transient Graph<TopologyNode, TopologyLink> graph;
    private transient VisualizationViewer<TopologyNode, TopologyLink> vv;
    private transient DefaultModalGraphMouse<TopologyNode, TopologyLink> graphMouse;
    private JPanel graphContainer;
    private RSTPDetailsPanel detailsPanel;
    private JLabel emptyStateLabel;
    private JToggleButton panModeButton;

    public RSTPGraphPanel() {
        initComponents();
    }

    private void initComponents() {
        setLayout(new BorderLayout());
        setBackground(COLOR_BACKGROUND);

        add(createControlBar(), BorderLayout.NORTH);

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        splitPane.setResizeWeight(0.75);
        splitPane.setDividerSize(5);
        splitPane.setBorder(null);

        graphContainer = new JPanel(new BorderLayout());
        graphContainer.setBackground(COLOR_BACKGROUND);

        emptyStateLabel = new JLabel(lang.get("topology.graph_empty_state"), SwingConstants.CENTER);
        emptyStateLabel.setFont(new Font("Segoe UI", Font.ITALIC, 14));
        emptyStateLabel.setForeground(COLOR_EMPTY_STATE);
        graphContainer.add(emptyStateLabel, BorderLayout.CENTER);

        splitPane.setLeftComponent(graphContainer);

        detailsPanel = new RSTPDetailsPanel();
        splitPane.setRightComponent(detailsPanel);

        add(splitPane, BorderLayout.CENTER);
    }

    private JToolBar createControlBar() {
        JToolBar toolbar = new JToolBar();
        toolbar.setFloatable(false);
        toolbar.setPreferredSize(new Dimension(0, TOOLBAR_HEIGHT));
        toolbar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(224, 224, 224)));

        JButton zoomInButton = createToolbarButton(lang.get("topology.control_zoom_in"), _ -> zoomIn());
        JButton zoomOutButton = createToolbarButton(lang.get("topology.control_zoom_out"), _ -> zoomOut());
        JButton fitButton = createToolbarButton(lang.get("topology.control_fit"), _ -> fitToWindow());
        JButton resetButton = createToolbarButton(lang.get("topology.control_reset"), _ -> resetLayout());

        // Pan mode toggle button
        panModeButton = new JToggleButton(lang.get("topology.control_pan"));
        panModeButton.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        panModeButton.setFocusPainted(false);
        panModeButton.setToolTipText(lang.get("topology.control_pan_tooltip"));
        panModeButton.addActionListener(_ -> togglePanMode());

        toolbar.add(Box.createHorizontalStrut(8));
        toolbar.add(zoomInButton);
        toolbar.add(Box.createHorizontalStrut(4));
        toolbar.add(zoomOutButton);
        toolbar.add(Box.createHorizontalStrut(12));
        toolbar.addSeparator();
        toolbar.add(Box.createHorizontalStrut(12));
        toolbar.add(panModeButton);
        toolbar.add(Box.createHorizontalStrut(12));
        toolbar.addSeparator();
        toolbar.add(Box.createHorizontalStrut(12));
        toolbar.add(fitButton);
        toolbar.add(Box.createHorizontalStrut(4));
        toolbar.add(resetButton);
        toolbar.add(Box.createHorizontalGlue());

        return toolbar;
    }

    /**
     * Toggles between picking mode (move individual nodes) and pan mode (move entire view).
     */
    private void togglePanMode() {
        if (graphMouse == null) return;

        if (panModeButton.isSelected()) {
            graphMouse.setMode(ModalGraphMouse.Mode.TRANSFORMING);
        } else {
            graphMouse.setMode(ModalGraphMouse.Mode.PICKING);
        }
    }

    private JButton createToolbarButton(String text, java.awt.event.ActionListener listener) {
        JButton button = new JButton(text);
        button.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        button.setFocusPainted(false);
        button.addActionListener(listener);
        return button;
    }

    /**
     * Sets the topology to display in the graph.
     *
     * @param topology the RSTP topology data
     */
    public void setTopology(RSTPTopology topology) {
        this.topology = topology;

        if (topology == null || !topology.isValid()) {
            showEmptyState();
            return;
        }

        SwingUtilities.invokeLater(this::buildAndDisplayGraph);
    }

    private void showEmptyState() {
        graphContainer.removeAll();
        emptyStateLabel.setText(lang.get("topology.graph_empty_state"));
        graphContainer.add(emptyStateLabel, BorderLayout.CENTER);
        graphContainer.revalidate();
        graphContainer.repaint();
        detailsPanel.showEmptyState();
    }

    private void buildAndDisplayGraph() {
        graph = buildGraph(topology);

        Dimension viewSize = graphContainer.getSize();
        if (viewSize.width == 0 || viewSize.height == 0) {
            viewSize = new Dimension(800, 600);
        }

        Layout<TopologyNode, TopologyLink> layout = createHierarchicalLayout(graph, topology);

        // Pass metrics to renderer for adaptive sizing and position-aware labels
        int totalNodes = topology.getNodeCount() + topology.getEndDeviceCount();
        renderer.setGraphMetrics(totalNodes, viewSize);
        renderer.setLayout(layout);

        vv = new VisualizationViewer<>(layout);
        configureVisualization();
        setupMouseHandlers();

        graphContainer.removeAll();
        graphContainer.add(vv, BorderLayout.CENTER);
        graphContainer.revalidate();
        graphContainer.repaint();
    }

    private Graph<TopologyNode, TopologyLink> buildGraph(RSTPTopology topology) {
        UndirectedSparseGraph<TopologyNode, TopologyLink> g = new UndirectedSparseGraph<>();

        // Add switch nodes
        for (RSTPNode node : topology.getNodes()) {
            g.addVertex(node);
        }

        // Add end device nodes
        for (EndDevice endDevice : topology.getEndDevices()) {
            g.addVertex(endDevice);
        }

        // Add switch-to-switch links
        for (RSTPLink link : topology.getLinks()) {
            g.addEdge(link, link.sourceNode(), link.destinationNode());
        }

        // Add switch-to-end-device links
        for (EndDeviceLink link : topology.getEndDeviceLinks()) {
            g.addEdge(link, link.switchNode(), link.endDevice());
        }

        return g;
    }

    private Layout<TopologyNode, TopologyLink> createHierarchicalLayout(Graph<TopologyNode, TopologyLink> graph, RSTPTopology topology) {
        Dimension size = graphContainer.getSize();
        if (size.width == 0 || size.height == 0) {
            size = new Dimension(800, 600);
        }

        Function<TopologyNode, Point2D> initializer = new HierarchicalPositionFunction(topology, size);
        return new StaticLayout<>(graph, initializer, size);
    }

    /**
     * Function that positions nodes based on topology structure.
     * Uses hierarchical layout when hop counts differ, or circular layout otherwise.
     * End devices are positioned below their connected switches.
     */
    private static class HierarchicalPositionFunction implements Function<TopologyNode, Point2D> {

        private final Map<TopologyNode, Point2D> positions = new HashMap<>();

        public HierarchicalPositionFunction(RSTPTopology topology, Dimension size) {
            calculatePositions(topology, size);
        }

        private void calculatePositions(RSTPTopology topology, Dimension size) {
            List<RSTPNode> nodes = topology.getNodes();
            if (nodes.isEmpty()) return;

            Map<Integer, List<RSTPNode>> nodesByHopCount = nodes.stream()
                    .collect(Collectors.groupingBy(RSTPNode::getHopCount));

            int maxHopCount = nodesByHopCount.keySet().stream()
                    .mapToInt(Integer::intValue)
                    .max()
                    .orElse(0);

            // If all nodes have the same hop count, use circular/grid layout
            if (nodesByHopCount.size() == 1) {
                calculateCircularLayout(nodes, topology, size);
            } else {
                calculateHierarchicalLayout(nodesByHopCount, maxHopCount, topology, size);
            }
        }

        /**
         * Circular layout when all nodes are at the same level.
         */
        private void calculateCircularLayout(List<RSTPNode> nodes, RSTPTopology topology, Dimension size) {
            double centerX = size.width / 2.0;
            double centerY = size.height / 2.0;
            double radius = Math.min(size.width, size.height) / 2.0 - 100;

            // Find the root bridge and place it in the center
            RSTPNode rootBridge = null;
            List<RSTPNode> otherNodes = new ArrayList<>();

            for (RSTPNode node : nodes) {
                if (node.isRootBridge()) {
                    rootBridge = node;
                } else {
                    otherNodes.add(node);
                }
            }

            // Place the root bridge in a center if found
            if (rootBridge != null) {
                positions.put(rootBridge, new Point2D.Double(centerX, centerY));
            }

            // Place other nodes in a circle around the center
            List<RSTPNode> nodesToArrange = rootBridge != null ? otherNodes : nodes;
            int nodeCount = nodesToArrange.size();

            if (nodeCount == 0 && rootBridge == null) return;

            // Adjust radius if the root is in a center
            if (rootBridge != null && nodeCount > 0) {
                radius = Math.min(radius, Math.min(size.width, size.height) / 3.0);
            }

            for (int i = 0; i < nodeCount; i++) {
                double angle = 2 * Math.PI * i / nodeCount - Math.PI / 2;
                double x = centerX + radius * Math.cos(angle);
                double y = centerY + radius * Math.sin(angle);
                positions.put(nodesToArrange.get(i), new Point2D.Double(x, y));
            }

            // Position end devices around their connected switches
            positionEndDevices(topology, size);
        }

        /**
         * Hierarchical layout based on hop count (root at top).
         */
        private void calculateHierarchicalLayout(Map<Integer, List<RSTPNode>> nodesByHopCount,
                                                  int maxHopCount, RSTPTopology topology, Dimension size) {
            double marginTop = 80;
            double marginBottom = 60;
            double marginSide = 100;
            double availableHeight = size.height - marginTop - marginBottom;
            double availableWidth = size.width - (2 * marginSide);

            // Reserve space for end devices at the bottom
            int endDeviceCount = topology.getEndDeviceCount();
            if (endDeviceCount > 0) {
                availableHeight -= 80;
            }

            double layerHeight = maxHopCount > 0 ? availableHeight / (maxHopCount + 1) : availableHeight / 2;

            for (Map.Entry<Integer, List<RSTPNode>> entry : nodesByHopCount.entrySet()) {
                int hopCount = entry.getKey();
                List<RSTPNode> nodesInLayer = entry.getValue();

                double y = marginTop + (hopCount * layerHeight);
                double spacing = availableWidth / (nodesInLayer.size() + 1);

                for (int i = 0; i < nodesInLayer.size(); i++) {
                    RSTPNode node = nodesInLayer.get(i);
                    double x = marginSide + spacing * (i + 1);
                    positions.put(node, new Point2D.Double(x, y));
                }
            }

            // Position end devices below their connected switches
            positionEndDevices(topology, size);
        }

        /**
         * Positions end devices below their connected switches.
         */
        private void positionEndDevices(RSTPTopology topology, Dimension size) {
            // Group end devices by their connected switch
            Map<RSTPNode, List<EndDevice>> devicesBySwitch = new HashMap<>();
            for (EndDevice device : topology.getEndDevices()) {
                devicesBySwitch
                        .computeIfAbsent(device.getConnectedSwitch(), _ -> new ArrayList<>())
                        .add(device);
            }

            double verticalOffset = 70;

            for (Map.Entry<RSTPNode, List<EndDevice>> entry : devicesBySwitch.entrySet()) {
                RSTPNode switchNode = entry.getKey();
                List<EndDevice> devices = entry.getValue();

                Point2D switchPos = positions.get(switchNode);
                if (switchPos == null) continue;

                int deviceCount = devices.size();
                double spacing = Math.min(60, size.width * 0.8 / (deviceCount + 1));
                double startX = switchPos.getX() - (deviceCount - 1) * spacing / 2.0;

                for (int i = 0; i < deviceCount; i++) {
                    double x = startX + i * spacing;
                    double y = switchPos.getY() + verticalOffset;
                    positions.put(devices.get(i), new Point2D.Double(x, y));
                }
            }
        }

        @Override
        public Point2D apply(TopologyNode node) {
            return positions.getOrDefault(node, new Point2D.Double(400, 300));
        }
    }

    private void configureVisualization() {
        vv.setBackground(COLOR_BACKGROUND);

        vv.getRenderContext().setVertexFillPaintTransformer(renderer.createVertexPaintTransformer());
        vv.getRenderContext().setVertexShapeTransformer(renderer.createVertexShapeTransformer());
        vv.getRenderContext().setVertexLabelTransformer(renderer.createVertexLabelTransformer());
        vv.getRenderContext().setVertexStrokeTransformer(renderer.createVertexStrokeTransformer());
        vv.getRenderContext().setVertexDrawPaintTransformer(renderer.createVertexDrawPaintTransformer());

        vv.getRenderContext().setEdgeDrawPaintTransformer(renderer.createEdgePaintTransformer());
        vv.getRenderContext().setEdgeStrokeTransformer(renderer.createEdgeStrokeTransformer());
        vv.getRenderContext().setEdgeLabelTransformer(renderer.createEdgeLabelTransformer());
        vv.getRenderContext().setEdgeShapeTransformer(EdgeShape.line(graph));

        vv.getRenderer().getVertexLabelRenderer().setPosition(Renderer.VertexLabel.Position.CNTR);
        vv.getRenderContext().setVertexFontTransformer(_ -> renderer.getVertexLabelFont());
        vv.getRenderContext().setEdgeFontTransformer(_ -> renderer.getEdgeLabelFont());

        PickedState<TopologyNode> pickedState = vv.getPickedVertexState();
        pickedState.addItemListener(e -> {
            if (e.getStateChange() == ItemEvent.SELECTED) {
                TopologyNode node = (TopologyNode) e.getItem();
                if (node instanceof RSTPNode rstpNode) {
                    detailsPanel.setSelectedNode(rstpNode);
                } else if (node instanceof EndDevice endDevice) {
                    detailsPanel.setSelectedEndDevice(endDevice);
                }
            } else if (e.getStateChange() == ItemEvent.DESELECTED && pickedState.getPicked().isEmpty()) {
                    detailsPanel.showEmptyState();
            }
        });
    }

    private void setupMouseHandlers() {
        graphMouse = new DefaultModalGraphMouse<>();
        graphMouse.setMode(ModalGraphMouse.Mode.PICKING);
        vv.setGraphMouse(graphMouse);
        panModeButton.setSelected(false);

        vv.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    fitToWindow();
                }
            }
        });

        vv.setVertexToolTipTransformer(this::createTooltip);
    }

    private String createTooltip(TopologyNode node) {
        if (node == null) return null;

        if (node instanceof RSTPNode rstpNode) {
            return createSwitchTooltip(rstpNode);
        } else if (node instanceof EndDevice endDevice) {
            return createEndDeviceTooltip(endDevice);
        }
        return null;
    }

    private String createSwitchTooltip(RSTPNode node) {
        StringBuilder html = new StringBuilder("<html>");
        html.append(node.getDeviceName()).append("<br/>");
        html.append("IP: ").append(node.getIpAddress()).append("<br/>");

        if (node.isRootBridge()) {
            html.append("Root bridge<br/>");
        } else {
            html.append("Root Cost: ").append(node.getRootCost()).append("<br/>");
            html.append("Hops: ").append(node.getHopCount()).append("<br/>");
        }

        if (node.getVersion() != null) {
            html.append("Version: ").append(node.getVersion()).append("<br/>");
        }

        html.append("</html>");
        return html.toString();
    }

    private String createEndDeviceTooltip(EndDevice device) {
        StringBuilder html = new StringBuilder("<html><b>End Device</b><br/>");
        html.append("MAC: ").append(device.getMacAddress()).append("<br/>");
        html.append("Connected to: ").append(device.getConnectedSwitch().getDeviceName()).append("<br/>");
        html.append("Port: ").append(device.getConnectedPort()).append("<br/>");
        html.append("</html>");
        return html.toString();
    }

    private void zoomIn() {
        if (vv != null) {
            scaler.scale(vv, 1.2f, vv.getCenter());
        }
    }

    private void zoomOut() {
        if (vv != null) {
            scaler.scale(vv, 0.8f, vv.getCenter());
        }
    }

    private void fitToWindow() {
        if (vv == null || graph == null || graph.getVertexCount() == 0) return;

        Dimension viewSize = vv.getSize();
        Layout<TopologyNode, TopologyLink> layout = vv.getGraphLayout();

        double minX = Double.MAX_VALUE;
        double maxX = Double.MIN_VALUE;
        double minY = Double.MAX_VALUE;
        double maxY = Double.MIN_VALUE;

        for (TopologyNode node : graph.getVertices()) {
            Point2D point = layout.apply(node);
            minX = Math.min(minX, point.getX());
            maxX = Math.max(maxX, point.getX());
            minY = Math.min(minY, point.getY());
            maxY = Math.max(maxY, point.getY());
        }

        double graphWidth = maxX - minX + 100;
        double graphHeight = maxY - minY + 100;

        double scaleX = viewSize.width / graphWidth;
        double scaleY = viewSize.height / graphHeight;
        float scale = (float) Math.min(scaleX, scaleY) * 0.9f;

        vv.getRenderContext().getMultiLayerTransformer().getTransformer(edu.uci.ics.jung.visualization.Layer.LAYOUT).setScale(scale, scale, vv.getCenter());
        vv.repaint();
    }

    private void resetLayout() {
        if (topology != null) {
            buildAndDisplayGraph();
            detailsPanel.showEmptyState();
        }
    }
}
