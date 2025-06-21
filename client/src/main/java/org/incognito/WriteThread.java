package org.incognito;

import org.incognito.crypto.CryptoManager;
import java.util.Base64;
import org.incognito.ChatMessage;

import java.io.ObjectOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Logger;

public class WriteThread extends Thread {
    private static Logger logger = Logger.getLogger(WriteThread.class.getName());

    private CryptoManager cryptoManager;

    private BlockingQueue<String> messageQueue = new LinkedBlockingQueue<>();
    private ObjectOutputStream outputStream;
    private Socket socket;
    private GUITest client;

    public WriteThread(Socket socket, GUITest client, CryptoManager cryptoManager) {
        this.socket = socket;
        this.client = client;
        this.cryptoManager = cryptoManager;

        try {
            outputStream = new ObjectOutputStream(socket.getOutputStream());
        } catch (IOException e) {
            ErrorHandler.handleConnectionError(
                client,
                "Failed to initialize message sending",
                true,
                () -> {
                    try {
                        outputStream = new ObjectOutputStream(socket.getOutputStream());
                    } catch (IOException retryEx) {
                        ErrorHandler.handleFatalError(
                            client,
                            "Failed to initialize message sending after retry",
                            retryEx
                        );
                    }
                }
            );
        }
    }

    @Override
    public void run() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                String message = messageQueue.take();

                if (outputStream == null) {
                    ErrorHandler.handleConnectionError(
                        client,
                        "Cannot send message - connection lost",
                        true,
                        () -> {
                            try {
                                client.initializeConnection(new Connection());
                                messageQueue.put(message); // Retry sending the message
                            } catch (Exception ex) {
                                ErrorHandler.handleFatalError(
                                    client,
                                    "Failed to reconnect",
                                    ex
                                );
                            }
                        }
                    );
                    continue;
                }

                if (message.startsWith("USERLIST:") ||
                        message.startsWith("CONNECT:") ||
                        message.startsWith("DISCONNECT:")) {
                    outputStream.writeObject(message);
                } else {
                    if (cryptoManager.getAesSessionKey() == null) {
                        ErrorHandler.handleCryptoError(
                            client,
                            "Cannot send encrypted message - no session key available",
                            new Exception("Missing AES session key"),
                            () -> AutoKeyExchange.performKeyExchange(
                                client.getUserName(),
                                message,
                                cryptoManager,
                                this
                            )
                        );
                        continue;
                    }

                    try {
                        byte[] encrypted = cryptoManager.encryptAES(message);
                        String encoded = Base64.getEncoder().encodeToString(encrypted);
                        ChatMessage chatMsg = new ChatMessage(client.getUserName(), encoded);
                        outputStream.writeObject(chatMsg);
                    } catch (Exception e) {
                        ErrorHandler.handleCryptoError(
                            client,
                            "Failed to encrypt message",
                            e,
                            () -> messageQueue.put(message) // Retry sending the message
                        );
                        continue;
                    }
                }

                outputStream.flush();

            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception ex) {
                ErrorHandler.handleSessionError(
                    client,
                    "Error sending message: " + ex.getMessage(),
                    reconnect -> {
                        if (reconnect) {
                            try {
                                client.initializeConnection(new Connection());
                            } catch (Exception e) {
                                ErrorHandler.handleFatalError(
                                    client,
                                    "Failed to reconnect",
                                    e
                                );
                            }
                        }
                    }
                );
            }
        }
        close();
    }

    public void close() {
        try {
            if (outputStream != null) {
                outputStream.close();
            }
            if (socket != null) {
                socket.close();
            }
        } catch (IOException e) {
            LocalLogger.logSevere("Error closing write thread: " + e.getMessage());
            logger.severe("Error closing write thread: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void sendMessage(String message) {
        messageQueue.offer(message);
    }

    public void sendKeyExchangeMessage(KeyExchangeMessage keyExchangeMessage) {
        try {
            if (outputStream == null) {
                ErrorHandler.handleConnectionError(
                    client,
                    "Cannot send key exchange - connection lost",
                    true,
                    () -> {
                        try {
                            client.initializeConnection(new Connection());
                            sendKeyExchangeMessage(keyExchangeMessage); // Retry
                        } catch (Exception ex) {
                            ErrorHandler.handleFatalError(
                                client,
                                "Failed to reconnect for key exchange",
                                ex
                            );
                        }
                    }
                );
                return;
            }

            logger.info("Sending key exchange message: " + keyExchangeMessage.getType());
            outputStream.writeObject(keyExchangeMessage);
            outputStream.flush();
        } catch (Exception e) {
            ErrorHandler.handleCryptoError(
                client,
                "Failed to send key exchange message",
                e,
                () -> sendKeyExchangeMessage(keyExchangeMessage) // Retry
            );
        }
    }
}