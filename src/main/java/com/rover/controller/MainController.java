package com.rover.controller;

import com.rover.model.NetworkInterfaceCustom;
import com.rover.model.PlanetSwitchDevice;
import com.rover.services.*;
import com.rover.ui.APP_STATE;
import com.rover.ui.MainWindow;
import com.rover.utils.LanguageManager;
import com.rover.utils.PreferencesManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.io.File;
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
 * @author Benjamin FOUILHAC, ADM21 - January/February 2026
 * @see MainWindow
 * @see MainWindow.UIEventListener
 * @see NetworkAdapterService
 * @see PlanetDiscoveryService
 */
public class MainController implements MainWindow.UIEventListener {

    private static final Logger logger = LoggerFactory.getLogger(MainController.class);
    private static final int COUNTDOWN_INTERVAL_MS = 1000;

    private final LanguageManager lang = LanguageManager.getInstance();

    private final MainWindow view;

    private final NetworkAdapterService adapterService;
    private final PlanetDiscoveryService discoveryService;
    private final SwitchConfigService configService;

    // Protocol enrichment tracking
    private int expectedProtocolTests = 0;
    private int completedProtocolTests = 0;

    /**
     * Constructs a new MainController and initializes all required services.
     * <p>
     * This constructor creates instances of NetworkAdapterService and PlanetDiscoveryService
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
        this.configService = new SwitchConfigService();

        view.setEventListener(this);
        logger.info("MainController initialized");
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
     * Handles the discover button click event.
     * <p>
     * This method initiates a network discovery operation on the selected adapter.
     * It validates the adapter, creates a SwingWorker to perform discovery in a background
     * thread, and updates the UI in real-time as devices are discovered. A countdown thread
     * displays the remaining discovery time to the user.
     * </p>
     *
     * @param sAdapter the network adapter to use for discovery
     * @see SwingWorker
     * @see DiscoveryCallback
     * @see PlanetDiscoveryService#discoverDevices(String, DiscoveryCallback)
     */
    @Override
    public void onDiscoverClicked(NetworkInterfaceCustom sAdapter) {
        view.clearResults();
        logger.info("Discover clicked for adapter: {}", sAdapter.getName());

        // Reset protocol test tracking
        expectedProtocolTests = 0;
        completedProtocolTests = 0;

        if (!adapterService.isAdapterValid(sAdapter)) {
            showInvalidAdapterError();
            return;
        }

        view.setScanningMode(true);
        view.setAppState(APP_STATE.SEARCHING);

        executeDiscoveryWorker(sAdapter);
    }

    /**
     * Handles the update device button click event.
     * <p>
     * This method is called when the user clicks the "Update Device" button
     * for a selected switch. Currently, it displays an information dialog indicating
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
     * the "Update Multi" button. Currently, it displays an information dialog with
     * the list of selected devices and indicates that a batch update is not yet implemented.
     * </p>
     *
     * @param devices the list of PLANET switches to update
     * @see MainWindow#showInfo(String, String)
     */
    @Override
    public void onUpdateMultiClicked(List<PlanetSwitchDevice> devices) {
        logger.info("Update multi clicked: {} devices", devices.size());

        String deviceList = formatDeviceList(devices);

        // TODO: Implement batch firmware update
        view.showInfo(
                lang.get("info.update_multi_title"),
                lang.get("info.update_multi_message", devices.size(), deviceList)
        );
    }

    /**
     * Handles the startup config button click event.
     * <p>
     * This method downloads the configuration via SSH using "show running-config"
     * which is universal across all PLANET switch models.
     * </p>
     *
     * @param device the device from which to download the config
     */
    @Override
    public void onGetStartupConfigClicked(PlanetSwitchDevice device) {
        logger.info("Get startup config requested for: {}", device.getIpAddress());

        if (!device.isSSHUp()) { // Checking SSH
            view.showError(
                    lang.get("error.ssh_unavailable_title"),
                    lang.get("error.ssh_unavailable_message", device.getIpAddress())
            );
            return;
        }

        if (device.getUsername() == null || device.getPassword() == null) {
            view.showError(
                    lang.get("error.no_credentials_title"),
                    lang.get("error.no_credentials_message", device.getIpAddress())
            );
            return;
        }

        File saveFile = promptForSaveLocation(device);
        if (saveFile == null) { // Choosing a file location
            logger.info("Config download cancelled by user");
            return;
        }

        downloadConfigInBackground(device, saveFile);
    }

    /**
     * Handles the connect to device button click event.
     * <p>
     * This method is called when the user wants to open the web interface
     * of a selected switch. Currently, it displays an information dialog indicating
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
     * list to a CSV file. Currently, it displays an information dialog indicating
     * that the CSV export feature is not yet implemented.
     * </p>
     *
     * @param devices the list of discovered PLANET switches to export
     * @see MainWindow#showInfo(String, String)
     */
    @Override
    public void onExportClicked(List<PlanetSwitchDevice> devices, String fileType) {
        logger.info("Export clicked: {} devices", devices.size());

        boolean isCsv = fileType.equals("CSV");
        String fileExtension = fileType.toLowerCase();
        String fileDescription = isCsv ? "CSV Files (*.csv)" : "Excel Files (*.xlsx)";

        JFileChooser fileChooser = createFileChooser();
        fileChooser.setDialogTitle(lang.get("info.export_title"));
        fileChooser.setFileFilter(new FileNameExtensionFilter(fileDescription, fileExtension));
        fileChooser.setSelectedFile(new File("planet_devices_export." + fileExtension));

        String lastExportPath = PreferencesManager.getLastExportPath();
        if (lastExportPath != null && !lastExportPath.isEmpty()) {
            File lastDir = new File(lastExportPath);
            if (lastDir.exists() && lastDir.isDirectory()) {
                fileChooser.setCurrentDirectory(lastDir);
            }
        }

        File selectedFile = null;

        while (selectedFile == null) {
            int dialogResult = fileChooser.showSaveDialog(view);

            if (dialogResult != JFileChooser.APPROVE_OPTION) {
                logger.info("Export cancelled by user");
                view.showInfo(
                        lang.get("info.export_cancelled_title"),
                        lang.get("info.export_cancelled_message")
                );
                return;
            }

            selectedFile = fileChooser.getSelectedFile();

            if (!selectedFile.getName().toLowerCase().endsWith("." + fileExtension)) {
                selectedFile = new File(selectedFile.getAbsolutePath() + "." + fileExtension);
            }

            if (selectedFile.exists()) {
                int overwriteResult = JOptionPane.showOptionDialog(
                        view,
                        lang.get("info.export_overwrite_message", selectedFile.getName()),
                        lang.get("info.export_overwrite_title"),
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.WARNING_MESSAGE,
                        null,
                        new String[]{lang.get("button.yes"), lang.get("button.no")},
                        lang.get("button.no")
                );

                if (overwriteResult != JOptionPane.YES_OPTION) {
                    selectedFile = null;
                }
            }
        }

        PreferencesManager.saveLastExportPath(selectedFile.getParentFile().getAbsolutePath());

        ExportService exportService = new ExportService();

        try {
            if (isCsv) {
                exportService.exportToCSV(devices, selectedFile);
            } else {
                exportService.exportToXLSX(devices, selectedFile);
            }

            logger.info("Export successful: {} devices exported to {}", devices.size(), selectedFile.getAbsolutePath());
            view.showInfo(
                    lang.get("info.export_success_title"),
                    lang.get("info.export_success_message", devices.size(), selectedFile.getAbsolutePath())
            );

        } catch (Exception e) {
            logger.error("Export failed", e);
            view.showError(
                    lang.get("info.export_error_title"),
                    lang.get("info.export_error_message", e.getMessage())
            );
        }
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
        logger.info("Clear results clicked");
        view.clearResults();
        view.setStatusMessage(lang.get("status.cleared"));
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
                handleNoAdaptersFound();
            } else {
                handleAdaptersFound(adapters);
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
     * Handles the case when no network adapters are found.
     */
    private void handleNoAdaptersFound() {
        view.showError(
                lang.get("error.no_adapters_title"),
                lang.get("error.no_adapters_message")
        );
        view.setStatusMessage(lang.get("status.no_adapters"));
    }

    /**
     * Handles the case when adapters are found.
     */
    private void handleAdaptersFound(List<NetworkInterfaceCustom> adapters) {
        view.loadNetworkInterfaceCustoms(adapters);
        view.setStatusMessage(lang.get("status.ready_adapters", adapters.size()));
        view.setAppState(APP_STATE.ADAPTERS_FOUND);
        logger.info("Loaded {} network adapter(s)", adapters.size());
    }

    /**
     * Shows an error dialog for invalid adapter selection.
     */
    private void showInvalidAdapterError() {
        view.showError(
                lang.get("error.invalid_adapter_title"),
                lang.get("error.invalid_adapter_message")
        );
    }

    /**
     * Executes the discovery operation in a background SwingWorker.
     *
     * @param selectedAdapter the adapter to use for discovery
     */
    private void executeDiscoveryWorker(NetworkInterfaceCustom selectedAdapter) {
        SwingWorker<List<PlanetSwitchDevice>, PlanetSwitchDevice> worker = new SwingWorker<>() {

            @Override
            protected List<PlanetSwitchDevice> doInBackground() {
                Thread countdownThread = startCountdownThread();

                DiscoveryCallback callback = createDiscoveryCallback();
                List<PlanetSwitchDevice> devices = discoveryService.discoverDevices(selectedAdapter.getIpAddress(), callback);

                countdownThread.interrupt();
                return devices;
            }

            @Override
            protected void process(List<PlanetSwitchDevice> chunks) {
                for (PlanetSwitchDevice device : chunks) {
                    view.addOrUpdateDevice(device);
                }
            }

            @Override
            protected void done() {
                handleDiscoveryComplete();
            }

            /**
             * Creates the discovery callback for real-time UI updates.
             */
            private DiscoveryCallback createDiscoveryCallback() {
                return new DiscoveryCallback() {
                    @Override
                    public void onDeviceDiscovered(PlanetSwitchDevice device) {
                        publish(device);
                    }

                    @Override
                    public void onDeviceEnriched(PlanetSwitchDevice device) {
                        publish(device);
                    }

                    @Override
                    public void onProtocolsTestedCallback(PlanetSwitchDevice device, ProtocolTestService.ProtocolTestResult result) {
                        updateProtocolStatus(device, result);
                    }
                };
            }

            /**
             * Updates the protocol status in the UI.
             */
            private void updateProtocolStatus(PlanetSwitchDevice device, ProtocolTestService.ProtocolTestResult result) {
                String mac = device.getMacAddress();

                view.setProtocolStatus(mac, "telnet", result.isTelnetAvailable());
                view.setProtocolStatus(mac, "ssh", result.isSshAvailable());
                view.setProtocolStatus(mac, "http", result.isHttpAvailable());
                view.setProtocolStatus(mac, "https", result.isHttpsAvailable());
                view.setSecurityStatus(mac, result.getSecurityLockStatus());

                device.setTelnetUp(result.isTelnetAvailable());
                device.setSSHUp(result.isSshAvailable());
                device.setHTTPUp(result.isHttpAvailable());
                device.setHTTPSUp(result.isHttpsAvailable());
                device.setUsername(result.getUsername());
                device.setPassword(result.getPassword());

                // Track protocol test completion
                checkProtocolTestsCompletion();
            }

            /**
             * Handles completion of the discovery operation.
             */
            private void handleDiscoveryComplete() {
                try {
                    view.setScanningMode(false);
                    view.setAppState(APP_STATE.DISCOVERY_END);
                    List<PlanetSwitchDevice> devices = get();

                    processDiscoveryResults(devices);

                    logger.info("Discovery completed: {} device(s) found", devices.size());

                    // Initialize protocol test tracking
                    expectedProtocolTests = devices.size();

                    // If no devices found, go directly to the final state
                    if (devices.isEmpty()) {
                        view.setAppState(APP_STATE.FINAL_STATE);
                    }
                    // Otherwise, wait for protocol tests to complete
                    // (FINAL_STATE will be set when all protocol tests are done)

                } catch (Exception e) {
                    handleDiscoveryError(e);
                }
            }

            /**
             * Processes the discovery results and updates the UI.
             */
            private void processDiscoveryResults(List<PlanetSwitchDevice> devices) {
                List<PlanetSwitchDevice> duplicates = findDevicesWithDuplicateIPs(devices);
                String duplicateMACs = view.formatDuplicateMACs(duplicates);

                view.setDiscoveryState(devices.size(), duplicateMACs);

                if (devices.isEmpty()) {
                    view.setStatusMessage(lang.get("status.completed_none"));
                } else {
                    String statusMsg = lang.get("status.completed_found", devices.size());

                    if (!duplicateMACs.isEmpty()) {
                        statusMsg += " " + lang.get("error.duplicate_message", duplicateMACs);
                    }

                    view.setStatusMessage(statusMsg);
                }
            }

            /**
             * Handles errors during discovery.
             */
            private void handleDiscoveryError(Exception e) {
                logger.error("Error during discovery", e);
                view.setScanningMode(false);
                view.showError(
                        lang.get("error.discovery_title"),
                        lang.get("error.discovery_message", e.getMessage())
                );
                view.setStatusMessage(lang.get("status.failed"));
            }
        };

        worker.execute();
    }

    /**
     * Checks if all protocol tests have completed and updates the UI accordingly.
     * <p>
     * This method is called from the protocol test callback and is synchronized
     * to ensure thread-safe counting of completed tests.
     * </p>
     */
    private synchronized void checkProtocolTestsCompletion() {
        completedProtocolTests++;
        logger.debug("Protocol tests completed: {}/{}", completedProtocolTests, expectedProtocolTests);

        if (completedProtocolTests >= expectedProtocolTests && expectedProtocolTests > 0) {
            logger.info("All protocol tests completed for {} device(s)", expectedProtocolTests);

            // Update status to show enrichment is complete
            SwingUtilities.invokeLater(() -> {
                view.setAppState(APP_STATE.PROTOCOL_TEST_END);
                view.setStatusMessage(lang.get("status.enrichment_complete", completedProtocolTests));
                view.setAppState(APP_STATE.FINAL_STATE);
            });
        }
    }

    /**
     * Starts a countdown thread that updates the UI with remaining discovery time.
     * <p>
     * This thread runs independently and updates the status bar every second
     * with the remaining time until discovery completes.
     * </p>
     *
     * @return the countdown Thread (already started)
     */
    private Thread startCountdownThread() {
        Thread countdownThread = new Thread(() -> {
            int timeout = PreferencesManager.getDiscoveryTimeout();

            for (int remainingSeconds = timeout; remainingSeconds >= 0; remainingSeconds--) {
                try {
                    updateTimeRemaining(remainingSeconds);
                    Thread.sleep(COUNTDOWN_INTERVAL_MS);
                } catch (InterruptedException e) {
                    break; // exit loop if interrupted
                }
            }
        });

        countdownThread.setDaemon(true);
        countdownThread.start();
        return countdownThread;
    }

    /**
     * Updates the UI status message with the remaining discovery time.
     * <p>
     * This method uses singular or plural form based on the remaining seconds.
     * </p>
     *
     * @param remainingSeconds the number of seconds remaining
     */
    private void updateTimeRemaining(int remainingSeconds) {
        if (remainingSeconds > 1) {
            view.setStatusMessage(lang.get("status.scanning_progress", remainingSeconds));
        } else {
            view.setStatusMessage(lang.get("status.scanning_progress_singular", remainingSeconds));
        }
    }

    /**
     * Analyzes devices to find those with duplicate IPv4 addresses.
     * <p>
     * This method groups devices by IP address and returns only those
     * devices that share an IP with at least one other device.
     * </p>
     *
     * @param devices the list of discovered devices
     * @return a list of PlanetSwitchDevice objects that have duplicate IP addresses
     */
    private List<PlanetSwitchDevice> findDevicesWithDuplicateIPs(List<PlanetSwitchDevice> devices) {
        return devices.stream()
                .collect(Collectors.groupingBy(PlanetSwitchDevice::getIpAddress))
                .values().stream()
                .filter(group -> group.size() > 1)
                .flatMap(List::stream)
                .collect(Collectors.toList());
    }

    /**
     * Formats a list of devices into a displayable string.
     *
     * @param devices the devices to format
     * @return a newline-separated string of device names and IPs
     */
    private String formatDeviceList(List<PlanetSwitchDevice> devices) {
        return devices.stream()
                .map(device -> device.getDeviceName() + " (" + device.getIpAddress() + ")")
                .collect(Collectors.joining("\n"));
    }

    /**
     * Create a JFileChooser with translated elements depending on the locale language
     *
     * @return the JFileChooser created and updated
     * @see JFileChooser
     */
    private JFileChooser createFileChooser(){
        UIManager.put("FileChooser.saveButtonText", lang.get("button.save"));
        UIManager.put("FileChooser.cancelButtonText", lang.get("button.cancel"));
        UIManager.put("FileChooser.saveDialogTitleText", lang.get("info.export_title"));
        UIManager.put("FileChooser.lookInLabelText", lang.get("filechooser.look_in"));
        UIManager.put("FileChooser.fileNameLabelText", lang.get("filechooser.file_name"));
        UIManager.put("FileChooser.filesOfTypeLabelText", lang.get("filechooser.files_of_type"));
        UIManager.put("FileChooser.upFolderToolTipText", lang.get("filechooser.up_folder"));
        UIManager.put("FileChooser.homeFolderToolTipText", lang.get("filechooser.home_folder"));
        UIManager.put("FileChooser.newFolderToolTipText", lang.get("filechooser.new_folder"));
        UIManager.put("FileChooser.listViewButtonToolTipText", lang.get("filechooser.list_view"));
        UIManager.put("FileChooser.detailsViewButtonToolTipText", lang.get("filechooser.details_view"));

        JFileChooser fileChooser = new JFileChooser();
        fileChooser.updateUI();
        return fileChooser;
    }

    /**
     * Prompts the user to choose a save location for the config file.
     *
     * @param device the device for which to save the config
     * @return the selected File, or null if canceled
     */
    private File promptForSaveLocation(PlanetSwitchDevice device) {
        JFileChooser fileChooser = createFileChooser();
        fileChooser.setDialogTitle(lang.get("info.save_config_title"));
        fileChooser.setFileFilter(new FileNameExtensionFilter("Config files (*.cfg)", "cfg"));

        String lastPath = PreferencesManager.getLastExportPath();
        if (!lastPath.isEmpty()) {
            fileChooser.setCurrentDirectory(new File(lastPath));
        }

        String defaultFileName = sanitizeFileName(device.getDeviceName()) + "_startup_config.cfg";
        fileChooser.setSelectedFile(new File(defaultFileName));

        int result = fileChooser.showSaveDialog(view);

        if (result == JFileChooser.APPROVE_OPTION) {
            File selectedFile = fileChooser.getSelectedFile();

            if (!selectedFile.getName().endsWith(".cfg")) {
                selectedFile = new File(selectedFile.getAbsolutePath() + ".cfg");
            }

            PreferencesManager.saveLastExportPath(selectedFile.getParent());

            if (selectedFile.exists()) {
                int overwrite = JOptionPane.showConfirmDialog(
                        view,
                        lang.get("info.overwrite_message", selectedFile.getName()),
                        lang.get("info.overwrite_title"),
                        JOptionPane.YES_NO_OPTION
                );

                if (overwrite != JOptionPane.YES_OPTION) {
                    return null;
                }
            }

            return selectedFile;
        }

        return null;
    }

    /**
     * Downloads the config file in a background thread to avoid blocking the UI.
     *
     * @param device the device to download from
     * @param destination the file to save to
     */
    private void downloadConfigInBackground(PlanetSwitchDevice device, File destination) {
        view.setStatusMessage(lang.get("status.downloading_config", device.getDeviceName()));

        SwingWorker<Void, Void> worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() throws Exception {
                configService.downloadConfig(device, SwitchConfigService.ConfigType.STARTUP, destination);
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                    view.showInfo(
                            lang.get("info.config_download_success_title"),
                            lang.get("info.config_download_success_message", destination.getAbsolutePath())
                    );
                    view.setStatusMessage(lang.get("status.download_success"));
                    logger.info("Config downloaded successfully to {}", destination.getAbsolutePath());
                } catch (Exception e) {
                    logger.error("Failed to download config from {}", device.getIpAddress(), e);

                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    String errorMsg = cause.getMessage();

                    view.showError(
                            lang.get("error.config_download_title"),
                            lang.get("error.config_download_message", device.getIpAddress(), errorMsg)
                    );
                }
            }
        };

        worker.execute();
    }

    /**
     * Sanitizes a device name to be safe for use as a filename.
     *
     * @param deviceName the device name to sanitize
     * @return sanitized filename
     */
    private String sanitizeFileName(String deviceName) {
        if (deviceName == null || deviceName.isEmpty()) {
            return "device";
        }
        return deviceName.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}