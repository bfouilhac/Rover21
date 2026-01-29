package com.rover.services;

import com.jcraft.jsch.JSchException;
import com.rover.model.rstp.RSTPLink;
import com.rover.model.rstp.RSTPNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;

/**
 * Service for discovering physical links between switches using MAC address tables.
 *
 * @author Benjamin FOUILHAC, ADM21 - January 2026
 */
public class LinkDiscoveryService {

    private static final Logger logger = LoggerFactory.getLogger(LinkDiscoveryService.class);

    private final RSTPSSHService sshService;
    private final MACTableParser parser;

    public LinkDiscoveryService() {
        this.sshService = new RSTPSSHService();
        this.parser = new MACTableParser();
    }

    /**
     * Discovers links between nodes using MAC address tables.
     *
     * @param nodes list of network nodes
     * @return list of discovered links
     */
    public List<RSTPLink> discoverLinks(List<RSTPNode> nodes) {
        logger.info("Discovering links between {} nodes", nodes.size());

        Map<String, RSTPNode> macToNode = buildMacIndex(nodes);
        Map<RSTPNode, List<MACTableParser.MACTableEntry>> macTables = retrieveMacTables(nodes);

        List<RSTPLink> links = new ArrayList<>();
        Set<String> processedPairs = new HashSet<>();

        for (Map.Entry<RSTPNode, List<MACTableParser.MACTableEntry>> entry : macTables.entrySet()) {
            RSTPNode sourceNode = entry.getKey();
            List<MACTableParser.MACTableEntry> entries = entry.getValue();

            for (MACTableParser.MACTableEntry macEntry : entries) {
                String targetMac = macEntry.getMacAddress();
                RSTPNode targetNode = macToNode.get(targetMac);

                if (targetNode != null && !targetNode.equals(sourceNode)) {
                    String sourcePort = macEntry.getPort();
                    String targetPort = findRemotePort(targetNode, sourceNode.getDevice().getMacAddress(), macTables);

                    if (sourcePort != null && targetPort != null) {
                        String linkId = createLinkId(sourceNode, targetNode);
                        if (processedPairs.add(linkId)) {
                            RSTPLink link = new RSTPLink(
                                    sourceNode, extractPortNumber(sourcePort),
                                    targetNode, extractPortNumber(targetPort)
                            );
                            links.add(link);
                            logger.debug("Discovered link: {} [{}] <-> {} [{}]",
                                    sourceNode.getDeviceName(), sourcePort,
                                    targetNode.getDeviceName(), targetPort);
                        }
                    }
                }
            }
        }

        logger.info("Discovered {} links", links.size());
        return links;
    }

    /**
     * Builds an index mapping MAC addresses to nodes.
     */
    private Map<String, RSTPNode> buildMacIndex(List<RSTPNode> nodes) {
        Map<String, RSTPNode> index = new HashMap<>();
        for (RSTPNode node : nodes) {
            String mac = node.getDevice().getMacAddress().toUpperCase();
            index.put(mac, node);
        }
        return index;
    }

    /**
     * Retrieves MAC address tables for all nodes.
     */
    private Map<RSTPNode, List<MACTableParser.MACTableEntry>> retrieveMacTables(List<RSTPNode> nodes) {
        Map<RSTPNode, List<MACTableParser.MACTableEntry>> tables = new HashMap<>();

        for (RSTPNode node : nodes) {
            try {
                String output = sshService.executeMacTableCommand(node.getDevice());
                List<MACTableParser.MACTableEntry> entries = parser.parse(output);
                tables.put(node, entries);
                logger.debug("Retrieved {} MAC entries from {}", entries.size(), node.getDeviceName());
            } catch (IOException e) {
                logger.error("Failed to get MAC table from {}: {}", node.getDeviceName(), e.getMessage());
                tables.put(node, new ArrayList<>());
            } catch (JSchException | InterruptedException e) {
                logger.error("SSH connection failed for {}: {}", node.getDeviceName(), e.getMessage());
                Thread.currentThread().interrupt();
            }
        }

        return tables;
    }

    /**
     * Finds the remote port on the target node that connects back to the source.
     */
    private String findRemotePort(RSTPNode targetNode, String sourceMac,
                                  Map<RSTPNode, List<MACTableParser.MACTableEntry>> macTables) {
        List<MACTableParser.MACTableEntry> targetEntries = macTables.get(targetNode);
        if (targetEntries == null) return null;

        for (MACTableParser.MACTableEntry entry : targetEntries) {
            if (entry.getMacAddress().equalsIgnoreCase(sourceMac)) {
                return entry.getPort();
            }
        }

        return null;
    }

    /**
     * Creates a bidirectional link ID (sorted to avoid duplicates).
     */
    private String createLinkId(RSTPNode node1, RSTPNode node2) {
        List<String> ips = Arrays.asList(node1.getIpAddress(), node2.getIpAddress());
        Collections.sort(ips);
        return ips.get(0) + "<->" + ips.get(1);
    }

    /**
     * Extracts port number from the port string.
     * "gi2" -> 2, "gi1/2" -> 2
     */
    private int extractPortNumber(String port) {
        if (port == null) return 0;
        String[] parts = port.split("/");
        String lastPart = parts[parts.length - 1];
        return Integer.parseInt(lastPart.replace("\\D", ""));
    }
}