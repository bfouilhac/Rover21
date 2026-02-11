package rover.exception;

import java.io.Serial;

/**
 * Exception for RSTP topology analysis failures.
 * <p>
 * Covers RSTP discovery issues, LLDP parsing errors, and incomplete
 * topology data during network visualization.
 * </p>
 *
 * @author Benjamin FOUILHAC, ADM21
 */
public class TopologyException extends RoverException {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Constructs a TopologyException with full context.
     *
     * @param errorCode the error code
     * @param userMessage the user-friendly message
     * @param technicalMessage the technical details
     * @param deviceIp the device IP
     * @param operation the operation name
     * @param cause the underlying cause
     */
    public TopologyException(ErrorCode errorCode, String userMessage, String technicalMessage,
                             String deviceIp, String operation, Throwable cause) {
        super(errorCode, userMessage, technicalMessage, deviceIp, operation, cause);
    }

    /**
     * Creates an exception for no RSTP-enabled devices.
     *
     * @return the configured exception
     */
    public static TopologyException noRstpDevices() {
        return new TopologyException(
                ErrorCode.TOPOLOGY_NO_RSTP_DEVICES,
                "No RSTP-enabled devices found",
                "At least one device must have RSTP enabled for topology discovery",
                null,
                "RSTP_SCAN",
                null
        );
    }

    /**
     * Creates an exception for root bridge not found.
     *
     * @return the configured exception
     */
    public static TopologyException rootNotFound() {
        return new TopologyException(
                ErrorCode.TOPOLOGY_ROOT_NOT_FOUND,
                "Root bridge not found in topology",
                "The root bridge may be on a device outside the discovered set",
                null,
                "RSTP_ANALYZE",
                null
        );
    }

    /**
     * Creates an exception for LLDP parsing error.
     *
     * @param deviceIp the IP of the device
     * @param reason the reason parsing failed
     * @return the configured exception
     */
    public static TopologyException lldpParseError(String deviceIp, String reason) {
        return new TopologyException(
                ErrorCode.TOPOLOGY_LLDP_PARSE_ERROR,
                "Failed to parse LLDP data from " + deviceIp,
                reason,
                deviceIp,
                "LLDP_PARSE",
                null
        );
    }

    /**
     * Creates an exception for incomplete topology.
     *
     * @param missingNodes the number of missing nodes
     * @return the configured exception
     */
    public static TopologyException incomplete(int missingNodes) {
        return new TopologyException(
                ErrorCode.TOPOLOGY_INCOMPLETE,
                "Topology data is incomplete",
                "Missing nodes: " + missingNodes,
                null,
                "TOPOLOGY_BUILD",
                null
        );
    }

    /**
     * Creates an exception for RSTP parsing error.
     *
     * @param deviceIp the IP of the device
     * @param reason the reason parsing failed
     * @return the configured exception
     */
    public static TopologyException rstpParseError(String deviceIp, String reason) {
        return new TopologyException(
                ErrorCode.TOPOLOGY_RSTP_PARSE_ERROR,
                "Failed to parse RSTP data from " + deviceIp,
                reason,
                deviceIp,
                "RSTP_PARSE",
                null
        );
    }

    /**
     * Creates an exception for general topology discovery failure.
     *
     * @param deviceIp the IP of the device
     * @param cause the underlying exception
     * @return the configured exception
     */
    public static TopologyException discoveryFailed(String deviceIp, Throwable cause) {
        return new TopologyException(
                ErrorCode.TOPOLOGY_DISCOVERY_FAILED,
                "Topology discovery failed for " + deviceIp,
                cause != null ? cause.getMessage() : null,
                deviceIp,
                "TOPOLOGY_DISCOVER",
                cause
        );
    }
}
