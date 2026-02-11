package rover.exception;

import java.io.Serial;

/**
 * Exception for SNMP query failures.
 * <p>
 * Covers initialization errors, query timeouts, protocol issues,
 * and credential problems during SNMP communication.
 * </p>
 *
 * @author Benjamin FOUILHAC, ADM21
 */
public class SNMPException extends RoverException {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Constructs an SNMPException with full context.
     *
     * @param errorCode the error code
     * @param userMessage the user-friendly message
     * @param technicalMessage the technical details
     * @param deviceIp the device IP
     * @param operation the operation name
     * @param cause the underlying cause
     */
    public SNMPException(ErrorCode errorCode, String userMessage, String technicalMessage,
                         String deviceIp, String operation, Throwable cause) {
        super(errorCode, userMessage, technicalMessage, deviceIp, operation, cause);
    }

    /**
     * Creates an exception for SNMP service initialization failure.
     *
     * @param cause the underlying exception
     * @return the configured exception
     */
    public static SNMPException initFailed(Throwable cause) {
        return new SNMPException(
                ErrorCode.SNMP_INIT_FAILED,
                "Failed to initialize SNMP service",
                cause != null ? cause.getMessage() : null,
                null,
                "SNMP_INIT",
                cause
        );
    }

    /**
     * Creates an exception for SNMP query timeout.
     *
     * @param deviceIp the IP of the device
     * @param oid the OID that timed out
     * @return the configured exception
     */
    public static SNMPException queryTimeout(String deviceIp, String oid) {
        return new SNMPException(
                ErrorCode.SNMP_QUERY_TIMEOUT,
                "SNMP query timed out for " + deviceIp,
                "OID: " + oid,
                deviceIp,
                "SNMP_GET",
                null
        );
    }

    /**
     * Creates an exception for no SNMP response.
     *
     * @param deviceIp the IP of the device
     * @return the configured exception
     */
    public static SNMPException noResponse(String deviceIp) {
        return new SNMPException(
                ErrorCode.SNMP_NO_RESPONSE,
                "No SNMP response from " + deviceIp,
                "Device may not support SNMP or community string is incorrect",
                deviceIp,
                "SNMP_QUERY",
                null
        );
    }

    /**
     * Creates an exception for SNMP protocol error.
     *
     * @param deviceIp the IP of the device
     * @param errorStatus the SNMP error status
     * @param errorIndex the SNMP error index
     * @return the configured exception
     */
    public static SNMPException protocolError(String deviceIp, int errorStatus, int errorIndex) {
        return new SNMPException(
                ErrorCode.SNMP_PROTOCOL_ERROR,
                "SNMP protocol error from " + deviceIp,
                "Error status: " + errorStatus + ", Error index: " + errorIndex,
                deviceIp,
                "SNMP_QUERY",
                null
        );
    }

    /**
     * Creates an exception for invalid SNMP credentials.
     *
     * @param deviceIp the IP of the device
     * @param version the SNMP version (v1, v2c, v3)
     * @return the configured exception
     */
    public static SNMPException invalidCredentials(String deviceIp, String version) {
        return new SNMPException(
                ErrorCode.SNMP_INVALID_CREDENTIALS,
                "SNMP authentication failed for " + deviceIp,
                "SNMP version: " + version,
                deviceIp,
                "SNMP_AUTH",
                null
        );
    }

    /**
     * Creates an exception for invalid OID.
     *
     * @param deviceIp the IP of the device
     * @param oid the invalid OID
     * @param cause the underlying exception
     * @return the configured exception
     */
    public static SNMPException invalidOid(String deviceIp, String oid, Throwable cause) {
        return new SNMPException(
                ErrorCode.SNMP_INVALID_OID,
                "Invalid SNMP OID for " + deviceIp,
                "OID: " + oid,
                deviceIp,
                "SNMP_QUERY",
                cause
        );
    }
}
