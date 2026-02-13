package rover.services;

import rover.model.PlanetSwitchDevice;
import rover.model.rstp.EndDevice;
import rover.model.rstp.EndDeviceLink;
import rover.model.rstp.MacAddressEntry;
import rover.model.rstp.RSTPLink;
import rover.model.rstp.RSTPNode;
import rover.model.rstp.RSTPTopology;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Service for discovering and building RSTP topology via SSH.
 * Handles both RSTP information retrieval and LLDP link discovery.
 *
 * @author Benjamin FOUILHAC, ADM21 - January/February 2026
 */
public class RSTPDiscoveryService {

    private static final Logger logger = LoggerFactory.getLogger(RSTPDiscoveryService.class);

    /**
     * Callback interface for real-time discovery progress updates.
     */
    public interface DiscoveryProgressCallback {
        void onNodeDiscovered(RSTPNode node);
        void onLinkDiscovered(RSTPLink link);
        void onEndDeviceDiscovered(EndDevice endDevice, EndDeviceLink link);
        void onProgress(String messageKey);
    }

    private final RSTPSSHService sshService;
    private final LinkDiscoveryService linkDiscoveryService;
    private DiscoveryProgressCallback progressCallback;

    public RSTPDiscoveryService() {
        this.sshService = new RSTPSSHService();
        this.linkDiscoveryService = new LinkDiscoveryService(sshService);
    }

    /**
     * Sets the callback for real-time progress updates.
     *
     * @param callback the callback to notify on discoveries
     */
    public void setProgressCallback(DiscoveryProgressCallback callback) {
        this.progressCallback = callback;
    }

    /**
     * Discovers RSTP topology from a list of devices.
     * <p>
     * Two-phase discovery:
     * 1. RSTP information retrieval for each device (parallel)
     * 2. LLDP link discovery between devices (sequential per device, but can overlap)
     * </p>
     *
     * @param devices list of devices to analyze
     * @return complete RSTP topology
     */
    public RSTPTopology discoverTopology(List<PlanetSwitchDevice> devices) {
        RSTPTopology topology = new RSTPTopology();

        // Filter SSH-capable devices
        List<PlanetSwitchDevice> sshDevices = devices.stream()
                .filter(d -> d.isSSHUp() && d.getUsername() != null && d.getPassword() != null)
                .toList();

        if (sshDevices.isEmpty()) {
            logger.warn("No SSH-capable devices to discover");
            return topology;
        }

        // Use limited thread pool to avoid overwhelming devices
        ExecutorService executor = Executors.newFixedThreadPool(Math.min(sshDevices.size(), 5));

        try {
            // Phase 1: Discover RSTP info for each device
            notifyProgress("status.topology_discovering");
            logger.info("Phase 1: Starting RSTP discovery for {} devices", sshDevices.size());

            List<Future<RSTPNode>> futures = new ArrayList<>();
            for (PlanetSwitchDevice device : sshDevices) {
                futures.add(executor.submit(() -> fetchAndEnrichNode(device)));
            }

            for (Future<RSTPNode> future : futures) {
                try {
                    RSTPNode node = future.get(20, TimeUnit.SECONDS);
                    if (node != null) {
                        topology.addNode(node);
                        notifyNodeDiscovered(node);
                    }
                } catch (Exception e) {
                    logger.error("Error fetching node details: {}", e.getMessage());
                }
            }

            logger.info("Phase 1 complete: {} RSTP nodes discovered", topology.getNodeCount());

            // Phase 2: Discover links via LLDP in parallel (only if we have multiple nodes)
            if (topology.isValid() && topology.getNodeCount() > 1) {
                notifyProgress("status.topology_discovering_links");
                logger.info("Phase 2: Starting parallel LLDP link discovery...");

                // Use the callback version to notify UI as links are discovered
                List<RSTPLink> links = linkDiscoveryService.discoverLinks(
                        topology.getNodes(),
                        link -> {
                            topology.addLink(link);
                            notifyLinkDiscovered(link);
                        }
                );

                logger.info("Phase 2 complete: {} unique links discovered", links.size());
            }

            // Phase 3: Identify root bridge
            identifyRootBridge(topology);

            // Phase 4: Discover end devices via MAC address table
            if (topology.isValid()) {
                notifyProgress("status.topology_discovering_enddevices");
                logger.info("Phase 4: Starting end device discovery via MAC address table...");
                discoverEndDevices(topology, devices);
                logger.info("Phase 4 complete: {} end devices discovered", topology.getEndDeviceCount());
            }

        } finally {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        return topology;
    }

    /**
     * Fetches and enriches a node with RSTP information.
     * First checks if RSTP is enabled, then retrieves full RSTP details.
     */
    private RSTPNode fetchAndEnrichNode(PlanetSwitchDevice device) {
        try {
            if (sshService.isRSTPEnabled(device)) {
                logger.debug("RSTP enabled on {}, retrieving info...", device.getIpAddress());
                return sshService.getRSTPInfo(device);
            } else {
                logger.debug("RSTP not enabled on {}", device.getIpAddress());
            }
        } catch (Exception e) {
            logger.error("Failed to get RSTP info for {}: {}", device.getIpAddress(), e.getMessage());
        }
        return null;
    }

    private void notifyProgress(String messageKey) {
        if (progressCallback != null) {
            progressCallback.onProgress(messageKey);
        }
    }

    private void notifyNodeDiscovered(RSTPNode node) {
        if (progressCallback != null) {
            progressCallback.onNodeDiscovered(node);
        }
    }

    private void notifyLinkDiscovered(RSTPLink link) {
        if (progressCallback != null) {
            progressCallback.onLinkDiscovered(link);
        }
    }

    /**
     * Identifies the root bridge in the topology.
     * The root bridge is either:
     * 1. The device with rootCost = 0 (it IS the root bridge)
     * 2. The device whose rootBridgeId matches its own bridgeId
     * 3. The device with the lowest bridge priority (if the root is not in our topology)
     */
    private void identifyRootBridge(RSTPTopology topology) {
        RSTPNode rootBridge = null;

        // First, check if any device has cost 0 (it IS the root bridge)
        for (RSTPNode node : topology.getNodes()) {
            if (node.getRootCost() == 0) {
                rootBridge = node;
                logger.info("Root bridge identified by cost=0: {}", rootBridge.getDeviceName());
                break;
            }
        }

        // If not found, check if any device's bridgeId matches its rootBridgeId
        if (rootBridge == null) {
            for (RSTPNode node : topology.getNodes()) {
                if (node.getBridgeId() != null && node.getBridgeId().equals(node.getRootBridgeId())) {
                    rootBridge = node;
                    logger.info("Root bridge identified by matching Bridge ID: {}", rootBridge.getDeviceName());
                    break;
                }
            }
        }

        if (rootBridge != null) {
            topology.setRootBridge(rootBridge);
        } else {
            logger.warn("No root bridge found in discovered topology (root bridge may be on another device)");
        }
    }

    /** Maximum threads for parallel MAC table discovery */
    private static final int MAX_MAC_DISCOVERY_THREADS = 10;

    /** Timeout per device for MAC table query (seconds) */
    private static final int MAC_DISCOVERY_TIMEOUT_SECONDS = 15;

    /**
     * Discovers end devices by analyzing MAC address tables in parallel.
     * <p>
     * An end device is identified when:
     * - A port has MAC addresses in the MAC table
     * - That port does NOT have an LLDP neighbor (meaning it's not a switch)
     * - The MAC address is not from another known switch
     * </p>
     *
     * @param topology the topology to enrich with end devices
     * @param discoveredDevices the list of the devices found during the discovery
     */
    private void discoverEndDevices(RSTPTopology topology, List<PlanetSwitchDevice> discoveredDevices) {
        List<RSTPNode> nodes = topology.getNodes();
        if (nodes.isEmpty()) return;

        // Build lookup sets (read-only, shared across threads)
        Set<String> portsWithLldpNeighbors = buildLldpPortsSet(topology);
        Set<String> knownSwitchMacs = buildKnownSwitchMacsSet(topology);

        // Thread-safe collection for discovered devices
        Map<String, EndDevice> topologyDevices = new ConcurrentHashMap<>();

        // Parallel MAC table discovery
        int poolSize = Math.min(nodes.size(), MAX_MAC_DISCOVERY_THREADS);
        ExecutorService executor = Executors.newFixedThreadPool(poolSize);

        try {
            List<Future<?>> futures = new ArrayList<>();

            for (RSTPNode node : nodes) {
                futures.add(executor.submit(() ->
                    discoverEndDevicesForNode(node, portsWithLldpNeighbors, knownSwitchMacs, topologyDevices, topology, discoveredDevices)
                ));
            }

            // Collect results with timeout
            for (int i = 0; i < futures.size(); i++) {
                try {
                    futures.get(i).get(MAC_DISCOVERY_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                } catch (TimeoutException e) {
                    logger.warn("MAC table discovery timed out for node {}", nodes.get(i).getDeviceName());
                } catch (Exception e) {
                    logger.error("MAC table discovery failed for node {}: {}",
                            nodes.get(i).getDeviceName(), e.getMessage());
                }
            }

            logger.info("Parallel MAC discovery completed: {} end devices found", discoveredDevices.size());

        } finally {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(2, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Discovers end devices for a single node (called from thread pool).
     */
    private void discoverEndDevicesForNode(RSTPNode node, Set<String> portsWithLldpNeighbors, Set<String> knownSwitchMacs, Map<String, EndDevice> topologyDevices, RSTPTopology topology, List<PlanetSwitchDevice> discoveredDevices) {

        List<String> discoveredMAC = discoveredDevices.stream().map(PlanetSwitchDevice::getMacAddress).toList();

        try {
            List<MacAddressEntry> macEntries = sshService.getMacAddressTable(node.getDevice());

            for (MacAddressEntry entry : macEntries) {
                if (!entry.isDynamic() || !entry.isOnPhysicalPort()) {
                    continue;
                }

                String portKey = node.getIpAddress() + ":" + entry.port();

                // Skip if this port has an LLDP neighbor (connected to another switch)
                if (portsWithLldpNeighbors.contains(portKey)) {
                    continue;
                }

                // Skip if this MAC belongs to a known switch
                if (knownSwitchMacs.contains(entry.macAddress())) {
                    continue;
                }

                // Use MAC as a key to avoid duplicates across VLANs
                String deviceKey = entry.macAddress();

                if (!deviceKey.startsWith("A8:F7:E0") || (discoveredMAC.contains(deviceKey))) {
                    EndDevice endDevice = new EndDevice(entry.macAddress(), node, entry.port());

                    if (discoveredMAC.contains(deviceKey)) {
                        Optional<PlanetSwitchDevice> found = Optional.empty();
                        for (PlanetSwitchDevice d : discoveredDevices) {
                            if (d.getMacAddress().equals(deviceKey)) {
                                found = Optional.of(d);
                                break;
                            }
                        }
                        found.ifPresent(planetSwitchDevice -> endDevice.setName(planetSwitchDevice.getDeviceName()));
                    } else {
                        endDevice.setName("ICMP");
                    }

                    topologyDevices.put(deviceKey, endDevice);

                    // Thread-safe add to topology (synchronized internally)
                    synchronized (topology) {
                        topology.addEndDevice(endDevice);
                        EndDeviceLink link = new EndDeviceLink(node, entry.port(), endDevice);
                        topology.addEndDeviceLink(link);
                        notifyEndDeviceDiscovered(endDevice, link);
                    }

                    logger.debug("Discovered end device: {} on {}:{}", entry.macAddress(), node.getDeviceName(), entry.port());
                }
            }

        } catch (Exception e) {
            logger.error("Failed to get MAC table from {}: {}", node.getDeviceName(), e.getMessage());
        }
    }

    /**
     * Builds a set of ports that have LLDP neighbors (switch-to-switch links).
     */
    private Set<String> buildLldpPortsSet(RSTPTopology topology) {
        Set<String> ports = new HashSet<>();
        for (RSTPLink link : topology.getLinks()) {
            ports.add(link.sourceNode().getIpAddress() + ":" + link.sourcePort());
            ports.add(link.destinationNode().getIpAddress() + ":" + link.destinationPort());
        }
        return ports;
    }

    /**
     * Builds a set of known switch MAC addresses.
     */
    private Set<String> buildKnownSwitchMacsSet(RSTPTopology topology) {
        Set<String> macs = new HashSet<>();
        for (RSTPNode node : topology.getNodes()) {
            String mac = node.getDevice().getMacAddress();
            if (mac != null) {
                macs.add(mac.toUpperCase().replace("-", ":"));
            }
        }
        return macs;
    }

    private void notifyEndDeviceDiscovered(EndDevice endDevice, EndDeviceLink link) {
        if (progressCallback != null) {
            progressCallback.onEndDeviceDiscovered(endDevice, link);
        }
    }

    /**
     * Shuts down the discovery service and cleans up all resources.
     * This includes closing SSH connections and clearing callbacks.
     */
    public void shutdown() {
        logger.info("Shutting down RSTPDiscoveryService");
        progressCallback = null; // Clear callback to prevent stale references
        sshService.shutdown();
        logger.info("RSTPDiscoveryService shutdown complete");
    }
}