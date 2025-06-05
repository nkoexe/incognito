package org.incognito;

import org.incognito.crypto.CryptoManager;
import org.incognito.GUI.*;

import javax.swing.*;
import java.util.logging.Logger;

public class MainApplication {
    private static GUITest chatClient;
    private static Logger logger = Logger.getLogger(MainApplication.class.getName());

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                // Initialize CryptoManager (generate both user's RSA keys)
                CryptoManager cryptoManager = new CryptoManager();

                // Prompt for username
                String username = promptForUsername();
                if (username == null || username.trim().isEmpty()) {
                    logger.info("Application startup cancelled by user.");
                    System.exit(0);
                    return;
                } // Create and show the UserSelectionPage for automated key exchange
                UserSelectionPage.UserSelectionListener userSelectionListener = new UserSelectionPage.UserSelectionListener() {
                    @Override
                    public void onAutomaticChatRequested(Connection connection, String targetUser,
                            UserSelectionPage userSelectionPage) {
                        try {
                            // Show a waiting message
                            userSelectionPage.setStatus("Starting chat with " + targetUser + "...");

                            // Disconnect the user selection page cleanly to avoid stream conflicts
                            userSelectionPage.disconnect();

                            // Create a fresh connection for the chat
                            Connection chatConnection = new Connection();
                            boolean connected = chatConnection.connect();
                            if (!connected) {
                                JOptionPane.showMessageDialog(userSelectionPage, "Failed to connect to server for chat",
                                        "Connection error",
                                        JOptionPane.ERROR_MESSAGE);
                                return;
                            }

                            // Create GUITest with the configured CryptoManager
                            chatClient = new GUITest(cryptoManager);

                            // Initialize connection with the provided username and target
                            chatClient.initializeConnectionWithUsername(chatConnection, username, targetUser);

                            // Close the user selection page and show chat
                            userSelectionPage.dispose();
                            chatClient.setVisible(true);

                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            logger.severe("Connection initialization interrupted: " + e.getMessage());
                            JOptionPane.showMessageDialog(userSelectionPage, "Connection initialization failed",
                                    "Error",
                                    JOptionPane.ERROR_MESSAGE);
                            if (chatClient != null)
                                chatClient.dispose();
                        } catch (Exception e) {
                            logger.severe("Error while initializing connection: " + e.getMessage());
                            JOptionPane.showMessageDialog(userSelectionPage,
                                    "Error while initializing connection: " + e.getMessage(),
                                    "Connection error", JOptionPane.ERROR_MESSAGE);
                            if (chatClient != null)
                                chatClient.dispose();
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
                                    logger.severe(
                                            "Key exchange incomplete: Other user's public key is missing in CryptoManager.");
                                    JOptionPane.showMessageDialog(
                                            menuPageInstance,
                                            "Incomplete key exchange: the contact's public key has not been loaded.\n" +
                                                    "Make sure you have scanned the contact's QR code and/or imported the encrypted AES key.",
                                            "Key Exchange Error",
                                            JOptionPane.ERROR_MESSAGE);
                                    return;
                                }

                                if (menuPageInstance != null) {
                                    menuPageInstance.dispose();
                                }

                                // Create GUITest with the configured CryptoManager
                                chatClient = new GUITest(readyCryptoManager);
                                chatClient.setVisible(true);

                                // Initialize the connection
                                Connection connection = new Connection();
                                boolean connected = false;
                                try {
                                    connected = connection.connect();
                                    if (!connected) {
                                        logger.severe("Impossible connecting to server.");
                                        JOptionPane.showMessageDialog(null, "Impossible connecting to server.",
                                                "Connection error",
                                                JOptionPane.ERROR_MESSAGE);
                                        chatClient.dispose();
                                        System.exit(1);
                                        return;
                                    }
                                } catch (Exception e) {
                                    logger.severe("Failed to connect to server: " + e.getMessage());
                                    JOptionPane.showMessageDialog(null,
                                            "Failed to connect to server: " + e.getMessage(),
                                            "Connection error", JOptionPane.ERROR_MESSAGE);
                                    chatClient.dispose();
                                    System.exit(1);
                                    return;
                                }

                                try {
                                    chatClient.initializeConnection(connection);
                                } catch (InterruptedException e) {
                                    Thread.currentThread().interrupt();
                                    logger.severe("Connection initialization interrupted: " + e.getMessage());
                                    JOptionPane.showMessageDialog(null, "Connection initialization failed", "Error",
                                            JOptionPane.ERROR_MESSAGE);
                                    System.exit(1);
                                } catch (Exception e) {
                                    logger.severe("Error while initializing connection: " + e.getMessage());
                                    JOptionPane.showMessageDialog(null,
                                            "Error while initializing connection: " + e.getMessage(),
                                            "Connection error", JOptionPane.ERROR_MESSAGE);
                                    System.exit(1);
                                }
                            }

                            @Override
                            public void onCancel(MenuPage menuPageInstance) {
                                if (menuPageInstance != null) {
                                    menuPageInstance.dispose();
                                }
                                // Return to user selection page
                                userSelectionPage.setVisible(true);
                            }
                        };

                        userSelectionPage.setVisible(false);
                        MenuPage menuPage = new MenuPage(cryptoManager, menuListener);
                        menuPage.setVisible(true);
                    }

                    @Override
                    public void onCancel(UserSelectionPage userSelectionPage) {
                        if (userSelectionPage != null) {
                            userSelectionPage.dispose();
                        }
                        System.exit(0);
                    }
                };

                UserSelectionPage userSelectionPage = new UserSelectionPage(username, userSelectionListener);
                userSelectionPage.setVisible(true);

            } catch (Exception e) {
                e.printStackTrace();
                logger.severe("Error during application startup: " + e.getMessage());
                JOptionPane.showMessageDialog(null, "Unable to start application: " + e.getMessage(), "Startup error",
                        JOptionPane.ERROR_MESSAGE);
                System.exit(1);
            }
        });
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
}