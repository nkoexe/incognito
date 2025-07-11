package org.incognito;

import org.incognito.crypto.CryptoManager;
import org.incognito.crypto.AutoKeyExchange;
import org.incognito.GUI.UI;

import javax.swing.*;
import java.util.Base64;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Logger;

import java.io.ObjectInputStream;
import java.io.IOException;
import java.net.Socket;

public class ReadThread extends Thread {
    private static Logger logger = Logger.getLogger(ReadThread.class.getName());

    private CryptoManager cryptoManager;
    private ObjectInputStream inputStream;
    private Socket socket;
    private UI client;

    private final BlockingQueue<Object> messageQueue = new LinkedBlockingQueue<>();
    private BlockingQueue<String> loginResponseQueue;

    public ReadThread(Socket socket, UI client, CryptoManager cryptoManager) {
        this(socket, client, cryptoManager, null);
    }

    public ReadThread(Socket socket, UI client, CryptoManager cryptoManager,
            BlockingQueue<String> loginResponseQueue) {
        this.socket = socket;
        this.client = client;
        this.cryptoManager = cryptoManager;
        this.loginResponseQueue = loginResponseQueue;

        try {
            if (socket.isClosed()) {
                ErrorHandler.handleConnectionError(
                        client,
                        "Cannot create input stream - socket is closed",
                        false,
                        null);
                return;
            }
            inputStream = new ObjectInputStream(socket.getInputStream());
        } catch (IOException ex) {
            ErrorHandler.handleConnectionError(
                    client,
                    "Failed to initialize read stream",
                    true,
                    () -> {
                        try {
                            inputStream = new ObjectInputStream(socket.getInputStream());
                        } catch (IOException retryEx) {
                            ErrorHandler.handleFatalError(
                                    client,
                                    "Failed to initialize read stream after retry",
                                    retryEx);
                        }
                    });
        }
    }

    @Override
    public void run() {
        while (!Thread.currentThread().isInterrupted()) {
            Object obj = receiveObject();
            if (obj == null)
                break;

            try {
                // Login system messages are expected to be Strings
                if (obj instanceof String msgStr) {
                    if (loginResponseQueue != null &&
                            (msgStr.equals("USERNAME_ACCEPTED") || msgStr.equals("USERNAME_TAKEN")
                                    || msgStr.equals("INVALID_COMMAND"))) {
                        loginResponseQueue.put(msgStr);
                        loginResponseQueue = null; // Only used once
                        continue;
                    }

                    // Store in messageQueue for blocking reads
                    messageQueue.put(msgStr);

                    // Process system or chat messages
                    if (msgStr.startsWith("USERLIST:") ||
                            msgStr.startsWith("CONNECT:") ||
                            msgStr.startsWith("DISCONNECT:") ||
                            msgStr.startsWith("SERVER:") ||
                            msgStr.startsWith("ERROR:") ||
                            msgStr.startsWith("PEER_CONNECTED:") ||
                            msgStr.startsWith("INFO:")) {
                        processSystemMessage(msgStr);
                    } else {
                        client.appendMessage(msgStr);
                    }
                } else if (obj instanceof ChatMessage chatMsg) {
                    logger.info("Received ChatMessage from: " + chatMsg.getSender());
                    byte[] encrypted = Base64.getDecoder().decode(chatMsg.getEncryptedContent());
                    String decrypted = cryptoManager.decryptAES(encrypted);

                    // For manual key exchange flow, don't display our own messages since they're
                    // already shown locally
                    if (!chatMsg.getSender().equals(client.getUserName())) {
                        client.appendMessage(chatMsg.getSender() + ": " + decrypted);
                        logger.info("Displayed message from " + chatMsg.getSender() + ": " + decrypted);
                    } else {
                        logger.info("Ignoring own message from " + chatMsg.getSender());
                    }
                    messageQueue.put(chatMsg);
                } else if (obj instanceof KeyExchangeMessage keyExchangeMsg) {
                    try {
                        // Handle automatic key exchange
                        AutoKeyExchange.handleIncomingKeyExchange(keyExchangeMsg, cryptoManager,
                                client.getWriteThread(), client);
                        messageQueue.put(keyExchangeMsg);
                    } catch (Exception e) {
                        ErrorHandler.handleCryptoError(
                                client,
                                "Failed to handle key exchange message",
                                e,
                                () -> AutoKeyExchange.handleIncomingKeyExchange(keyExchangeMsg, cryptoManager,
                                        client.getWriteThread(), client));
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                ErrorHandler.handleSessionError(
                        client,
                        "Error processing incoming message",
                        reconnect -> {
                            if (reconnect) {
                                try {
                                    client.initializeConnection(new Connection());
                                } catch (Exception ex) {
                                    ErrorHandler.handleConnectionError(
                                            client,
                                            "Failed to reconnect: " + ex.getMessage(),
                                            false,
                                            null);
                                }
                            }
                        });
            }
        }
        close();
    }

    private Object receiveObject() {
        try {
            return inputStream.readObject();
        } catch (IOException | ClassNotFoundException e) {
            // Se il thread è stato interrotto, ignora silenziosamente l'errore di socket
            // chiusa
            if (Thread.currentThread().isInterrupted()
                    || (e instanceof IOException && socket != null && socket.isClosed())) {
                logger.info("ReadThread interrotto o socket chiusa: chiusura silenziosa.");
                return null;
            }
            ErrorHandler.handleConnectionError(
                    client,
                    "Lost connection to server",
                    true,
                    () -> {
                        try {
                            client.initializeConnection(new Connection());
                        } catch (Exception ex) {
                            ErrorHandler.handleFatalError(
                                    client,
                                    "Failed to reconnect",
                                    ex);
                        }
                    });
            return null;
        }
    }

    private void processSystemMessage(String message) {
        if (message.startsWith("USERLIST:")) {
            String userListStr = message.substring("USERLIST:".length());
            client.updateUsersList(userListStr);
        } else if (message.startsWith("CONNECT:")) {
            // User connected - handled by server broadcast
        } else if (message.startsWith("DISCONNECT:")) {
            String username = message.substring("DISCONNECT:".length());
            SwingUtilities.invokeLater(() -> {
                client.removeUser(username);
            });
        } else if (message.startsWith("SERVER:")) {
            String serverMessage = message.substring("SERVER:".length());
            client.appendMessage("[Server] " + serverMessage);
        } else if (message.startsWith("ERROR:")) {
            String errorMessage = message.substring("ERROR:".length());
            client.appendMessage("[Error] " + errorMessage);
        } else if (message.startsWith("INFO:")) {
            String infoMessage = message.substring("INFO:".length());
            client.appendMessage("[Info] " + infoMessage);
        } else if (message.startsWith("PEER_CONNECTED:")) {
            String[] parts = message.split(":", 3);
            if (parts.length == 3) {
                String peerUsername = parts[1];
                String sessionId = parts[2];

                // Log the peer connection
                LocalLogger.logInfo("Peer connected: " + peerUsername + " with session: " + sessionId);
                // Log for the chat session
                ChatSessionLogger.logInfo("Peer connected: " + peerUsername + " with session: " + sessionId);
                client.handlePeerConnected(peerUsername, sessionId);

                // Only the first user (alphabetically) starts the key exchange to avoid
                // duplicates
                if (client.getUserName().compareTo(peerUsername) < 0) {
                    logger.info("Starting key exchange as initiator with " + peerUsername);
                    AutoKeyExchange.performKeyExchange(peerUsername, client.getUserName(), cryptoManager,
                            client.getWriteThread());
                } else {
                    logger.info("Waiting for key exchange from " + peerUsername);
                }
            }
        } else {
            client.handleServerNotification(message);
        }
    }

    public void close() {
        try {
            if (inputStream != null)
                inputStream.close();
            if (socket != null)
                socket.close();
        } catch (IOException e) {
            LocalLogger.logSevere("Error closing read thread: " + e.getMessage());
            logger.severe("Error closing read thread: " + e.getMessage());
        }
    }
}
