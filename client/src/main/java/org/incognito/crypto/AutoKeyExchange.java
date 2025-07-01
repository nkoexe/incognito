package org.incognito.crypto;

import org.incognito.*;
import org.incognito.GUI.UI;

import javax.crypto.SecretKey;
import javax.swing.SwingUtilities;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

public class AutoKeyExchange {
    private static final Logger logger = Logger.getLogger(AutoKeyExchange.class.getName());

    // Track active key exchanges to prevent duplicates
    private static final java.util.Set<String> activeExchanges = java.util.concurrent.ConcurrentHashMap.newKeySet();

    public static CompletableFuture<Boolean> performKeyExchange(
            String targetUsername,
            String currentUsername,
            CryptoManager cryptoManager,
            WriteThread writeThread) {

        return CompletableFuture.supplyAsync(() -> {
            try {
                // Create a unique exchange identifier
                String exchangeKey = currentUsername.compareTo(targetUsername) < 0
                        ? currentUsername + "-" + targetUsername
                        : targetUsername + "-" + currentUsername; // Check if exchange is already in progress
                if (!activeExchanges.add(exchangeKey)) {
                    LocalLogger.logInfo("Key exchange already in progress with " + targetUsername
                            + ". Clearing old exchange and starting new one.");
                    logger.info("Key exchange already in progress with " + targetUsername
                            + ". Clearing old exchange and starting new one.");
                    // Remove the old exchange and proceed with new one (this handles rejoining
                    // scenarios)
                    activeExchanges.remove(exchangeKey);
                    activeExchanges.add(exchangeKey);
                }

                LocalLogger.logInfo("Starting automatic key exchange with " + targetUsername);
                logger.info("Starting automatic key exchange with " + targetUsername);

                // Send initiation request
                KeyExchangeMessage initMsg = new KeyExchangeMessage(
                        KeyExchangeMessage.Type.INITIATE_EXCHANGE,
                        currentUsername,
                        targetUsername);
                writeThread.sendKeyExchangeMessage(initMsg); // The key exchange will be handled by the ReadThread
                // and will complete automatically
                return true;

            } catch (Exception e) {
                ErrorHandler.handleCryptoError(
                        writeThread.getClient(),
                        "Failed to initiate key exchange with " + targetUsername,
                        e,
                        () -> performKeyExchange(targetUsername, currentUsername, cryptoManager, writeThread));
                // Clean up on failure
                String exchangeKey = currentUsername.compareTo(targetUsername) < 0
                        ? currentUsername + "-" + targetUsername
                        : targetUsername + "-" + currentUsername;
                activeExchanges.remove(exchangeKey);
                return false;
            }
        });
    }

    public static void handleIncomingKeyExchange(KeyExchangeMessage message,
            CryptoManager cryptoManager,
            WriteThread writeThread,
            UI chatClient) {
        try {
            LocalLogger.logInfo("Handling key exchange message: " + message);
            logger.info("Handling key exchange message: " + message);

            switch (message.getType()) {
                case INITIATE_EXCHANGE:
                    try {
                        // Another user wants to chat - auto-accept and send public key
                        // Don't generate AES key here - wait for the initiator to send it

                        KeyExchangeMessage response = new KeyExchangeMessage(
                                KeyExchangeMessage.Type.PUBLIC_KEY_OFFER,
                                chatClient.getUserName(),
                                message.getSenderUsername());
                        response.setPayload(cryptoManager.getPublicKeyBase64());
                        writeThread.sendKeyExchangeMessage(response);

                        // Nascondiamo i messaggi tecnici di scambio chiavi
                        // chatClient.appendMessage("[System] Key exchange initiated with " +
                        // message.getSenderUsername());
                    } catch (Exception e) {
                        ErrorHandler.handleCryptoError(
                                chatClient,
                                "Failed to process key exchange initiation",
                                e,
                                () -> handleIncomingKeyExchange(message, cryptoManager, writeThread, chatClient));
                    }
                    break;

                case PUBLIC_KEY_OFFER:
                    try {
                        // Received public key - store it and send session key
                        cryptoManager.setOtherUserPublicKey(message.getPayload());

                        // Generate and send encrypted session key
                        SecretKey sessionKey = cryptoManager.generateAESKey();
                        cryptoManager.setAesSessionKey(sessionKey);

                        String encryptedKey = cryptoManager.encryptSessionKeyForPeer();
                        KeyExchangeMessage sessionKeyMsg = new KeyExchangeMessage(
                                KeyExchangeMessage.Type.SESSION_KEY_OFFER,
                                chatClient.getUserName(),
                                message.getSenderUsername());
                        sessionKeyMsg.setPayload(encryptedKey);
                        writeThread.sendKeyExchangeMessage(sessionKeyMsg);

                        // Hidden: indicate successful public key exchange
                        // chatClient.appendMessage("[System] Public key received, sending session
                        // key...");
                    } catch (Exception e) {
                        ErrorHandler.handleCryptoError(
                                chatClient,
                                "Failed to process public key and generate session key",
                                e,
                                () -> handleIncomingKeyExchange(message, cryptoManager, writeThread, chatClient));
                    }
                    break;
                case SESSION_KEY_OFFER:
                    try {
                        // Received encrypted session key - decrypt and confirm
                        boolean success = cryptoManager.setSessionKeyFromEncrypted(message.getPayload());

                        KeyExchangeMessage confirmMsg = new KeyExchangeMessage(
                                success ? KeyExchangeMessage.Type.EXCHANGE_COMPLETE
                                        : KeyExchangeMessage.Type.EXCHANGE_ERROR,
                                chatClient.getUserName(),
                                message.getSenderUsername());
                        if (success) {
                            confirmMsg.setPayload("Key exchange completed successfully");

                            // Clean up the active exchange tracking for this client
                            cleanupExchange(chatClient.getUserName(), message.getSenderUsername());

                            // Enable chat interface immediately for the receiver
                            SwingUtilities.invokeLater(() -> {
                                chatClient.enableChatInterface();
                            });
                        } else {
                            confirmMsg.setPayload("Failed to decrypt session key");
                            chatClient.appendMessage("[System] ERROR: Failed to decrypt session key");
                        }

                        writeThread.sendKeyExchangeMessage(confirmMsg);
                    } catch (Exception e) {
                        ErrorHandler.handleCryptoError(
                                chatClient,
                                "Error processing session key",
                                e,
                                () -> handleIncomingKeyExchange(message, cryptoManager, writeThread, chatClient));
                    }
                    break;
                case EXCHANGE_COMPLETE:
                    LocalLogger.logInfo("Key exchange completed with " + message.getSenderUsername());
                    logger.info("Key exchange completed with " + message.getSenderUsername());

                    // Clean up the active exchange tracking
                    cleanupExchange(chatClient.getUserName(), message.getSenderUsername());

                    // Enable the chat interface now that key exchange is complete
                    SwingUtilities.invokeLater(() -> {
                        chatClient.enableChatInterface();
                    });
                    break;
                case EXCHANGE_ERROR:
                    ErrorHandler.handleCryptoError(
                            chatClient,
                            "Key exchange failed: " + message.getPayload(),
                            new Exception(message.getPayload()),
                            () -> performKeyExchange(message.getSenderUsername(), chatClient.getUserName(),
                                    cryptoManager, writeThread));
                    cleanupExchange(chatClient.getUserName(), message.getSenderUsername());
                    break;
            }
        } catch (Exception e) {
            ErrorHandler.handleFatalError(
                    chatClient,
                    "Critical error during key exchange",
                    e);
        }
    }

    public static void cleanupExchange(String user1, String user2) {
        String exchangeKey = user1.compareTo(user2) < 0
                ? user1 + "-" + user2
                : user2 + "-" + user1;
        activeExchanges.remove(exchangeKey);
    }
}
