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
                        menuPageInstance.dispose(); // Close menu page

                        // Creates GUITest with the configured CryptoManager
                        chatClient = new GUITest(readyCryptoManager);
                        chatClient.setVisible(true);

                        // Initiialize the connection
                        Connection connection = new Connection();
                        // Importante da sistemare: !!!!!!!!!!!!!!!!!!
                        // Assumiamo che connection.connect() prepari la connessione.
                        // Se fallisce, initializeConnection dovrebbe gestirlo o lanciare un errore.
                        connection.connect(); // Questo metodo dovrebbe essere reso più robusto o restituire lo stato

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