package org.incognito;

import org.incognito.crypto.CryptoManager;
import org.incognito.crypto.QRUtil;

import javax.crypto.SecretKey;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.logging.Logger;

public class GUITest extends JFrame {

    // Set up logging
    private Logger logger = Logger.getLogger(GUITest.class.getName());
    private Connection connection;

    // UI components
    private JTextArea chatArea;
    private JTextField messageField;
    private JButton sendButton;
    private JList<String> usersList;
    private DefaultListModel<String> usersModel;
    private ReadThread readThread;
    private WriteThread writeThread;
    private String userName;

    private CryptoManager cryptoManager;

    public GUITest(CryptoManager cryptoManager) {
        this.cryptoManager = cryptoManager;
        // Set up the UI components

        try {
            SecretKey sessionKey = this.cryptoManager.generateAESKey();
            this.cryptoManager.setAesSessionKey(sessionKey);
            logger.info("AES session key generated and set.");
        } catch (Exception e) {
            logger.severe("Error while generating AES session key: " + e.getMessage());
            JOptionPane.showMessageDialog(this, "Fatal error: unable to generate session key.", "Cryptography error", JOptionPane.ERROR_MESSAGE);
            System.exit(1); // O gestisci diversamente
        }

        setTitle("Incognito Chat");
        setSize(720, 480);
        setLocationRelativeTo(null); // Center the window
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        // Chat display area
        chatArea = new JTextArea();
        chatArea.setEditable(false);
        chatArea.setLineWrap(true);
        JScrollPane chatScrollPane = new JScrollPane(chatArea);

        // Users list on the right
        usersModel = new DefaultListModel<>();
        usersModel.addElement("You");
        usersList = new JList<>(usersModel);
        usersList.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                          boolean isSelected, boolean cellHasFocus) {
                Component renderer = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value.toString().contains(" (tu)")) {
                    renderer.setFont(renderer.getFont().deriveFont(Font.BOLD));
                } else if (value.toString().contains(" (contatto)")) {
                    renderer.setForeground(new Color(0, 102, 204)); // Blu per il contatto
                }
                return renderer;
            }
        });
        JScrollPane usersScrollPane = new JScrollPane(usersList);
        usersScrollPane.setPreferredSize(new Dimension(100, 0));

        // Message input area at bottom
        JPanel inputPanel = new JPanel(new BorderLayout());
        messageField = new JTextField();
        sendButton = new JButton("Send");
        inputPanel.add(messageField, BorderLayout.CENTER);
        inputPanel.add(sendButton, BorderLayout.EAST);

        // Add components to frame
        add(chatScrollPane, BorderLayout.CENTER);
        add(usersScrollPane, BorderLayout.EAST);
        add(inputPanel, BorderLayout.SOUTH);

        // Event listeners
        sendButton.addActionListener(this::sendMessage);
        messageField.addActionListener(this::sendMessage);


        // Handle window closing to disconnect properly
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                disconnect();
            }
        });

        setVisible(true);
    }

    public void initializeConnection(Connection connection) throws InterruptedException {

        logger.info("Initializing connection...");
        this.connection = connection;

        if (connection.getSocket() != null) {
            chatArea.append("Connected to server.\n");

            BlockingQueue<String> loginQueue = new ArrayBlockingQueue<>(1);

            // Start read and write threads
            writeThread = new WriteThread(connection.getSocket(), this, this.cryptoManager);
            readThread = new ReadThread(connection.getSocket(), this, this.cryptoManager, loginQueue);

            writeThread.start();
            readThread.start();

            String inputName = JOptionPane.showInputDialog(
                    this,
                    "Enter your username:",
                    "Username",
                    JOptionPane.QUESTION_MESSAGE);

            if (inputName == null || inputName.trim().isEmpty()) {
                inputName = "Guest" + (int) (Math.random() * 1000);
            }

            // New comand for 1-to-1 chat
            String sessionId = generateSessionId();
            logger.info("Sending message PRIVATE_CHAT with sessionId: " + sessionId);
            writeThread.sendMessage("PRIVATE_CHAT:" + inputName + ":" + sessionId);

            try {
                Object response = loginQueue.poll(5, java.util.concurrent.TimeUnit.SECONDS);

                if (response instanceof String str) {
                    switch (str) {
                        case "USERNAME_ACCEPTED":
                            this.userName = inputName;
                            break;
                        case "USERNAME_TAKEN":
                            JOptionPane.showMessageDialog(this, "Nome utente già in uso. Riprova.");
                            // Chiamata ricorsiva per richiedere un nuovo nome utente
                            initializeConnection(connection);
                            return;
                        case "WAITING_FOR_PEER":
                            chatArea.append("In attesa che il tuo contatto si connetta...\n");
                            break;
                        case "PEER_CONNECTED":
                            chatArea.append("Il tuo contatto si è connesso! Puoi iniziare a chattare.\n");
                            break;
                        default:
                            logger.warning("Risposta del server non riconosciuta: " + str);
                            JOptionPane.showMessageDialog(this, "Risposta del server inattesa. Riprova.");
                            break;
                    }
                } else if (response == null) {
                    chatArea.append("Server response is null.\n");
                    logger.warning("Timout waiting for server response.");
                }
            } catch (InterruptedException e) {
                logger.severe("Error while waiting for server response: " + e.getMessage());
                chatArea.append("Error while waiting for server response: " + e.getMessage() + "\n");
            }


            // Update UI
            setTitle("Incognito Chat - " + userName);
            updateUsersList(userName);
        } else {
            chatArea.append("Failed to connect to server.\n");
            return;
        }
    }

    // Method to generate a unique session ID
    private String generateSessionId() {
        try {
            String combinedKeys = cryptoManager.getPublicKeyBase64() + cryptoManager.getOtherUserPublicKeyBase64();
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(combinedKeys.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            logger.severe("Error generating session ID: " + e.getMessage());
            return "session-" + System.currentTimeMillis(); // Fallback
        }

    }

    void updateUsersList(String userListStr) {
        try {
            SwingUtilities.invokeLater(() -> {
                // Clear current list except for the current user
                usersModel.clear();

                // Adds first the current user
                usersModel.addElement(userName + " (you)");

                if (userListStr != null && !userListStr.isEmpty()) {
                    // Parse userListStr (format: "user1,user2,user3")
                    String[] users = userListStr.split(",");
                    for (String user : users) {
                        user = user.trim();
                        // Avoid adding current user twice
                        if (!user.isEmpty() && !user.equals(userName)) {
                            usersModel.addElement(user + " (contact)");
                            break; // Only add the first user for now in a 1-to-1 chat
                        }
                    }
                }
                logger.info("Updated users list: " + userListStr);
            });
        } catch (Exception e) {
            logger.severe("Error updating users list: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // Method to remove a user from the list
    public void removeUser(String username) {
        for (int i = 0; i < usersModel.getSize(); i++) {
            String user = usersModel.getElementAt(i);
            if (user.equals(username)) {
                usersModel.removeElementAt(i);
                break;
            }
        }
    }

    String getUserName() {
        return userName;
    }

    // Method to append messages to chat area (can be called from ReadThread)
    public void appendMessage(String message) {
        SwingUtilities.invokeLater(() -> {
            chatArea.append(message + "\n");
        });
    }

    private void disconnect() {
        try {
            if (readThread != null) {
                readThread.interrupt();
            }

            if (writeThread != null) {
                writeThread.interrupt();
            }

            if (connection != null) {
                connection.close();
            }

            chatArea.append("Disconnected from server.\n");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void sendMessage(ActionEvent e) {
        String message = messageField.getText().trim();
        if (message.isEmpty())
            return;

        if (writeThread == null) {
            chatArea.append("ERROR: Cannot send message - not connected to server\n");
            logger.severe("WriteThread is null when trying to send: " + message);
            return;
        }

        // Display in local chat area
        // testing broadcast: message is echoed back to sender
        // chatArea.append("You: " + message + "\n");

        // Clear message field
        messageField.setText("");

        // The actual sending is handled by WriteThread
        logger.info("Sending message: " + message);
        //writeThread.sendMessage(userName + ": " + message);
        writeThread.sendMessage(message);
    }
}