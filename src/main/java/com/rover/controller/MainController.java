package com.rover.controller;

import com.rover.model.NetworkInterfaceCustom;
import com.rover.model.PlanetSwitchDevice;
import com.rover.services.NetworkAdapterService;
import com.rover.services.PlanetDiscoveryService;
import com.rover.ui.MainWindow;
import com.rover.utils.LanguageManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.util.List;

/**
 * Main controller for the Rover21 application.
 * Coordinates interactions between the view (MainWindow) and services.
 * @author Benjamin FOUILHAC, ADM21 in January-February 2026.
 */
public class MainController implements MainWindow.UIEventListener {

    private static final Logger logger = LoggerFactory.getLogger(MainController.class);

    // Language manager for translations
    private final LanguageManager lang = LanguageManager.getInstance();

    private final MainWindow view;
    private final NetworkAdapterService adapterService;
    private final PlanetDiscoveryService discoveryService;

    /**
     * Constructor, initialize attributes and set the listener for the interactions.
     * @param view the main page that contains all the information and graphics elements.
     * @see MainWindow
     */
    public MainController(MainWindow view) {
        this.view = view;
        this.adapterService = new NetworkAdapterService();
        this.discoveryService = new PlanetDiscoveryService();

        view.setEventListener(this); // Register this controller as a listener for UI events

        logger.info("MainController initialized.");
    }

    /**
     * Initializes the application (loads network adapters).
     * Needs to be cast at the start of the program.
     * @implNote Could be coded in a better way than that...
     * @see com.rover.Main#main(String[])
     */
    public void initialize() {
        logger.info("Initializing application...");
        loadNetworkAdapters();
    }

    /**
     * Use the service to load the various network interfaces,
     * then call the UI to integrate them into the drop-down list.
     * Finally, set the UI in "Ready" mode.
     * @implNote might show an error pop-up if no adapters found.
     * @see NetworkAdapterService#getAvailableAdapters()
     */
    private void loadNetworkAdapters() {
        try {
            List<NetworkInterfaceCustom> adapters = adapterService.getAvailableAdapters();

            if (adapters.isEmpty()) {
                view.showError(
                        lang.get("error.no_adapters_title"),
                        lang.get("error.no_adapters_message")
                );
                view.setStatusMessage(lang.get("status.no_adapters"));
            } else {
                view.loadNetworkInterfaceCustoms(adapters);
                view.setStatusMessage(lang.get("status.ready_adapters", adapters.size()));
                logger.info("Loaded {} network adapter(s)", adapters.size());
            }

        } catch (Exception e) {
            view.showError(
                    lang.get("error.loading_adapters_title"),
                    lang.get("error.loading_adapters_message", e.getMessage())
            );
        }
    }

    /**
     * Override method that manages interaction with the "Discover" button.
     * Checks that the selected interface is valid : IP address, non-null, not loopback address.
     * Launches operations in a SwingWorker.
     * @param selectedAdapter the interface selected for discovering PLANET products.
     * @see SwingWorker
     * @see NetworkInterfaceCustom
     */
    @Override
    public void onDiscoverClicked(NetworkInterfaceCustom selectedAdapter) {
        view.clearResults();
        logger.info("Discover clicked for adapter: {}", selectedAdapter.getName());
        if (!adapterService.isAdapterValid(selectedAdapter)) {
            view.showError(
                    lang.get("error.invalid_adapter_title"),
                    lang.get("error.invalid_adapter_message")
            );
            return;
        }
        view.setScanningMode(true);
        SwingWorker<List<PlanetSwitchDevice>, Integer> worker = new SwingWorker<>() {
            @Override
            protected List<PlanetSwitchDevice> doInBackground(){
                Thread countdownThread = new Thread(() -> {
                    for (int i = discoveryService.getTimeout() / 1000; i >= 0; i--) {
                        publish(i); // Send the value to process()
                        try {
                            Thread.sleep(1000); // Wait one second between each (so we can get 5 ... 4 ... 3 ...)
                        } catch (InterruptedException e) {
                            break;
                        }
                    }
                });
                countdownThread.start();
                List<PlanetSwitchDevice> devices = discoveryService.discoverDevices(selectedAdapter.getIpAddress());
                countdownThread.interrupt();
                return devices;
            }

            /**
             * This method is call on the EDT (UI thread).
             * It receives the data sent by publish()
             * @param chunks intermediate results to process
             */
            @Override
            protected void process(List<Integer> chunks) {
                if (!chunks.isEmpty()) {
                    int secondsRemaining = chunks.get(chunks.size() - 1);
                    if (secondsRemaining > 1) {
                        view.setStatusMessage(lang.get("status.scanning_progress", secondsRemaining));
                    } else {
                        view.setStatusMessage(lang.get("status.scanning_progress_singular", secondsRemaining));
                    }
                }
            }

            @Override
            protected void done() {
                try {
                    List<PlanetSwitchDevice> devices = get();
                    view.displayDevices(devices);
                    view.setScanningMode(false);

                    if (devices.isEmpty()) {
                        view.setStatusMessage(lang.get("status.completed_none"));
                    } else {
                        view.setStatusMessage(lang.get("status.completed_found", devices.size()));
                    }

                    logger.info("Discovery completed: {} device(s) found", devices.size());

                } catch (Exception e) {
                    logger.error("Error during discovery", e);
                    view.setScanningMode(false);
                    view.showError(
                            lang.get("error.discovery_title"),
                            lang.get("error.discovery_message", e.getMessage()));
                    view.setStatusMessage(lang.get("status.failed"));
                }
            }
        };
        worker.execute();
    }

    /**
     * Override method that manages the interaction with the "Update Device" button.
     * @param device the current PLANET switch that will be updated.
     * @implNote For the moment, this method is useless since this part has not been defined in the specifications.
     * @see PlanetSwitchDevice
     */
    @Override
    public void onUpdateDeviceClicked(PlanetSwitchDevice device) {
        logger.info("Update device clicked: {}", device.getDeviceName());
        // TODO: Implémenter la mise à jour d'un device
        view.showInfo(
                lang.get("info.update_device_title"),
                lang.get("info.update_device_message", device.getDeviceName(), device.getIpAddress())
        );
    }

    /**
     * Override method that manages the interaction with the "Update Multi" button.
     * @param devices the current PLANET switchs that will be updated.
     * @implNote For the moment, this method is useless since this part has not been defined in the specifications.
     * @see PlanetSwitchDevice
     */
    @Override
    public void onUpdateMultiClicked(List<PlanetSwitchDevice> devices) {
        logger.info("Update multi clicked: {} devices", devices.size());
        // TODO: Implémenter la mise à jour multiple
        StringBuilder message = new StringBuilder("Updating " + devices.size() + " devices :\n\n");
        for (PlanetSwitchDevice device : devices) {
            message.append("- ").append(device.getDeviceName())
                    .append(" (").append(device.getIpAddress()).append(")\n");
        }
        message.append("\nThis feature will be implemented soon !");
        view.showInfo(
                lang.get("info.update_multi_title"),
                lang.get("info.update_multi_message")
        );
    }

    /**
     * Override method that manages the interaction with the "Update All" button.
     * @param devices all the PLANET switchs that has were found.
     * @implNote For the moment, this method is useless since this part has not been defined in the specifications.
     * @see PlanetSwitchDevice
     */
    @Override
    public void onUpdateAllClicked(List<PlanetSwitchDevice> devices) {
        logger.info("Update all clicked: {} devices", devices.size());
        // TODO: Implémenter la mise à jour des informations / firmware des appareils sélectionnés
        view.showInfo(
                lang.get("info.update_all_title"),
                lang.get("info.update_all_message", devices.size())
        );
    }

    /**
     * Override method that manages the interaction with the "Connect to Device" button.
     * @param device The device that we want to be connected to
     * @implNote For the moment, this method is useless since this part has not been defined in the specifications.
     * @see PlanetSwitchDevice
     */
    @Override
    public void onConnectToDeviceClicked(PlanetSwitchDevice device) {
        logger.info("Connect to device clicked: {}", device.getDeviceName());
        // TODO: Implémenter la connexion à l'appareil (ouvrir navigateur?)
        view.showInfo(
                lang.get("info.connect_title"),
                lang.get("info.connect_message", device.getDeviceName(), device.getIpAddress())
        );
    }

    /**
     * Override method that manages the interaction with the "Export to CSV" option.
     * @param devices The list of devices we want in the CSV file
     * @implNote For the moment, this method is useless since this part has not been defined in the specifications.
     * @see PlanetSwitchDevice
     */
    @Override
    public void onExportClicked(List<PlanetSwitchDevice> devices) {
        logger.info("Export clicked: {} devices", devices.size());
        // TODO: Implémenter l'export CSV
        view.showInfo(
                lang.get("info.export_title"),
                lang.get("info.export_message", devices.size())
        );
    }

    /**
     * Allows you to clear the device table data and reset the application to “Ready” status.
     * @see MainWindow#clearResults()
     */
    @Override
    public void onClearResultsClicked() {
        logger.info("Clear results clicked.");
        view.clearResults();
        view.setStatusMessage(lang.get("status.cleared"));
    }
}