package com.rover.controller;

import com.rover.model.NetworkInterfaceCustom;
import com.rover.model.PlanetSwitchDevice;
import com.rover.services.NetworkAdapterService;
import com.rover.services.PlanetDiscoveryService;
import com.rover.ui.MainWindow;
import com.rover.utils.LanguageManager;
import com.rover.utils.PreferencesManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Main controller for the Rover21 application.
 * <p>
 * This class acts as the central coordinator between the user interface (MainWindow)
 * and the business logic services (NetworkAdapterService, PlanetDiscoveryService).
 * It implements the UIEventListener interface to handle all user interactions
 * from the main window and delegates tasks to appropriate services.
 * </p>
 *
 * @author Benjamin FOUILHAC, ADM21 in January-February 2026
 * @see MainWindow
 * @see MainWindow.UIEventListener
 * @see NetworkAdapterService
 * @see PlanetDiscoveryService
 */
public class MainController implements MainWindow.UIEventListener {

    private static final Logger logger = LoggerFactory.getLogger(MainController.class);

    private final LanguageManager lang = LanguageManager.getInstance();
    private final MainWindow view;
    private final NetworkAdapterService adapterService;
    private final PlanetDiscoveryService discoveryService;

    /**
     * Constructs a new MainController and initializes all required services.
     * <p>
     * This constructor creates instances of NetworkAdapterService and PlanetDiscoveryService,
     * and registers itself as the event listener for the MainWindow to handle user interactions.
     * </p>
     *
     * @param view the main window view that displays the user interface
     * @see MainWindow#setEventListener(MainWindow.UIEventListener)
     */
    public MainController(MainWindow view) {
        this.view = view;
        this.adapterService = new NetworkAdapterService();
        this.discoveryService = new PlanetDiscoveryService();

        view.setEventListener(this);

        logger.info("MainController initialized.");
    }

    /**
     * Initializes the application by loading available network adapters.
     * <p>
     * This method should be called once during application startup to populate
     * the network adapter dropdown list in the user interface.
     * </p>
     *
     * @see #loadNetworkAdapters()
     * @see com.rover.Main#main(String[])
     */
    public void initialize() {
        logger.info("Initializing application...");
        loadNetworkAdapters();
    }

    /**
     * Loads available network adapters and populates the UI dropdown list.
     * <p>
     * This method queries the system for all available network interfaces with valid IPv4 addresses,
     * then displays them in the main window's adapter selection dropdown.
     * If no adapters are found, an error dialog is shown to the user.
     * </p>
     *
     * @see NetworkAdapterService#getAvailableAdapters()
     * @see MainWindow#loadNetworkInterfaceCustoms(List)
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
            logger.error("Failed to load network adapters", e);
            view.showError(
                    lang.get("error.loading_adapters_title"),
                    lang.get("error.loading_adapters_message", e.getMessage())
            );
        }
    }

    /**
     * Handles the refresh adapters button click event.
     * <p>
     * This method clears the current adapter list and reloads all available network
     * interfaces from the system. It allows users to detect newly connected or
     * disconnected network adapters without restarting the application.
     * </p>
     *
     * @see MainWindow#clearAdapters()
     * @see #loadNetworkAdapters()
     */
    @Override
    public void onRefreshAdaptersClicked() {
        logger.info("Refresh adapters requested");
        view.clearAdapters();
        view.setStatusMessage(lang.get("status.refreshing_adapters"));
        loadNetworkAdapters();
        logger.info("Adapters refreshed");
    }

    /**
     * Handles the discover button click event to scan for PLANET switches on the network.
     * <p>
     * This method validates the selected network adapter, then launches an asynchronous
     * discovery process using a SwingWorker. The discovery sends UDP broadcast packets
     * to detect PLANET switches on the local network and displays a countdown timer
     * during the scan. Results are displayed in the main window table once completed.
     * </p>
     *
     * @param selectedAdapter the network interface to use for the discovery scan
     * @see NetworkAdapterService#isAdapterValid(NetworkInterfaceCustom)
     * @see PlanetDiscoveryService#discoverDevices(String)
     * @see MainWindow#setScanningMode(boolean)
     * @see MainWindow#displayDevices(List)
     */
    @Override
    public void onDiscoverClicked(NetworkInterfaceCustom selectedAdapter) {
        view.clearResults();
        logger.info("Discover clicked for adapter: {}", selectedAdapter.getName());

        // Validate the selected adapter
        if (!adapterService.isAdapterValid(selectedAdapter)) {
            view.showError(
                    lang.get("error.invalid_adapter_title"),
                    lang.get("error.invalid_adapter_message")
            );
            return;
        }

        view.setScanningMode(true);

        // Create a background worker to perform discovery without blocking the UI
        SwingWorker<List<PlanetSwitchDevice>, Integer> worker = new SwingWorker<>() {

            /**
             * Performs the network discovery in a background thread.
             * <p>
             * This method starts a countdown timer thread and initiates the UDP-based
             * discovery process to find PLANET switches on the network.
             * </p>
             *
             * @return a list of discovered PLANET switches
             */
            @Override
            protected List<PlanetSwitchDevice> doInBackground() {
                Thread countdownThread = new Thread(() -> { // Start a countdown timer thread
                    int timeoutSeconds = PreferencesManager.getDiscoveryTimeout() / 1000;
                    for (int i = timeoutSeconds; i >= 0; i--) {
                        publish(i);
                        try {
                            Thread.sleep(1000);
                        } catch (InterruptedException e) {
                            break;
                        }
                    }
                });
                countdownThread.start();

                List<PlanetSwitchDevice> devices = discoveryService.discoverDevices(selectedAdapter.getIpAddress()); // Perform the actual discovery

                countdownThread.interrupt();
                return devices;
            }

            /**
             * Updates the UI with countdown progress.
             * <p>
             * This method is called on the Event Dispatch Thread (EDT) and receives
             * published countdown values from the background thread.
             * </p>
             *
             * @param chunks list of published countdown values (seconds remaining)
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

            /**
             * Handles the completion of the discovery process.
             * <p>
             * This method is called on the EDT after the background discovery completes.
             * It displays the found devices in the table and updates the status message.
             * </p>
             */
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
                            lang.get("error.discovery_message", e.getMessage())
                    );
                    view.setStatusMessage(lang.get("status.failed"));
                }
            }
        };

        worker.execute();
    }

    /**
     * Handles the update device button click event.
     * <p>
     * This method is called when the user clicks the "Update Device" button
     * for a selected switch. Currently, displays an information dialog indicating
     * that the firmware update feature is not yet implemented.
     * </p>
     *
     * @param device the PLANET switch to update
     * @see MainWindow#showInfo(String, String)
     */
    @Override
    public void onUpdateDeviceClicked(PlanetSwitchDevice device) {
        logger.info("Update device clicked: {}", device.getDeviceName());

        // TODO: Implement firmware update for single device
        view.showInfo(
                lang.get("info.update_device_title"),
                lang.get("info.update_device_message", device.getDeviceName(), device.getIpAddress())
        );
    }

    /**
     * Handles the update multiple devices button click event.
     * <p>
     * This method is called when the user selects multiple switches and clicks
     * the "Update Multi" button. Currently, displays an information dialog with
     * the list of selected devices and indicates that batch update is not yet implemented.
     * </p>
     *
     * @param devices the list of PLANET switches to update
     * @see MainWindow#showInfo(String, String)
     */
    @Override
    public void onUpdateMultiClicked(List<PlanetSwitchDevice> devices) {
        logger.info("Update multi clicked: {} devices", devices.size());

        // Build device list for display
        String deviceList = devices.stream()
                .map(device -> device.getDeviceName() + " (" + device.getIpAddress() + ")")
                .collect(Collectors.joining("\n"));

        // TODO: Implement batch firmware update
        view.showInfo(
                lang.get("info.update_multi_title"),
                lang.get("info.update_multi_message", devices.size(), deviceList)
        );
    }

    /**
     * Handles the update all devices button click event.
     * <p>
     * This method is called when the user clicks the "Update All" button to update
     * all discovered switches at once. Currently, displays an information dialog
     * indicating that the mass update feature is not yet implemented.
     * </p>
     *
     * @param devices all discovered PLANET switches
     * @see MainWindow#showInfo(String, String)
     */
    @Override
    public void onUpdateAllClicked(List<PlanetSwitchDevice> devices) {
        logger.info("Update all clicked: {} devices", devices.size());

        // TODO: Implement mass firmware update for all devices
        view.showInfo(
                lang.get("info.update_all_title"),
                lang.get("info.update_all_message", devices.size())
        );
    }

    /**
     * Handles the connect to device button click event.
     * <p>
     * This method is called when the user wants to open the web interface
     * of a selected switch. Currently, displays an information dialog indicating
     * that the browser integration feature is not yet implemented.
     * </p>
     *
     * @param device the PLANET switch to connect to
     * @see MainWindow#showInfo(String, String)
     */
    @Override
    public void onConnectToDeviceClicked(PlanetSwitchDevice device) {
        logger.info("Connect to device clicked: {}", device.getDeviceName());

        // TODO: Implement browser opening to device web interface (http://IP)
        view.showInfo(
                lang.get("info.connect_title"),
                lang.get("info.connect_message", device.getDeviceName(), device.getIpAddress())
        );
    }

    /**
     * Handles the export to CSV button click event.
     * <p>
     * This method is called when the user wants to export the discovered devices
     * list to a CSV file. Currently, displays an information dialog indicating
     * that the CSV export feature is not yet implemented.
     * </p>
     *
     * @param devices the list of discovered PLANET switches to export
     * @see MainWindow#showInfo(String, String)
     */
    @Override
    public void onExportClicked(List<PlanetSwitchDevice> devices) {
        logger.info("Export clicked: {} devices", devices.size());

        // TODO: Implement CSV export functionality
        view.showInfo(
                lang.get("info.export_title"),
                lang.get("info.export_message", devices.size())
        );
    }

    /**
     * Handles the clear results button click event.
     * <p>
     * This method clears all discovered devices from the table and resets
     * the application to its initial "Ready" state.
     * </p>
     *
     * @see MainWindow#clearResults()
     */
    @Override
    public void onClearResultsClicked() {
        logger.info("Clear results clicked.");
        view.clearResults();
        view.setStatusMessage(lang.get("status.cleared"));
    }
}