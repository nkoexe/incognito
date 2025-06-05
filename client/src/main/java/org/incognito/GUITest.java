package org.incognito;

import org.incognito.crypto.CryptoManager;
import org.incognito.crypto.QRUtil;
import org.incognito.ChatSessionLogger;

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
  private volatile boolean isSessionActive = false; // Flag to indicate if a session is active

  public GUITest(CryptoManager cryptoManager) {
    this.cryptoManager = cryptoManager;
    // Set up the UI components

    try {
      SecretKey sessionKey = this.cryptoManager.generateAESKey();
      this.cryptoManager.setAesSessionKey(sessionKey);
      logger.info("AES session key generated and set.");
    } catch (Exception e) {
      logger.severe("Error while generating AES session key: " + e.getMessage());
      JOptionPane.showMessageDialog(this, "Fatal error: unable to generate session key.", "Cryptography error",
          JOptionPane.ERROR_MESSAGE);
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

    // Disable message input and send button until connected
    messageField.setEnabled(false);
    sendButton.setEnabled(false);

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
              JOptionPane.showMessageDialog(this, "Username already in use. Please try a different username.");
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
          switch (str) {
            case "USERNAME_ACCEPTED":
              chatArea.append("Username '" + userName + "' accepted.\n");
              break;
            case "USERNAME_TAKEN":
              JOptionPane.showMessageDialog(this, "Username already in use. Retry.");
              // Recursively call initializeConnection to retry
              initializeConnection(connection);
              return;
            case "WAITING_FOR_PEER":
              chatArea.append("Waiting for contact to connect...\n");
              isSessionActive = false; // Reset session active flag
              messageField.setEnabled(false);
              sendButton.setEnabled(false);
              break;
            case "PEER_CONNECTED":
              chatArea.append("Contact connected! You can now start chatting.\n");
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
      logger.info("Disconnected from server.");
    } catch (Exception e) {
      logger.severe("Error during disconnection: " + e.getMessage());
      e.printStackTrace();
    } finally {
      isSessionActive = false;
      if (messageField != null)
        messageField.setEnabled(false);
      if (sendButton != null)
        sendButton.setEnabled(false);
    }
  }

  private void sendMessage(ActionEvent e) {
    if (!isSessionActive) {
      ChatSessionLogger.logWarning("Attempted to send message while session is not active.");
      SwingUtilities
          .invokeLater(() -> chatArea.append("[System] Unable to send: session not active or peer not connected.\n"));
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

    // Display in local chat area
    // testing broadcast: message is echoed back to sender
    // chatArea.append("You: " + message + "\n");

    // Clear message field
    messageField.setText("");

    // The actual sending is handled by WriteThread
    logger.info("Sending message: " + message);
    // writeThread.sendMessage(userName + ": " + message);
    writeThread.sendMessage(message);
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
        sendButton.setEnabled(false);
        chatArea.append("[System] Peer disconnected.\n");
        logger.info("Peer disconnected. Chat UI disabled.");
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
}