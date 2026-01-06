package com.rover.model;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Template representing a PLANET switch discovered on the network.
 * Contains all information retrieved via SNMP and network discovery.
 * @author Benjamin FOUILHAC, ADM21 in January-February 2026.
 * @implNote For now, all the information are gathered by UDP, SNMP will come soon.
 */
public class PlanetSwitchDevice {

    private String macAddress;
    private String deviceName; // (ex: IGS-6325-16P4S)
    private String version; // (ex: v1.440b300826)
    private String deviceIp;
    private String ipAddress;
    private String netMask;
    private String gateway;
    private String description;
    private boolean isOnline;

    /**
     * Constructor, initiate an empty PlanetSwitchDevice.
     */
    public PlanetSwitchDevice() {
        this.isOnline = false;
    }

    // Getters
    public String getMacAddress() {return macAddress;}
    public String getDeviceName() {return deviceName;}
    public String getVersion() {return version;}
    public String getDeviceIp() {return deviceIp;}
    public String getIpAddress() {return ipAddress;}
    public String getNetMask() {return netMask;}
    public String getGateway() {return gateway;}
    public String getDescription() {return description;}

    // Setters
    public void setMacAddress(String macAddress) {this.macAddress = macAddress;}
    public void setDeviceName(String deviceName) {this.deviceName = deviceName;}
    public void setVersion(String version) {this.version = version;}
    public void setDeviceIp(String deviceIp) {this.deviceIp = deviceIp;}
    public void setIpAddress(String ipAddress) {this.ipAddress = ipAddress;}
    public void setNetMask(String netMask) {this.netMask = netMask;}
    public void setGateway(String gateway) {this.gateway = gateway;}
    public void setDescription(String description) {this.description = description;}
    public void setOnline(boolean online) {this.isOnline = online;}

    /**
     * @return a short string summarizing important product information.
     */
    @Override
    public String toString() {
        return "NetworkDevice{" +
                "macAddress='" + macAddress + '\'' +
                ", deviceName='" + deviceName + '\'' +
                ", ipAddress='" + ipAddress + '\'' +
                ", isOnline=" + isOnline +
                '}';
    }

    /**
     * Simple method to check if this device is equals to the o device.
     * @param o the reference object with which to compare.
     * @return whether the devices are the same or not.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PlanetSwitchDevice that = (PlanetSwitchDevice) o;
        return Objects.equals(macAddress, that.macAddress);
    }

    @Override
    public int hashCode() {
        return Objects.hash(macAddress);
    }
}