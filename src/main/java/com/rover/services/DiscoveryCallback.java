package com.rover.services;

import com.rover.model.PlanetSwitchDevice;

/**
 * Callback interface for real-time device discovery notifications.
 * Implementations receive updates as devices are discovered.
 */
public interface DiscoveryCallback {
    /**
     * Called when a new device is discovered.
     * This method is invoked from the discovery thread.
     *
     * @param device the newly discovered device (may not have SNMP data yet)
     */
    void onDeviceDiscovered(PlanetSwitchDevice device);

    /**
     * Called when SNMP enrichment completes for a device.
     * This method is invoked from the device enrichment thread.
     *
     * @param device the device with updated SNMP data
     */
    void onDeviceEnriched(PlanetSwitchDevice device);

    /**
     * Called when all the tests for differents protocols are completed.
     * This method is invoked from the device enrichment thread.
     * @param device
     * @param result
     */
    void onProtocolsTestedCallback(PlanetSwitchDevice device, ProtocolTestService.ProtocolTestResult result);
}