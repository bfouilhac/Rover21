package rover.model.rstp;

/**
 * Interface representing a link in the topology graph.
 * <p>
 * Implemented by both RSTPLink (switch-to-switch) and EndDeviceLink (switch-to-device)
 * to allow unified graph visualization.
 * </p>
 *
 * @author Benjamin FOUILHAC, ADM21 - February 2026
 */
public interface TopologyLink {

    /**
     * Returns the source node of this link.
     *
     * @return the source topology node
     */
    TopologyNode getSourceNode();

    /**
     * Returns the destination node of this link.
     *
     * @return the destination topology node
     */
    TopologyNode getDestinationNode();

    /**
     * Returns the source port number.
     *
     * @return source port number
     */
    int getSourcePort();

    /**
     * Returns the destination port number.
     * For end device links, this returns 0 (not applicable).
     *
     * @return destination port number
     */
    int getDestinationPort();

    /**
     * Checks if this is a switch-to-switch link.
     *
     * @return true if both endpoints are switches
     */
    boolean isSwitchLink();

    /**
     * Checks if the link is active (forwarding traffic).
     *
     * @return true if the link is active
     */
    boolean isActive();
}
