package org.incognito.GUI;

import org.incognito.Connection;
import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.logging.Logger;

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
    void onAutomaticChatRequested(String targetUser, UserSelectionPage userSelectionPage);

    void onManualKeyExchange(UserSelectionPage userSelectionPage);

    void onCancel(UserSelectionPage userSelectionPage);
  }

  public UserSelectionPage(String username, UserSelectionListener listener) {
    this.currentUsername = username;
    this.listener = listener;

    setTitle("Select Contact - " + username);
    setSize(450, 400);
    setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    setLocationRelativeTo(null);
    setLayout(new BorderLayout());

    initComponents();
    connectToServerAndLoadUsers();
  }

  private void initComponents() {
    // Header
    JLabel headerLabel = new JLabel(
        "<html><center>Select a contact to start chatting:<br/></center></html>");
    headerLabel.setHorizontalAlignment(SwingConstants.CENTER);
    headerLabel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

    // Users list
    usersModel = new DefaultListModel<>();
    usersList = new JList<>(usersModel);
    usersList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    usersList.setCellRenderer(new DefaultListCellRenderer() {
      @Override
      public Component getListCellRendererComponent(JList<?> list, Object value, int index,
          boolean isSelected, boolean cellHasFocus) {
        Component c = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
        if (value.toString().equals(currentUsername)) {
          setText(value + " (you)");
          setFont(getFont().deriveFont(Font.ITALIC));
          setEnabled(false);
        } else {
          setText(value.toString());
          setEnabled(true);
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
    scrollPane.setBorder(BorderFactory.createTitledBorder("Available Users"));
    scrollPane.setPreferredSize(new Dimension(400, 200));

    // Status and buttons
    statusLabel = new JLabel("Loading users...");
    statusLabel.setHorizontalAlignment(SwingConstants.CENTER);
    statusLabel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

    JPanel buttonPanel = new JPanel(new FlowLayout());
    JButton selectButton = new JButton("Start Chatting");
    JButton refreshButton = new JButton("Refresh");
    JButton manualButton = new JButton("Manual Key Exchange");
    JButton cancelButton = new JButton("Exit");

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

    JPanel southPanel = new JPanel(new BorderLayout());
    southPanel.add(statusLabel, BorderLayout.NORTH);
    southPanel.add(buttonPanel, BorderLayout.SOUTH);

    add(headerLabel, BorderLayout.NORTH);
    add(scrollPane, BorderLayout.CENTER);
    add(southPanel, BorderLayout.SOUTH);
  }

  private void selectUser() {
    String selectedUser = usersList.getSelectedValue();
    if (selectedUser == null) {
      JOptionPane.showMessageDialog(this, "Please select a user first.", "No Selection", JOptionPane.WARNING_MESSAGE);
      return;
    }

    if (selectedUser.equals(currentUsername)) {
      JOptionPane.showMessageDialog(this, "You cannot chat with yourself.", "Invalid Selection",
          JOptionPane.WARNING_MESSAGE);
      return;
    }

    statusLabel.setText("Starting secure chat with " + selectedUser + "...");

    if (listener != null) {
      listener.onAutomaticChatRequested(selectedUser, this);
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
  }

  private void connectToServerAndLoadUsers() {
    // Initialize connection and load users from server
    SwingUtilities.invokeLater(() -> {
      statusLabel.setText("Connecting to server...");
    });

    // Connect to server in background thread to avoid blocking UI
    new Thread(() -> {
      try {
        connection = new Connection();
        boolean connected = connection.connect();

        if (!connected) {
          SwingUtilities.invokeLater(() -> {
            statusLabel.setText("Failed to connect to server");
            logger.severe("Failed to connect to server");
          });
          return;
        }

        SwingUtilities.invokeLater(() -> {
          statusLabel.setText("Connected to server, authenticating...");
        });

        // Initialize read and write threads for server communication
        initializeServerCommunication();

      } catch (Exception e) {
        logger.severe("Error connecting to server: " + e.getMessage());
        SwingUtilities.invokeLater(() -> {
          statusLabel.setText("Connection error: " + e.getMessage());
        });
      }
    }).start();
  }

  private void initializeServerCommunication() {
    try {
      // Simple direct communication for user list only
      serverOutput = new java.io.ObjectOutputStream(connection.getSocket().getOutputStream());
      java.io.ObjectInputStream in = new java.io.ObjectInputStream(connection.getSocket().getInputStream());

      // Send username authentication
      serverOutput.writeObject("USERLIST:" + currentUsername);
      serverOutput.flush();

      // Wait for authentication response
      Object response = in.readObject();

      if (response instanceof String str) {
        if ("USERNAME_ACCEPTED".equals(str)) {
          SwingUtilities.invokeLater(() -> {
            statusLabel.setText("Connected - loading users...");
          });

          // Start a background thread to listen for user list updates
          startUserListListener(in);

        } else if ("USERNAME_TAKEN".equals(str)) {
          SwingUtilities.invokeLater(() -> {
            statusLabel.setText("Username taken - please try again later");
          });
          disconnect();
        } else {
          SwingUtilities.invokeLater(() -> {
            statusLabel.setText("Authentication failed: " + str);
          });
          disconnect();
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
        while (!Thread.currentThread().isInterrupted() && connection != null && !connection.getSocket().isClosed()) {
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
  }

  private void disconnect() {
    try {
      if (connection != null) {
        connection.close();
      }
    } catch (Exception e) {
      logger.warning("Error during disconnect: " + e.getMessage());
    }
  }
}
