package rover.exception;

import java.io.Serial;

/**
 * Exception for configuration download/upload failures.
 * <p>
 * Covers file operations, SSH availability issues, and parsing errors
 * during switch configuration management.
 * </p>
 *
 * @author Benjamin FOUILHAC, ADM21
 */
public class ConfigurationException extends RoverException {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Constructs a ConfigurationException with full context.
     *
     * @param errorCode the error code
     * @param userMessage the user-friendly message
     * @param technicalMessage the technical details
     * @param deviceIp the device IP
     * @param operation the operation name
     * @param cause the underlying cause
     */
    public ConfigurationException(ErrorCode errorCode, String userMessage, String technicalMessage,
                                  String deviceIp, String operation, Throwable cause) {
        super(errorCode, userMessage, technicalMessage, deviceIp, operation, cause);
    }

    /**
     * Creates an exception for configuration download failure.
     *
     * @param deviceIp the IP of the device
     * @param cause the underlying exception
     * @return the configured exception
     */
    public static ConfigurationException downloadFailed(String deviceIp, Throwable cause) {
        return new ConfigurationException(
                ErrorCode.CONFIG_DOWNLOAD_FAILED,
                "Failed to download configuration from " + deviceIp,
                cause != null ? cause.getMessage() : null,
                deviceIp,
                "CONFIG_DOWNLOAD",
                cause
        );
    }

    /**
     * Creates an exception for configuration upload failure.
     *
     * @param deviceIp the IP of the device
     * @param cause the underlying exception
     * @return the configured exception
     */
    public static ConfigurationException uploadFailed(String deviceIp, Throwable cause) {
        return new ConfigurationException(
                ErrorCode.CONFIG_UPLOAD_FAILED,
                "Failed to upload configuration to " + deviceIp,
                cause != null ? cause.getMessage() : null,
                deviceIp,
                "CONFIG_UPLOAD",
                cause
        );
    }

    /**
     * Creates an exception for configuration parsing error.
     *
     * @param deviceIp the IP of the device
     * @param reason the reason parsing failed
     * @return the configured exception
     */
    public static ConfigurationException parseError(String deviceIp, String reason) {
        return new ConfigurationException(
                ErrorCode.CONFIG_PARSE_ERROR,
                "Failed to parse configuration from " + deviceIp,
                reason,
                deviceIp,
                "CONFIG_PARSE",
                null
        );
    }

    /**
     * Creates an exception for file I/O error.
     *
     * @param filePath the path of the file
     * @param cause the underlying exception
     * @return the configured exception
     */
    public static ConfigurationException fileIOError(String filePath, Throwable cause) {
        return new ConfigurationException(
                ErrorCode.CONFIG_FILE_IO_ERROR,
                "Failed to access configuration file",
                "File: " + filePath,
                null,
                "FILE_IO",
                cause
        );
    }

    /**
     * Creates an exception for invalid configuration format.
     *
     * @param deviceIp the IP of the device
     * @param expectedFormat the expected format
     * @return the configured exception
     */
    public static ConfigurationException invalidFormat(String deviceIp, String expectedFormat) {
        return new ConfigurationException(
                ErrorCode.CONFIG_INVALID_FORMAT,
                "Invalid configuration format from " + deviceIp,
                "Expected format: " + expectedFormat,
                deviceIp,
                "CONFIG_VALIDATE",
                null
        );
    }

    /**
     * Creates an exception for SSH not being available.
     *
     * @param deviceIp the IP of the device
     * @return the configured exception
     */
    public static ConfigurationException sshNotAvailable(String deviceIp) {
        return new ConfigurationException(
                ErrorCode.CONFIG_SSH_NOT_AVAILABLE,
                "SSH is not available on " + deviceIp,
                "SSH must be enabled and accessible for configuration operations",
                deviceIp,
                "SSH_CHECK",
                null
        );
    }
}
