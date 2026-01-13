package com.rover.services;

import com.rover.model.PlanetSwitchDevice;

/**
 * Callback interface for real-time device discovery notifications.
 * Implementations receive updates as devices are discovered and enriched with
 * additional data. This interface allows the UI to be updated asynchronously
 * as discovery operations progress, without blocking the discovery thread.
 *
 * @author Benjamin FOUILHAC, ADM21 in January-February 2026
 * @see PlanetDiscoveryService
 * @see com.rover.controller.MainController
 */
public interface DiscoveryCallback {

    /**
     * Called when a new device is discovered during UDP broadcast scanning.
     * This method is invoked from the discovery thread and may not yet have
     * SNMP enrichment data. Implementations should handle thread-safety when
     * updating UI components.
     *
     * @param device the newly discovered device (may not have SNMP data yet)
     */
    void onDeviceDiscovered(PlanetSwitchDevice device);

    /**
     * Called when SNMP enrichment completes for a device.
     * This method is invoked from the enrichment thread after successfully
     * retrieving system location and uptime information via SNMP. Implementations
     * should handle thread-safety when updating UI components.
     *
     * @param device the device with updated SNMP data
     */
    void onDeviceEnriched(PlanetSwitchDevice device);

    /**
     * Called when protocol connectivity tests complete for a device.
     * This method is invoked from the enrichment thread after testing
     * Telnet, SSH, HTTP, and HTTPS connectivity. Implementations should
     * handle thread-safety when updating UI components.
     *
     * @param device the device that was tested
     * @param result the protocol test results containing availability status
     * @see ProtocolTestService.ProtocolTestResult
     */
    void onProtocolsTestedCallback(PlanetSwitchDevice device, ProtocolTestService.ProtocolTestResult result);
}