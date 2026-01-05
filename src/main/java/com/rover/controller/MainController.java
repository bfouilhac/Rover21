package com.rover.controller;

import com.rover.model.NetworkInterfaceCustom;
import com.rover.model.PlanetSwitchDevice;
import com.rover.services.NetworkAdapterService;
import com.rover.ui.MainWindow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Contrôleur principal de l'application Rover21
 * Coordonne les interactions entre la view (MainWindow) et les services
 * Implémente le pattern MVC (Modèle - Vue - Contrôleur)
 */
public class MainController implements MainWindow.UIEventListener {

    private static final Logger logger = LoggerFactory.getLogger(MainController.class);

    private final MainWindow view;
    private final NetworkAdapterService adapterService;

    /**
     * Constructeur du contrôleur
     * @param view La fenêtre principale
     */
    public MainController(MainWindow view) {
        this.view = view;
        this.adapterService = new NetworkAdapterService();

        view.setEventListener(this); // Enregistrer ce contrôleur comme listener des événements UI

        logger.info("MainController initialized");
    }

    /**
     * Initialise l'application (charge les adaptateurs réseau)
     */
    public void initialize() {
        logger.info("Initializing application...");
        loadNetworkAdapters();
    }

    /**
     * Charge les interfaces réseau disponibles
     */
    private void loadNetworkAdapters() {
        try {
            List<NetworkInterfaceCustom> adapters = adapterService.getAvailableAdapters();

            if (adapters.isEmpty()) {
                view.showError(
                        "No Network Adapters Found",
                        "No valid network adapters with IPv4 addresses were found.\n" +
                                "Please check your network configuration."
                );
                view.setStatusMessage("No network adapters found");
            } else {
                view.loadNetworkInterfaceCustoms(adapters);
                view.setStatusMessage("Ready - " + adapters.size() + " adapter(s) available");
                logger.info("Loaded {} network adapter(s)", adapters.size());
            }

        } catch (Exception e) {
            logger.error("Error loading network adapters", e);
            view.showError(
                    "Error Loading Adapters",
                    "An error occurred while scanning network adapters:\n" + e.getMessage()
            );
        }
    }

    /**
     * Méthode override qui permet de gérer l'interaction avec le bouton "Discover".
     * Vérifie que l'interface choisie est valide : adresse IP, non-nulle, pas loopback.
     * Lance les opérations dans un SwingWorker
     * @param selectedAdapter l'interface choisie pour faire la découverte des produits PLANET
     */
    @Override
    public void onDiscoverClicked(NetworkInterfaceCustom selectedAdapter) {
        logger.info("Discover clicked for adapter: {}", selectedAdapter.getName());
        if (!adapterService.isAdapterValid(selectedAdapter)) { // Vérifier que l'adaptateur est valide (non nul etc ...)
            view.showError(
                    "Invalid Adapter",
                    "The selected network adapter is not valid or not available."
            );
            return;
        }

        view.setScanningMode(true);
        view.setStatusMessage("Scanning network on " + selectedAdapter.getIpAddress() + "...");

        SwingWorker<List<PlanetSwitchDevice>, Void> worker = new SwingWorker<>() {
            @Override
            protected List<PlanetSwitchDevice> doInBackground() throws Exception { // On lance la discovery dans un SwingWorker pour isoler le traitement "backend"
                // TODO: Appeler les vrais services de scan
                return performDiscovery(selectedAdapter);
            }

            @Override
            protected void done() { // Méthode automatiquement trigger quand le travail du SwingWorker sera terminé
                try {
                    List<PlanetSwitchDevice> devices = get();

                    view.displayDevices(devices);
                    view.setScanningMode(false);

                    if (devices.isEmpty()) {
                        view.setStatusMessage("Scan completed - No PLANET switches found");
                    } else {
                        view.setStatusMessage("Scan completed - " + devices.size() + " device(s) found");
                    }

                    logger.info("Discovery completed: {} device(s) found", devices.size());

                } catch (Exception e) {
                    logger.error("Error during discovery", e);
                    view.setScanningMode(false);
                    view.showError(
                            "Discovery Error",
                            "An error occurred during network discovery:\n" + e.getMessage()
                    );
                    view.setStatusMessage("Discovery failed");
                }
            }
        };
        worker.execute();
    }

    @Override
    public void onUpdateDeviceClicked(PlanetSwitchDevice device) {
        logger.info("Update device clicked: {}", device.getDeviceName());

        // TODO: Implémenter la mise à jour d'un device
        view.showInfo(
                "Update Device",
                "Updating device: " + device.getDeviceName() + "\n" +
                        "IP: " + device.getIpAddress() + "\n\n" +
                        "This feature will be implemented soon!"
        );
    }

    @Override
    public void onUpdateMultiClicked(List<PlanetSwitchDevice> devices) {
        logger.info("Update multi clicked: {} devices", devices.size());

        // TODO: Implémenter la mise à jour multiple
        StringBuilder message = new StringBuilder("Updating " + devices.size() + " devices:\n\n");
        for (PlanetSwitchDevice device : devices) {
            message.append("- ").append(device.getDeviceName())
                    .append(" (").append(device.getIpAddress()).append(")\n");
        }
        message.append("\nThis feature will be implemented soon!");

        view.showInfo("Update Multiple Devices", message.toString());
    }

    @Override
    public void onUpdateAllClicked(List<PlanetSwitchDevice> devices) {
        logger.info("Update all clicked: {} devices", devices.size());

        // TODO: Implémenter la mise à jour des informations / firmware des appareils sélectionnés
        view.showInfo(
                "Update All Devices",
                "Updating all " + devices.size() + " devices.\n\n" +
                        "This feature will be implemented soon!"
        );
    }

    @Override
    public void onConnectToDeviceClicked(PlanetSwitchDevice device) {
        logger.info("Connect to device clicked : {}", device.getDeviceName());

        // TODO: Implémenter la connexion à l'appareil (SNMP?)
        view.showInfo(
                "Connect to Device",
                "Connecting to device: " + device.getDeviceName() + "\n" +
                        "IP: " + device.getIpAddress() + "\n\n" +
                        "This will open the device's web interface.\n" +
                        "Feature coming soon!"
        );
    }

    @Override
    public void onExportClicked(List<PlanetSwitchDevice> devices) {
        logger.info("Export clicked: {} devices", devices.size());

        // TODO: Implémenter l'export CSV
        view.showInfo(
                "Export to CSV",
                "Exporting " + devices.size() + " device(s) to CSV.\n\n" +
                        "This feature will be implemented soon!"
        );
    }

    @Override
    public void onClearResultsClicked() {
        logger.info("Clear results clicked");
        view.clearResults();
        view.setStatusMessage("Results cleared - Ready to scan");
    }

    /**
     * Simule la découverte de switchs sur le réseau
     * Sera implémentée dans le futur
     * TODO: Remplacer par les vrais services (NetworkScannerService, SNMPService, etc.)
     */
    private List<PlanetSwitchDevice> performDiscovery(NetworkInterfaceCustom adapter) throws InterruptedException {
        logger.info("Starting discovery simulation on adapter: {}", adapter.getName());

        // Simuler un délai de scan
        Thread.sleep(2500);

        // Créer des devices de test
        List<PlanetSwitchDevice> devices = new ArrayList<>();

        // Device 1
        PlanetSwitchDevice device1 = new PlanetSwitchDevice();
        device1.setMacAddress("00:30:4F:C5:10:5D");
        device1.setDeviceName("IGS-6325-16P4S");
        device1.setVersion("v1.440b300826");
        device1.setDeviceIp("192.168.1.100");
        device1.setIpAddress("192.168.1.100");
        device1.setNetMask("255.255.255.0");
        device1.setGateway("192.168.1.1");
        device1.setDescription("IE L3 Managed PoE+ Switch");
        device1.setOnline(true);

        // Device 2
        PlanetSwitchDevice device2 = new PlanetSwitchDevice();
        device2.setMacAddress("00:30:4F:E3:D4:A7");
        device2.setDeviceName("GS-4210-24T2S");
        device2.setVersion("v2.150c120920");
        device2.setDeviceIp("192.168.1.101");
        device2.setIpAddress("192.168.1.101");
        device2.setNetMask("255.255.255.0");
        device2.setGateway("192.168.1.1");
        device2.setDescription("24-Port Managed Switch");
        device2.setOnline(true);

        // Device 3
        PlanetSwitchDevice device3 = new PlanetSwitchDevice();
        device3.setMacAddress("00:50:FC:A8:B9:C0");
        device3.setDeviceName("IGS-1020PTF");
        device3.setVersion("v1.200a050815");
        device3.setDeviceIp("192.168.1.102");
        device3.setIpAddress("192.168.1.102");
        device3.setNetMask("255.255.255.0");
        device3.setGateway("192.168.1.1");
        device3.setDescription("10-Port Industrial PoE Switch");
        device3.setOnline(true);

        devices.add(device1);
        devices.add(device2);
        devices.add(device3);

        logger.info("Discovery simulation completed: {} device(s)", devices.size());

        return devices;
    }
}