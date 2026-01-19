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


public class ProtocolTestService {

    private static final Logger logger = LoggerFactory.getLogger(ProtocolTestService.class);

    private static final int PORT_CHECK_TIMEOUT = 500;
    private static final int TELNET_TIMEOUT = 1500;
    private static final int SSH_TIMEOUT = 2000;
    private static final int SSH_READ_TIMEOUT = 2000;
    private static final int HTTP_TIMEOUT = 500;
    private static final int HTTP_PORT = 80;

    private static final String DEFAULT_USERNAME = "admin";
    private static final String DEFAULT_PASSWORD = "admin";
    private static final String MAC_PASSWORD_PREFIX = "sw";

    /**
     * Check if an IPv4 address is in a local private network
     *
     * @param ipAddress the address to check
     * @return either true or false, depends on the RFC1918
     */
    private boolean isLocalPrivateNetwork(String ipAddress) {
        try {
            InetAddress addr = InetAddress.getByName(ipAddress);

            if (addr.isLoopbackAddress()) {
                return false; // 127.0.0.1
            }

            if (addr.isLinkLocalAddress()) {
                return false; // 169.254.x.x (APIPA)
            }

            // Accept only private networks RFC1918
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

    public ProtocolTestResult testAllProtocols(String ipAddress, String macAddress) {
        logger.info("Testing protocols for device: {}", ipAddress);
        if (isLocalPrivateNetwork(ipAddress)) {
            long startTime = System.currentTimeMillis();

            ProtocolTestResult result = new ProtocolTestResult(ipAddress);

            SecurityTestResult sshResult = testSSHSecurity(ipAddress, macAddress);
            result.setSshAvailable(sshResult.available());
            result.setSshSecure(sshResult.secure());

            SecurityTestResult telnetResult = testTelnetSecurity(ipAddress, macAddress);
            result.setTelnetAvailable(telnetResult.available());
            result.setTelnetSecure(telnetResult.secure());

            result.setHttpAvailable(isHTTPAvailable(ipAddress));
            result.setHttpsAvailable(isHTTPSAvailable(ipAddress));

            long elapsed = System.currentTimeMillis() - startTime;
            logger.info("Protocol tests for {} completed in {}ms (security: {})", ipAddress, elapsed, result.getSecurityStatus());
            return result;
        } else {
            logger.error("Security: rejected non-local IPv4 address: {}", ipAddress);
            throw new SecurityException("Security rejected non-local IPv4 address: " + ipAddress);
        }
    }

    private SecurityTestResult testTelnetSecurity(String ipAddress, String macAddress) {
        int telnetPort = PreferencesManager.getTelnetPort();

        if (!isPortOpen(ipAddress, telnetPort)) {
            return new SecurityTestResult(false, true);
        }

        List<String> customAccList = PreferencesManager.getCustomAccountMap();
        if (!customAccList.isEmpty()) {
            for (String i : customAccList) {
                if (testTelnetLogin(ipAddress, telnetPort, i.split(":")[0], i.split(":")[1])) {
                    logger.warn("Telnet INSECURE on {}: credentials {}/{} work", ipAddress, i.split(":")[0], i.split(":")[1]);
                    return new SecurityTestResult(true, false);
                }
            }
        } else {
            if (testTelnetLogin(ipAddress, telnetPort, DEFAULT_USERNAME, DEFAULT_PASSWORD)) {
                logger.warn("Telnet INSECURE on {}: factory credentials {}/{} work", ipAddress, DEFAULT_USERNAME, DEFAULT_PASSWORD);
                return new SecurityTestResult(true, false);
            }

            String macBasedPassword = generateMACBasedPassword(macAddress);
            if (testTelnetLogin(ipAddress, telnetPort, DEFAULT_USERNAME, macBasedPassword)) {
                logger.warn("Telnet INSECURE on {}: factory credentials {}/{} work", ipAddress, DEFAULT_USERNAME, macBasedPassword);
                return new SecurityTestResult(true, false);
            }
        }
        logger.info("Telnet SECURE on {}:", ipAddress);
        return new SecurityTestResult(true, true);
    }

    private boolean testTelnetLogin(String ipAddress, int port, String username, String password) {
        Socket socket = null;

        try {
            socket = new Socket();
            socket.connect(new InetSocketAddress(ipAddress, port), PORT_CHECK_TIMEOUT);
            socket.setSoTimeout(300);

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
                        if (c >= 32 && c <= 126 || c == '\n' || c == '\r') {
                            buffer.append(c);
                        }

                        String text = buffer.toString().toLowerCase();

                        if (!usernameSent && (text.contains("login:") || text.contains("username:"))) {
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
                            if (text.contains("incorrect") ||
                                    text.contains("denied") ||
                                    text.contains("failed") ||
                                    text.contains("wrong")) {
                                return false;
                            }
                            if (text.contains("#") || text.contains(">") || text.contains("$")) {
                                return true;
                            }
                        }
                    }
                } catch (SocketTimeoutException e) {
                    logger.error("Error timeout during telnet login :{}", e.getMessage());
                }
                Thread.sleep(50); // Small pause between each login
            }
            return false;
        } catch (Exception e) {
            logger.debug("Telnet login test failed for {}: {}", ipAddress, e.getMessage());
            return false;
        } finally {
            if (socket != null && !socket.isClosed()) {
                try {
                    socket.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    private SecurityTestResult testSSHSecurity(String ipAddress, String macAddress) {
        int sshPort = PreferencesManager.getSshPort();

        if (!isPortOpen(ipAddress, sshPort)) {
            return new SecurityTestResult(false, true);
        }

        List<String> customAccList = PreferencesManager.getCustomAccountMap();
        if (!customAccList.isEmpty()) {
            for (String i : customAccList) {
                if (testSSHLogin(ipAddress, sshPort, i.split(":")[0], i.split(":")[1])) {
                    logger.warn("SSH INSECURE on {}: credentials {}/{} work", ipAddress, i.split(":")[0], i.split(":")[1]);
                    return new SecurityTestResult(true, false);
                }
            }
        } else {
            if (testSSHLogin(ipAddress, sshPort, DEFAULT_USERNAME, DEFAULT_PASSWORD)) {
                logger.warn("SSH INSECURE on {}: factory credentials {}/{} work", ipAddress, DEFAULT_USERNAME, DEFAULT_PASSWORD);
                return new SecurityTestResult(true, false);
            }

            String macBasedPassword = generateMACBasedPassword(macAddress);
            if (testSSHLogin(ipAddress, sshPort, DEFAULT_USERNAME, macBasedPassword)) {
                logger.warn("SSH INSECURE on {}: factory credentials {}/{} work", ipAddress, DEFAULT_USERNAME, macBasedPassword);
                return new SecurityTestResult(true, false);
            }
        }

        logger.info("SSH SECURE on {}", ipAddress);
        return new SecurityTestResult(true, true);
    }

    private boolean testSSHLogin(String ipAddress, int port, String username, String password) {
        JSch jsch = new JSch();
        Session session = null;
        ChannelShell channel = null;

        try {
            session = jsch.getSession(username, ipAddress, port);
            session.setPassword(password);
            session.setConfig("StrictHostKeyChecking", "no");
            session.setTimeout(SSH_TIMEOUT);

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

            InputStream in = channel.getInputStream();
            OutputStream out = channel.getOutputStream();

            long startTime = System.currentTimeMillis();
            StringBuilder fullResponse = new StringBuilder();

            while (System.currentTimeMillis() - startTime < SSH_READ_TIMEOUT) {
                if (in.available() > 0) {
                    byte[] tmp = new byte[1024];
                    int bytesRead = in.read(tmp, 0, 1024);
                    if (bytesRead > 0) {
                        String chunk = new String(tmp, 0, bytesRead);
                        fullResponse.append(chunk);
                    }
                }

                String currentResponse = fullResponse.toString().toLowerCase();

                if (currentResponse.contains("press <enter>") ||
                        currentResponse.contains("press enter") ||
                        currentResponse.contains("continue...")) {
                    out.write("\n".getBytes());
                    out.flush();
                    logger.debug("SSH: Sent <enter> to continue past welcome screen on {}", ipAddress);
                    Thread.sleep(300);
                    continue;
                }

                if (currentResponse.contains("username:") || currentResponse.contains("user name:")) {
                    logger.info("SSH server on {} asks for username after welcome screen - treating as NOT authenticated", ipAddress);
                    return false;
                }

                if (currentResponse.contains("password:")) {
                    logger.info("SSH server on {} asks for password after welcome screen - treating as NOT authenticated", ipAddress);
                    return false;
                }

                if (currentResponse.contains("denied") ||
                        currentResponse.contains("incorrect") ||
                        currentResponse.contains("failed") ||
                        currentResponse.contains("invalid")) {
                    logger.debug("SSH authentication failed for {}: response contains failure keyword", ipAddress);
                    return false;
                }

                if (currentResponse.contains("#") ||
                        currentResponse.contains(">") ||
                        currentResponse.contains("$")) {
                    logger.info("SSH authentication successful for {} - got shell prompt", ipAddress);
                    return true;
                }

                Thread.sleep(50);
            }

            String finalResponse = fullResponse.toString();
            logger.info("SSH server response for {} (password=***): [{}]",
                    ipAddress, finalResponse.replace("\n", "\\n").replace("\r", "\\r"));

            if (finalResponse.toLowerCase().contains("username:") ||
                    finalResponse.toLowerCase().contains("password:")) {
                logger.warn("SSH authentication incomplete on {} - still asking for credentials", ipAddress);
                return false;
            }

            logger.warn("SSH response unclear on {} - treating as authentication failure", ipAddress);
            return false;

        } catch (JSchException e) {
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

        } catch (Exception e) {
            logger.debug("SSH test error for {}: {}", ipAddress, e.getMessage());
            return false;

        } finally {
            if (channel != null && channel.isConnected()) {
                channel.disconnect();
            }
            if (session != null && session.isConnected()) {
                session.disconnect();
            }
        }
    }

    private String generateMACBasedPassword(String macAddress) {
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

    public boolean isHTTPAvailable(String ipAddress) {
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

    public boolean isHTTPSAvailable(String ipAddress) {
        try {
            URL url = new URL("https://" + ipAddress);
            HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();
            connection.setConnectTimeout(HTTP_TIMEOUT);
            connection.setReadTimeout(HTTP_TIMEOUT);
            connection.setRequestMethod("HEAD");
            connection.setInstanceFollowRedirects(false);

            // Configuration locale (pas globale)
            connection.setSSLSocketFactory(createTrustAllSSLContext().getSocketFactory());
            connection.setHostnameVerifier((hostname, session) -> true);

            int responseCode = connection.getResponseCode();
            connection.disconnect();

            return responseCode >= 200 && responseCode < 300;

        } catch (Exception e) {
            return false;
        }
    }

    private SSLContext createTrustAllSSLContext() throws Exception {
        TrustManager[] trustAllCerts = new TrustManager[]{
                new X509TrustManager() {
                    public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                        return null;
                    }
                    public void checkClientTrusted(java.security.cert.X509Certificate[] certs, String authType) {}
                    public void checkServerTrusted(java.security.cert.X509Certificate[] certs, String authType) {}
                }
        };

        SSLContext sc = SSLContext.getInstance("TLS");
        sc.init(null, trustAllCerts, new java.security.SecureRandom());
        return sc;
    }

    private boolean isPortOpen(String ipAddress, int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(ipAddress, port), PORT_CHECK_TIMEOUT);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    public record SecurityTestResult(boolean available, boolean secure) {
    }

    public static class ProtocolTestResult {
        private boolean telnetAvailable;
        private boolean sshAvailable;
        private boolean httpAvailable;
        private boolean httpsAvailable;
        private boolean telnetSecure = true;
        private boolean sshSecure = true;

        public ProtocolTestResult(String ipAddress) {
        }

        public boolean isTelnetAvailable() { return telnetAvailable; }
        public void setTelnetAvailable(boolean telnetAvailable) { this.telnetAvailable = telnetAvailable; }

        public boolean isSshAvailable() { return sshAvailable; }
        public void setSshAvailable(boolean sshAvailable) { this.sshAvailable = sshAvailable; }

        public boolean isHttpAvailable() { return httpAvailable; }
        public void setHttpAvailable(boolean httpAvailable) { this.httpAvailable = httpAvailable; }

        public boolean isHttpsAvailable() { return httpsAvailable; }
        public void setHttpsAvailable(boolean httpsAvailable) { this.httpsAvailable = httpsAvailable; }

        public boolean isTelnetSecure() { return telnetSecure; }
        public void setTelnetSecure(boolean telnetSecure) { this.telnetSecure = telnetSecure; }

        public boolean isSshSecure() { return sshSecure; }
        public void setSshSecure(boolean sshSecure) { this.sshSecure = sshSecure; }

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