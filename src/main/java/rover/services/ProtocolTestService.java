package rover.services;

import com.jcraft.jsch.ChannelShell;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import rover.utils.PreferencesManager;
import rover.utils.SSHConnectionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Service for testing protocol connectivity and security on PLANET switches.
 * <p>
 * For Telnet and SSH, the service performs security testing by attempting to authenticate
 * with credentials in the following order:
 * 1. Custom accounts configured by the user in preferences
 * 2. Default credentials (admin/admin)
 * 3. MAC-based passwords (sw + last 6 chars of MAC)
 * This helps identify devices that still use factory default passwords or known credentials.
 * </p>
 * <p>
 * This service uses {@link SSHConnectionManager} for SSH operations to avoid code duplication.
 * </p>
 *
 * @author Benjamin FOUILHAC, ADM21 - January/February 2026
 * @see PlanetDiscoveryService
 */
public class ProtocolTestService {

    private static final Logger logger = LoggerFactory.getLogger(ProtocolTestService.class);

    private static final int TELNET_TIMEOUT = 1500;
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
        logger.info("Protocol tests for {} completed in {}ms (security: {})", ipAddress, elapsed, result.getSecurityStatus());
        return result;
    }

    /**
     * Checks if HTTP is available on the device.
     */
    public boolean isHttpAvailable(String ipAddress) {
        return isPortOpen(ipAddress, HTTP_PORT);
    }

    /**
     * Checks if HTTPS is available on the device.
     * <p>
     * Uses a simple port check (443) to verify HTTPS availability.
     * This approach avoids SSL certificate validation issues with self-signed
     * certificates while maintaining security (no TrustManager bypass needed).
     * </p>
     */
    public boolean isHttpsAvailable(String ipAddress) {
        return isPortOpen(ipAddress, 443);
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
            socket.connect(new InetSocketAddress(ipAddress, port), PreferencesManager.getProtocolTestTimeout());
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

        for (String credential : buildCredentialsList(macAddress)) {
            String[] parts = credential.split(":");
            String username = parts[0];
            String password = parts[1];

            if (testTelnetLogin(ipAddress, telnetPort, username, password)) {
                logger.warn("Telnet open on {}: credentials {}/{} work", ipAddress, username, password);
                result.setUsername(username);
                result.setPassword(password);
                return new SecurityTestResult(true, false);
            }
        }

        logger.info("Telnet secured on {}", ipAddress);
        return new SecurityTestResult(true, true);
    }

    private boolean testTelnetLogin(String ipAddress, int port, String username, String password) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(ipAddress, port), PreferencesManager.getProtocolTestTimeout());
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

                        if (!usernameSent && sshManager.isUsernamePrompt(text)) {
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
                            if (sshManager.isShellPrompt(text)) {
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

        for (String credential : buildCredentialsList(macAddress)) {
            String[] parts = credential.split(":");
            String username = parts[0];
            String password = parts[1];

            if (testSshLogin(ipAddress, sshPort, username, password)) {
                logger.warn("SSH OPEN on {}: credentials {}/{} work", ipAddress, username, password);
                result.setUsername(username);
                result.setPassword(password);
                return new SecurityTestResult(true, false);
            }
        }

        logger.info("SSH secured on {}", ipAddress);
        return new SecurityTestResult(true, true);
    }

    /**
     * Builds the list of credentials to test for a device.
     * Custom accounts are tested first, followed by default credentials.
     *
     * @param macAddress the MAC address used to generate the MAC-based password
     * @return list of credentials in format "username:password"
     */
    private List<String> buildCredentialsList(String macAddress) {
        List<String> credentials = new ArrayList<>(PreferencesManager.getCustomAccountMap());
        credentials.add(DEFAULT_USERNAME + ":" + DEFAULT_PASSWORD);
        credentials.add(DEFAULT_USERNAME + ":" + generateMacBasedPassword(macAddress));
        return credentials;
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

            logger.info("Testing SSH Auth on {} ({}/{})", ipAddress, username, password);
            return sshManager.handleSshAuthentication(channel, ipAddress, username, password);

        } catch (JSchException e) {
            return handleSshException(e, ipAddress);
        } catch (Exception e) {
            logger.debug("SSH test error for {}: {}", ipAddress, e.getMessage());
            return false;
        } finally {
            sshManager.disconnect(channel, session);
        }
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

    private boolean isPrintableOrWhitespace(char c) {
        return (c >= 32 && c <= 126) || c == '\n' || c == '\r';
    }

    private boolean isAuthenticationFailed(String text) {
        return text.contains("incorrect") ||
                text.contains("denied") ||
                text.contains("failed") ||
                text.contains("invalid") ||
                text.contains("wrong");
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