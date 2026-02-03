package com.rover.services;

import com.rover.model.rstp.LLDPEntry;
import com.rover.model.rstp.RSTPLink;
import com.rover.model.rstp.RSTPNode;
import com.rover.utils.LLDPParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * High-level service for network topology discovery.
 * <p>
 * Uses LLDP data to identify physical connections between switches.
 * Discovery is performed in parallel with a configurable thread pool.
 * </p>
 *
 * @author Benjamin FOUILHAC, ADM21 - February 2026
 */
public class LinkDiscoveryService {

    private static final Logger logger = LoggerFactory.getLogger(LinkDiscoveryService.class);

    /** Maximum number of threads for parallel LLDP discovery */
    private static final int MAX_THREAD_POOL_SIZE = 20;

    /** Timeout per device for LLDP query (seconds) */
    private static final int DISCOVERY_TIMEOUT_SECONDS = 30;

    private final RSTPSSHService sshService;

    public LinkDiscoveryService(RSTPSSHService sshService) {
        this.sshService = sshService;
    }

    /**
     * Discovers links between nodes using LLDP, with parallel execution.
     *
     * @param nodes the list of nodes to query
     * @return list of discovered links (deduplicated)
     */
    public List<RSTPLink> discoverLinks(List<RSTPNode> nodes) {
        return discoverLinks(nodes, null);
    }

    /**
     * Discovers links between nodes using LLDP, with parallel execution and progress callback.
     *
     * @param nodes the list of nodes to query
     * @param onLinkDiscovered callback invoked for each newly discovered link (can be null)
     * @return list of discovered links (deduplicated)
     */
    public List<RSTPLink> discoverLinks(List<RSTPNode> nodes, Consumer<RSTPLink> onLinkDiscovered) {
        if (nodes == null || nodes.isEmpty()) {
            return Collections.emptyList();
        }

        logger.info("Starting parallel LLDP discovery for {} nodes", nodes.size());
        long startTime = System.currentTimeMillis();

        // Build MAC lookup map (read-only, shared across threads)
        Map<String, RSTPNode> macMap = buildMacMap(nodes);

        // Create thread pool: min(nodes.size(), MAX_THREAD_POOL_SIZE)
        int poolSize = Math.min(nodes.size(), MAX_THREAD_POOL_SIZE);
        ExecutorService executor = Executors.newFixedThreadPool(poolSize);

        try {
            // Submit discovery tasks for each node
            List<Future<List<RSTPLink>>> futures = new ArrayList<>();
            for (RSTPNode srcNode : nodes) {
                futures.add(executor.submit(() -> discoverLinksForNode(srcNode, macMap)));
            }

            // Collect and deduplicate results
            List<RSTPLink> allLinks = new ArrayList<>();
            Set<String> seen = new HashSet<>();

            for (int i = 0; i < futures.size(); i++) {
                Future<List<RSTPLink>> future = futures.get(i);
                RSTPNode srcNode = nodes.get(i);

                try {
                    List<RSTPLink> nodeLinks = future.get(DISCOVERY_TIMEOUT_SECONDS, TimeUnit.SECONDS);

                    for (RSTPLink link : nodeLinks) {
                        String key = createLinkKey(link);
                        if (seen.add(key)) {
                            allLinks.add(link);
                            // Notify callback if provided
                            if (onLinkDiscovered != null) {
                                onLinkDiscovered.accept(link);
                            }
                        }
                    }

                    logger.debug("LLDP discovery for {} returned {} links",
                            srcNode.getDeviceName(), nodeLinks.size());

                } catch (TimeoutException e) {
                    logger.warn("LLDP discovery timed out for {} after {}s",
                            srcNode.getDeviceName(), DISCOVERY_TIMEOUT_SECONDS);
                } catch (ExecutionException e) {
                    logger.error("LLDP discovery failed for {}: {}",
                            srcNode.getDeviceName(), e.getCause().getMessage());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    logger.warn("LLDP discovery interrupted");
                    break;
                }
            }

            long elapsed = System.currentTimeMillis() - startTime;
            logger.info("LLDP discovery completed: {} links found in {}ms (parallel with {} threads)",
                    allLinks.size(), elapsed, poolSize);

            return allLinks;

        } finally {
            shutdownExecutor(executor);
        }
    }

    /**
     * Builds a MAC address to node lookup map.
     */
    private Map<String, RSTPNode> buildMacMap(List<RSTPNode> nodes) {
        Map<String, RSTPNode> macMap = new HashMap<>();
        for (RSTPNode node : nodes) {
            String mac = LLDPParser.normalizeMac(node.getDevice().getMacAddress());
            macMap.put(mac, node);
        }
        return macMap;
    }

    /**
     * Discovers links for a single node by querying its LLDP neighbors.
     * This method is called from worker threads.
     *
     * @param srcNode the source node to query
     * @param macMap the MAC to node lookup map (read-only)
     * @return list of links found from this node
     */
    private List<RSTPLink> discoverLinksForNode(RSTPNode srcNode, Map<String, RSTPNode> macMap) {
        List<RSTPLink> links = new ArrayList<>();

        try {
            String output = sshService.getLLDPNeighborInfo(srcNode.getDevice());
            List<LLDPEntry> entries = LLDPParser.parse(output);

            for (LLDPEntry entry : entries) {
                RSTPNode dstNode = macMap.get(entry.remoteMac());

                if (dstNode != null && !srcNode.equals(dstNode)) {
                    int srcPort = extractPort(entry.localPort());
                    int dstPort = extractPort(entry.remotePort());
                    links.add(new RSTPLink(srcNode, srcPort, dstNode, dstPort));
                }
            }

        } catch (Exception e) {
            logger.error("LLDP query failed for {}: {}", srcNode.getDeviceName(), e.getMessage());
        }

        return links;
    }

    /**
     * Gracefully shuts down the executor service.
     */
    private void shutdownExecutor(ExecutorService executor) {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                logger.warn("LLDP executor did not terminate in time, forcing shutdown");
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Creates a unique key for a link to prevent duplicates.
     * The key is symmetric: A-B and B-A produce the same key.
     */
    private String createLinkKey(RSTPLink link) {
        String m1 = link.getSourceNode().getDevice().getMacAddress();
        String m2 = link.getDestinationNode().getDevice().getMacAddress();
        int p1 = link.getSourcePort();
        int p2 = link.getDestinationPort();

        return (m1.compareTo(m2) < 0)
                ? m1 + p1 + m2 + p2
                : m2 + p2 + m1 + p1;
    }

    /**
     * Extracts the port number from a port identifier string.
     * Examples: "gi1" -> 1, "Ethernet1/0/5" -> 5
     */
    private int extractPort(String portString) {
        if (portString == null) return 0;

        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("(\\d+)$")
                .matcher(portString.trim());

        return matcher.find() ? Integer.parseInt(matcher.group(1)) : 0;
    }
}