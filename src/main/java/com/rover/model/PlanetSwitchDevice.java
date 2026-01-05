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
    private LocalDateTime discoveredAt; // Date/heure de découverte (pour des logs éventuelles)
    private boolean isOnline; // État du device (en ligne ou non)

    // Constructeur complet
    public PlanetSwitchDevice(String macAddress, String deviceName, String version,
                         String deviceIp, String ipAddress, String netMask,
                         String gateway, String description) {
        this.macAddress = macAddress;
        this.deviceName = deviceName;
        this.version = version;
        this.deviceIp = deviceIp;
        this.ipAddress = ipAddress;
        this.netMask = netMask;
        this.gateway = gateway;
        this.description = description;
        this.discoveredAt = LocalDateTime.now();
        this.isOnline = true;
    }

    // Constructeur vide (si attribut(s) nul(s)...)
    public PlanetSwitchDevice() {
        this.discoveredAt = LocalDateTime.now();
        this.isOnline = false;
    }

    /**
     * Vérifie si ce device est un switch PLANET en se basant sur l'OUI MAC
     * Les OUI connus de PLANET : 00:30:4F, 00:50:FC, etc.
     */
    public boolean isPlanetDevice() {
        if (macAddress == null || macAddress.length() < 8) {
            return false;
        }
        String oui = macAddress.substring(0, 8).toUpperCase();
        return oui.startsWith("00:30:4F") || oui.startsWith("00:50:FC");
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