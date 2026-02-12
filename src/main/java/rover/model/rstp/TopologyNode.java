package rover.model.rstp;

/**
 * Interface representing a node in the topology graph.
 * <p>
 * Implemented by both RSTPNode (switches) and EndDevice (PCs, printers, etc.)
 * to allow unified graph visualization.
 * </p>
 *
 * @author Benjamin FOUILHAC, ADM21 - February 2026
 */
public interface TopologyNode {

    /**
     * Returns the display label for this node.
     *
     * @return the label to display in the graph
     */
    String getDisplayLabel();

    /**
     * Returns a unique identifier for this node.
     *
     * @return unique identifier string
     */
    String getUniqueId();

    /**
     * Checks if this node is a switch.
     *
     * @return true if this is a switch (RSTPNode), false if end device
     */
    boolean isSwitch();

    /**
     * Checks if this node is the root bridge.
     * Only applicable for switches.
     *
     * @return true if this is the root bridge
     */
    default boolean isRootBridge() {
        return false;
    }
}
