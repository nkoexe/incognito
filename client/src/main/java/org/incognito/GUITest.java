package org.incognito;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.io.EOFException;

public class GUITest extends JFrame {
    private Connection connection;
    private ObjectOutputStream outputStream;
    private ObjectInputStream inputStream;
    private JTextArea chatArea;
    private JTextField messageField;
    private JButton sendButton;
    private JList<String> usersList;
    private DefaultListModel<String> usersModel;
    private Thread readerThread;

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
        connection = new Connection();
        connection.connect();

        try {
            // Setup output stream
            outputStream = new ObjectOutputStream(connection.getSocket().getOutputStream());
            chatArea.append("Connected to server.\n");

            // Setup input stream
            inputStream = new ObjectInputStream(connection.getSocket().getInputStream());

            // Start reader thread
            readerThread = new Thread(this::readMessages);
            readerThread.start();
        } catch (IOException e) {
            chatArea.append("Failed to establish data stream with server.\n");
            e.printStackTrace();
        }
    }

    private void readMessages() {
        try {
            while (!Thread.currentThread().isInterrupted() &&
                    connection.getSocket() != null &&
                    !connection.getSocket().isClosed()) {

                try {
                    Object message = inputStream.readObject();
                    if (message == null) break;

                    final String messageStr = message.toString();
                    SwingUtilities.invokeLater(() -> {
                        chatArea.append("Server: " + messageStr + "\n");
                    });
                } catch (EOFException e) {
                    // Server closed the connection normally
                    break;
                }
            }
        } catch (IOException | ClassNotFoundException e) {
            if (connection.getSocket() != null && !connection.getSocket().isClosed()) {
                e.printStackTrace();
                SwingUtilities.invokeLater(() -> {
                    chatArea.append("Connection to server lost.\n");
                });
            }
        } finally {
            SwingUtilities.invokeLater(() -> {
                chatArea.append("Disconnected from server.\n");
            });
        }
    }

    private void disconnect() {
        try {
            if (readerThread != null) {
                readerThread.interrupt();
            }

            if (outputStream != null) {
                // Send exit message
                outputStream.writeObject("exit");
                outputStream.flush();
                outputStream.close();
            }

            if (inputStream != null) {
                inputStream.close();
            }

            if (connection != null) {
                connection.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void sendMessage(ActionEvent e) {
        String message = messageField.getText().trim();
        if (message.isEmpty()) return;

        try {
            outputStream.writeObject(message);
            outputStream.flush();
            chatArea.append("You: " + message + "\n");
            messageField.setText("");
        } catch (IOException ex) {
            chatArea.append("Failed to send message.\n");
            ex.printStackTrace();
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