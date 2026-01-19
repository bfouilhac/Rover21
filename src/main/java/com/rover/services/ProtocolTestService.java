package com.rover.services;

import com.jcraft.jsch.ChannelShell;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;

import com.rover.utils.PreferencesManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.*;
import java.util.List;

/**
 * Service for testing protocol connectivity and security on PLANET switches.
 * <p>
 * Additionally, for Telnet and SSH, the service performs security testing by attempting
 * to authenticate with default credentials (admin/admin) and MAC-based passwords
 * (sw + last 6 chars of MAC). This helps identify devices that still use factory
 * default passwords and represent a security risk.
 * </p>
 *
 * @author Benjamin FOUILHAC, ADM21 - January/February 2026
 * @see PlanetDiscoveryService
 */
public class ProtocolTestService {

    private static final Logger logger = LoggerFactory.getLogger(ProtocolTestService.class);

    // Timeout constants (milliseconds)
    private static final int PORT_CHECK_TIMEOUT = 500;
    private static final int TELNET_TIMEOUT = 1500;
    private static final int SSH_TIMEOUT = 2000;
    private static final int SSH_READ_TIMEOUT = 2000;
    private static final int HTTP_TIMEOUT = 500;

    // Default ports
    private static final int HTTP_PORT = 80;
    private static final int HTTPS_PORT = 443;

    // Default credentials for security testing
    private static final String DEFAULT_USERNAME = "admin";
    private static final String DEFAULT_PASSWORD = "admin";
    private static final String MAC_PASSWORD_PREFIX = "sw";

    // Thread sleep intervals (milliseconds)
    private static final int LOGIN_WAIT_INTERVAL = 50;
    private static final int PROMPT_RESPONSE_DELAY = 300;

    /**
     * Tests all protocols for a given device.
     * <p>
     * This method tests connectivity on Telnet, SSH, HTTP, and HTTPS ports,
     * and performs security testing on Telnet and SSH to check if default
     * credentials work.
     * </p>
     *
     * @param ipAddress  the IP address of the device to test
     * @param macAddress the MAC address of the device (used for MAC-based password generation)
     * @return a ProtocolTestResult containing the test results
     * @throws SecurityException if the IP address is not in a private network range
     */
    public ProtocolTestResult testAllProtocols(String ipAddress, String macAddress) {
        logger.info("Testing protocols for device: {}", ipAddress);

        if (!isLocalPrivateNetwork(ipAddress)) {
            logger.error("Security: rejected non-local IPv4 address: {}", ipAddress);
            throw new SecurityException("Security rejected non-local IPv4 address: " + ipAddress);
        }

        long startTime = System.currentTimeMillis();
        ProtocolTestResult result = new ProtocolTestResult(ipAddress);

        SecurityTestResult sshResult = testSshSecurity(ipAddress, macAddress);
        result.setSshAvailable(sshResult.available());
        result.setSshSecure(sshResult.secure());

        SecurityTestResult telnetResult = testTelnetSecurity(ipAddress, macAddress);
        result.setTelnetAvailable(telnetResult.available());
        result.setTelnetSecure(telnetResult.secure());

        result.setHttpAvailable(isHttpAvailable(ipAddress));
        result.setHttpsAvailable(isHttpsAvailable(ipAddress));

        long elapsed = System.currentTimeMillis() - startTime;
        logger.info("Protocol tests for {} completed in {}ms (security: {})",
                ipAddress, elapsed, result.getSecurityStatus());

        return result;
    }

    /**
     * Checks if HTTP is available on the device.
     *
     * @param ipAddress the IP address to test
     * @return true if HTTP responds, false otherwise
     */
    public boolean isHttpAvailable(String ipAddress) {
        if (!isPortOpen(ipAddress, HTTP_PORT)) {
            return false;
        }

        try {
            URL url = new URL("http://" + ipAddress);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(HTTP_TIMEOUT);
            connection.setReadTimeout(HTTP_TIMEOUT);
            connection.setRequestMethod("HEAD");
            connection.setInstanceFollowRedirects(false);

            int responseCode = connection.getResponseCode();
            connection.disconnect();

            return responseCode > 0;

        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Checks if HTTPS is available on the device.
     * <p>
     * This method trusts all certificates since network devices typically use
     * self-signed certificates.
     * </p>
     *
     * @param ipAddress the IP address to test
     * @return true if HTTPS responds with a 2xx status code, false otherwise
     */
    public boolean isHttpsAvailable(String ipAddress) {
        try {
            URL url = new URL("https://" + ipAddress);
            HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();
            connection.setConnectTimeout(HTTP_TIMEOUT);
            connection.setReadTimeout(HTTP_TIMEOUT);
            connection.setRequestMethod("HEAD");
            connection.setInstanceFollowRedirects(false);

            connection.setSSLSocketFactory(createTrustAllSslContext().getSocketFactory());
            connection.setHostnameVerifier((hostname, session) -> true);

            int responseCode = connection.getResponseCode();
            connection.disconnect();

            return responseCode >= 200 && responseCode < 300;

        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Checks if an IPv4 address is in a local private network (RFC 1918).
     *
     * @param ipAddress the address to check
     * @return true if the address is in a private network range, false otherwise
     */
    private boolean isLocalPrivateNetwork(String ipAddress) {
        try {
            InetAddress addr = InetAddress.getByName(ipAddress);

            if (addr.isLoopbackAddress() || addr.isLinkLocalAddress()) {
                return false;
            }

            byte[] bytes = addr.getAddress();

            // 10.0.0.0/8
            if (bytes[0] == 10) {
                return true;
            }

            // 172.16.0.0/12
            if (bytes[0] == (byte) 172 && (bytes[1] & 0xF0) == 16) {
                return true;
            }

            // 192.168.0.0/16
            return bytes[0] == (byte) 192 && bytes[1] == (byte) 168;

        } catch (UnknownHostException e) {
            logger.warn("Invalid IP address: {}", ipAddress);
            return false;
        }
    }

    /**
     * Checks if a TCP port is open on the specified host.
     *
     * @param ipAddress the IP address to test
     * @param port the port number to test
     * @return true if the port is open, false otherwise
     */
    private boolean isPortOpen(String ipAddress, int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(ipAddress, port), PORT_CHECK_TIMEOUT);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Tests Telnet security by attempting to authenticate with default credentials.
     *
     * @param ipAddress the IP address to test
     * @param macAddress the MAC address for generating MAC-based password
     * @return SecurityTestResult with availability and security status
     */
    private SecurityTestResult testTelnetSecurity(String ipAddress, String macAddress) {
        int telnetPort = PreferencesManager.getTelnetPort();

        if (!isPortOpen(ipAddress, telnetPort)) {
            return new SecurityTestResult(false, true);
        }

        List<String> customAccounts = PreferencesManager.getCustomAccountMap();

        if (!customAccounts.isEmpty()) {
            return testTelnetWithCustomCredentials(ipAddress, telnetPort, customAccounts);
        } else {
            return testTelnetWithDefaultCredentials(ipAddress, telnetPort, macAddress);
        }
    }

    /**
     * Tests Telnet with custom credentials from preferences.
     */
    private SecurityTestResult testTelnetWithCustomCredentials(String ipAddress, int port,
                                                               List<String> credentials) {
        for (String credential : credentials) {
            String[] parts = credential.split(":");
            String username = parts[0];
            String password = parts[1];

            if (testTelnetLogin(ipAddress, port, username, password)) {
                logger.warn("Telnet INSECURE on {}: credentials {}/{} work", ipAddress, username, password);
                return new SecurityTestResult(true, false);
            }
        }

        logger.info("Telnet SECURE on {}", ipAddress);
        return new SecurityTestResult(true, true);
    }

    /**
     * Tests Telnet with factory default credentials.
     */
    private SecurityTestResult testTelnetWithDefaultCredentials(String ipAddress, int port,
                                                                String macAddress) {
        if (testTelnetLogin(ipAddress, port, DEFAULT_USERNAME, DEFAULT_PASSWORD)) {
            logger.warn("Telnet INSECURE on {}: factory credentials {}/{} work",
                    ipAddress, DEFAULT_USERNAME, DEFAULT_PASSWORD);
            return new SecurityTestResult(true, false);
        }

        String macBasedPassword = generateMacBasedPassword(macAddress);
        if (testTelnetLogin(ipAddress, port, DEFAULT_USERNAME, macBasedPassword)) {
            logger.warn("Telnet INSECURE on {}: factory credentials {}/{} work",
                    ipAddress, DEFAULT_USERNAME, macBasedPassword);
            return new SecurityTestResult(true, false);
        }

        logger.info("Telnet SECURE on {}", ipAddress);
        return new SecurityTestResult(true, true);
    }

    /**
     * Tests Telnet login with specific credentials.
     * <p>
     * This method connects to the Telnet port and interacts with the login prompts,
     * sending username and password when prompted.
     * </p>
     *
     * @param ipAddress the IP address to connect to
     * @param port the Telnet port
     * @param username the username to test
     * @param password the password to test
     * @return true if login succeeds (shell prompt detected), false otherwise
     */
    private boolean testTelnetLogin(String ipAddress, int port, String username, String password) {
        Socket socket = null;

        try {
            socket = new Socket();
            socket.connect(new InetSocketAddress(ipAddress, port), PORT_CHECK_TIMEOUT);
            socket.setSoTimeout(PROMPT_RESPONSE_DELAY);

            InputStream in = socket.getInputStream();
            OutputStream out = socket.getOutputStream();

            long startTime = System.currentTimeMillis();
            StringBuilder buffer = new StringBuilder();
            boolean usernameSent = false;
            boolean passwordSent = false;

            while (System.currentTimeMillis() - startTime < TELNET_TIMEOUT) {
                try {
                    if (in.available() > 0) {
                        int b = in.read();
                        if (b == -1) break;

                        char c = (char) b;
                        if (isPrintableOrWhitespace(c)) {
                            buffer.append(c);
                        }

                        String text = buffer.toString().toLowerCase();

                        if (!usernameSent && isUsernamePrompt(text)) {
                            out.write((username + "\n").getBytes());
                            out.flush();
                            usernameSent = true;
                            buffer.setLength(0);
                        } else if (usernameSent && !passwordSent && text.contains("password:")) {
                            out.write((password + "\n").getBytes());
                            out.flush();
                            passwordSent = true;
                            buffer.setLength(0);
                        } else if (passwordSent) {
                            if (isAuthenticationFailed(text)) {
                                return false;
                            }
                            if (isShellPrompt(text)) {
                                return true;
                            }
                        }
                    }
                } catch (SocketTimeoutException e) {
                    logger.debug("Telnet timeout during login: {}", e.getMessage());
                }
                Thread.sleep(LOGIN_WAIT_INTERVAL);
            }

            return false;

        } catch (Exception e) {
            logger.debug("Telnet login test failed for {}: {}", ipAddress, e.getMessage());
            return false;
        } finally {
            closeSocket(socket);
        }
    }

    /**
     * Tests SSH security by attempting to authenticate with default credentials.
     *
     * @param ipAddress the IP address to test
     * @param macAddress the MAC address for generating MAC-based password
     * @return SecurityTestResult with availability and security status
     */
    private SecurityTestResult testSshSecurity(String ipAddress, String macAddress) {
        int sshPort = PreferencesManager.getSshPort();

        if (!isPortOpen(ipAddress, sshPort)) {
            return new SecurityTestResult(false, true);
        }

        List<String> customAccounts = PreferencesManager.getCustomAccountMap();

        if (!customAccounts.isEmpty()) {
            return testSshWithCustomCredentials(ipAddress, sshPort, customAccounts);
        } else {
            return testSshWithDefaultCredentials(ipAddress, sshPort, macAddress);
        }
    }

    /**
     * Tests SSH with custom credentials from preferences.
     */
    private SecurityTestResult testSshWithCustomCredentials(String ipAddress, int port,
                                                            List<String> credentials) {
        for (String credential : credentials) {
            String[] parts = credential.split(":");
            String username = parts[0];
            String password = parts[1];

            if (testSshLogin(ipAddress, port, username, password)) {
                logger.warn("SSH INSECURE on {}: credentials {}/{} work", ipAddress, username, password);
                return new SecurityTestResult(true, false);
            }
        }

        logger.info("SSH SECURE on {}", ipAddress);
        return new SecurityTestResult(true, true);
    }

    /**
     * Tests SSH with factory default credentials.
     */
    private SecurityTestResult testSshWithDefaultCredentials(String ipAddress, int port,
                                                             String macAddress) {
        if (testSshLogin(ipAddress, port, DEFAULT_USERNAME, DEFAULT_PASSWORD)) {
            logger.warn("SSH INSECURE on {}: factory credentials {}/{} work",
                    ipAddress, DEFAULT_USERNAME, DEFAULT_PASSWORD);
            return new SecurityTestResult(true, false);
        }

        String macBasedPassword = generateMacBasedPassword(macAddress);
        if (testSshLogin(ipAddress, port, DEFAULT_USERNAME, macBasedPassword)) {
            logger.warn("SSH INSECURE on {}: factory credentials {}/{} work",
                    ipAddress, DEFAULT_USERNAME, macBasedPassword);
            return new SecurityTestResult(true, false);
        }

        logger.info("SSH SECURE on {}", ipAddress);
        return new SecurityTestResult(true, true);
    }

    /**
     * Tests SSH authentication by attempting to log in with provided credentials.
     *
     * @param ipAddress the IP address of the device
     * @param port the SSH port number
     * @param username the username to test
     * @param password the password to test
     * @return true if authentication succeeded and shell prompt was reached, false otherwise
     */
    private boolean testSshLogin(String ipAddress, int port, String username, String password) {
        JSch jsch = new JSch();
        Session session = null;
        ChannelShell channel = null;

        try {
            session = createSshSession(jsch, ipAddress, port, username, password);
            session.connect();

            if (!session.isConnected()) {
                logger.debug("SSH session failed to connect for {}", ipAddress);
                return false;
            }

            channel = (ChannelShell) session.openChannel("shell");
            channel.connect(SSH_READ_TIMEOUT);

            if (!channel.isConnected()) {
                logger.debug("SSH channel failed to connect for {}", ipAddress);
                return false;
            }

            return handleSshAuthentication(channel, ipAddress, username, password);

        } catch (JSchException e) {
            return handleSshException(e, ipAddress);
        } catch (Exception e) {
            logger.debug("SSH test error for {}: {}", ipAddress, e.getMessage());
            return false;
        } finally {
            closeSshResources(channel, session);
        }
    }

    /**
     * Creates and configures an SSH session.
     */
    private Session createSshSession(JSch jsch, String ipAddress, int port, String username, String password) throws JSchException {
        Session session = jsch.getSession(username, ipAddress, port);
        session.setPassword(password);
        session.setConfig("StrictHostKeyChecking", "no");
        session.setTimeout(SSH_TIMEOUT);
        return session;
    }

    /**
     * Handles the SSH authentication flow after channel is connected.
     */
    private boolean handleSshAuthentication(ChannelShell channel, String ipAddress, String username, String password) throws Exception {
        InputStream in = channel.getInputStream();
        OutputStream out = channel.getOutputStream();

        long startTime = System.currentTimeMillis();
        StringBuilder fullResponse = new StringBuilder();

        int usernamePromptCount = 0;
        int passwordPromptCount = 0;
        boolean welcomeScreenPassed = false;

        while (System.currentTimeMillis() - startTime < SSH_READ_TIMEOUT) {
            readAvailableData(in, fullResponse);

            String currentResponse = fullResponse.toString().toLowerCase();

            if (!welcomeScreenPassed && isWelcomeScreen(currentResponse)) {
                sendNewline(out);
                logger.debug("SSH: Sent <enter> to continue past welcome screen on {}", ipAddress);
                welcomeScreenPassed = true;
                fullResponse.setLength(0);
                Thread.sleep(PROMPT_RESPONSE_DELAY);
                continue;
            }

            if (isUsernamePrompt(currentResponse)) {
                usernamePromptCount++;
                logger.debug("SSH: Username prompt #{} detected on {}", usernamePromptCount, ipAddress);

                if (usernamePromptCount > 2) {
                    logger.warn("SSH: Too many username prompts on {} - authentication likely failed", ipAddress);
                    return false;
                }

                sendCredential(out, username);
                fullResponse.setLength(0);
                Thread.sleep(PROMPT_RESPONSE_DELAY);
                continue;
            }

            if (currentResponse.contains("password:")) {
                passwordPromptCount++;
                logger.debug("SSH: Password prompt #{} detected on {}", passwordPromptCount, ipAddress);

                if (passwordPromptCount > 2) {
                    logger.warn("SSH: Too many password prompts on {} - authentication likely failed", ipAddress);
                    return false;
                }

                sendCredential(out, password);
                fullResponse.setLength(0);
                Thread.sleep(PROMPT_RESPONSE_DELAY);
                continue;
            }

            if (isAuthenticationFailed(currentResponse)) {
                logger.info("SSH authentication explicitly failed for {}", ipAddress);
                return false;
            }

            if (isShellPrompt(currentResponse)) {
                logger.info("SSH authentication successful for {} - got shell prompt", ipAddress);
                return true;
            }

            Thread.sleep(LOGIN_WAIT_INTERVAL);
        }

        return analyzeFinalSshState(fullResponse.toString(), ipAddress, usernamePromptCount, passwordPromptCount);
    }

    /**
     * Analyzes the final state after SSH timeout.
     */
    private boolean analyzeFinalSshState(String finalResponse, String ipAddress, int usernamePromptCount, int passwordPromptCount) {
        String response = finalResponse.toLowerCase();

        logger.info("SSH timeout reached for {} after {}ms", ipAddress, SSH_READ_TIMEOUT);

        if (isUsernamePrompt(response) || response.contains("password:")) {
            logger.warn("SSH authentication incomplete on {} - still asking for credentials", ipAddress);
            return false;
        }

        if (usernamePromptCount > 0 || passwordPromptCount > 0) {
            logger.warn("SSH response unclear on {} after sending credentials", ipAddress);
            return false;
        }

        logger.warn("SSH response unclear on {} - no prompts detected", ipAddress);
        return false;
    }

    /**
     * Handles JSch exceptions and determines if they indicate auth failure.
     */
    private boolean handleSshException(JSchException e, String ipAddress) {
        String errorMsg = e.getMessage();

        if (errorMsg != null) {
            String lowerMsg = errorMsg.toLowerCase();
            if (lowerMsg.contains("auth fail") ||
                    lowerMsg.contains("auth cancel") ||
                    lowerMsg.contains("access denied") ||
                    lowerMsg.contains("authentication failed")) {
                logger.debug("SSH authentication failed for {}: {}", ipAddress, errorMsg);
                return false;
            }
        }

        logger.debug("SSH connection error for {}: {}", ipAddress, errorMsg);
        return false;
    }

    /**
     * Generates a MAC-based password (sw + last 6 chars of MAC).
     *
     * @param macAddress the MAC address
     * @return the generated password
     */
    private String generateMacBasedPassword(String macAddress) {
        if (macAddress == null || macAddress.length() < 6) {
            return DEFAULT_PASSWORD;
        }

        String macClean = macAddress.replace(":", "").toLowerCase();
        if (macClean.length() >= 6) {
            String last6 = macClean.substring(macClean.length() - 6);
            return MAC_PASSWORD_PREFIX + last6;
        }

        return DEFAULT_PASSWORD;
    }

    /**
     * Creates an SSL context that trusts all certificates.
     */
    private SSLContext createTrustAllSslContext() throws Exception {
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

        SSLContext sc = SSLContext.getInstance("TLS");
        sc.init(null, trustAllCerts, new java.security.SecureRandom());
        return sc;
    }

    /**
     * Checks if a character is printable ASCII or whitespace.
     */
    private boolean isPrintableOrWhitespace(char c) {
        return (c >= 32 && c <= 126) || c == '\n' || c == '\r';
    }

    /**
     * Checks if text contains a username prompt.
     */
    private boolean isUsernamePrompt(String text) {
        return text.contains("login:") ||
                text.contains("username:") ||
                text.contains("user name:");
    }

    /**
     * Checks if text indicates authentication failure.
     */
    private boolean isAuthenticationFailed(String text) {
        return text.contains("incorrect") ||
                text.contains("denied") ||
                text.contains("failed") ||
                text.contains("invalid") ||
                text.contains("wrong");
    }

    /**
     * Checks if text contains a shell prompt.
     */
    private boolean isShellPrompt(String text) {
        return text.contains("#") || text.contains(">") || text.contains("$");
    }

    /**
     * Checks if text is a welcome screen requiring enter key.
     */
    private boolean isWelcomeScreen(String text) {
        return text.contains("press <enter>") ||
                text.contains("press enter") ||
                text.contains("continue...");
    }

    /**
     * Reads available data from input stream into buffer.
     */
    private void readAvailableData(InputStream in, StringBuilder buffer) throws IOException {
        if (in.available() > 0) {
            byte[] tmp = new byte[1024];
            int bytesRead = in.read(tmp, 0, 1024);
            if (bytesRead > 0) {
                buffer.append(new String(tmp, 0, bytesRead));
            }
        }
    }

    /**
     * Sends a newline character.
     */
    private void sendNewline(OutputStream out) throws IOException {
        out.write("\n".getBytes());
        out.flush();
    }

    /**
     * Sends a credential followed by newline.
     */
    private void sendCredential(OutputStream out, String credential) throws IOException {
        out.write((credential + "\n").getBytes());
        out.flush();
    }

    /**
     * Closes a socket safely.
     */
    private void closeSocket(Socket socket) {
        if (socket != null && !socket.isClosed()) {
            try {
                socket.close();
            } catch (IOException ignored) {
            }
        }
    }

    /**
     * Closes SSH channel and session safely.
     */
    private void closeSshResources(ChannelShell channel, Session session) {
        if (channel != null && channel.isConnected()) {
            channel.disconnect();
        }
        if (session != null && session.isConnected()) {
            session.disconnect();
        }
    }

    /**
     * Record representing the result of a security test.
     *
     * @param available true if the protocol is available (port open)
     * @param secure true if default credentials do not work
     */
    public record SecurityTestResult(boolean available, boolean secure) {
    }

    /**
     * Class representing the results of protocol tests for a device.
     */
    public static class ProtocolTestResult {

        private boolean telnetAvailable;
        private boolean sshAvailable;
        private boolean httpAvailable;
        private boolean httpsAvailable;
        private boolean telnetSecure = true;
        private boolean sshSecure = true;

        /**
         * Constructs a new ProtocolTestResult for the specified IP address.
         *
         * @param ipAddress the IP address of the tested device
         */
        public ProtocolTestResult(String ipAddress) {}

        // Getters and setters

        public boolean isTelnetAvailable() {return telnetAvailable;}

        public void setTelnetAvailable(boolean telnetAvailable) {this.telnetAvailable = telnetAvailable;}

        public boolean isSshAvailable() {return sshAvailable;}

        public void setSshAvailable(boolean sshAvailable) {this.sshAvailable = sshAvailable;}

        public boolean isHttpAvailable() {return httpAvailable;}

        public void setHttpAvailable(boolean httpAvailable) {this.httpAvailable = httpAvailable;}

        public boolean isHttpsAvailable() {return httpsAvailable;}

        public void setHttpsAvailable(boolean httpsAvailable) {this.httpsAvailable = httpsAvailable;}

        public void setTelnetSecure(boolean telnetSecure) {this.telnetSecure = telnetSecure;}

        public void setSshSecure(boolean sshSecure) {this.sshSecure = sshSecure;}

        /**
         * Returns the security lock status as an integer.
         *
         * @return the security lock status
         */
        public Integer getSecurityLockStatus() {
            if (!telnetAvailable && !sshAvailable) {
                return 0; // Unknown - cannot test
            }

            if ((telnetAvailable && !telnetSecure) || (sshAvailable && !sshSecure)) {
                return 2; // Insecure - default credentials work
            }

            return 1; // Secure - default credentials do not work
        }

        /**
         * Returns a human-readable security status string.
         *
         * @return "UNKNOWN", "SECURE", or "INSECURE"
         */
        public String getSecurityStatus() {
            Integer lockStatus = getSecurityLockStatus();
            if (lockStatus == 0) return "UNKNOWN";
            if (lockStatus == 2) return "INSECURE";
            return "SECURE";
        }
    }
}