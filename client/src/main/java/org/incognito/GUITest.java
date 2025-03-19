package org.incognito;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.*;

public class GUITest extends JFrame {
    private Connection connection;
    private JTextArea chatArea;
    private JTextField messageField;
    private JButton sendButton;
    private JList<String> usersList;
    private DefaultListModel<String> usersModel;
    private ReadThread readThread;
    private WriteThread writeThread;
    private String userName;

    public GUITest() {
        // Set up the UI components
        setTitle("Incognito Chat");
        setSize(720, 480);
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
    }

    public void connect() {
        // Initialize connection
        connection = new Connection();
        connection.connect();

        if (connection.getSocket() != null) {
            chatArea.append("Connected to server.\n");

            // Prompt for username
            String userName = JOptionPane.showInputDialog(
                    this,
                    "Enter your username:",
                    "Username",
                    JOptionPane.QUESTION_MESSAGE
            );

            if (userName == null || userName.trim().isEmpty()) {
                userName = "Guest" + (int)(Math.random() * 1000);
            }

            this.userName = userName;

            // Start read and write threads
            readThread = new ReadThread(connection.getSocket(), this);
            writeThread = new WriteThread(connection.getSocket(), this);

            readThread.start();
            writeThread.start();

            // Update UI
            setTitle("Incognito Chat - " + userName);
            usersModel.setElementAt(userName, 0);
        } else {
            chatArea.append("Failed to connect to server.\n");
        }
    }

    void setUserName(String userName) {
        this.userName = userName;
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
        if (message.isEmpty()) return;

        // Display in local chat area
        chatArea.append("You: " + message + "\n");

        // Clear message field
        messageField.setText("");

        // The actual sending is handled by WriteThread
        // We just need to make the text available to it
        if (writeThread != null) {
            writeThread.sendMessage(message);
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            GUITest client = new GUITest();
            client.setVisible(true);
            client.connect();
        });
    }
}