package rover.exception;

import java.io.Serial;

/**
 * Exception for SSH-related failures in PLANET switch communication.
 * <p>
 * Covers connection failures, authentication errors, command execution issues,
 * and session management problems.
 * </p>
 *
 * @author Benjamin FOUILHAC, ADM21
 */
public class SSHException extends RoverException {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Constructs an SSHException with full context.
     *
     * @param errorCode the error code
     * @param userMessage the user-friendly message
     * @param technicalMessage the technical details
     * @param deviceIp the device IP
     * @param operation the operation name
     * @param cause the underlying cause
     */
    public SSHException(ErrorCode errorCode, String userMessage, String technicalMessage,
                        String deviceIp, String operation, Throwable cause) {
        super(errorCode, userMessage, technicalMessage, deviceIp, operation, cause);
    }

    /**
     * Creates an exception for connection failure.
     *
     * @param deviceIp the IP of the device
     * @param cause the underlying exception
     * @return the configured exception
     */
    public static SSHException connectionFailed(String deviceIp, Throwable cause) {
        return new SSHException(
                ErrorCode.SSH_CONNECTION_FAILED,
                "Failed to connect to " + deviceIp,
                cause != null ? cause.getMessage() : null,
                deviceIp,
                "SSH_CONNECT",
                cause
        );
    }

    /**
     * Creates an exception for authentication failure.
     *
     * @param deviceIp the IP of the device
     * @param username the username that was rejected
     * @return the configured exception
     */
    public static SSHException authenticationFailed(String deviceIp, String username) {
        return new SSHException(
                ErrorCode.SSH_AUTHENTICATION_FAILED,
                "Authentication failed for user '" + username + "' on " + deviceIp,
                "Login credentials rejected by device",
                deviceIp,
                "SSH_AUTH",
                null
        );
    }

    /**
     * Creates an exception for channel opening failure.
     *
     * @param deviceIp the IP of the device
     * @param channelType the type of channel that failed (exec, shell, etc.)
     * @param cause the underlying exception
     * @return the configured exception
     */
    public static SSHException channelError(String deviceIp, String channelType, Throwable cause) {
        return new SSHException(
                ErrorCode.SSH_CHANNEL_ERROR,
                "Failed to open " + channelType + " channel on " + deviceIp,
                cause != null ? cause.getMessage() : null,
                deviceIp,
                "CHANNEL_OPEN",
                cause
        );
    }

    /**
     * Creates an exception for command execution failure.
     *
     * @param deviceIp the IP of the device
     * @param command the command that failed
     * @param errorOutput the error output from the command
     * @return the configured exception
     */
    public static SSHException commandFailed(String deviceIp, String command, String errorOutput) {
        return new SSHException(
                ErrorCode.SSH_COMMAND_FAILED,
                "Command execution failed on " + deviceIp,
                "Command: " + command + ", Error: " + errorOutput,
                deviceIp,
                "SSH_COMMAND",
                null
        );
    }

    /**
     * Creates an exception for SSH operation timeout.
     *
     * @param deviceIp the IP of the device
     * @param operation the operation that timed out
     * @return the configured exception
     */
    public static SSHException timeout(String deviceIp, String operation) {
        return new SSHException(
                ErrorCode.SSH_TIMEOUT,
                "SSH operation timed out for " + deviceIp,
                "Operation: " + operation,
                deviceIp,
                operation,
                null
        );
    }

    /**
     * Creates an exception for unexpected session closure.
     *
     * @param deviceIp the IP of the device
     * @return the configured exception
     */
    public static SSHException sessionClosed(String deviceIp) {
        return new SSHException(
                ErrorCode.SSH_SESSION_CLOSED,
                "SSH session closed unexpectedly for " + deviceIp,
                null,
                deviceIp,
                "SESSION",
                null
        );
    }

    /**
     * Creates an exception for host key verification failure.
     *
     * @param deviceIp the IP of the device
     * @return the configured exception
     */
    public static SSHException hostKeyRejected(String deviceIp) {
        return new SSHException(
                ErrorCode.SSH_HOST_KEY_REJECTED,
                "Host key verification failed for " + deviceIp,
                "The host key has changed or is not trusted",
                deviceIp,
                "HOST_KEY_CHECK",
                null
        );
    }
}
