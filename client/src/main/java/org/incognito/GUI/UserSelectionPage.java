package org.incognito.GUI;

import org.incognito.Connection;
import org.incognito.GUI.theme.ModernTheme;
import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.logging.Logger;
import java.io.IOException;

public class UserSelectionPage extends JFrame {
    private static final Logger logger = Logger.getLogger(UserSelectionPage.class.getName());

    private DefaultListModel<String> usersModel;
    private JList<String> usersList;
    private JLabel statusLabel;
    private UserSelectionListener listener;
    private String currentUsername;
    private Connection connection;
    private java.io.ObjectOutputStream serverOutput;

    public interface UserSelectionListener {
        void onAutomaticChatRequested(Connection connection, String targetUser, UserSelectionPage userSelectionPage);

        void onManualKeyExchange(UserSelectionPage userSelectionPage);

        void onCancel(UserSelectionPage userSelectionPage);
    }    public UserSelectionPage(String username, UserSelectionListener listener) {
        this.currentUsername = username;
        this.listener = listener;
        
        setTitle("Select Contact - " + username);
        setSize(500, 450);
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(ModernTheme.SPACING_MEDIUM, ModernTheme.SPACING_MEDIUM));
        
        // Set modern background
        getContentPane().setBackground(ModernTheme.BACKGROUND_PRIMARY);
        
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                if (listener != null) {
                    listener.onCancel(UserSelectionPage.this);
                }
            }
        });

        initComponents();
        connectToServerAndLoadUsers();
    }    private void initComponents() {
        // Header
        JLabel headerLabel = ModernTheme.createLabel(
                "<html><center><b>Select a contact to start chatting</b><br/><span style='color: #8E8E93;'>Choose someone to begin a secure conversation</span></center></html>", 
                ModernTheme.LabelType.TITLE);
        headerLabel.setHorizontalAlignment(SwingConstants.CENTER);
        headerLabel.setBorder(BorderFactory.createEmptyBorder(
                ModernTheme.SPACING_LARGE, ModernTheme.SPACING_MEDIUM, 
                ModernTheme.SPACING_MEDIUM, ModernTheme.SPACING_MEDIUM));

        // Users list
        usersModel = new DefaultListModel<>();
        usersList = new JList<>(usersModel);
        usersList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        usersList.setBackground(ModernTheme.BACKGROUND_SECONDARY);
        usersList.setForeground(ModernTheme.TEXT_PRIMARY);
        usersList.setFont(ModernTheme.FONT_MEDIUM);
        usersList.setBorder(BorderFactory.createEmptyBorder(
                ModernTheme.SPACING_SMALL, ModernTheme.SPACING_MEDIUM,
                ModernTheme.SPACING_SMALL, ModernTheme.SPACING_MEDIUM));
        
        usersList.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                    boolean isSelected, boolean cellHasFocus) {
                Component c = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                
                // Apply modern styling
                setFont(ModernTheme.FONT_MEDIUM);
                setBorder(BorderFactory.createEmptyBorder(
                        ModernTheme.SPACING_SMALL, ModernTheme.SPACING_MEDIUM,
                        ModernTheme.SPACING_SMALL, ModernTheme.SPACING_MEDIUM));
                
                if (value.toString().equals(currentUsername)) {
                    setText(value + " (you)");
                    setFont(getFont().deriveFont(Font.ITALIC));
                    setEnabled(false);
                    setForeground(ModernTheme.TEXT_TERTIARY);
                } else {
                    setText(value.toString());
                    setEnabled(true);
                    if (isSelected) {
                        setBackground(ModernTheme.ACCENT_BLUE);
                        setForeground(Color.WHITE);
                    } else {
                        setBackground(ModernTheme.BACKGROUND_SECONDARY);
                        setForeground(ModernTheme.TEXT_PRIMARY);
                    }
                }
                return c;
            }
        });

        usersList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    selectUser();
                }
            }
        });

        JScrollPane scrollPane = new JScrollPane(usersList);
        scrollPane.setBorder(ModernTheme.createRoundedBorder(ModernTheme.BORDER_COLOR, 1));
        scrollPane.setBackground(ModernTheme.BACKGROUND_SECONDARY);
        scrollPane.getViewport().setBackground(ModernTheme.BACKGROUND_SECONDARY);
        scrollPane.setPreferredSize(new Dimension(450, 200));

        // Status label
        statusLabel = ModernTheme.createLabel("Loading users...", ModernTheme.LabelType.CAPTION);
        statusLabel.setHorizontalAlignment(SwingConstants.CENTER);
        statusLabel.setBorder(BorderFactory.createEmptyBorder(
                ModernTheme.SPACING_SMALL, ModernTheme.SPACING_SMALL, 
                ModernTheme.SPACING_SMALL, ModernTheme.SPACING_SMALL));

        // Buttons panel
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, ModernTheme.SPACING_MEDIUM, 0));
        buttonPanel.setBackground(ModernTheme.BACKGROUND_PRIMARY);
        
        JButton selectButton = ModernTheme.createButton("Start Chatting", ModernTheme.ButtonType.PRIMARY);
        JButton refreshButton = ModernTheme.createButton("Refresh", ModernTheme.ButtonType.SECONDARY);
        JButton manualButton = ModernTheme.createButton("Manual Key Exchange", ModernTheme.ButtonType.SECONDARY);
        JButton cancelButton = ModernTheme.createButton("Exit", ModernTheme.ButtonType.DANGER);

        selectButton.addActionListener(e -> selectUser());
        refreshButton.addActionListener(e -> requestUserListUpdate());
        manualButton.addActionListener(e -> {
            if (listener != null) {
                listener.onManualKeyExchange(this);
            }
        });
        cancelButton.addActionListener(e -> {
            if (listener != null) {
                listener.onCancel(this);
            }
        });

        buttonPanel.add(selectButton);
        buttonPanel.add(refreshButton);
        buttonPanel.add(manualButton);
        buttonPanel.add(cancelButton);

        // Main content panel
        JPanel contentPanel = ModernTheme.createPanel();
        contentPanel.setLayout(new BorderLayout(ModernTheme.SPACING_MEDIUM, ModernTheme.SPACING_MEDIUM));
        contentPanel.setBorder(BorderFactory.createEmptyBorder(
                ModernTheme.SPACING_MEDIUM, ModernTheme.SPACING_MEDIUM,
                ModernTheme.SPACING_MEDIUM, ModernTheme.SPACING_MEDIUM));
        contentPanel.add(scrollPane, BorderLayout.CENTER);

        // South panel for status and buttons
        JPanel southPanel = new JPanel(new BorderLayout());
        southPanel.setBackground(ModernTheme.BACKGROUND_PRIMARY);
        southPanel.add(statusLabel, BorderLayout.NORTH);
        southPanel.add(buttonPanel, BorderLayout.SOUTH);

        add(headerLabel, BorderLayout.NORTH);
        add(contentPanel, BorderLayout.CENTER);
        add(southPanel, BorderLayout.SOUTH);
    }

    private void selectUser() {
        String selectedUser = usersList.getSelectedValue();
        if (selectedUser == null) {
            JOptionPane.showMessageDialog(this, "Please select a user first.", "No Selection",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        if (selectedUser.equals(currentUsername)) {
            JOptionPane.showMessageDialog(this, "You cannot chat with yourself.", "Invalid Selection",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        statusLabel.setText("Starting secure chat with " + selectedUser + "...");

        if (listener != null) {
            listener.onAutomaticChatRequested(this.connection, selectedUser, this);
        }
    }

    public void updateUsersList(String userListStr) {
        SwingUtilities.invokeLater(() -> {
            usersModel.clear();

            if (userListStr != null && !userListStr.isEmpty()) {
                String[] users = userListStr.split(",");
                for (String user : users) {
                    user = user.trim();
                    if (!user.isEmpty()) {
                        usersModel.addElement(user);
                    }
                }
                if (usersModel.getSize() <= 1) { // Only current user or empty
                    statusLabel.setText("No other users online - waiting for contacts...");
                } else {
                    statusLabel.setText("Select a user to start chatting");
                }
            } else {
                statusLabel.setText("No users online");
            }
        });
    }

    public void setStatus(String status) {
        SwingUtilities.invokeLater(() -> statusLabel.setText(status));
    }

    private void requestUserListUpdate() {
        statusLabel.setText("Refreshing user list...");

        // Send request to server for updated user list
        if (serverOutput != null) {
            try {
                serverOutput.writeObject("REQUEST_USERLIST");
                serverOutput.flush();
            } catch (Exception e) {
                logger.warning("Error requesting user list update: " + e.getMessage());
                SwingUtilities.invokeLater(() -> {
                    statusLabel.setText("Failed to refresh user list");
                });
            }
        } else {
            SwingUtilities.invokeLater(() -> {
                statusLabel.setText("Not connected to server");
            });
        }
    }

    public String getCurrentUsername() {
        return currentUsername;
    }    private void connectToServerAndLoadUsers() {
        // Initialize connection and load users from server
        SwingUtilities.invokeLater(() -> {
            statusLabel.setText("Connecting to server...");
        });

        // Connect to server in background thread to avoid blocking UI
        Thread connectionThread = new Thread(() -> {
            try {
                // 1. Handle initial connection errors
                if (connection != null) {
                    try {
                        connection.close(); // Clean up any existing connection
                    } catch (Exception e) {
                        logger.warning("Error cleaning up existing connection: " + e.getMessage());
                    }
                }

                connection = new Connection();
                boolean connected = connection.connect();

                // 2. Handle connection failure
                if (!connected) {
                    SwingUtilities.invokeLater(() -> {
                        statusLabel.setText("Failed to connect to server");
                        logger.severe("Failed to connect to server");
                        
                        // Show error dialog to user
                        JOptionPane.showMessageDialog(this,
                            "Could not connect to server. Please check your network connection.",
                            "Connection Failed",
                            JOptionPane.ERROR_MESSAGE);
                    });
                    return;
                }

                // 3. Handle successful connection
                SwingUtilities.invokeLater(() -> {
                    statusLabel.setText("Connected to server, authenticating...");
                });

                // 4. Initialize server communication with error handling
                try {
                    initializeServerCommunication();
                } catch (Exception e) {
                    throw new RuntimeException("Failed to initialize server communication", e);
                }

            } catch (Exception e) {
                // 5. Handle all other errors (including runtime errors)
                logger.severe("Error connecting to server: " + e.getMessage());
                SwingUtilities.invokeLater(() -> {
                    statusLabel.setText("Connection error: " + e.getMessage());
                    
                    // Clean up any partial connection state
                    if (connection != null) {
                        disconnect();
                    }
                    
                    // Show detailed error to user
                    JOptionPane.showMessageDialog(this,
                        "Failed to establish connection: " + e.getMessage() +
                        "\nPlease try again later.",
                        "Connection Error",
                        JOptionPane.ERROR_MESSAGE);
                });
            }
        }, "ServerConnection");
        
        connectionThread.setDaemon(true); // Allow JVM to exit if thread is still running
        connectionThread.start();
    }

    private void initializeServerCommunication() {
        try {
            // Simple direct communication for user list only
            serverOutput = new java.io.ObjectOutputStream(connection.getSocket().getOutputStream());
            java.io.ObjectInputStream in = new java.io.ObjectInputStream(connection.getSocket().getInputStream());

            // Try authentication with username, retry if taken
            boolean authenticated = false;
            String usernameToTry = currentUsername;

            while (!authenticated) {
                // Send username authentication
                serverOutput.writeObject("USERLIST:" + usernameToTry);
                serverOutput.flush();

                // Wait for authentication response
                Object response = in.readObject();

                if (response instanceof String str) {
                    if ("USERNAME_ACCEPTED".equals(str)) {
                        // Update the current username if it was changed
                        currentUsername = usernameToTry;
                        setTitle("Select Contact - " + currentUsername);

                        SwingUtilities.invokeLater(() -> {
                            statusLabel.setText("Connected - loading users...");
                        });

                        // Start a background thread to listen for user list updates
                        startUserListListener(in);
                        authenticated = true;

                    } else if ("USERNAME_TAKEN".equals(str)) {
                        // Prompt for new username on UI thread
                        final String currentAttempt = usernameToTry;
                        final String[] newUsername = { null };
                        SwingUtilities.invokeAndWait(() -> {
                            newUsername[0] = JOptionPane.showInputDialog(
                                    this,
                                    "Username '" + currentAttempt
                                            + "' is already taken.\nPlease enter a different username:",
                                    "Username Taken",
                                    JOptionPane.WARNING_MESSAGE);
                        });

                        if (newUsername[0] == null || newUsername[0].trim().isEmpty()) {
                            // User cancelled or entered empty username
                            SwingUtilities.invokeLater(() -> {
                                statusLabel.setText("Authentication cancelled");
                            });
                            disconnect();
                            return;
                        }

                        usernameToTry = newUsername[0].trim();
                        final String nextAttempt = usernameToTry;
                        SwingUtilities.invokeLater(() -> {
                            statusLabel.setText("Trying username: " + nextAttempt + "...");
                        });

                    } else {
                        SwingUtilities.invokeLater(() -> {
                            statusLabel.setText("Authentication failed: " + str);
                        });
                        disconnect();
                        return;
                    }
                } else {
                    SwingUtilities.invokeLater(() -> {
                        statusLabel.setText("Invalid server response");
                    });
                    disconnect();
                    return;
                }
            }

        } catch (Exception e) {
            logger.severe("Error initializing server communication: " + e.getMessage());
            SwingUtilities.invokeLater(() -> {
                statusLabel.setText("Failed to initialize connection: " + e.getMessage());
            });
            disconnect();
        }
    }

    private void startUserListListener(java.io.ObjectInputStream in) {
        // Background thread to listen for server messages
        new Thread(() -> {
            try {
                while (!Thread.currentThread().isInterrupted() && connection != null
                        && !connection.getSocket().isClosed()) {
                    Object message = in.readObject();
                    if (message instanceof String msgStr) {
                        if (msgStr.startsWith("USERLIST:")) {
                            String userListStr = msgStr.substring("USERLIST:".length());
                            updateUsersList(userListStr);
                        } else if (msgStr.startsWith("CONNECT:")) {
                            // User connected - request updated user list
                            requestUserListUpdate();
                        } else if (msgStr.startsWith("DISCONNECT:")) {
                            String disconnectedUser = msgStr.substring("DISCONNECT:".length());
                            removeUserFromList(disconnectedUser);
                        }
                    }
                }
            } catch (Exception e) {
                if (!Thread.currentThread().isInterrupted()) {
                    logger.warning("Error listening for user list updates: " + e.getMessage());
                }
            }
        }, "UserListListener").start();
    }

    private void removeUserFromList(String username) {
        SwingUtilities.invokeLater(() -> {
            for (int i = 0; i < usersModel.getSize(); i++) {
                String element = usersModel.getElementAt(i);
                if (element.equals(username) || element.startsWith(username + " ")) {
                    usersModel.removeElementAt(i);
                    break;
                }
            }
        });
    }    /**
     * Disconnects from the server and cleans up resources in a specific order:
     * 1. Notify server of disconnect (if possible)
     * 2. Close output stream (first to prevent pipe broken errors)
     * 3. Close input streams (if any are open)
     * 4. Close socket connection
     * 5. Clean up UI state
     */
    public void disconnect() {
        Thread cleanupThread = new Thread(() -> {
            try {
                // 1. Try to notify server about disconnection if connection is still alive
                if (serverOutput != null && connection != null && 
                    connection.getSocket() != null && !connection.getSocket().isClosed()) {
                    try {
                        serverOutput.writeObject("DISCONNECT:" + currentUsername);
                        serverOutput.flush();
                        logger.info("Sent disconnect notification to server");
                    } catch (IOException e) {
                        // Non-critical error - connection might already be down
                        logger.warning("Could not send disconnect notification: " + e.getMessage());
                    }
                }

                // 2. Close output stream first to prevent pipe broken errors
                if (serverOutput != null) {
                    try {
                        serverOutput.close();
                        serverOutput = null;
                        logger.fine("Closed server output stream");
                    } catch (IOException e) {
                        logger.warning("Error closing output stream: " + e.getMessage());
                    }
                }

                // 3. Close the connection (this will close the socket and input streams)
                if (connection != null) {
                    try {
                        connection.close();
                        connection = null;
                        logger.fine("Closed connection");
                    } catch (Exception e) {
                        logger.severe("Error closing connection: " + e.getMessage());
                    }
                }

                // 4. Update UI state in a thread-safe way
                SwingUtilities.invokeLater(() -> {
                    usersModel.clear();
                    statusLabel.setText("Disconnected from server");
                });

            } catch (Exception e) {
                // Catch any unexpected errors during cleanup
                logger.severe("Unexpected error during disconnect: " + e.getMessage());
                e.printStackTrace();
            } finally {
                // 5. Ensure UI reflects disconnected state
                SwingUtilities.invokeLater(() -> {
                    statusLabel.setText("Disconnected");
                });
            }
        }, "DisconnectCleanup");

        // Start cleanup in background to not block UI
        cleanupThread.setDaemon(true);
        cleanupThread.start();
    }

    public UserSelectionListener getListener() {
        return listener;
    }

    public Connection getConnection() {
        return connection;
    }
      /**
     * Disconnect from server but keep the connection object for reuse
     * This is used during manual key exchange to avoid creating a new connection
     */
    public void disconnectWithoutClosingConnection() {
        try {
            // Close server output stream to signal disconnect but keep the socket
            if (serverOutput != null) {
                try {
                    serverOutput.close();
                    serverOutput = null;
                    logger.fine("Closed server output stream for connection reuse");
                } catch (IOException e) {
                    logger.warning("Error closing output stream: " + e.getMessage());
                }
            }

            // Note: We intentionally do NOT close the connection here 
            // as it will be reused by the chat application

            // Update UI state in a thread-safe way
            SwingUtilities.invokeLater(() -> {
                usersModel.clear();
                statusLabel.setText("Preparing for manual key exchange...");
            });

        } catch (Exception e) {
            // Catch any unexpected errors during cleanup
            logger.severe("Unexpected error during partial disconnect: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
