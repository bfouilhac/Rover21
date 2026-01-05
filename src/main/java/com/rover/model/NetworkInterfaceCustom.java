package com.rover.model;

import java.util.Objects;

public class NetworkInterfaceCustom {

    private String name; // Nom de l'interface
    private String displayName; // Nom d'affichage
    private String ipAddress; // Adresse IP de l'interface
    private String macAddress; // Adresse MAC de l'interface
    private String netMask; // Masque de sous-réseau
    private String networkAddress; // Adresse du réseau (calculée)
    private boolean isUp; // Interface active ou non
    private boolean isLoopback;

    public String getName() {return name;}
    public String getIpAddress() {return ipAddress;}

    public void setUp(boolean up) {this.isUp = up;}
    public void setLoopback(boolean loopback) {this.isLoopback = loopback;}


    public enum AdapterType {
        ETHERNET("Ethernet"),
        WIFI("Wi-Fi"),
        VPN("VPN"),
        LOOPBACK("Loopback"),
        UKN("Unknown");

        private final String displayName;

        AdapterType(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    // Constructeur complet
    public NetworkInterfaceCustom(String name, String displayName, String ipAddress,
                          String macAddress, String netMask, AdapterType type) {
        this.name = name;
        this.displayName = displayName;
        this.ipAddress = ipAddress;
        this.macAddress = macAddress;
        this.netMask = netMask;
        this.isUp = true;
        this.isLoopback = false;
        this.networkAddress = calculateNetworkAddress(ipAddress, netMask);
    }

    /**
     * Calcule l'adresse réseau à partir de l'IP et du masque
     * Exemple: IP=192.168.1.100, Mask=255.255.255.0 => Network=192.168.1.0
     */
    private String calculateNetworkAddress(String ip, String mask) {
        if (ip == null || mask == null) {
            return null;
        }
        try {
            String[] ipParts = ip.split("\\.");
            String[] maskParts = mask.split("\\.");

            if (ipParts.length != 4 || maskParts.length != 4) {
                return null;
            }
            StringBuilder network = new StringBuilder();
            for (int i = 0; i < 4; i++) {
                int ipByte = Integer.parseInt(ipParts[i]);
                int maskByte = Integer.parseInt(maskParts[i]);
                int networkByte = ipByte & maskByte;

                network.append(networkByte);
                if (i < 3) {
                    network.append(".");
                }
            }
            return network.toString();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Retourne une chaîne formatée pour affichage dans la ComboBox
     * Format: "192.168.1.100 - Nom de l'interface"
     */
    public String toDisplayString() {
        StringBuilder sb = new StringBuilder();

        if (ipAddress != null) {
            sb.append(ipAddress);
        }

        if (displayName != null && !displayName.equals(name)) {
            sb.append(" - ").append(displayName);
        }
        return sb.toString();
    }

    /**
     * Vérifie si cet adresse est valide pour scanner
     * (doit être active, avoir une IP, ne pas être loopback)
     */
    public boolean isValidForScanning() {
        return isUp &&
                !isLoopback &&
                ipAddress != null &&
                !ipAddress.isEmpty() &&
                !ipAddress.startsWith("127.");
    }

    @Override
    public String toString() {
        return toDisplayString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        NetworkInterfaceCustom that = (NetworkInterfaceCustom) o;
        return Objects.equals(name, that.name) &&
                Objects.equals(ipAddress, that.ipAddress);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, ipAddress);
    }
}
