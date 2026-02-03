package com.rover.services;

import com.rover.model.PlanetSwitchDevice;
import com.rover.model.rstp.RSTPLink;
import com.rover.model.rstp.RSTPNode;
import com.rover.model.rstp.RSTPTopology;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

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

                // Use callback version to notify UI as links are discovered
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

    /**
     * Shuts down the discovery service and cleans up all resources.
     * This includes closing SSH connections managed by the SSH service.
     */
    public void shutdown() {
        logger.info("Shutting down RSTPDiscoveryService");
        sshService.shutdown();
        logger.info("RSTPDiscoveryService shutdown complete");
    }
}