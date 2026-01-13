package com.rover.services;

import com.rover.utils.PreferencesManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.net.*;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;

/**
 * Service for testing protocol connectivity with PLANET network switches.
 * Tests are performed using simple socket connections or HTTP requests
 * with short timeouts to avoid blocking the UI. All tests are non-invasive
 * and only verify that the port is open and responding.
 *
 * @author Benjamin FOUILHAC, ADM21 in January-February 2026
 * @see com.rover.model.PlanetSwitchDevice
 */
public class ProtocolTestService {

    private static final Logger logger = LoggerFactory.getLogger(ProtocolTestService.class);
    private static final int HTTP_PORT = 80;
    private static final int DEFAULT_TIMEOUT = 1000;

    /**
     * Tests if Telnet is available on the device.
     * This method attempts to open a TCP socket connection to the configured Telnet port.
     * If the connection succeeds within the timeout period, Telnet is considered available.
     *
     * @param ipAddress the device IP address to test
     * @return true if Telnet port is open and accepting connections
     */
    public boolean isTelnetAvailable(String ipAddress) {
        return isPortOpen(ipAddress, PreferencesManager.getTelnetPort(), "Telnet");
    }

    /**
     * Tests if SSH is available on the device.
     * This method attempts to open a TCP socket connection to the configured SSH port.
     * If the connection succeeds within the timeout period, SSH is considered available.
     *
     * @param ipAddress the device IP address to test
     * @return true if SSH port is open and accepting connections
     */
    public boolean isSSHAvailable(String ipAddress) {
        return isPortOpen(ipAddress, PreferencesManager.getSshPort(), "SSH");
    }

    /**
     * Tests if HTTP is available on the device.
     * This method first checks if port 80 is open, then attempts an HTTP HEAD request
     * to verify that the device is actually responding to HTTP requests.
     *
     * @param ipAddress the device IP address to test
     * @return true if the HTTP port is open and responding to requests
     */
    public boolean isHTTPAvailable(String ipAddress) {
        if (!isPortOpen(ipAddress, HTTP_PORT, "HTTP")) { // test if the port is open
            return false;
        }

        try {
            URL url = new URL("http://" + ipAddress);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(DEFAULT_TIMEOUT);
            connection.setReadTimeout(DEFAULT_TIMEOUT);
            connection.setRequestMethod("HEAD");
            connection.setInstanceFollowRedirects(false);

            int responseCode = connection.getResponseCode();
            connection.disconnect();

            logger.debug("HTTP test for {}: response code {}", ipAddress, responseCode);
            return responseCode > 0;

        } catch (SocketTimeoutException e) {
            logger.debug("HTTP timeout on {} (port open but no response)", ipAddress);
            return false; // port is open but no valid HTTP response
        } catch (IOException e) {
            logger.debug("HTTP error on {}: {}", ipAddress, e.getMessage());
            return false;
        }
    }

    /**
     * Tests if HTTPS is available on the device.
     * This method attempts an HTTPS connection with SSL verification disabled
     * to accommodate self-signed certificates commonly used by network devices.
     * Redirects from HTTPS to HTTP are considered failures.
     *
     * @param ipAddress the device IP address to test
     * @return true if the HTTPS port is open and responding with valid HTTPS
     */
    public boolean isHTTPSAvailable(String ipAddress) {
        try {
            disableSSLVerification();

            URL url = new URL("https://" + ipAddress);
            HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();
            connection.setConnectTimeout(DEFAULT_TIMEOUT);
            connection.setReadTimeout(DEFAULT_TIMEOUT);
            connection.setRequestMethod("HEAD");
            connection.setInstanceFollowRedirects(false); // avoid HTTPS redirections to HTTP
            connection.setHostnameVerifier((hostname, session) -> true);

            int responseCode = connection.getResponseCode();
            connection.disconnect();

            if (responseCode >= 300 && responseCode < 400) { // redirect detected
                logger.debug("HTTPS redirects to HTTP on {}", ipAddress);
                return false;
            }

            return responseCode >= 200 && responseCode < 300;

        } catch (Exception e) {
            logger.debug("HTTPS not available on {}: {}", ipAddress, e.getMessage());
            return false;
        }
    }

    /**
     * Tests if a TCP port is open on the device.
     * This is a generic method used by protocol-specific tests to check
     * if a port is accessible by attempting a socket connection.
     *
     * @param ipAddress the device IP address
     * @param port the TCP port to test
     * @param protocolName the protocol name for logging purposes
     * @return true if the port is open and accepting connections
     */
    private boolean isPortOpen(String ipAddress, int port, String protocolName) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(ipAddress, port), DEFAULT_TIMEOUT);
            logger.debug("{} is available on {} (port {})", protocolName, ipAddress, port);
            return true;
        } catch (IOException e) {
            logger.debug("{} not available on {} (port {}): {}", protocolName, ipAddress, port, e.getMessage());
            return false;
        }
    }

    /**
     * Disables SSL certificate verification for HTTPS connections.
     * This method configures the SSL context to accept all certificates,
     * including self-signed certificates commonly used by network devices.
     * This is necessary because switches often use self-signed certificates
     * that would normally fail validation. Warning: This makes the connection
     * vulnerable to man-in-the-middle attacks. Only use this for testing
     * device availability, not for secure communication.
     */
    private void disableSSLVerification() {
        try {
            TrustManager[] trustAllCerts = new TrustManager[]{
                    new X509TrustManager() {
                        public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                            return null;
                        }

                        public void checkClientTrusted(java.security.cert.X509Certificate[] certs, String authType) {
                        }

                        public void checkServerTrusted(java.security.cert.X509Certificate[] certs, String authType) {
                        }
                    }
            };

            SSLContext sc = SSLContext.getInstance("SSL");
            sc.init(null, trustAllCerts, new java.security.SecureRandom());
            HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
        } catch (KeyManagementException | NoSuchAlgorithmException e) {
            logger.error("Error disabling SSL verification", e);
            throw new RuntimeException(e);
        }
    }

    /**
     * Tests all protocols on a device and returns the results.
     * This convenience method tests all four protocols (Telnet, SSH, HTTP, HTTPS)
     * sequentially and returns a ProtocolTestResult object containing the results.
     *
     * @param ipAddress the device IP address to test
     * @return a ProtocolTestResult with availability status for each protocol
     * @see ProtocolTestResult
     */
    public ProtocolTestResult testAllProtocols(String ipAddress) {
        logger.info("Testing all protocols for device: {}", ipAddress);

        ProtocolTestResult result = new ProtocolTestResult(ipAddress);
        result.setTelnetAvailable(isTelnetAvailable(ipAddress));
        result.setSshAvailable(isSSHAvailable(ipAddress));
        result.setHttpAvailable(isHTTPAvailable(ipAddress));
        result.setHttpsAvailable(isHTTPSAvailable(ipAddress));

        logger.info("Protocol test completed for {}", ipAddress);

        return result;
    }

    /**
     * Result object containing protocol availability test results.
     * This class holds the results of connectivity tests for all supported
     * protocols (Telnet, SSH, HTTP, HTTPS) for a single device.
     */
    public static class ProtocolTestResult {

        private final String ipAddress;
        private boolean telnetAvailable;
        private boolean sshAvailable;
        private boolean httpAvailable;
        private boolean httpsAvailable;

        /**
         * Constructs a new ProtocolTestResult for the specified IP address.
         * All protocol availability flags are initialized to false.
         *
         * @param ipAddress the IP address of the tested device
         */
        public ProtocolTestResult(String ipAddress) {
            this.ipAddress = ipAddress;
        }

        /**
         * Returns whether Telnet is available on the device.
         *
         * @return true if Telnet is available, false otherwise
         */
        public boolean isTelnetAvailable() {
            return telnetAvailable;
        }

        /**
         * Sets the Telnet availability status.
         *
         * @param telnetAvailable true if Telnet is available
         */
        public void setTelnetAvailable(boolean telnetAvailable) {
            this.telnetAvailable = telnetAvailable;
        }

        /**
         * Returns whether SSH is available on the device.
         *
         * @return true if SSH is available, false otherwise
         */
        public boolean isSshAvailable() {
            return sshAvailable;
        }

        /**
         * Sets the SSH availability status.
         *
         * @param sshAvailable true if SSH is available
         */
        public void setSshAvailable(boolean sshAvailable) {
            this.sshAvailable = sshAvailable;
        }

        /**
         * Returns whether HTTP is available on the device.
         *
         * @return true if HTTP is available, false otherwise
         */
        public boolean isHttpAvailable() {
            return httpAvailable;
        }

        /**
         * Sets the HTTP availability status.
         *
         * @param httpAvailable true if HTTP is available
         */
        public void setHttpAvailable(boolean httpAvailable) {
            this.httpAvailable = httpAvailable;
        }

        /**
         * Returns whether HTTPS is available on the device.
         *
         * @return true if HTTPS is available, false otherwise
         */
        public boolean isHttpsAvailable() {
            return httpsAvailable;
        }

        /**
         * Sets the HTTPS availability status.
         *
         * @param httpsAvailable true if HTTPS is available
         */
        public void setHttpsAvailable(boolean httpsAvailable) {
            this.httpsAvailable = httpsAvailable;
        }

        /**
         * Returns a string representation of the test results.
         *
         * @return a formatted string showing all protocol availability statuses
         */
        @Override
        public String toString() {
            return "ProtocolTestResult{" +
                    "ipAddress='" + ipAddress + '\'' +
                    ", telnet=" + telnetAvailable +
                    ", ssh=" + sshAvailable +
                    ", http=" + httpAvailable +
                    ", https=" + httpsAvailable +
                    '}';
        }
    }
}