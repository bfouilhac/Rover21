package com.rover.model;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Modèle représentant un switch PLANET découvert sur le réseau
 * Contient toutes les informations récupérées via SNMP et découverte réseau
 */
public class PlanetSwitchDevice {

    private String macAddress; // Adresse MAC du switch
    private String deviceName; // Nom du modèle (ex: IGS-6325-16P4S)
    private String version; // Version firmware (ex: v1.440b300826)
    private String deviceIp; // IP du device (peut être différent de ipAddress)
    private String ipAddress; // Adresse IP actuelle
    private String netMask; // Masque de sous-réseau
    private String gateway; // Passerelle par défaut
    private String description; // Description du switch
    private final LocalDateTime discoveredAt; // Date/heure de découverte (pour des logs éventuelles)
    private boolean isOnline; // État du device (en ligne ou non)

    // Constructeur vide (si attribut(s) nul(s)...)
    public PlanetSwitchDevice() {
        this.discoveredAt = LocalDateTime.now();
        this.isOnline = false;
    }

    // Liste de tous les getters (récupère la valeur de l'attribut)
    public String getMacAddress() {return macAddress;}
    public String getDeviceName() {return deviceName;}
    public String getVersion() {return version;}
    public String getDeviceIp() {return deviceIp;}
    public String getIpAddress() {return ipAddress;}
    public String getNetMask() {return netMask;}
    public String getGateway() {return gateway;}
    public String getDescription() {return description;}

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
     * Vérifie si ce device est un switch PLANET en se basant sur l'OUI MAC
     * Les OUI connus de PLANET : 00:30:4F et A8:F7:E0
     */
    public boolean isPlanetDevice() {
        if (macAddress == null || macAddress.length() < 8) {
            return false;
        }
        String oui = macAddress.substring(0, 8).toUpperCase();
        return oui.startsWith("00:30:4F") || oui.startsWith("A8:F7:E0");
    }

    /**
     * Retourne une représentation simple pour le debugging
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
     * Comparaison basée sur l'adresse MAC (identifiant unique)
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