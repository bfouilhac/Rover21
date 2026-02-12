package rover.model.rstp;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents the complete RSTP topology of the network.
 * <p>
 * Contains all switches (nodes), their interconnections (links),
 * end devices (PCs, printers, etc.), and the identification of the root bridge.
 * </p>
 *
 * @author Benjamin FOUILHAC, ADM21 - January/February 2026
 */
public class RSTPTopology {

    private final List<RSTPNode> nodes;
    private final List<RSTPLink> links;
    private final List<EndDevice> endDevices;
    private final List<EndDeviceLink> endDeviceLinks;
    private RSTPNode rootBridge;

    public RSTPTopology() {
        this.nodes = new ArrayList<>();
        this.links = new ArrayList<>();
        this.endDevices = new ArrayList<>();
        this.endDeviceLinks = new ArrayList<>();
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

    /**
     * Returns all discovered end devices in the topology.
     *
     * @return defensive copy of the end devices list
     */
    public List<EndDevice> getEndDevices() {
        return new ArrayList<>(endDevices);
    }

    /**
     * Adds an end device to the topology if not already present.
     *
     * @param device the end device to add
     */
    public void addEndDevice(EndDevice device) {
        if (device != null && !endDevices.contains(device)) {
            endDevices.add(device);
        }
    }

    /**
     * Returns all end device links in the topology.
     *
     * @return defensive copy of the end device links list
     */
    public List<EndDeviceLink> getEndDeviceLinks() {
        return new ArrayList<>(endDeviceLinks);
    }

    /**
     * Adds an end device link to the topology if not already present.
     *
     * @param link the end device link to add
     */
    public void addEndDeviceLink(EndDeviceLink link) {
        if (link != null && !endDeviceLinks.contains(link)) {
            endDeviceLinks.add(link);
        }
    }

    /**
     * Gets the number of end devices in the topology.
     */
    public int getEndDeviceCount() {
        return endDevices.size();
    }

    @Override
    public String toString() {
        return String.format("RstpTopology{nodes=%d, links=%d, endDevices=%d, root=%s}",
                nodes.size(), links.size(), endDevices.size(),
                (rootBridge != null ? rootBridge.getDeviceName() : "none"));
    }
}