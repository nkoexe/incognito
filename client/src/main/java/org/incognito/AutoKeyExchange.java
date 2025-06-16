package org.incognito;

import org.incognito.crypto.CryptoManager;
import javax.crypto.SecretKey;
import javax.swing.SwingUtilities;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
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
                        : targetUsername + "-" + currentUsername;

                // Check if exchange is already in progress
                if (!activeExchanges.add(exchangeKey)) {
                    LocalLogger.logInfo("Key exchange already in progress with " + targetUsername);
                    logger.info("Key exchange already in progress with " + targetUsername);
                    return true; // Already in progress, consider it successful
                }

                LocalLogger.logInfo("Starting automatic key exchange with " + targetUsername);
                logger.info("Starting automatic key exchange with " + targetUsername);

                // Step 1: Send initiation request
                KeyExchangeMessage initMsg = new KeyExchangeMessage(
                        KeyExchangeMessage.Type.INITIATE_EXCHANGE,
                        currentUsername,
                        targetUsername);
                writeThread.sendKeyExchangeMessage(initMsg); // The key exchange will be handled by the ReadThread
                // and will complete automatically
                return true;

            } catch (Exception e) {
                // Log the error and clean up
                LocalLogger.logSevere("Key exchange failed: " + e.getMessage());
                logger.severe("Key exchange failed: " + e.getMessage());
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
            GUITest chatClient) {
        try {
            LocalLogger.logInfo("Handling key exchange message: " + message);
            logger.info("Handling key exchange message: " + message);

            switch (message.getType()) {
                case INITIATE_EXCHANGE:
                    // Another user wants to chat - auto-accept and send public key
                    // Don't generate AES key here - wait for the initiator to send it

                    KeyExchangeMessage response = new KeyExchangeMessage(
                            KeyExchangeMessage.Type.PUBLIC_KEY_OFFER,
                            chatClient.getUserName(),
                            message.getSenderUsername());                    response.setPayload(cryptoManager.getPublicKeyBase64());
                    writeThread.sendKeyExchangeMessage(response);

                    // Hidden: Technical key exchange message - not needed for user
                    // chatClient.appendMessage("[System] Key exchange initiated with " + message.getSenderUsername());
                    break;

                case PUBLIC_KEY_OFFER:
                    // Received public key - store it and send session key
                    cryptoManager.setOtherUserPublicKey(message.getPayload());

                    // Generate and send encrypted session key
                    SecretKey sessionKey = cryptoManager.generateAESKey();
                    cryptoManager.setAesSessionKey(sessionKey);

                    String encryptedKey = cryptoManager.encryptSessionKeyForPeer();
                    KeyExchangeMessage sessionKeyMsg = new KeyExchangeMessage(
                            KeyExchangeMessage.Type.SESSION_KEY_OFFER,
                            chatClient.getUserName(),
                            message.getSenderUsername());                    sessionKeyMsg.setPayload(encryptedKey);
                    writeThread.sendKeyExchangeMessage(sessionKeyMsg);

                    // Hidden: Technical key exchange progress message - not needed for user
                    // chatClient.appendMessage("[System] Public key received, sending session key...");
                    break;
                case SESSION_KEY_OFFER:
                    // Received encrypted session key - decrypt and confirm
                    boolean success = cryptoManager.setSessionKeyFromEncrypted(message.getPayload());

                    KeyExchangeMessage confirmMsg = new KeyExchangeMessage(
                            success ? KeyExchangeMessage.Type.EXCHANGE_COMPLETE
                                    : KeyExchangeMessage.Type.EXCHANGE_ERROR,
                            chatClient.getUserName(),
                            message.getSenderUsername());                    if (success) {
                        confirmMsg.setPayload("Key exchange completed successfully");
                        // Hidden: Technical success message - replaced with clearer message below
                        // chatClient.appendMessage("[System] Session key received and decrypted successfully");

                        // Clean up the active exchange tracking for this client
                        String exchangeKey = chatClient.getUserName().compareTo(message.getSenderUsername()) < 0
                                ? chatClient.getUserName() + "-" + message.getSenderUsername()
                                : message.getSenderUsername() + "-" + chatClient.getUserName();
                        activeExchanges.remove(exchangeKey);

                        // Enable chat interface immediately for the receiver
                        SwingUtilities.invokeLater(() -> {
                            chatClient.enableChatInterface();
                        });
                        // Hidden: Technical completion message - replaced with clearer message in enableChatInterface
                        // chatClient.appendMessage("[System] Key exchange completed! Chat is now secure.");
                    } else {
                        confirmMsg.setPayload("Failed to decrypt session key");
                        chatClient.appendMessage("[System] ERROR: Failed to decrypt session key");
                    }

                    writeThread.sendKeyExchangeMessage(confirmMsg);
                    break;                case EXCHANGE_COMPLETE:
                    LocalLogger.logInfo("Key exchange completed with " + message.getSenderUsername());
                    logger.info("Key exchange completed with " + message.getSenderUsername());
                    // Hidden: Technical completion message - replaced with clearer message in enableChatInterface
                    // chatClient.appendMessage("[System] Key exchange completed! Chat is now secure.");

                    // Clean up the active exchange tracking
                    String completedExchangeKey = chatClient.getUserName().compareTo(message.getSenderUsername()) < 0
                            ? chatClient.getUserName() + "-" + message.getSenderUsername()
                            : message.getSenderUsername() + "-" + chatClient.getUserName();
                    activeExchanges.remove(completedExchangeKey);

                    // Enable the chat interface now that key exchange is complete
                    SwingUtilities.invokeLater(() -> {
                        chatClient.enableChatInterface();
                    });
                    break;
                case EXCHANGE_ERROR:
                    LocalLogger.logSevere("Key exchange error: " + message.getPayload());
                    logger.severe("Key exchange error: " + message.getPayload());
                    chatClient.appendMessage("[System] Key exchange error: " + message.getPayload());

                    // Clean up the active exchange tracking on error
                    String errorExchangeKey = chatClient.getUserName().compareTo(message.getSenderUsername()) < 0
                            ? chatClient.getUserName() + "-" + message.getSenderUsername()
                            : message.getSenderUsername() + "-" + chatClient.getUserName();
                    activeExchanges.remove(errorExchangeKey);
                    break;
            }
        } catch (Exception e) {
            LocalLogger.logSevere("Error handling key exchange message: " + e.getMessage());
            logger.severe("Error handling key exchange message: " + e.getMessage());
            e.printStackTrace();
            chatClient.appendMessage("[System] Error during key exchange: " + e.getMessage());
        }
    }
}
