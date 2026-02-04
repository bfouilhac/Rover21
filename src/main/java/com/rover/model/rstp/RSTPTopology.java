package com.rover.model.rstp;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents the complete RSTP topology of the network.
 * <p>
 * Contains all switches (nodes) and their interconnections (links),
 * along with the identification of the root bridge.
 * </p>
 *
 * @author Benjamin FOUILHAC, ADM21 - January 2026
 */
public class RSTPTopology {

    private final List<RSTPNode> nodes;
    private final List<RSTPLink> links;
    private RSTPNode rootBridge;
    private final long discoveryTimestamp;

    public RSTPTopology() {
        this.nodes = new ArrayList<>();
        this.links = new ArrayList<>();
        this.discoveryTimestamp = System.currentTimeMillis();
    }

    public List<RSTPNode> getNodes() {return nodes;}

    public void addNode(RSTPNode node) {
        if (!nodes.contains(node)) {
            nodes.add(node);
        }
    }

    public void setRootBridge(RSTPNode rootBridge) {
        this.rootBridge = rootBridge;
        if (rootBridge != null) {
            rootBridge.setRootBridge(true);
        }
    }

    /**
     * Returns all discovered links in the topology.
     *
     * @return defensive copy of the links list
     */
    public List<RSTPLink> getLinks() {
        return new ArrayList<>(links);
    }

    /**
     * Adds a link to the topology if not already present.
     *
     * @param link the link to add
     */
    public void addLink(RSTPLink link) {
        if (link != null && !links.contains(link)) {
            links.add(link);
        }
    }

    /**
     * Checks if the topology is valid (has at least one node).
     */
    public boolean isValid() {return !nodes.isEmpty();}

    /**
     * Gets the number of nodes in the topology.
     */
    public int getNodeCount() {return nodes.size();}

    @Override
    public String toString() {
        return String.format("RstpTopology{nodes=%d, links=%d, root=%s, timestamp=%d}",
                nodes.size(), links.size(),
                (rootBridge != null ? rootBridge.getDeviceName() : "none"),
                discoveryTimestamp);
    }
}