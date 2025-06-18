package org.incognito;

import org.incognito.crypto.CryptoManager;
import org.incognito.GUI.UserSelectionPage;
import org.incognito.GUI.theme.ModernTheme;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
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
    private UserSelectionPage.UserSelectionListener userSelectionListener;    /**
     * Stores the current username to maintain identity when returning to main menu
     */
    private String currentUsername;

    /**
     * Stores the username of the current chat partner (used for cleanup when disconnecting)
     */
    private String currentChatPartner;

    public GUITest(CryptoManager cryptoManager, String username, UserSelectionPage.UserSelectionListener listener) {        this.cryptoManager = cryptoManager;        this.currentUsername = username;
        this.userSelectionListener = listener;
        // Set up the UI components        // Check if the CryptoManager already has an AES session key (from manual key exchange)
        try {
            if (this.cryptoManager.getAesSessionKey() != null) {
                logger.info("Using existing AES session key from manual key exchange.");
            } else {
                logger.info("No AES session key found - will be generated during automatic key exchange.");
                // For automatic key exchange, the session key will be generated during the exchange process
                // Do NOT generate one here as it will conflict with the AutoKeyExchange logic
            }
        } catch (Exception e) {
            logger.severe("Error while checking AES session key: " + e.getMessage());
            JOptionPane.showMessageDialog(this, "Fatal error: unable to check session key.", "Cryptography error",
                    JOptionPane.ERROR_MESSAGE);
            System.exit(1);
        }

        setTitle("Incognito Chat");
        setSize(800, 550);
        setLocationRelativeTo(null); // Center the window
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE); // Handle close in windowClosing event
        setLayout(new BorderLayout(ModernTheme.SPACING_MEDIUM, ModernTheme.SPACING_MEDIUM));
        
        // Set modern background
        getContentPane().setBackground(ModernTheme.BACKGROUND_PRIMARY);

        // Chat display area
        chatArea = ModernTheme.createTextArea();
        chatArea.setEditable(false);
        chatArea.setLineWrap(true);
        chatArea.setWrapStyleWord(true);
        chatArea.setBackground(ModernTheme.BACKGROUND_SECONDARY);
        chatArea.setForeground(ModernTheme.TEXT_PRIMARY);
        
        JScrollPane chatScrollPane = new JScrollPane(chatArea);
        chatScrollPane.setBorder(ModernTheme.createRoundedBorder(ModernTheme.BORDER_COLOR, 1));
        chatScrollPane.getViewport().setBackground(ModernTheme.BACKGROUND_SECONDARY);

        // Users list on the right
        usersModel = new DefaultListModel<>();
        // usersModel.addElement("You");
        usersList = new JList<>(usersModel);
        usersList.setBackground(ModernTheme.BACKGROUND_SECONDARY);
        usersList.setForeground(ModernTheme.TEXT_PRIMARY);
        usersList.setFont(ModernTheme.FONT_MEDIUM);
        
        usersList.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                    boolean isSelected, boolean cellHasFocus) {
                Component renderer = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                setFont(ModernTheme.FONT_MEDIUM);
                setBorder(BorderFactory.createEmptyBorder(
                        ModernTheme.SPACING_SMALL, ModernTheme.SPACING_MEDIUM,
                        ModernTheme.SPACING_SMALL, ModernTheme.SPACING_MEDIUM));
                
                if (value.toString().contains(" (you)")) {
                    renderer.setFont(renderer.getFont().deriveFont(Font.BOLD));
                    setForeground(ModernTheme.TEXT_PRIMARY);
                } else if (value.toString().contains(" (contact)")) {
                    setForeground(ModernTheme.ACCENT_BLUE);
                }
                
                if (isSelected) {
                    setBackground(ModernTheme.ACCENT_BLUE);
                    setForeground(Color.WHITE);
                } else {
                    setBackground(ModernTheme.BACKGROUND_SECONDARY);
                }
                
                return renderer;
            }
        });
        
        JScrollPane usersScrollPane = new JScrollPane(usersList);
        usersScrollPane.setBorder(ModernTheme.createRoundedBorder(ModernTheme.BORDER_COLOR, 1));
        usersScrollPane.getViewport().setBackground(ModernTheme.BACKGROUND_SECONDARY);
        usersScrollPane.setPreferredSize(new Dimension(150, 0));

        // Message input area at bottom
        JPanel inputPanel = new JPanel(new BorderLayout(ModernTheme.SPACING_SMALL, 0));
        inputPanel.setBackground(ModernTheme.BACKGROUND_PRIMARY);
        inputPanel.setBorder(BorderFactory.createEmptyBorder(
                ModernTheme.SPACING_MEDIUM, ModernTheme.SPACING_MEDIUM,
                ModernTheme.SPACING_MEDIUM, ModernTheme.SPACING_MEDIUM));
        
        messageField = ModernTheme.createTextField();
        messageField.setFont(ModernTheme.FONT_MEDIUM);
        
        sendButton = ModernTheme.createButton("Send", ModernTheme.ButtonType.PRIMARY);
        
        // Add Exit Chat button in a panel at the top of the window
        exitChatButton = ModernTheme.createButton("← Exit Chat", ModernTheme.ButtonType.DANGER);
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        buttonPanel.setBackground(ModernTheme.BACKGROUND_PRIMARY);
        buttonPanel.setBorder(BorderFactory.createEmptyBorder(
                ModernTheme.SPACING_MEDIUM, ModernTheme.SPACING_MEDIUM,
                0, ModernTheme.SPACING_MEDIUM));
        buttonPanel.add(exitChatButton);
        
        inputPanel.add(messageField, BorderLayout.CENTER);
        inputPanel.add(sendButton, BorderLayout.EAST);
        
        // Main chat panel
        JPanel chatPanel = ModernTheme.createPanel();
        chatPanel.setLayout(new BorderLayout(ModernTheme.SPACING_MEDIUM, 0));
        chatPanel.setBorder(BorderFactory.createEmptyBorder(
                0, ModernTheme.SPACING_MEDIUM,
                0, ModernTheme.SPACING_MEDIUM));
        chatPanel.add(chatScrollPane, BorderLayout.CENTER);

        // Add components to frame
        add(buttonPanel, BorderLayout.NORTH);
        add(chatPanel, BorderLayout.CENTER);
        add(usersScrollPane, BorderLayout.EAST);
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
            throws InterruptedException {        logger.info("Initializing connection with username: " + username + " targeting: " + targetUser);
        this.connection = connection;
        this.userName = username;
        this.currentChatPartner = targetUser; // Track target user for cleanup

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
                String response = loginResponseQueue.poll(5, java.util.concurrent.TimeUnit.SECONDS);                if ("USERNAME_ACCEPTED".equals(response)) {
                    logger.info("Username " + username + " accepted by server");                    // Reset crypto manager session state for fresh key exchange
                    cryptoManager.resetSession();
                    
                    // Start automatic key exchange with target user
                    // Hidden: Technical message about initiating connection - not needed for user
                    // appendMessage("[System] Initiating secure connection with " + targetUser + "...");
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
    }    /**
     * Initialize connection with a provided username (used by manual key exchange flow)
     */    public void initializeConnectionWithUsername(Connection connection, String username) throws InterruptedException {
        logger.info("Initializing connection with username: " + username);
        this.connection = connection;
        this.userName = username;

        if (connection.getSocket() != null) {
            // Hidden: Basic connection confirmation message - not needed for user
            // chatArea.append("Connected to server.\n");

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

            // Register username first for manual key exchange flow
            logger.info("Sending username for registration: " + username);
            writeThread.sendMessage("USERLIST:" + this.userName);

            try {
                Object response = loginQueue.poll(5, java.util.concurrent.TimeUnit.SECONDS);

                if (response instanceof String str) {                    switch (str) {                        case "USERNAME_ACCEPTED":                            // Hidden: Technical username acceptance message - not needed for user
                            // chatArea.append("Username '" + userName + "' accepted.\n");
                            // Hidden: Confusing message about pre-exchanged keys - not clear to user
                            // chatArea.append("Ready for secure communication using pre-exchanged keys.\n");
                            
                            // For manual key exchange, enable chat interface immediately since keys are already exchanged
                            // Request user list to show available users for messaging
                            writeThread.sendMessage("REQUEST_USERLIST");
                            
                            isSessionActive = true;
                            messageField.setEnabled(true);
                            sendButton.setEnabled(true);
                            
                            // Clear and simple message when chat is ready
                            chatArea.append("✅ Chat is ready! You can now send secure messages.\n");
                            
                            break;
                        case "USERNAME_TAKEN":
                            JOptionPane.showMessageDialog(this,
                                    "Username already in use. Please try a different username.");
                            throw new RuntimeException("Username already taken");
                        default:
                            logger.warning("Unexpected response: " + str);
                            break;
                    }
                } else if (response == null) {
                    chatArea.append("Server response timeout.\n");
                    logger.warning("Timeout waiting for server response.");
                    disconnect();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw e;
            }

        } else {
            throw new RuntimeException("Connection socket is null");
        }
    }    /**
     * Initialize connection with a provided username for reused connections (used by manual key exchange flow)
     */
    public void initializeConnectionWithUsernameReused(Connection connection, String username) throws InterruptedException {
        logger.info("Initializing connection with username (reused connection): " + username);
        this.connection = connection;
        this.userName = username;        if (connection.getSocket() != null) {
            // Hidden: Basic connection confirmation message - not needed for user
            // chatArea.append("Connected to server.\n");

            // Start read and write threads for the reused connection
            writeThread = new WriteThread(connection.getSocket(), this, this.cryptoManager);
            readThread = new ReadThread(connection.getSocket(), this, this.cryptoManager);

            writeThread.start();
            readThread.start();

            // Update UI with current user's name
            setTitle("Incognito Chat - " + this.userName);
            usersModel.clear();
            usersModel.addElement(this.userName + " (you)");            // For reused connections in manual key exchange, username is already registered
            // But we still need to request the user list to see other users
            // Hidden: Technical message about username registration - not needed for user
            // chatArea.append("Username '" + userName + "' already registered.\n");
            // Hidden: Confusing message about pre-exchanged keys - not clear to user
            // chatArea.append("Ready for secure communication using pre-exchanged keys.\n");
            
            // Request user list to populate the users list for manual key exchange
            writeThread.sendMessage("REQUEST_USERLIST");
            
            // For manual key exchange, enable chat interface immediately since keys are already exchanged
            isSessionActive = true;
            messageField.setEnabled(true);
            sendButton.setEnabled(true);

            // Clear and simple message when chat is ready
            chatArea.append("✅ Chat is ready! You can now send secure messages.\n");

        } else {
            throw new RuntimeException("Connection socket is null");
        }
    }

    public void initializeConnection(Connection connection) throws InterruptedException {

        logger.info("Initializing connection...");
        this.connection = connection;        if (connection.getSocket() != null) {
            // Hidden: Basic connection confirmation message - not needed for user
            // chatArea.append("Connected to server.\n");

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
                logger.warning("Session ID is null.");
                JOptionPane.showMessageDialog(this, "Session ID cannot be null.", "Error", JOptionPane.ERROR_MESSAGE);
                logger.warning("Couldn't send session ID to server.");
                disconnect();
                return;
            }

            try {
                Object response = loginQueue.poll(5, java.util.concurrent.TimeUnit.SECONDS);

                if (response instanceof String str) {
                    switch (str) {                        case "USERNAME_ACCEPTED":
                            // Hidden: Technical username acceptance message - not needed for user
                            // chatArea.append("Username '" + userName + "' accepted.\n");
                            break;
                        case "USERNAME_TAKEN":
                            JOptionPane.showMessageDialog(this, "Username already in use. Retry.");
                            // Recursively call initializeConnection to retry
                            initializeConnection(connection);
                            return;                        case "WAITING_FOR_PEER":
                            // Hidden: Technical waiting message - not clear to user what peer means
                            // chatArea.append("Waiting for contact to connect...\n");
                            isSessionActive = false; // Reset session active flag
                            messageField.setEnabled(false);
                            sendButton.setEnabled(false);
                            break;                        case "PEER_CONNECTED":
                            // Hidden: Technical peer connection message - will be replaced with clearer message
                            // chatArea.append("Contact connected! You can now start chatting.\n");
                            // Enable message input and send button happens when ReadThread processes
                            // PEER_CONNECTED:namePeer:sessionId and will call handlePeerConnected
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
                Thread.currentThread().interrupt(); // Restore the interrupted status
                disconnect();
            }

        } else {
            chatArea.append("Failed to connect to server.\n");
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
    }    private void disconnect() {        try {
            // Log disconnection attempt
            logger.info("Initiating disconnection for user: " + userName);
            
            // Clean up key exchange tracking if we have a chat partner
            if (currentChatPartner != null && userName != null) {
                AutoKeyExchange.cleanupExchange(userName, currentChatPartner);
                currentChatPartner = null; // Clear the partner reference
            }
            
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
            }            // Update UI state
            SwingUtilities.invokeLater(() -> {
                // Hidden: Technical disconnection message - not needed for user
                // chatArea.append("[System] Disconnected from server.\n");
                isSessionActive = false;
                if (messageField != null) {
                    messageField.setEnabled(false);
                    messageField.setText("");
                }
                if (sendButton != null) {
                    sendButton.setEnabled(false);
                }
                if (exitChatButton != null) {
                    exitChatButton.setEnabled(true); // Always allow exiting
                }
                if (usersModel != null) {
                    usersModel.clear();
                }
            });
            
            // Log successful disconnection
            logger.info("Successfully disconnected from server");
            ChatSessionLogger.logInfo("Chat session ended - disconnected from server");
            
        } catch (Exception e) {
            logger.severe("Error during disconnection: " + e.getMessage());
            e.printStackTrace();
            ChatSessionLogger.logInfo("Error occurred during disconnection: " + e.getMessage());
        }
    }

    private void sendMessage(ActionEvent e) {        if (!isSessionActive) {
            ChatSessionLogger.logWarning("Attempted to send message while session is not active.");
            SwingUtilities
                    .invokeLater(() -> chatArea
                            // Hidden: Technical error message - simplified for user
                            // .append("[System] Unable to send: session not active or peer not connected.\n"));
                            .append("Please wait for the chat to be ready before sending messages.\n"));
            return;
        }

        String message = messageField.getText().trim();
        ChatSessionLogger
                .logInfo("Attempting to send message: " + message.substring(0, Math.min(20, message.length())) + "...");
        if (message.isEmpty())
            return;

        if (writeThread == null || !writeThread.isAlive()) {
            chatArea.append("ERROR: Cannot send message - not connected to server or writer thread inactive\n");
            logger.severe("WriteThread is null or not alive when trying to send: " + message);
            return;
        }

        // Display message immediately in local chat area
        chatArea.append("You: " + message + "\n");

        // Clear message field
        messageField.setText("");

        // The actual sending is handled by WriteThread
        logger.info("Sending message: " + message);
        writeThread.sendMessage(message);
    }    public void handlePeerConnected(String peerUsername, String sessionId) {
        this.isSessionActive = true;
        this.currentChatPartner = peerUsername; // Track current chat partner for cleanup
        ChatSessionLogger.logInfo("Peer connected: " + peerUsername + ", session: " + sessionId);SwingUtilities.invokeLater(() -> {
            messageField.setEnabled(true);
            sendButton.setEnabled(true);
            // Hidden: Technical session details - not needed for user
            // chatArea.append("[System] Connected with " + peerUsername + ". Session " + sessionId + " active.\n");

            // Clear and simple message when chat is ready
            chatArea.append("✅ Chat is ready! You can now send secure messages.\n");

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
            chatArea.append("Server: " + serverMessage + "\n");            if (serverMessage.startsWith("WAITING_FOR_PEER")) {
                // Hidden: Technical waiting message - not clear to user what peer means
                // chatArea.append("[System] Waiting for peer...\n");
                this.isSessionActive = false;
                messageField.setEnabled(false);
                sendButton.setEnabled(false);
                logger.info("Waiting for peer to connect... Chat UI disabled");            } else if (serverMessage.startsWith("PEER_DISCONNECTED")) {
                this.isSessionActive = false;
                messageField.setEnabled(false);
                sendButton.setEnabled(false);                // Hidden: Technical peer disconnection message - simplified for user
                // chatArea.append("[System] " + serverMessage.substring("PEER_DISCONNECTED:".length()) + " has disconnected from the session.\n");
                chatArea.append("Contact has left the chat.\n");
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
    public void enableChatInterface() {        SwingUtilities.invokeLater(() -> {
            isSessionActive = true;
            messageField.setEnabled(true);
            sendButton.setEnabled(true);
            // Hidden: Technical message about interface enabling - replaced with clearer message
            // appendMessage("[System] Chat interface enabled - you can now send messages securely!");
            appendMessage("✅ Chat is ready! You can now send secure messages.");
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
  
}