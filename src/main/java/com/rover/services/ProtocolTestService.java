package com.rover.services;

import com.jcraft.jsch.ChannelShell;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.rover.utils.PreferencesManager;
import com.rover.utils.SSHConnectionManager;
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
 * <p>
 * This service now uses {@link SSHConnectionManager} for SSH operations to avoid code duplication.
 * </p>
 *
 * @author Benjamin FOUILHAC, ADM21 - January/February 2026
 * @see PlanetDiscoveryService
 */
public class ProtocolTestService {

    private static final Logger logger = LoggerFactory.getLogger(ProtocolTestService.class);

    private static final int PORT_CHECK_TIMEOUT = 1000;
    private static final int TELNET_TIMEOUT = 1500;
    private static final int SSH_READ_TIMEOUT = 3000;
    private static final int HTTP_TIMEOUT = 500;
    private static final int HTTP_PORT = 80;

    private static final String DEFAULT_USERNAME = "admin";
    private static final String DEFAULT_PASSWORD = "admin";
    private static final String MAC_PASSWORD_PREFIX = "sw";

    private static final int PROMPT_RESPONSE_DELAY = 750;

    private final ProtocolTestResult result = new ProtocolTestResult();
    private final SSHConnectionManager sshManager = new SSHConnectionManager();

    /**
     * Tests all protocols for a given device.
     *
     * @param ipAddress the IP address of the device to test
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
     */
    public boolean isHttpAvailable(String ipAddress) {
        if (!isPortOpen(ipAddress, HTTP_PORT)) {
            return false;
        }

        try {
            URL url = new URI("http://" + ipAddress).toURL();
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(HTTP_TIMEOUT);
            connection.setReadTimeout(HTTP_TIMEOUT);
            connection.setRequestMethod("HEAD");
            connection.setInstanceFollowRedirects(false);

            int responseCode = connection.getResponseCode();
            connection.disconnect();

            return responseCode > 0;

        } catch (IOException e) {
            logger.error("HTTP connection failed for {}: {}", ipAddress, e.getMessage());
            return false;
        } catch (URISyntaxException e) {
            logger.error("Invalid IP address syntax: {}", ipAddress);
            return false;
        }
    }

    /**
     * Checks if HTTPS is available on the device.
     */
    public boolean isHttpsAvailable(String ipAddress) {
        try {
            URL url = new URI("https://" + ipAddress).toURL();
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

    private boolean isLocalPrivateNetwork(String ipAddress) {
        try {
            InetAddress addr = InetAddress.getByName(ipAddress);

            if (addr.isLoopbackAddress() || addr.isLinkLocalAddress()) {
                return false;
            }

            byte[] bytes = addr.getAddress();

            if (bytes[0] == 10) {
                return true;
            }

            if (bytes[0] == (byte) 172 && (bytes[1] & 0xF0) == 16) {
                return true;
            }

            return bytes[0] == (byte) 192 && bytes[1] == (byte) 168;

        } catch (UnknownHostException e) {
            logger.warn("Invalid IP address: {}", ipAddress);
            return false;
        }
    }

    private boolean isPortOpen(String ipAddress, int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(ipAddress, port), PORT_CHECK_TIMEOUT);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private SecurityTestResult testTelnetSecurity(String ipAddress, String macAddress) {
        int telnetPort = PreferencesManager.getTelnetPort();

        if (!isPortOpen(ipAddress, telnetPort)) {
            logger.warn("Telnet port not open on {}: port {} closed", ipAddress, telnetPort);
            return new SecurityTestResult(false, true);
        }

        List<String> customAccounts = PreferencesManager.getCustomAccountMap();

        if (!customAccounts.isEmpty()) {
            return testTelnetWithCustomCredentials(ipAddress, telnetPort, customAccounts);
        } else {
            return testTelnetWithDefaultCredentials(ipAddress, telnetPort, macAddress);
        }
    }

    private SecurityTestResult testTelnetWithCustomCredentials(String ipAddress, int port, List<String> credentials) {
        for (String credential : credentials) {
            String[] parts = credential.split(":");
            String username = parts[0];
            String password = parts[1];

            if (testTelnetLogin(ipAddress, port, username, password)) {
                logger.warn("Telnet INSECURE on {}: credentials {}/{} work", ipAddress, username, password);
                result.setPassword(password);
                return new SecurityTestResult(true, false);
            }
        }

        logger.info("Telnet custom cred SECURE on {}", ipAddress);
        return new SecurityTestResult(true, true);
    }

    private SecurityTestResult testTelnetWithDefaultCredentials(String ipAddress, int port, String macAddress) {
        if (testTelnetLogin(ipAddress, port, DEFAULT_USERNAME, DEFAULT_PASSWORD)) {
            logger.warn("Telnet INSECURE on {}: factory credentials {}/{} work", ipAddress, DEFAULT_USERNAME, DEFAULT_PASSWORD);
            result.setPassword(DEFAULT_PASSWORD);
            return new SecurityTestResult(true, false);
        }

        String macBasedPassword = generateMacBasedPassword(macAddress);
        if (testTelnetLogin(ipAddress, port, DEFAULT_USERNAME, macBasedPassword)) {
            logger.warn("Telnet INSECURE on {}: factory credentials {}/{} work", ipAddress, DEFAULT_USERNAME, macBasedPassword);
            result.setPassword(macBasedPassword);
            return new SecurityTestResult(true, false);
        }

        logger.info("Telnet SECURE on {}", ipAddress);
        return new SecurityTestResult(true, true);
    }

    private boolean testTelnetLogin(String ipAddress, int port, String username, String password) {
        try (Socket socket = new Socket()) {
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
            }

            return false;

        } catch (Exception e) {
            logger.debug("Telnet login test failed for {}: {}", ipAddress, e.getMessage());
            return false;
        }
    }

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

    private SecurityTestResult testSshWithCustomCredentials(String ipAddress, int port, List<String> credentials) {
        for (String credential : credentials) {
            String[] parts = credential.split(":");
            String username = parts[0];
            String password = parts[1];

            if (testSshLogin(ipAddress, port, username, password)) {
                result.setUsername(username);
                result.setPassword(password);
                logger.warn("SSH INSECURE on {}: credentials {}/{} work", ipAddress, username, password);
                return new SecurityTestResult(true, false);
            }
        }

        logger.info("SSH custom cred SECURE on {}", ipAddress);
        return new SecurityTestResult(true, true);
    }

    private SecurityTestResult testSshWithDefaultCredentials(String ipAddress, int port, String macAddress) {
        if (testSshLogin(ipAddress, port, DEFAULT_USERNAME, DEFAULT_PASSWORD)) {
            result.setUsername(DEFAULT_USERNAME);
            result.setPassword(DEFAULT_PASSWORD);
            logger.warn("SSH INSECURE on {}: factory credentials {}/{} work", ipAddress, DEFAULT_USERNAME, DEFAULT_PASSWORD);
            return new SecurityTestResult(true, false);
        }

        String macBasedPassword = generateMacBasedPassword(macAddress);
        if (testSshLogin(ipAddress, port, DEFAULT_USERNAME, macBasedPassword)) {
            result.setUsername(DEFAULT_USERNAME);
            result.setPassword(macBasedPassword);
            logger.warn("SSH INSECURE on {}: factory credentials {}/{} work", ipAddress, DEFAULT_USERNAME, macBasedPassword);
            return new SecurityTestResult(true, false);
        }

        logger.info("SSH SECURE on {}", ipAddress);
        return new SecurityTestResult(true, true);
    }

    /**
     * Tests SSH authentication using the centralized SSHConnectionManager.
     */
    private boolean testSshLogin(String ipAddress, int port, String username, String password) {
        Session session = null;
        ChannelShell channel = null;

        try {
            session = sshManager.createSession(ipAddress, port, username, password);
            session.connect();

            if (!session.isConnected()) {
                logger.debug("SSH session failed to connect for {}", ipAddress);
                return false;
            }

            channel = sshManager.openShellChannel(session);

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
            sshManager.disconnect(channel, session);
        }
    }

    /**
     * Handles SSH authentication with improved stability for devices with complex login flows.
     * <p>
     * This method handles various authentication scenarios:
     * - Welcome screens requiring <enter> to continue
     * - Multiple username/password prompts
     * - "% Authentication Failed" informational messages before re-prompting
     * - Shell prompt detection for successful login
     * </p>
     *
     * @param channel the SSH channel
     * @param ipAddress the IP address (for logging)
     * @param username the username
     * @param password the password
     * @return true if authentication succeeded, false otherwise
     * @throws Exception if errors occur
     */
    private boolean handleSshAuthentication(ChannelShell channel, String ipAddress, String username, String password) throws Exception {
        InputStream in = channel.getInputStream();
        OutputStream out = channel.getOutputStream();

        long startTime = System.currentTimeMillis();
        long lastActivityTime = System.currentTimeMillis();
        StringBuilder fullResponse = new StringBuilder();

        int usernamePromptCount = 0;
        int passwordPromptCount = 0;
        boolean welcomeScreenPassed = false;
        boolean authFailedMessageSeen = false;

        logger.debug("SSH: Starting authentication for {}", ipAddress);

        while (System.currentTimeMillis() - startTime < SSH_READ_TIMEOUT) {
            boolean dataRead = readAvailableDataBoolean(in, fullResponse);

            if (dataRead) {
                lastActivityTime = System.currentTimeMillis();
            }

            String currentResponse = fullResponse.toString().toLowerCase();
            String recentResponse = getRecentResponse(fullResponse, 200);

            // Handle welcome screen
            if (!welcomeScreenPassed && isWelcomeScreen(recentResponse)) {
                sendNewline(out);
                logger.debug("SSH: Sent <enter> to continue past welcome screen on {}", ipAddress);
                welcomeScreenPassed = true;
                fullResponse.setLength(0);
                Thread.sleep(200);
                continue;
            }

            // Detect "% Authentication Failed" informational message
            if (detectAuthFailedMessage(recentResponse)) {
                if (!authFailedMessageSeen) {
                    logger.debug("SSH: 'Authentication Failed' message detected on {} - device will re-prompt", ipAddress);
                    authFailedMessageSeen = true;
                }
                // Clear old buffer but keep recent content
                if (fullResponse.length() > 500) {
                    fullResponse.delete(0, fullResponse.length() - 200);
                }
                Thread.sleep(200);
                continue;
            }

            // Handle username prompt
            if (isUsernamePrompt(recentResponse)) {
                usernamePromptCount++;
                logger.debug("SSH: Username prompt #{} detected on {}", usernamePromptCount, ipAddress);

                if (usernamePromptCount > 3) {
                    logger.warn("SSH: Too many username prompts ({}) on {} - authentication likely failed", usernamePromptCount, ipAddress);
                    return false;
                }

                sendCredential(out, username);
                fullResponse.setLength(0);
                authFailedMessageSeen = false; // Reset flag
                Thread.sleep(200);
                continue;
            }

            // Handle password prompt
            if (recentResponse.contains("password:")) {
                passwordPromptCount++;
                logger.debug("SSH: Password prompt #{} detected on {}", passwordPromptCount, ipAddress);

                if (passwordPromptCount > 3) {
                    logger.warn("SSH: Too many password prompts ({}) on {} - authentication likely failed", passwordPromptCount, ipAddress);
                    return false;
                }

                sendCredential(out, password);
                fullResponse.setLength(0);
                Thread.sleep(200);
                continue;
            }

            // Check for shell prompt (success)
            if (isShellPrompt(recentResponse)) {
                logger.info("SSH: Authentication successful for {} - got shell prompt", ipAddress);
                return true;
            }

            // Check for terminal authentication failure (permanent rejection)
            if (isTerminalAuthFailure(currentResponse, usernamePromptCount, passwordPromptCount)) {
                logger.info("SSH: Authentication permanently failed for {}", ipAddress);
                return false;
            }

            // Check for inactivity timeout
            if (System.currentTimeMillis() - lastActivityTime > 2000 && usernamePromptCount > 0) {
                logger.debug("SSH: No activity for 2 seconds after sending credentials on {}", ipAddress);
                if (fullResponse.length() < 20) {
                    // No meaningful data received
                    return false;
                }
            }

            Thread.sleep(100);
        }

        return analyzeFinalSshState(fullResponse.toString(), ipAddress, usernamePromptCount, passwordPromptCount);
    }

    /**
     * Gets the most recent response text to avoid false positives from old buffer content.
     *
     * @param buffer the full response buffer
     * @param maxChars maximum number of recent characters to return
     * @return the recent response text
     */
    private String getRecentResponse(StringBuilder buffer, int maxChars) {
        int length = buffer.length();
        if (length <= maxChars) {
            return buffer.toString().toLowerCase();
        }
        return buffer.substring(length - maxChars).toLowerCase();
    }

    /**
     * Detects the "% Authentication Failed" informational message.
     *
     * @param text the text to check
     * @return true if authentication failed message detected
     */
    private boolean detectAuthFailedMessage(String text) {
        return text.contains("% authentication failed") ||
               text.contains("%authentication failed") ||
               text.contains("authentication failed");
    }

    /**
     * Detects terminal authentication failure (permanent rejection).
     *
     * @param text the full text buffer
     * @param usernamePromptCount number of username prompts seen
     * @param passwordPromptCount number of password prompts seen
     * @return true if authentication has permanently failed
     */
    private boolean isTerminalAuthFailure(String text, int usernamePromptCount, int passwordPromptCount) {
        // Only consider terminal failure if we've made attempts
        if (usernamePromptCount == 0 && passwordPromptCount == 0) {
            return false;
        }

        // Look for definitive rejection messages
        return text.contains("access denied") ||
               text.contains("incorrect") ||
               text.contains("invalid credentials") ||
               text.contains("login incorrect");
    }

    /**
     * Reads available data from input stream and returns whether data was read.
     *
     * @param in the input stream
     * @param buffer the buffer to append to
     * @return true if data was read, false otherwise
     * @throws IOException if I/O errors occur
     */
    private boolean readAvailableDataBoolean(InputStream in, StringBuilder buffer) throws IOException {
        if (in.available() > 0) {
            byte[] tmp = new byte[1024];
            int bytesRead = in.read(tmp, 0, 1024);
            if (bytesRead > 0) {
                buffer.append(new String(tmp, 0, bytesRead));
                return true;
            }
        }
        return false;
    }

    private boolean analyzeFinalSshState(String finalResponse, String ipAddress, int usernamePromptCount, int passwordPromptCount) {
        String response = finalResponse.toLowerCase();

        logger.info("SSH timeout reached for {} after {}ms", ipAddress, SSH_READ_TIMEOUT);

        if (isUsernamePrompt(response) || response.contains("password:")) {
            logger.warn("SSH authentication incomplete on {} - still asking for credentials", ipAddress);
        }

        if (usernamePromptCount > 0 || passwordPromptCount > 0) {
            logger.warn("SSH response unclear on {} after sending credentials", ipAddress);
        }

        logger.warn("SSH response unclear on {} - no prompts detected", ipAddress);
        return false;
    }

    private boolean handleSshException(JSchException e, String ipAddress) {
        String errorMsg = e.getMessage();

        if (errorMsg != null) {
            String lowerMsg = errorMsg.toLowerCase();
            if (lowerMsg.contains("auth fail") ||
                    lowerMsg.contains("auth cancel") ||
                    lowerMsg.contains("access denied") ||
                    lowerMsg.contains("authentication failed")) {
                logger.debug("SSH authentication failed for {}: {}", ipAddress, errorMsg);
            }
        }

        logger.debug("SSH connection error for {}: {}", ipAddress, errorMsg);
        return false;
    }

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
     * <p>
     * WARNING: This disables SSL certificate validation.
     * This is required because PLANET switches use self-signed certificates (IStaX-Selfsigned).
     * This should only be used on trusted private networks.
     * </p>
     *
     * @return SSLContext configured to accept self-signed certificates
     * @throws Exception if SSL context creation fails
     */
    @SuppressWarnings("java:S4830") // Intentionally trusting all certificates - switches use self-signed certs
    private SSLContext createTrustAllSslContext() throws Exception {
        TrustManager[] trustAllCerts = new TrustManager[]{
                new X509TrustManager() {
                    public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                         return new java.security.cert.X509Certificate[]{};
                    }

                    public void checkClientTrusted(java.security.cert.X509Certificate[] certs, String authType) {
                        // No need of implementation on this one
                    }

                    public void checkServerTrusted(java.security.cert.X509Certificate[] certs, String authType) {
                        // No need of implementation on this one
                    }
                }
        };

        SSLContext sc = SSLContext.getInstance("TLS");
        sc.init(null, trustAllCerts, new java.security.SecureRandom());
        return sc;
    }

    private boolean isPrintableOrWhitespace(char c) {
        return (c >= 32 && c <= 126) || c == '\n' || c == '\r';
    }

    private boolean isUsernamePrompt(String text) {
        return text.contains("login:") ||
                text.contains("username:") ||
                text.contains("user name:");
    }

    private boolean isAuthenticationFailed(String text) {
        return text.contains("incorrect") ||
                text.contains("denied") ||
                text.contains("failed") ||
                text.contains("invalid") ||
                text.contains("wrong");
    }

    private boolean isShellPrompt(String text) {
        return text.contains("#") || text.contains(">") || text.contains("$");
    }

    private boolean isWelcomeScreen(String text) {
        return text.contains("press <enter>") ||
                text.contains("press enter") ||
                text.contains("continue...");
    }

    private void readAvailableData(InputStream in, StringBuilder buffer) throws IOException {
        if (in.available() > 0) {
            byte[] tmp = new byte[1024];
            int bytesRead = in.read(tmp, 0, 1024);
            if (bytesRead > 0) {
                buffer.append(new String(tmp, 0, bytesRead));
            }
        }
    }

    private void sendNewline(OutputStream out) throws IOException {
        out.write("\n".getBytes());
        out.flush();
    }

    private void sendCredential(OutputStream out, String credential) throws IOException {
        out.write((credential + "\n").getBytes());
        out.flush();
    }

    public record SecurityTestResult(boolean available, boolean secure) {}

    public static class ProtocolTestResult {
        private boolean telnetAvailable;
        private boolean sshAvailable;
        private boolean httpAvailable;
        private boolean httpsAvailable;
        private boolean telnetSecure = true;
        private boolean sshSecure = true;
        private String username;
        private String password;

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {return password;}

        public void setPassword(String password) {
            this.password = password;
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

        public void setTelnetSecure(boolean telnetSecure) {
            this.telnetSecure = telnetSecure;
        }

        public void setSshSecure(boolean sshSecure) {
            this.sshSecure = sshSecure;
        }

        public Integer getSecurityLockStatus() {
            if (!telnetAvailable && !sshAvailable) {
                return 0;
            }

            if ((telnetAvailable && !telnetSecure) || (sshAvailable && !sshSecure)) {
                return 2;
            }

            return 1;
        }

        public String getSecurityStatus() {
            Integer lockStatus = getSecurityLockStatus();
            if (lockStatus == 0) return "UNKNOWN";
            if (lockStatus == 2) return "INSECURE";
            return "SECURE";
        }
    }
}