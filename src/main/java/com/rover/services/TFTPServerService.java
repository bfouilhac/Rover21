package com.rover.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Simple TFTP server for receiving configuration files from network switches.
 * <p>
 * This server implements RFC 1350 (TFTP Protocol) and is designed to run temporarily
 * to receive a single file from a switch during configuration backup operations.
 * It supports only write requests (WRQ) and runs on the standard TFTP port 69.
 * </p>
 *
 * @author Benjamin FOUILHAC, ADM21 - January/February 2026
 */
public class TFTPServerService {

    private static final Logger logger = LoggerFactory.getLogger(TFTPServerService.class);

    private static final int TFTP_PORT = 69;
    private static final int BUFFER_SIZE = 516;
    private static final int DATA_SIZE = 512;
    private static final int TIMEOUT_MS = 30000;

    private static final short OP_RRQ = 1;
    private static final short OP_WRQ = 2;
    private static final short OP_DATA = 3;
    private static final short OP_ACK = 4;
    private static final short OP_ERROR = 5;

    private final String listenAddress;

    private DatagramSocket socket;

    private final AtomicBoolean running = new AtomicBoolean(false);

    private final CountDownLatch completionLatch = new CountDownLatch(1);

    private ByteArrayOutputStream receivedData;

    private ExecutorService executor;

    /**
     * Creates a TFTP server that listens to the specified IP address.
     *
     * @param listenAddress the IP address to bind to
     */
    public TFTPServerService(String listenAddress) {
        this.listenAddress = listenAddress;
    }

    /**
     * Starts the TFTP server and waits for a file transfer.
     * <p>
     * This method blocks until a file is received or the timeout expires.
     * </p>
     *
     * @param timeoutMs maximum time to wait for file reception
     * @return the received file data as a byte array, or null if timeout/error
     * @throws IOException if server startup fails
     */
    public byte[] startAndWaitForFile(int timeoutMs) throws IOException {
        receivedData = new ByteArrayOutputStream();

        try {
            InetAddress bindAddress = InetAddress.getByName(listenAddress);
            socket = new DatagramSocket(TFTP_PORT, bindAddress);
            socket.setSoTimeout(1000);

            logger.info("TFTP server started on {}:{}", listenAddress, TFTP_PORT);

            running.set(true);
            executor = Executors.newSingleThreadExecutor();
            executor.submit(this::serverLoop);

            boolean completed = completionLatch.await(timeoutMs, TimeUnit.MILLISECONDS);

            if (completed) {
                logger.info("TFTP transfer completed, received {} bytes", receivedData.size());
                return receivedData.toByteArray();
            } else {
                logger.warn("TFTP transfer timeout after {}ms", timeoutMs);
                return null;
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("TFTP server interrupted", e);
            return null;
        } finally {
            stop();
        }
    }

    /**
     * Main server loop that handles incoming TFTP packets.
     */
    private void serverLoop() {
        byte[] buffer = new byte[BUFFER_SIZE];

        while (running.get()) {
            try {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);

                handlePacket(packet);

            } catch (SocketTimeoutException e) {
                // Normal timeout, continue loop
            } catch (IOException e) {
                if (running.get()) {
                    logger.error("TFTP server error", e);
                }
                break;
            }
        }
    }

    /**
     * Handles a single TFTP packet.
     */
    private void handlePacket(DatagramPacket packet) throws IOException {
        ByteBuffer buffer = ByteBuffer.wrap(packet.getData(), 0, packet.getLength());
        short opcode = buffer.getShort();

        InetAddress clientAddress = packet.getAddress();
        int clientPort = packet.getPort();

        switch (opcode) {
            case OP_WRQ:
                handleWriteRequest(clientAddress, clientPort, buffer);
                break;
            case OP_DATA:
                handleData(clientAddress, clientPort, buffer);
                break;
            case OP_RRQ:
                sendError(clientAddress, clientPort, 4, "Read not supported");
                break;
            default:
                sendError(clientAddress, clientPort, 4, "Illegal TFTP operation");
        }
    }

    /**
     * Handles a Write Request (WRQ) packet.
     */
    private void handleWriteRequest(InetAddress clientAddress, int clientPort, ByteBuffer buffer) throws IOException {
        String filename = readString(buffer);
        String mode = readString(buffer);

        logger.info("TFTP WRQ from {}:{} - file: {}, mode: {}",
                clientAddress.getHostAddress(), clientPort, filename, mode);

        receivedData.reset();
        sendAck(clientAddress, clientPort, 0);
    }

    /**
     * Handles a DATA packet.
     */
    private void handleData(InetAddress clientAddress, int clientPort, ByteBuffer buffer) throws IOException {
        short blockNumber = buffer.getShort();
        int dataLength = buffer.remaining();

        byte[] data = new byte[dataLength];
        buffer.get(data);

        receivedData.write(data);

        logger.debug("TFTP DATA block {} ({} bytes)", blockNumber, dataLength);

        sendAck(clientAddress, clientPort, blockNumber);

        if (dataLength < DATA_SIZE) {
            logger.info("TFTP transfer complete (last block: {})", blockNumber);
            completionLatch.countDown();
        }
    }

    /**
     * Sends an ACK packet.
     */
    private void sendAck(InetAddress address, int port, int blockNumber) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(4);
        buffer.putShort(OP_ACK);
        buffer.putShort((short) blockNumber);

        DatagramPacket packet = new DatagramPacket(
                buffer.array(), buffer.position(), address, port
        );

        socket.send(packet);
        logger.trace("Sent ACK for block {}", blockNumber);
    }

    /**
     * Sends an ERROR packet.
     */
    private void sendError(InetAddress address, int port, int errorCode, String message) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(5 + message.length());
        buffer.putShort(OP_ERROR);
        buffer.putShort((short) errorCode);
        buffer.put(message.getBytes());
        buffer.put((byte) 0);

        DatagramPacket packet = new DatagramPacket(
                buffer.array(), buffer.position(), address, port
        );

        socket.send(packet);
        logger.debug("Sent ERROR {} to {}:{} - {}", errorCode, address.getHostAddress(), port, message);
    }

    /**
     * Reads a null-terminated string from the buffer.
     */
    private String readString(ByteBuffer buffer) {
        ByteArrayOutputStream str = new ByteArrayOutputStream();

        while (buffer.hasRemaining()) {
            byte b = buffer.get();
            if (b == 0) break;
            str.write(b);
        }

        return str.toString();
    }

    /**
     * Stops the TFTP server and releases resources.
     */
    public void stop() {
        running.set(false);

        if (socket != null && !socket.isClosed()) {
            socket.close();
        }

        if (executor != null && !executor.isShutdown()) {
            executor.shutdownNow();
        }

        logger.info("TFTP server stopped");
    }
}