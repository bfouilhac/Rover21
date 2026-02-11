package rover.exception;

import java.io.Serial;

/**
 * Exception for device discovery failures.
 * <p>
 * Covers socket errors, timeouts, invalid responses, and network adapter issues
 * during the UDP broadcast discovery process.
 * </p>
 *
 * @author Benjamin FOUILHAC, ADM21
 */
public class DiscoveryException extends RoverException {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Constructs a DiscoveryException with full context.
     *
     * @param errorCode the error code
     * @param userMessage the user-friendly message
     * @param technicalMessage the technical details
     * @param deviceIp the device IP (may be null for broadcast errors)
     * @param operation the operation name
     * @param cause the underlying cause
     */
    public DiscoveryException(ErrorCode errorCode, String userMessage, String technicalMessage,
                              String deviceIp, String operation, Throwable cause) {
        super(errorCode, userMessage, technicalMessage, deviceIp, operation, cause);
    }

    /**
     * Creates an exception for socket binding failures.
     *
     * @param port the port that could not be bound
     * @param cause the underlying exception
     * @return the configured exception
     */
    public static DiscoveryException socketError(int port, Throwable cause) {
        return new DiscoveryException(
                ErrorCode.DISCOVERY_SOCKET_ERROR,
                "Could not bind to port " + port,
                cause != null ? cause.getMessage() : null,
                null,
                "SOCKET_BIND",
                cause
        );
    }

    /**
     * Creates an exception for discovery timeout.
     *
     * @param timeoutMs the timeout value in milliseconds
     * @return the configured exception
     */
    public static DiscoveryException timeout(int timeoutMs) {
        return new DiscoveryException(
                ErrorCode.DISCOVERY_TIMEOUT,
                "Discovery timed out after " + timeoutMs + "ms",
                null,
                null,
                "DISCOVERY",
                null
        );
    }

    /**
     * Creates an exception for invalid response parsing.
     *
     * @param deviceIp the IP of the device that sent the invalid response
     * @param reason the reason the response was invalid
     * @return the configured exception
     */
    public static DiscoveryException invalidResponse(String deviceIp, String reason) {
        return new DiscoveryException(
                ErrorCode.DISCOVERY_INVALID_RESPONSE,
                "Invalid response from " + deviceIp,
                reason,
                deviceIp,
                "PARSE_RESPONSE",
                null
        );
    }

    /**
     * Creates an exception for no available network adapters.
     *
     * @return the configured exception
     */
    public static DiscoveryException noAdapters() {
        return new DiscoveryException(
                ErrorCode.DISCOVERY_NO_ADAPTERS,
                "No network adapters available for discovery",
                null,
                null,
                "ADAPTER_SCAN",
                null
        );
    }

    /**
     * Creates an exception for broadcast send failure.
     *
     * @param adapterIp the adapter IP that failed to broadcast
     * @param cause the underlying exception
     * @return the configured exception
     */
    public static DiscoveryException broadcastFailed(String adapterIp, Throwable cause) {
        return new DiscoveryException(
                ErrorCode.DISCOVERY_BROADCAST_FAILED,
                "Failed to send broadcast on " + adapterIp,
                cause != null ? cause.getMessage() : null,
                null,
                "BROADCAST_SEND",
                cause
        );
    }
}
