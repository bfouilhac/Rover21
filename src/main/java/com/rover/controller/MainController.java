package com.rover.controller;

import com.rover.model.NetworkInterfaceCustom;
import com.rover.model.PlanetSwitchDevice;
import com.rover.services.NetworkAdapterService;
import com.rover.services.PlanetDiscoveryService;
import com.rover.ui.MainWindow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.util.List;

/**
 * Contrôleur principal de l'application Rover21
 * Coordonne les interactions entre la view (MainWindow) et les services
 */
public class MainController implements MainWindow.UIEventListener {

    private static final Logger logger = LoggerFactory.getLogger(MainController.class);

    private final MainWindow view;
    private final NetworkAdapterService adapterService;
    private final PlanetDiscoveryService discoveryService;

    /**
     * Constructeur du contrôleur
     * @param view La fenêtre principale
     */
    public MainController(MainWindow view) {
        this.view = view;
        this.adapterService = new NetworkAdapterService();
        this.discoveryService = new PlanetDiscoveryService();

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
        view.clearResults();
        logger.info("Discover clicked for adapter: {}", selectedAdapter.getName());
        if (!adapterService.isAdapterValid(selectedAdapter)) {
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
            protected List<PlanetSwitchDevice> doInBackground() {
                return discoveryService.discoverDevices(selectedAdapter.getIpAddress()); // On lance la découverte
            }

            @Override
            protected void done() {
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
        logger.info("Connect to device clicked: {}", device.getDeviceName());
        // TODO: Implémenter la connexion à l'appareil (ouvrir navigateur?)
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
}