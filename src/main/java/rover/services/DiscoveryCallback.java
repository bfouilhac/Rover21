package rover.services;

import rover.model.PlanetSwitchDevice;
import rover.controller.MainController;

/**
 * Callback interface for real-time device discovery notifications.
 * <p>
 * Implementations of this interface receive updates as devices are discovered
 * and enriched with additional data during the network scanning process.
 * This allows the UI to be updated asynchronously as discovery operations
 * progress, without blocking the main discovery thread.
 * </p>
 * <p>
 * Thread Safety Note: All callback methods are invoked from background threads
 * (discovery thread, SNMP enrichment thread, protocol test thread). Implementations
 * must handle thread-safety when updating UI components, typically by using
 * {@code SwingUtilities.invokeLater()} or similar mechanisms.
 * </p>
 *
 * @author Benjamin FOUILHAC, ADM21 - January/February 2026
 * @see PlanetDiscoveryService
 * @see MainController
 */
public interface DiscoveryCallback {

    /**
     * Called when a new device is discovered during UDP broadcast scanning.
     * <p>
     * This method is invoked from the discovery thread immediately after a device
     * responds to the UDP broadcast packet. At this point, the device contains
     * basic information (MAC, IP, firmware) but may not yet have SNMP enrichment
     * data (location, uptime).
     * </p>
     *
     * @param device the newly discovered device (basic data only, SNMP data pending)
     */
    void onDeviceDiscovered(PlanetSwitchDevice device);

    /**
     * Called when SNMP enrichment completes for a device.
     * <p>
     * This method is invoked from the SNMP enrichment thread after successfully
     * retrieving system location and uptime information via SNMP queries.
     * The device object passed to this method contains updated SNMP fields.
     * </p>
     *
     * @param device the device with updated SNMP data (location, uptime)
     */
    void onDeviceEnriched(PlanetSwitchDevice device);

    /**
     * Called when protocol connectivity tests complete for a device.
     * <p>
     * This method is invoked from the protocol test thread after testing
     * connectivity on Telnet, SSH, HTTP, and HTTPS ports. The result object
     * contains availability status for each protocol and security assessment
     * based on default credential testing.
     * </p>
     *
     * @param device the device that was tested
     * @param result the protocol test results containing availability and security status
     * @see ProtocolTestService.ProtocolTestResult
     */
    void onProtocolsTestedCallback(PlanetSwitchDevice device, ProtocolTestService.ProtocolTestResult result);
}