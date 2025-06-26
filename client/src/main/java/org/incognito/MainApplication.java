package org.incognito;

import org.incognito.crypto.CryptoManager;
import org.incognito.GUI.*;
import org.incognito.GUI.theme.ModernTheme;

import javax.swing.*;
import java.util.logging.Logger;

public class MainApplication {
    private static GUITest chatClient;
    private static Logger logger = Logger.getLogger(MainApplication.class.getName());
    private static UserSelectionPage.UserSelectionListener userSelectionListener;
    private static CryptoManager cryptoManager;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            // Initialize modern theme first
            ModernTheme.initialize();
            initializeApplication();
        });
    }

    private static void initializeApplication() {
        try {
            // Initialize CryptoManager (generate both user's RSA keys)
            cryptoManager = new CryptoManager();

            // Prompt for username
            String username = promptForUsername();
            if (username == null || username.trim().isEmpty()) {
                LocalLogger.logInfo("User cancelled the application startup.");
                logger.info("Application startup cancelled by user.");
                System.exit(0);
                return;
            }

            // Create UserSelectionListener instance
            userSelectionListener = new UserSelectionPage.UserSelectionListener() {
                @Override
                public void onAutomaticChatRequested(Connection connection, String targetUser,
                                                     UserSelectionPage userSelectionPage) {
                    try {
                        // Show a waiting message
                        userSelectionPage.setStatus("Starting chat with " + targetUser + "...");

                        // Using correct username from the user selection page
                        String currentUsername = userSelectionPage.getCurrentUsername();

                        // Disconnect the user selection page cleanly to avoid stream conflicts
                        userSelectionPage.disconnect();

                        // Create a fresh connection for the chat
                        Connection chatConnection = new Connection();
                        boolean connected = chatConnection.connect();
                        if (!connected) {
                            handleConnectionError(userSelectionPage, "Failed to connect to server for chat");
                            return;
                        }
                        // Create GUITest with the configured CryptoManager and user info
                        chatClient = new GUITest(cryptoManager, currentUsername, userSelectionListener);

                        // Initialize connection with the provided username and target
                        chatClient.initializeConnectionWithUsername(chatConnection, currentUsername, targetUser);

                        // Close the user selection page and show chat
                        userSelectionPage.dispose();
                        chatClient.setVisible(true);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        if (chatClient != null) {
                            chatClient.dispose();
                        }
                        ErrorHandler.handleConnectionError(userSelectionPage,
                                "Connection initialization interrupted",
                                false,
                                null);
                    } catch (Exception e) {
                        if (chatClient != null) {
                            chatClient.dispose();
                        }
                        ErrorHandler.handleConnectionError(userSelectionPage,
                                "Error while initializing connection: " + e.getMessage(),
                                false,
                                null);
                    }
                }

                @Override
                public void onManualKeyExchange(UserSelectionPage userSelectionPage) {
                    // Open the original MenuPage for manual key exchange
                    MenuPage.MenuListener menuListener = new MenuPage.MenuListener() {
                        @Override
                        public void onKeysExchangedAndProceed(CryptoManager readyCryptoManager,
                                                              MenuPage menuPageInstance) {
                            if (readyCryptoManager.getOtherUserPublicKeyBase64() == null) {
                                LocalLogger.logSevere("Key exchange incomplete: Other user's public key is missing in CryptoManager.");
                                logger.severe("Key exchange incomplete: Other user's public key is missing in CryptoManager.");
                                JOptionPane.showMessageDialog(
                                        menuPageInstance,
                                        "Incomplete key exchange: the contact's public key has not been loaded.\n" +
                                                "Make sure you have scanned the contact's QR code and/or imported the encrypted AES key.",
                                        "Key Exchange Error",
                                        JOptionPane.ERROR_MESSAGE);
                                return;
                            }                            // Store the current username before disposing menu page
                            String currentUsername = userSelectionPage.getCurrentUsername();

                            if (menuPageInstance != null) {
                                menuPageInstance.dispose();
                            }

                            // For manual key exchange, dispose the user selection page normally
                            // and create a fresh connection to avoid connection reuse complications
                            userSelectionPage.disconnect();
                            userSelectionPage.dispose();

                            // Create GUITest with the configured CryptoManager and user info
                            chatClient = new GUITest(readyCryptoManager, currentUsername, userSelectionListener);
                            chatClient.setVisible(true);

                            // Create a fresh connection for manual key exchange
                            Connection connection = new Connection();
                            boolean connected = false;
                            try {
                                connected = connection.connect();
                                if (!connected) {
                                    LocalLogger.logSevere("Impossible connecting to server.");
                                    logger.severe("Impossible connecting to server.");
                                    chatClient.dispose();
                                    ErrorHandler.handleConnectionError(userSelectionPage,
                                            "Could not connect to server",
                                            true,
                                            () -> initializeApplication());
                                    return;
                                }
                            } catch (Exception e) {
                                chatClient.dispose();
                                ErrorHandler.handleConnectionError(userSelectionPage,
                                        "Failed to connect to server: " + e.getMessage(),
                                        true,
                                        () -> initializeApplication());
                                return;
                            }
                            try {
                                // Use the standard method for manual key exchange with fresh connection
                                chatClient.initializeConnectionWithUsername(connection, currentUsername);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                LocalLogger.logSevere("Connection initialization interrupted: " + e.getMessage());
                                logger.severe("Connection initialization interrupted: " + e.getMessage());
                                chatClient.dispose();
                                handleConnectionError(null, "Connection initialization failed");
                            } catch (Exception e) {
                                LocalLogger.logSevere("Error while initializing connection: " + e.getMessage());
                                logger.severe("Error while initializing connection: " + e.getMessage());
                                chatClient.dispose();
                                handleConnectionError(null, "Error while initializing connection: " + e.getMessage());
                            }
                        }

                        @Override
                        public void onCancel(MenuPage menuPageInstance) {
                            if (menuPageInstance != null) {
                                menuPageInstance.dispose();
                            }
                            // Return to user selection page only if it's still valid
                            if (userSelectionPage != null && userSelectionPage.isDisplayable()) {
                                userSelectionPage.setVisible(true);
                            } else {
                                // Create a new user selection page if the old one was disposed
                                String currentUsername = userSelectionPage != null ?
                                        userSelectionPage.getCurrentUsername() :
                                        promptForUsername();
                                if (currentUsername != null && !currentUsername.trim().isEmpty()) {
                                    UserSelectionPage newPage = new UserSelectionPage(currentUsername, userSelectionListener);
                                    newPage.setVisible(true);
                                } else {
                                    System.exit(0);
                                }
                            }
                        }
                    };

                    userSelectionPage.setVisible(false);
                    MenuPage menuPage = new MenuPage(cryptoManager, menuListener);
                    menuPage.setVisible(true);
                }

                @Override
                public void onCancel(UserSelectionPage userSelectionPage) {
                    if (userSelectionPage != null) {
                        userSelectionPage.disconnect(); // Cleanup connection
                        userSelectionPage.dispose();
                    }

                    System.exit(0); // Exit the application
                }
            };

            UserSelectionPage userSelectionPage = new UserSelectionPage(username, userSelectionListener);
            userSelectionPage.setVisible(true);
        } catch (Exception e) {
            ErrorHandler.handleInitializationError(
                    null,
                    "Error during application startup",
                    e,
                    () -> {
                        String newUsername = promptForUsername();
                        if (newUsername != null && !newUsername.trim().isEmpty()) {
                            try {
                                cryptoManager = new CryptoManager();
                                UserSelectionPage newPage = new UserSelectionPage(newUsername, userSelectionListener);
                                newPage.setVisible(true);
                            } catch (Exception ex) {
                                ErrorHandler.handleFatalError(
                                        null,
                                        "Failed to initialize encryption",
                                        ex
                                );
                            }
                        } else {
                            System.exit(0);
                        }
                    }
            );
        }
    }

    private static String promptForUsername() {
        String username = null;
        while (username == null || username.trim().isEmpty()) {
            username = JOptionPane.showInputDialog(
                    null,
                    "Enter your username:",
                    "Username Required",
                    JOptionPane.QUESTION_MESSAGE);

            if (username == null) {
                // User cancelled
                return null;
            }

            username = username.trim();
            if (username.isEmpty()) {
                JOptionPane.showMessageDialog(
                        null,
                        "Username cannot be empty. Please enter a valid username.",
                        "Invalid Username",
                        JOptionPane.WARNING_MESSAGE);
            }
        }
        return username;
    }

    private static void handleConnectionError(UserSelectionPage currentPage, String errorMessage) {
        if (currentPage != null) {
            currentPage.disconnect();
            currentPage.dispose();
        }

        ErrorHandler.handleConnectionError(
                null,
                errorMessage,
                true,
                () -> {
                    String newUsername = promptForUsername();
                    if (newUsername != null && !newUsername.trim().isEmpty()) {
                        UserSelectionPage newPage = new UserSelectionPage(newUsername, userSelectionListener);
                        newPage.setVisible(true);
                    } else {
                        System.exit(0);
                    }
                }
        );
    }
}