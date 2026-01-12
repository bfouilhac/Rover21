package com.rover.services;

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
 * Service for testing several protocol connections with a PlanetSwitchDevice.
 * <p>
 * Tests are performed using simple socket connections or HTTP requests
 * with short timeouts to avoid blocking the UI. All tests are non-invasive
 * and only verify that the port is open and responding.
 * </p>
 *
 * @author Benjamin FOUILHAC, ADM21 in January-February 2026
 * @see com.rover.model.PlanetSwitchDevice
 */
public class ProtocolTestService {

    private static final Logger logger = LoggerFactory.getLogger(ProtocolTestService.class);

    private static final int TELNET_PORT = 23;
    private static final int SSH_PORT = 22;
    private static final int HTTP_PORT = 80;
    private static final int DEFAULT_TIMEOUT = 1000;

    /**
     * Tests if Telnet is available on the device.
     * <p>
     * This method attempts to open a TCP socket connection to port 23.
     * If the connection succeeds within the timeout period, Telnet is
     * considered available.
     * </p>
     *
     * @param ipAddress the device IP address to test
     * @return true if Telnet port 23 is open and accepting connections
     */
    public boolean isTelnetAvailable(String ipAddress) {
        return isPortOpen(ipAddress, TELNET_PORT, "Telnet");
    }

    /**
     * Tests if SSH is available on the device.
     * <p>
     * This method attempts to open a TCP socket connection to port 22.
     * If the connection succeeds within the timeout period, SSH is
     * considered available.
     * </p>
     *
     * @param ipAddress the device IP address to test
     * @return true if SSH port 22 is open and accepting connections
     */
    public boolean isSSHAvailable(String ipAddress) {
        return isPortOpen(ipAddress, SSH_PORT, "SSH");
    }

    /**
     * Tests if HTTP/HTTPS is available on the device.
     * <p>
     * This method attempts to open a TCP socket connection to either HTTP or HTTPS port.
     * If the connection succeeds within the timeout period, HTTP(s) is
     * considered available.
     * </p>
     *
     * @param ipAddress the device IP address to test
     * @return true if the port is open and accepting connections
     */
    public boolean isHTTPAvailable(String ipAddress) {
        if (!isPortOpen(ipAddress, HTTP_PORT, "HTTP")) { // Test if the port is open
            return false;
        }
        try { // Test if the device is answering to HTTP requests
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
            return false;  // Open port but no valid HTTP answer
        } catch (IOException e) {
            logger.debug("HTTP error on {}: {}", ipAddress, e.getMessage());
            return false;
        }
    }

    /**
     * Tests if HTTPS is available on the device.
     * @param ipAddress the device IP address to test
     * @return true if HTTPS port 443 is open and accepting connections
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

            if (responseCode >= 300 && responseCode < 400) {
                logger.debug("HTTPS redirects to HTTP on {}", ipAddress);
                return false;
            }
            connection.disconnect();
            return responseCode >= 200 && responseCode < 300;

        } catch (Exception e) {
            logger.debug("HTTPS not available on {}: {}", ipAddress, e.getMessage());
            return false;
        }
    }


    /**
     * Tests if a TCP port is open on the device.
     * <p>
     * This is a generic method used by protocol-specific tests to check
     * if a port is accessible by attempting a socket connection.
     * </p>
     *
     * @param ipAddress the device IP address
     * @param port the TCP port to test
     * @param protocolName the protocol name for logging
     * @return true if the port is open and accepting connections
     */
    private boolean isPortOpen(String ipAddress, int port, String protocolName) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(ipAddress, port), 1000);
            logger.debug("{} is available on {} (port {})", protocolName, ipAddress, port);
            return true;
        } catch (IOException e) {
            logger.debug("{} not available on {} (port {}): {}", protocolName, ipAddress, port, e.getMessage());
            return false;
        }
    }

    /**
     * Disables SSL certificate verification for HTTPS connections.
     * <p>
     * This method configures the SSL context to accept all certificates,
     * including self-signed certificates commonly used by network devices.
     * This is necessary because switches often use self-signed certificates
     * that would normally fail validation.
     * </p>
     * <p>
     * Warning: This makes the connection vulnerable to man-in-the-middle
     * attacks. Only use this for testing device availability, not for secure
     * communication.
     * </p>
     */
    public void disableSSLVerification(){
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
     * <p>
     * This convenience method tests all four protocols (Telnet, SSH, HTTP, HTTPS)
     * sequentially and returns a ProtocolTestResult object containing the results.
     * </p>
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

        logger.info("Protocol test ended for {}", ipAddress);

        return result;
    }

    /**
     * Result object containing protocol availability test results.
     */
    public static class ProtocolTestResult {
        private final String ipAddress;
        private boolean telnetAvailable;
        private boolean sshAvailable;
        private boolean httpAvailable;
        private boolean httpsAvailable;

        public ProtocolTestResult(String ipAddress) {
            this.ipAddress = ipAddress;
        }

        public boolean isTelnetAvailable() {
            return telnetAvailable;
        }

        public void setTelnetAvailable(boolean telnetAvailable) {
            this.telnetAvailable = telnetAvailable;
        }

        public boolean isSshAvailable() {
            return sshAvailable;
        }

        public void setSshAvailable(boolean sshAvailable) {
            this.sshAvailable = sshAvailable;
        }

        public boolean isHttpAvailable() {
            return httpAvailable;
        }

        public void setHttpAvailable(boolean httpAvailable) {
            this.httpAvailable = httpAvailable;
        }

        public boolean isHttpsAvailable() {
            return httpsAvailable;
        }

        public void setHttpsAvailable(boolean httpsAvailable) {
            this.httpsAvailable = httpsAvailable;
        }

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
