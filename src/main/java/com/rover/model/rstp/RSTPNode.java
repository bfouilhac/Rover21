package com.rover.model.rstp;

import com.rover.model.PlanetSwitchDevice;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a network switch node in the RSTP topology.
 * <p>
 * Contains all RSTP-related information for a single switch, including
 * bridge ID, root bridge information, port states, and MAC address table.
 * </p>
 *
 * @author Benjamin FOUILHAC, ADM21 - January 2026
 */
public class RSTPNode {

    private final PlanetSwitchDevice device;
    private String bridgeId;
    private String rootBridgeId;
    private int rootCost;
    private int hopCount;
    private boolean isRootBridge;
    private RSTPVersion version;
    private String topologyChangeTime;

    private List<RSTPPort> ports;

    public RSTPNode(PlanetSwitchDevice device) {
        this.device = device;
        this.ports = new ArrayList<>();
        this.isRootBridge = false;
        this.version = RSTPVersion.UNKNOWN;
    }

    public boolean isRootBridge() {return this.isRootBridge;}

    public PlanetSwitchDevice getDevice() {
        return device;
    }

    public String getDeviceName() {
        return device.getDeviceName();
    }

    public String getIpAddress() {
        return device.getIpAddress();
    }

    public String getBridgeId() {
        return bridgeId;
    }

    public void setBridgeId(String bridgeId) {
        this.bridgeId = bridgeId;
    }

    public String getRootBridgeId() {
        return rootBridgeId;
    }

    public void setRootBridgeId(String rootBridgeId) {
        this.rootBridgeId = rootBridgeId;
        this.isRootBridge = (bridgeId != null && bridgeId.equals(rootBridgeId));
    }

    public int getRootCost() {
        return rootCost;
    }

    public void setRootCost(int rootCost) {
        this.rootCost = rootCost;
    }

    public int getHopCount() {
        return hopCount;
    }

    public void setHopCount(int hopCount) {
        this.hopCount = hopCount;
    }

    public void setRootBridge(boolean rootBridge) {isRootBridge = rootBridge;}

    public RSTPVersion getVersion() {return version;}

    public void setVersion(RSTPVersion version) {
        this.version = version;
    }

    public String getTopologyChangeTime() {
        return topologyChangeTime;
    }

    public void setTopologyChangeTime(String topologyChangeTime) {
        this.topologyChangeTime = topologyChangeTime;
    }

    public List<RSTPPort> getPorts() {
        return ports;
    }

    public void setPorts(List<RSTPPort> ports) {
        this.ports = ports;
    }

    @Override
    public String toString() {
        return String.format("RstpNode{device=%s, bridgeId=%s, isRoot=%b, ports=%d}", device.getDeviceName(), bridgeId, isRootBridge, ports.size());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RSTPNode rstpNode = (RSTPNode) o;
        return device.equals(rstpNode.device);
    }

    @Override
    public int hashCode() {
        return device.hashCode();
    }
}