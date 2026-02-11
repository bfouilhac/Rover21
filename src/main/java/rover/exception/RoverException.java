package rover.exception;

import java.io.Serial;

/**
 * Base exception class for all Rover21 application exceptions.
 * <p>
 * Provides structured error information including error codes, device context,
 * and separation of user-friendly messages from technical details.
 * </p>
 *
 * @author Benjamin FOUILHAC, ADM21
 */
public class RoverException extends Exception {

    @Serial
    private static final long serialVersionUID = 1L;

    private final ErrorCode errorCode;
    private final String deviceIp;
    private final String operation;
    private final String technicalMessage;

    /**
     * Constructs a RoverException with full context.
     *
     * @param errorCode the error code categorizing this exception
     * @param userMessage user-friendly message suitable for display
     * @param technicalMessage detailed technical information for logging (may be null)
     * @param deviceIp the IP address of the device involved (may be null)
     * @param operation the operation that failed (may be null)
     * @param cause the underlying cause (may be null)
     */
    public RoverException(ErrorCode errorCode, String userMessage, String technicalMessage,
                          String deviceIp, String operation, Throwable cause) {
        super(userMessage, cause);
        this.errorCode = errorCode;
        this.deviceIp = deviceIp;
        this.operation = operation;
        this.technicalMessage = technicalMessage;
    }

    /**
     * Constructs a RoverException with error code, message and cause.
     *
     * @param errorCode the error code
     * @param userMessage the user-friendly message
     * @param cause the underlying cause
     */
    public RoverException(ErrorCode errorCode, String userMessage, Throwable cause) {
        this(errorCode, userMessage, null, null, null, cause);
    }

    /**
     * Constructs a RoverException with error code and message only.
     *
     * @param errorCode the error code
     * @param userMessage the user-friendly message
     */
    public RoverException(ErrorCode errorCode, String userMessage) {
        this(errorCode, userMessage, null, null, null, null);
    }

    /**
     * Returns the error code categorizing this exception.
     *
     * @return the error code
     */
    public ErrorCode getErrorCode() {
        return errorCode;
    }

    /**
     * Returns the IP address of the device involved, if applicable.
     *
     * @return the device IP or null
     */
    public String getDeviceIp() {
        return deviceIp;
    }

    /**
     * Returns the operation that was being performed when the error occurred.
     *
     * @return the operation name or null
     */
    public String getOperation() {
        return operation;
    }

    /**
     * Returns the technical message with detailed information for logging.
     *
     * @return the technical message or null
     */
    public String getTechnicalMessage() {
        return technicalMessage;
    }

    /**
     * Returns a detailed log message including all available context.
     * <p>
     * Format: [ERROR_CODE] message | Device: ip | Operation: op | Details: tech
     * </p>
     *
     * @return formatted message for logging
     */
    public String toLogMessage() {
        StringBuilder sb = new StringBuilder();
        sb.append("[").append(errorCode.name()).append("] ");
        sb.append(getMessage());

        if (deviceIp != null) {
            sb.append(" | Device: ").append(deviceIp);
        }
        if (operation != null) {
            sb.append(" | Operation: ").append(operation);
        }
        if (technicalMessage != null) {
            sb.append(" | Details: ").append(technicalMessage);
        }
        return sb.toString();
    }
}
