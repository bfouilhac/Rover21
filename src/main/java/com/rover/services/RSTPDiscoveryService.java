package com.rover.services;

import com.rover.model.PlanetSwitchDevice;
import com.rover.model.rstp.RSTPNode;
import com.rover.model.rstp.RSTPTopology;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Service for discovering and building RSTP topology via SSH.
 * Simplified version that only retrieves device information.
 *
 * @author Benjamin FOUILHAC, ADM21 - January 2026
 */
public class RSTPDiscoveryService {

    private static final Logger logger = LoggerFactory.getLogger(RSTPDiscoveryService.class);

    private final RSTPSSHService sshService;

    public RSTPDiscoveryService() {
        this.sshService = new RSTPSSHService();
    }

    /**
     * Discovers RSTP topology from a list of devices.
     * <p>
     * Optimized version using adaptive thread pool where each thread handles:
     * 1. RSTP version check
     * 2. Information retrieval if RSTP is enabled
     * </p>
     *
     * @param devices list of devices to analyze
     * @return complete RSTP topology
     */
    public RSTPTopology discoverTopology(List<PlanetSwitchDevice> devices) {
        logger.info("Starting RSTP topology discovery for {} devices", devices.size());

        RSTPTopology topology = new RSTPTopology();

        // Filter devices with SSH capabilities
        List<PlanetSwitchDevice> sshDevices = devices.stream()
                .filter(d -> d.isSSHUp() &&
                        d.getUsername() != null &&
                        d.getPassword() != null)
                .toList();

        if (sshDevices.isEmpty()) {
            logger.warn("No devices with SSH available");
            return topology;
        }

        logger.info("Found {} SSH-capable devices, checking RSTP in parallel", sshDevices.size());

        // Adaptive thread pool: min(device count, 20 threads max)
        int threadPoolSize = Math.min(sshDevices.size(), 20);
        logger.info("Creating adaptive thread pool with {} threads", threadPoolSize);

        try (ExecutorService executor = Executors.newFixedThreadPool(threadPoolSize)) {
            List<Future<RSTPNode>> futures = new ArrayList<>();

            // Each thread handles complete device processing
            for (PlanetSwitchDevice device : sshDevices) {
                futures.add(executor.submit(() -> processDevice(device)));
            }

            // Collect results with timeout
            for (Future<RSTPNode> future : futures) {
                try {
                    RSTPNode node = future.get(30, TimeUnit.SECONDS);
                    if (node != null) {
                        topology.addNode(node);
                        logger.info("Added node to topology: {} (Bridge: {}, Root: {})",
                            node.getDeviceName(), node.getBridgeId(), node.getRootBridgeId());
                    }
                } catch (Exception e) {
                    logger.error("Failed to get node from future: {}", e.getMessage());
                    Thread.currentThread().interrupt();
                }
            }
        } // ExecutorService automatically closed here

        identifyRootBridge(topology);

        logger.info("Topology discovery complete: {} nodes", topology.getNodeCount());

        return topology;
    }

    /**
     * Processes a single device: checks RSTP enablement and retrieves info if enabled.
     * <p>
     * This method is called by each worker thread in the pool.
     * </p>
     *
     * @param device the device to process
     * @return RSTPNode if RSTP is enabled and info retrieved successfully, null otherwise
     */
    private RSTPNode processDevice(PlanetSwitchDevice device) {
        try {
            logger.info("Processing device: {} ({})", device.getDeviceName(), device.getIpAddress());

            // Step 1: Check if RSTP is enabled
            boolean rstpEnabled = sshService.isRSTPEnabled(device);

            if (!rstpEnabled) {
                logger.info("RSTP not enabled on {} ({})", device.getDeviceName(), device.getIpAddress());
                return null;
            }

            logger.info("RSTP enabled on {}, retrieving info...", device.getIpAddress());

            // Step 2: Retrieve RSTP information
            RSTPNode node = sshService.getRSTPInfo(device);

            if (node != null) {
                logger.info("Successfully retrieved RSTP info from {} - Bridge: {}, Root: {}, Cost: {}",
                    device.getIpAddress(), node.getBridgeId(), node.getRootBridgeId(), node.getRootCost());
            } else {
                logger.warn("getRSTPInfo returned null for {}", device.getIpAddress());
            }

            return node;

        } catch (Exception e) {
            logger.error("Failed to process device {} ({}): {}", device.getDeviceName(), device.getIpAddress(), e.getMessage());
            return null;
        }
    }

    /**
     * Identifies the root bridge in the topology.
     * The root bridge is either:
     * 1. The device with rootCost = 0 (it IS the root bridge)
     * 2. The device whose rootBridgeId matches its own bridgeId
     * 3. The device with the lowest bridge priority (if root is not in our topology)
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