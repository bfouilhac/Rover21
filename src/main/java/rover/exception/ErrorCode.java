package rover.exception;

/**
 * Error codes for categorizing and identifying specific failure conditions.
 * <p>
 * Each code has a unique identifier organized by category (1xxx for discovery,
 * 2xxx for SSH, etc.) and a default message key for internationalization.
 * </p>
 *
 * @author Benjamin FOUILHAC, ADM21
 */
public enum ErrorCode {

    // Discovery errors (1xxx)
    DISCOVERY_SOCKET_ERROR(1001, "error.discovery.socket"),
    DISCOVERY_TIMEOUT(1002, "error.discovery.timeout"),
    DISCOVERY_INVALID_RESPONSE(1003, "error.discovery.invalid_response"),
    DISCOVERY_NO_ADAPTERS(1004, "error.discovery.no_adapters"),
    DISCOVERY_BROADCAST_FAILED(1005, "error.discovery.broadcast_failed"),

    // SSH errors (2xxx)
    SSH_CONNECTION_FAILED(2001, "error.ssh.connection_failed"),
    SSH_AUTHENTICATION_FAILED(2002, "error.ssh.auth_failed"),
    SSH_CHANNEL_ERROR(2003, "error.ssh.channel_error"),
    SSH_COMMAND_FAILED(2004, "error.ssh.command_failed"),
    SSH_TIMEOUT(2005, "error.ssh.timeout"),
    SSH_SESSION_CLOSED(2006, "error.ssh.session_closed"),
    SSH_HOST_KEY_REJECTED(2007, "error.ssh.host_key_rejected"),

    // SNMP errors (3xxx)
    SNMP_INIT_FAILED(3001, "error.snmp.init_failed"),
    SNMP_QUERY_TIMEOUT(3002, "error.snmp.timeout"),
    SNMP_NO_RESPONSE(3003, "error.snmp.no_response"),
    SNMP_PROTOCOL_ERROR(3004, "error.snmp.protocol_error"),
    SNMP_INVALID_CREDENTIALS(3005, "error.snmp.invalid_credentials"),
    SNMP_INVALID_OID(3006, "error.snmp.invalid_oid"),

    // Configuration errors (4xxx)
    CONFIG_DOWNLOAD_FAILED(4001, "error.config.download_failed"),
    CONFIG_UPLOAD_FAILED(4002, "error.config.upload_failed"),
    CONFIG_PARSE_ERROR(4003, "error.config.parse_error"),
    CONFIG_FILE_IO_ERROR(4004, "error.config.file_io"),
    CONFIG_INVALID_FORMAT(4005, "error.config.invalid_format"),
    CONFIG_SSH_NOT_AVAILABLE(4006, "error.config.ssh_not_available"),

    // Topology errors (5xxx)
    TOPOLOGY_NO_RSTP_DEVICES(5001, "error.topology.no_rstp_devices"),
    TOPOLOGY_ROOT_NOT_FOUND(5002, "error.topology.root_not_found"),
    TOPOLOGY_LLDP_PARSE_ERROR(5003, "error.topology.lldp_parse"),
    TOPOLOGY_INCOMPLETE(5004, "error.topology.incomplete"),
    TOPOLOGY_RSTP_PARSE_ERROR(5005, "error.topology.rstp_parse"),
    TOPOLOGY_DISCOVERY_FAILED(5006, "error.topology.discovery_failed"),

    // General errors (9xxx)
    UNKNOWN_ERROR(9999, "error.unknown");

    private final int code;
    private final String messageKey;

    ErrorCode(int code, String messageKey) {
        this.code = code;
        this.messageKey = messageKey;
    }

    /**
     * Returns the numeric error code.
     *
     * @return the error code number
     */
    public int getCode() {
        return code;
    }

    /**
     * Returns the i18n message key for this error.
     *
     * @return the message key
     */
    public String getMessageKey() {
        return messageKey;
    }
}
