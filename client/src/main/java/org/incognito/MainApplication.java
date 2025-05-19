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

                // Create and show the MenuPage
                // MenuPage will handle the key exchange process and proceed to the chat interface.
                MenuPage.MenuListener menuListener = new MenuPage.MenuListener() {
                    @Override
                    public void onKeysExchangedAndProceed(CryptoManager readyCryptoManager, MenuPage menuPageInstance) {
                        if (readyCryptoManager.getOtherUserPublicKeyBase64() == null) {
                            logger.severe("Key exchange incomplete: Other user's public key is missing in CryptoManager.");
                            JOptionPane.showMessageDialog(
                                    menuPageInstance, // Show dialog on the menu page
                                    "Incomplete key exchange: the contact's public key has not been loaded.\n" +
                                            "Make sure you have scanned the contact's QR code and/or imported the encrypted AES key.",
                                    "Key Exchange Error",
                                    JOptionPane.ERROR_MESSAGE);
                            return;
                        }

                        if (menuPageInstance != null) {
                            menuPageInstance.dispose(); // Close menu page
                        }

                        // Creates GUITest with the configured CryptoManager
                        chatClient = new GUITest(readyCryptoManager);
                        chatClient.setVisible(true);

                        // Initiialize the connection
                        Connection connection = new Connection();

                        boolean connected = false;
                        try {
                            connected = connection.connect();
                            if (!connected) {
                                logger.severe("Impossible connecting to server.");
                                JOptionPane.showMessageDialog(null, "Impossible connecting to server.", "Connection error", JOptionPane.ERROR_MESSAGE);
                                chatClient.dispose(); // Close the chat window
                                System.exit(1); // Exit the application
                                return; // Exit the listening thread if method fails
                            }

                        } catch (/*IOException*/ Exception e) { // TODO: Handle IOException
                            logger.severe("Failed to connect to server: " + e.getMessage());
                            JOptionPane.showMessageDialog(null, "Failed to connect to server: " + e.getMessage(), "Connection error", JOptionPane.ERROR_MESSAGE);
                            chatClient.dispose(); // Close the chat window
                            System.exit(1);
                            return;
                        }

                        try {
                            chatClient.initializeConnection(connection);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            logger.severe("Connection initialization interrupted: " + e.getMessage());
                            JOptionPane.showMessageDialog(null, "Connection initialization failed", "Error", JOptionPane.ERROR_MESSAGE);
                            System.exit(1);
                        } catch (Exception e) {
                            logger.severe("Error while initializing connection: " + e.getMessage());
                            JOptionPane.showMessageDialog(null, "Error while initializing connection: " + e.getMessage(), "Connection error", JOptionPane.ERROR_MESSAGE);
                            System.exit(1);
                        }
                    }

                    @Override
                    public void onCancel(MenuPage menuPageInstance) {
                        if (menuPageInstance != null) {
                            menuPageInstance.dispose();
                        }
                        System.exit(0); // Exit the application
                    }
                };

                MenuPage menuPage = new MenuPage(cryptoManager, menuListener);
                menuPage.setVisible(true);

            } catch (Exception e) {
                e.printStackTrace();
                logger.severe("Error during application startup: " + e.getMessage());
                JOptionPane.showMessageDialog(null, "Unable to start application: " + e.getMessage(), "Startup error", JOptionPane.ERROR_MESSAGE);
                System.exit(1);
            }
        });
    }
}