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
     *
     * @param devices list of devices to analyze
     * @return complete RSTP topology
     */
    public RSTPTopology discoverTopology(List<PlanetSwitchDevice> devices) {
        logger.info("Starting RSTP topology discovery for {} devices", devices.size());

        RSTPTopology topology = new RSTPTopology();

        // Filter devices with SSH and RSTP enabled
        List<PlanetSwitchDevice> rstpDevices = devices.stream()
                .filter(d -> d.isSSHUp() &&
                        d.getUsername() != null &&
                        d.getPassword() != null &&
                        sshService.isRSTPEnabled(d))
                .toList();

        if (rstpDevices.isEmpty()) {
            logger.warn("No devices with RSTP enabled found");
            return topology;
        }

        logger.info("Found {} devices with RSTP enabled", rstpDevices.size());

        // Parallelize discovery for speed (Java 19+ try-with-resources)
        try (ExecutorService executor = Executors.newFixedThreadPool(Math.min(5, rstpDevices.size()))) {
            List<Future<RSTPNode>> futures = new ArrayList<>();

            for (PlanetSwitchDevice device : rstpDevices) {
                futures.add(executor.submit(() -> {
                    try {
                        logger.info("Attempting SSH connection to {}", device.getIpAddress());
                        RSTPNode node = sshService.getRSTPInfo(device);
                        logger.info("Successfully retrieved RSTP info from {}", device.getIpAddress());
                        return node;
                    } catch (IOException e) {
                        logger.error("Failed to get RSTP info for {} ({}): {}",
                                device.getDeviceName(), device.getIpAddress(), e.getMessage());
                        return null;
                    }
                }));
            }

            // Collect results with a longer timeout
            for (Future<RSTPNode> future : futures) {
                try {
                    RSTPNode node = future.get(30, TimeUnit.SECONDS);
                    if (node != null) {
                        topology.addNode(node);
                        logger.debug("Added node: {}", node.getDeviceName());
                    }
                } catch (Exception e) {
                    logger.error("Failed to get node", e);
                    Thread.currentThread().interrupt();
                }
            }
        } // ExecutorService automatically closed here

        identifyRootBridge(topology);

        logger.info("Topology discovery complete: {} nodes", topology.getNodeCount());

        return topology;
    }

    /**
     * Identifies the root bridge in the topology.
     */
    private void identifyRootBridge(RSTPTopology topology) {
        RSTPNode rootBridge = null;
        for (RSTPNode node : topology.getNodes()) { // Root bridge has cost 0 to itself
            if (node.getRootCost() == 0) {
                rootBridge = node;
                break;
            }
        }

        if (rootBridge != null) {
            topology.setRootBridge(rootBridge);
            logger.info("Root bridge identified: {}", rootBridge.getDeviceName());
        } else {
            logger.warn("No root bridge found in topology");
        }
    }
}