package org.incognito;

import org.incognito.crypto.CryptoManager;
import org.incognito.crypto.QRUtil;
import org.incognito.ChatSessionLogger;
import org.incognito.GUI.UserSelectionPage;

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
    private static final Logger logger = Logger.getLogger(GUITest.class.getName());
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
    private volatile boolean isSessionActive = false; // Flag to indicate if a session is active

    /**
     * Button to allow users to return to the main menu
     * This is positioned at the top of the chat window and styled in red
     * to be easily visible but not interfere with the chat area
     */
    private JButton exitChatButton;

    /**
     * Reference to the selection page listener to enable returning to main menu
     */
    private UserSelectionPage.UserSelectionListener userSelectionListener;

    /**
     * Stores the current username to maintain identity when returning to main menu
     */
    private String currentUsername;

    public GUITest(CryptoManager cryptoManager, String username, UserSelectionPage.UserSelectionListener listener) {
        this.cryptoManager = cryptoManager;
        this.currentUsername = username;
        this.userSelectionListener = listener;

        try {
            SecretKey sessionKey = this.cryptoManager.generateAESKey();
            this.cryptoManager.setAesSessionKey(sessionKey);
            logger.info("AES session key generated and set.");
        } catch (Exception e) {
            ErrorHandler.handleCryptoError(
                this,
                "Failed to generate session key",
                e,
                () -> {
                    try {
                        SecretKey retryKey = this.cryptoManager.generateAESKey();
                        this.cryptoManager.setAesSessionKey(retryKey);
                    } catch (Exception retryEx) {
                        ErrorHandler.handleFatalError(
                            this,
                            "Failed to generate session key after retry",
                            retryEx
                        );
                    }
                }
            );
        }

        // Set up the UI components
        setTitle("Incognito Chat");
        setSize(720, 480);
        setLocationRelativeTo(null); // Center the window
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE); // Handle close in windowClosing event
        setLayout(new BorderLayout());

        // Chat display area
        chatArea = new JTextArea();
        chatArea.setEditable(false);
        chatArea.setLineWrap(true);
        JScrollPane chatScrollPane = new JScrollPane(chatArea);

        // Users list on the right
        usersModel = new DefaultListModel<>();
        // usersModel.addElement("You");
        usersList = new JList<>(usersModel);
        usersList.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                    boolean isSelected, boolean cellHasFocus) {
                Component renderer = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value.toString().contains(" (you)")) {
                    renderer.setFont(renderer.getFont().deriveFont(Font.BOLD));
                } else if (value.toString().contains(" (contact)")) {
                    renderer.setForeground(new Color(0, 102, 204)); // Blu for contact
                }
                return renderer;
            }
        });
        JScrollPane usersScrollPane = new JScrollPane(usersList);
        usersScrollPane.setPreferredSize(new Dimension(100, 0));        // Message input area at bottom
        JPanel inputPanel = new JPanel(new BorderLayout());
        messageField = new JTextField();
        sendButton = new JButton("Send");
        
        // Add Exit Chat button in a panel at the top of the window
        exitChatButton = new JButton("Exit Chat");
        exitChatButton.setBackground(new Color(255, 102, 102)); // Light red background for visibility
        exitChatButton.setForeground(Color.WHITE); // White text for contrast
        // Place button in top-right corner in its own panel
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonPanel.add(exitChatButton); // Right-aligned for consistency and visibility
        
        inputPanel.add(messageField, BorderLayout.CENTER);
        inputPanel.add(sendButton, BorderLayout.EAST);

        // Add components to frame
        add(chatScrollPane, BorderLayout.CENTER);
        add(usersScrollPane, BorderLayout.EAST);
        add(buttonPanel, BorderLayout.NORTH);
        add(inputPanel, BorderLayout.SOUTH);

        // Disable message input and send button until connected
        messageField.setEnabled(false);
        sendButton.setEnabled(false);

        // Event listeners
        sendButton.addActionListener(this::sendMessage);
        messageField.addActionListener(this::sendMessage);
        exitChatButton.addActionListener(e -> returnToMainMenu());

        // Handle window closing to disconnect properly
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                returnToMainMenu();
            }
        });

        setVisible(true);
    }

    /**
     * Initialize connection with a provided username and target user (used by
     * automated flow)
     */
    public void initializeConnectionWithUsername(Connection connection, String username, String targetUser)
            throws InterruptedException {
        logger.info("Initializing connection with username: " + username + " targeting: " + targetUser);
        this.connection = connection;
        this.userName = username;

        if (connection.getSocket() != null) {
            // Create threads for the existing connection
            BlockingQueue<String> loginResponseQueue = new ArrayBlockingQueue<>(1);
            readThread = new ReadThread(connection.getSocket(), this, cryptoManager, loginResponseQueue);
            writeThread = new WriteThread(connection.getSocket(), this, cryptoManager);

            readThread.start();
            writeThread.start();

            // Register username with server
            writeThread.sendMessage("USERLIST:" + username);

            // Wait for server to accept username
            try {
                String response = loginResponseQueue.poll(5, java.util.concurrent.TimeUnit.SECONDS);
                if ("USERNAME_ACCEPTED".equals(response)) {
                    logger.info("Username " + username + " accepted by server");

                    // Start automatic key exchange with target user
                    appendMessage("[System] Initiating secure connection with " + targetUser + "...");
                    AutoKeyExchange.performKeyExchange(targetUser, username, cryptoManager, writeThread);

                } else {
                    logger.severe("Server rejected username: " + response);
                    appendMessage("[ERROR] Server rejected username: " + response);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw e;
            }

        } else {
            throw new RuntimeException("Connection socket is null");
        }
    }

    /**
     * Initialize connection with a provided username (used by automated flow)
     */
    public void initializeConnectionWithUsername(Connection connection, String username) throws InterruptedException {
        logger.info("Initializing connection with username: " + username);
        this.connection = connection;
        this.userName = username;

        if (connection.getSocket() != null) {
            chatArea.append("Connected to server.\n");

            BlockingQueue<String> loginQueue = new ArrayBlockingQueue<>(1);

            // Start read and write threads
            writeThread = new WriteThread(connection.getSocket(), this, this.cryptoManager);
            readThread = new ReadThread(connection.getSocket(), this, this.cryptoManager, loginQueue);

            writeThread.start();
            readThread.start();

            // Update UI with current user's name
            setTitle("Incognito Chat - " + this.userName);
            usersModel.clear();
            usersModel.addElement(this.userName + " (you)");

            // username is already sent
            // logger.info("Sending username: " + username);
            // writeThread.sendMessage("USERLIST:" + this.userName);

            // Generate session ID
            String sessionId = generateSessionId();

            if (sessionId != null) {
                writeThread.sendMessage("PRIVATE_CHAT:" + username + ":" + sessionId);
                logger.info("Sending message PRIVATE_CHAT with sessionId: " + sessionId);
            } else {
                logger.warning("Session ID is null.");
                JOptionPane.showMessageDialog(this, "Session ID cannot be null.", "Error", JOptionPane.ERROR_MESSAGE);
                logger.warning("Couldn't send session ID to server.");
                disconnect();
                return;
            }

            try {
                Object response = loginQueue.poll(5, java.util.concurrent.TimeUnit.SECONDS);

                if (response instanceof String str) {
                    switch (str) {
                        case "USERNAME_ACCEPTED":
                            chatArea.append("Username '" + userName + "' accepted.\n");
                            break;
                        case "USERNAME_TAKEN":
                            JOptionPane.showMessageDialog(this,
                                    "Username already in use. Please try a different username.");
                            throw new RuntimeException("Username already taken");
                        case "WAITING_FOR_PEER":
                            chatArea.append("Waiting for contact to connect...\n");
                            isSessionActive = false;
                            messageField.setEnabled(false);
                            sendButton.setEnabled(false);
                            break;
                        case "PEER_CONNECTED":
                            chatArea.append("Contact connected! You can now start chatting.\n");
                            break;
                        default:
                            logger.warning("Unknown server response: " + str);
                            JOptionPane.showMessageDialog(this, "Unknown server response. Retry.");
                            disconnect();
                            break;
                    }
                } else if (response == null) {
                    chatArea.append("Server response is null.\n");
                    logger.warning("Timeout waiting for server response.");
                    disconnect();
                }
            } catch (InterruptedException e) {
                logger.severe("Error while waiting for server response: " + e.getMessage());
                chatArea.append("Error while waiting for server response: " + e.getMessage() + "\n");
                Thread.currentThread().interrupt();
                disconnect();
                throw e;
            }

        } else {
            chatArea.append("Failed to connect to server.\n");
            throw new RuntimeException("Failed to connect to server");
        }
    }

    public void initializeConnection(Connection connection) throws InterruptedException {

        logger.info("Initializing connection...");
        this.connection = connection;

        if (connection.getSocket() != null) {
            chatArea.append("Connected to server.\n");

            BlockingQueue<String> loginQueue = new ArrayBlockingQueue<>(1);

            try {
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

                // Check if inputName is null or empty
                if (inputName == null || inputName.trim().isEmpty()) {
                    inputName = "Guest" + (int) (Math.random() * 1000);
                }

                this.userName = inputName.trim();

                // Send username to server
                logger.info("Sending username: " + inputName);
                writeThread.sendMessage("USERLIST:" + this.userName);

                // Update UI with current user's name
                setTitle("Incognito Chat - " + this.userName);
                usersModel.clear();
                usersModel.addElement(this.userName + " (you)");

                // New for 1-to-1 chat
                String sessionId = generateSessionId();

                if (sessionId != null) {
                    writeThread.sendMessage("PRIVATE_CHAT:" + inputName + ":" + sessionId);
                    logger.info("Sending message PRIVATE_CHAT with sessionId: " + sessionId);
                } else {
                    ErrorHandler.handleSessionError(
                        this,
                        "Failed to generate session ID",
                        reconnect -> {
                            if (reconnect) {
                                try {
                                    String newSessionId = generateSessionId();
                                    writeThread.sendMessage("PRIVATE_CHAT:" + inputName + ":" + newSessionId);
                                } catch (Exception e) {
                                    ErrorHandler.handleFatalError(
                                        this,
                                        "Failed to generate session ID after retry",
                                        e
                                    );
                                }
                            } else {
                                disconnect();
                            }
                        }
                    );
                    return;
                }

                try {
                    Object response = loginQueue.poll(5, java.util.concurrent.TimeUnit.SECONDS);
                    if (response instanceof String str) {
                        switch (str) {
                            case "USERNAME_ACCEPTED":
                                chatArea.append("Username '" + userName + "' accepted.\n");
                                break;
                            case "USERNAME_TAKEN":
                                ErrorHandler.showWarning(
                                    this,
                                    "Username '" + userName + "' is already in use",
                                    "Please try a different username"
                                );
                                initializeConnection(connection); // Retry
                                return;
                            case "WAITING_FOR_PEER":
                                chatArea.append("Waiting for contact to connect...\n");
                                isSessionActive = false;
                                messageField.setEnabled(false);
                                sendButton.setEnabled(false);
                                break;
                            case "PEER_CONNECTED":
                                chatArea.append("Contact connected! You can now start chatting.\n");
                                break;
                            default:
                                ErrorHandler.showWarning(
                                    this,
                                    "Unknown server response: " + str,
                                    "The connection will be closed and retried"
                                );
                                disconnect();
                                break;
                        }
                    } else if (response == null) {
                        ErrorHandler.handleConnectionError(
                            this,
                            "Server response timeout",
                            true,
                            () -> {
                                try {
                                    initializeConnection(connection);
                                } catch (InterruptedException e) {
                                    Thread.currentThread().interrupt();
                                }
                            }
                        );
                    }
                } catch (InterruptedException e) {
                    logger.severe("Error while waiting for server response: " + e.getMessage());
                    Thread.currentThread().interrupt();
                    ErrorHandler.handleConnectionError(
                        this,
                        "Connection interrupted: " + e.getMessage(),
                        false,
                        null
                    );
                    disconnect();
                }
            } catch (Exception e) {
                ErrorHandler.handleConnectionError(
                    this,
                    "Failed to initialize connection: " + e.getMessage(),
                    true,
                    () -> {
                        try {
                            initializeConnection(connection);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                        }
                    }
                );
            }
        } else {
            ErrorHandler.handleConnectionError(
                this,
                "Connection socket is null",
                false,
                null
            );
        }
    }

    // Method to generate a unique session ID
    private String generateSessionId() {
        try {
            String pk1 = cryptoManager.getPublicKeyBase64();
            String pk2 = cryptoManager.getOtherUserPublicKeyBase64();

            if (pk1 == null || pk2 == null) {
                logger.severe("One or both public keys are null for session ID generation.");
                return "fallback-session-" + System.currentTimeMillis(); // Fallback
            }
            // Order the keys to ensure uniqueness and consistency
            String combinedKeys = pk1.compareTo(pk2) < 0 ? pk1 + pk2 : pk2 + pk1;

            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(combinedKeys.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            logger.severe("Error generating session ID: " + e.getMessage());
            return "error-session-" + System.currentTimeMillis(); // Fallback
        }
    }

    // Method to generate session ID based on usernames (for initial request)
    @SuppressWarnings("unused")
    private String generateSessionIdForUsers(String user1, String user2) {
        try {
            // Order the usernames to ensure consistency
            String combinedUsers = user1.compareTo(user2) < 0 ? user1 + user2 : user2 + user1;

            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(combinedUsers.getBytes(StandardCharsets.UTF_8));
            return "session_" + Base64.getEncoder().encodeToString(hash).substring(0, 16); // Shorter ID
        } catch (Exception e) {
            logger.severe("Error generating session ID for users: " + e.getMessage());
            return "session-" + Math.abs((user1 + user2).hashCode()); // Fallback
        }
    }

    // PER I BRO!!!!!!!!!!
    // Questo metodo dovremmo tenerlo se aggiungiamo chat di gruppo. finchè non lo
    // facciamo
    // basta handlePeerConnected e handleServerNotification (Controllare però se
    // viene usato dal server che non mi ricordo)
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
        SwingUtilities.invokeLater(() -> {
            for (int i = 0; i < usersModel.getSize(); i++) {
                if (usersModel.getElementAt(i).startsWith(username + " ")) { // Checks if the username is in the list
                    usersModel.removeElementAt(i);
                    break;
                }
            }
        });
    }

    String getUserName() {
        return userName;
    }

    // Method to append messages to chat area (can be called from ReadThread)
    public void appendMessage(String message) {
        SwingUtilities.invokeLater(() -> {
            chatArea.append(message + "\n");
        });
    }    private void disconnect() {
        try {
            // Log disconnection attempt
            logger.info("Initiating disconnection for user: " + userName);
            
            // Interrupt and cleanup threads
            if (readThread != null) {
                readThread.interrupt();
                readThread = null;
            }

            if (writeThread != null) {
                writeThread.interrupt();
                writeThread = null;
            }

            // Close connection
            if (connection != null) {
                try {
                    // Notify server about disconnection if possible
                    if (connection.getSocket() != null && !connection.getSocket().isClosed()) {
                        writeThread.sendMessage("DISCONNECT:" + userName);
                    }
                } catch (Exception ex) {
                    logger.warning("Could not send disconnect message: " + ex.getMessage());
                } finally {
                    connection.close();
                    connection = null;
                }
            }

            // Update UI state
            SwingUtilities.invokeLater(() -> {
                chatArea.append("[System] Disconnected from server.\n");
                isSessionActive = false;
                if (messageField != null) {
                    messageField.setEnabled(false);
                    messageField.setText("");
                }
                if (sendButton != null) {
                    sendButton.setEnabled(false);
                }
                if (exitChatButton != null) {
                    exitChatButton.setEnabled(true);
                }
                if (usersModel != null) {
                    usersModel.clear();
                }
            });
            
            // Log successful disconnection
            logger.info("Successfully disconnected from server");
            ChatSessionLogger.logInfo("Chat session ended - disconnected from server");
            
        } catch (Exception e) {
            ErrorHandler.showWarning(
                this,
                "Error during disconnection: " + e.getMessage(),
                "Some resources may not have been properly released"
            );
            logger.severe("Error during disconnection: " + e.getMessage());
            e.printStackTrace();
            ChatSessionLogger.logSevere("Error occurred during disconnection: " + e.getMessage());
        }
    }

    private void sendMessage(ActionEvent e) {
        if (!isSessionActive) {
            ErrorHandler.showWarning(
                this,
                "Cannot send message - session is not active",
                "Please wait for peer connection to be established"
            );
            return;
        }

        String message = messageField.getText().trim();
        if (message.isEmpty()) {
            return;
        }

        if (writeThread == null || !writeThread.isAlive()) {
            ErrorHandler.handleSessionError(
                this,
                "Cannot send message - connection to server lost",
                reconnect -> {
                    if (reconnect) {
                        try {
                            Connection newConnection = new Connection();
                            if (newConnection.connect()) {
                                initializeConnection(newConnection);
                            }
                        } catch (Exception ex) {
                            ErrorHandler.handleConnectionError(
                                this,
                                "Failed to reconnect: " + ex.getMessage(),
                                false,
                                null
                            );
                        }
                    }
                }
            );
            return;
        }

        try {
            // Display message immediately in local chat area
            chatArea.append("You: " + message + "\n");
            
            // Clear message field
            messageField.setText("");

            // Send through WriteThread
            logger.info("Sending message: " + message);
            writeThread.sendMessage(message);
            
        } catch (Exception ex) {
            ErrorHandler.handleSessionError(
                this,
                "Failed to send message: " + ex.getMessage(),
                reconnect -> {
                    if (reconnect) {
                        messageField.setText(message); // Restore message for retry
                    }
                }
            );
        }
    }

    public void handlePeerConnected(String peerUsername, String sessionId) {
        this.isSessionActive = true;
        ChatSessionLogger.logInfo("Peer connected: " + peerUsername + ", session: " + sessionId);
        SwingUtilities.invokeLater(() -> {
            messageField.setEnabled(true);
            sendButton.setEnabled(true);
            chatArea.append("[System] Connected with " + peerUsername + ". Session " + sessionId + " active.\n");

            // PER I BROOO!!!!!
            // Aggiorna la lista utenti per la chat 1-a-1
            usersModel.clear();
            if (this.userName != null) { // Check if userName is initialized
                usersModel.addElement(this.userName + " (you)");
            }
            usersModel.addElement(peerUsername + " (contact)");
            logger.info("Peer connected: " + peerUsername + ", session: " + sessionId + ". Chat UI enabled.");
        });
    }

    public void handleServerNotification(String serverMessage) {
        SwingUtilities.invokeLater(() -> {
            chatArea.append("Server: " + serverMessage + "\n");
            if (serverMessage.startsWith("WAITING_FOR_PEER")) {
                chatArea.append("[System] Waiting for peer...\n");
                this.isSessionActive = false;
                messageField.setEnabled(false);
                sendButton.setEnabled(false);
                logger.info("Waiting for peer to connect... Chat UI disabled");
            } else if (serverMessage.startsWith("PEER_DISCONNECTED")) {
                this.isSessionActive = false;
                messageField.setEnabled(false);
                sendButton.setEnabled(false);                chatArea.append("[System] " + serverMessage.substring("PEER_DISCONNECTED:".length()) + " has disconnected from the session.\n");
                logger.info("Peer " + serverMessage.substring("PEER_DISCONNECTED:".length()) + " disconnected. Chat UI disabled.");
                if (usersModel.size() > 0) {
                    for (int i = 0; i < usersModel.getSize(); i++) {
                        if (usersModel.getElementAt(i).contains(" (contact)")) {
                            usersModel.removeElementAt(i);
                            break;
                        }
                    }
                }
            } else if (serverMessage.startsWith("ERROR:You are not in an active private chat session.")) {
                chatArea.append("[System] Server error: You are not in an active chat session.\n");
                logger.warning("Received 'not in an active private chat session' error from server");
            }
        });
    }

    /**
     * Get the WriteThread instance for automated key exchange
     * 
     * @return WriteThread instance or null if not initialized
     */
    public WriteThread getWriteThread() {
        return writeThread;
    }

    /**
     * Enable chat interface after successful key exchange
     */
    public void enableChatInterface() {
        SwingUtilities.invokeLater(() -> {
            isSessionActive = true;
            messageField.setEnabled(true);
            sendButton.setEnabled(true);
            appendMessage("[System] Chat interface enabled - you can now send messages securely!");
        });
    }

    /**
     * Returns the user to the main menu (user selection screen) after confirming.
     * This method:
     * 1. Shows a confirmation dialog
     * 2. If confirmed, cleans up the chat resources
     * 3. Disposes of the chat window
     * 4. Creates and shows a new user selection page with the same username
     */
    private void returnToMainMenu() {
        int option = JOptionPane.showConfirmDialog(
            this,
            "Are you sure you want to exit the chat?\nYou will return to the main menu.",
            "Exit Chat",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.QUESTION_MESSAGE
        );
        
        if (option == JOptionPane.YES_OPTION) {
            // Log the action
            logger.info("User " + currentUsername + " returning to main menu");
            ChatSessionLogger.logInfo("Chat session ended by user returning to main menu");
            
            // Clean up chat resources
            disconnect();
            
            // Store username before disposing
            String preservedUsername = userName;
            
            // Clean up UI
            dispose();
            
            // Create and show new selection page with preserved state
            SwingUtilities.invokeLater(() -> {
                UserSelectionPage newPage = new UserSelectionPage(preservedUsername, userSelectionListener);
                newPage.setVisible(true);
                logger.info("Created new user selection page with preserved username: " + preservedUsername);
            });
        }
    }
  
    public Socket getSocket() {
        return socket;
    }

    public CryptoManager getCryptoManager() {
        return cryptoManager;
    }

    public WriteThread getWriteThread() {
        return writeThread;
    }
}