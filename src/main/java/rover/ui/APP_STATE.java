package rover.ui;

import rover.controller.MainController;

/**
 * Enumeration representing the different states of the Rover21 application.
 * <p>
 * This enum is used to track the current state of the application lifecycle,
 * from initialization through discovery to final results display. The UI components
 * use these states to determine which controls should be enabled or disabled,
 * and to display appropriate status messages.
 * </p>
 *
 * @author Benjamin FOUILHAC, ADM21 - January/February 2026
 * @see MainWindow
 * @see MainController
 */
public enum APP_STATE {

    /**
     * Initial state when the application starts.
     * <p>
     * In this state, the application is loading and no network adapters
     * have been detected yet.
     * </p>
     */
    INIT,

    /**
     * Active discovery state.
     * <p>
     * In this state, the application is actively scanning the network
     * for PLANET switches. The discovery button is disabled and a progress
     * indicator is shown.
     * </p>
     */
    SEARCHING,

    /**
     * Network adapters have been detected and loaded.
     * <p>
     * In this state, the application has successfully loaded the available
     * network adapters and is ready to start a discovery operation.
     * </p>
     */
    ADAPTERS_FOUND,

    /**
     * UDP discovery phase completed.
     * <p>
     * In this state, the UDP broadcast discovery has finished and devices
     * have been found. SNMP enrichment may still be in progress.
     * </p>
     */
    DISCOVERY_END,

    /**
     * Protocol testing phase completed.
     * <p>
     * In this state, all protocol tests (Telnet, SSH, HTTP, HTTPS) have
     * finished for all discovered devices.
     * </p>
     */
    PROTOCOL_TEST_END,

    /**
     * Final state after all operations are complete.
     * <p>
     * In this state, discovery, SNMP enrichment, and protocol tests have
     * all completed. The UI displays the final results, and all controls
     * are re-enabled for a new scan.
     * </p>
     */
    FINAL_STATE
}