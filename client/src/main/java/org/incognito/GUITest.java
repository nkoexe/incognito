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
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.logging.Logger;

public class GUITest extends JFrame {
    // Encryption manager + stuff
    private CryptoManager cryptoManager;
    private JLabel qrCodeLabel;   // To display QR code image
    private JButton saveQRButton;
    private JButton scanQRButton;
    private String myPublicKeyString;  // Store your own public key string for QR code

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

    public GUITest() {
        // Set up the UI components
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
        JScrollPane usersScrollPane = new JScrollPane(usersList);
        usersScrollPane.setPreferredSize(new Dimension(100, 0));

        // Message input area at bottom
        JPanel inputPanel = new JPanel(new BorderLayout());
        messageField = new JTextField();
        sendButton = new JButton("Send");
        inputPanel.add(messageField, BorderLayout.CENTER);
        inputPanel.add(sendButton, BorderLayout.EAST);

        // Set up QR code panel
        setupQRPanel();

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

//    public void initializeKeys() {
//        try {
//            this.cryptoManager = new CryptoManager();
//            SecretKey sessionKey = cryptoManager.generateAESKey();
//            cryptoManager.setAesSessionKey(sessionKey);
//        } catch (Exception e) {
//            JOptionPane.showMessageDialog(this, "Failed to initialize encryption keys.");
//            e.printStackTrace();
//        }
//    }

    public void initializeConnection(Connection connection) throws InterruptedException {

        initializeKeys();
        this.connection = connection;

        if (connection.getSocket() != null) {
            chatArea.append("Connected to server.\n");

            BlockingQueue<String> loginQueue = new ArrayBlockingQueue<>(1);

            // Start read and write threads
            writeThread = new WriteThread(connection.getSocket(), this, cryptoManager);
            readThread = new ReadThread(connection.getSocket(), this, cryptoManager, loginQueue);

            writeThread.start();
            readThread.start();

            label:
            while (true) {
                String inputName = JOptionPane.showInputDialog(
                        this,
                        "Enter your username:",
                        "Username",
                        JOptionPane.QUESTION_MESSAGE);

                if (inputName == null || inputName.trim().isEmpty()) {
                    inputName = "Guest" + (int) (Math.random() * 1000);
                }

                writeThread.sendMessage("USERLIST:" + inputName);

                Object response = loginQueue.take();

                if (response instanceof String str) {
                    switch (str) {
                        case "USERNAME_ACCEPTED":
                            this.userName = inputName;
                            break label;
                        case "USERNAME_TAKEN":
                            JOptionPane.showMessageDialog(this, "Username already taken. Please try another one.");
                            break;
                        case "INVALID_COMMAND":
                            logger.severe("Unexpected server response: " + str);
                            JOptionPane.showMessageDialog(this, "Unexpected server response. Try again.");
//                            break label;
                    }
                }
//                break label;
            }

            // Update UI
            setTitle("Incognito Chat - " + userName);
//            usersModel.setElementAt(userName, 0);
        } else {
            chatArea.append("Failed to connect to server.\n");
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
                            usersModel.addElement(user);
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

    private void setupQRPanel() {
        // Main Panel with BorderLayout
        JPanel qrPanel = new JPanel(new BorderLayout());
        qrPanel.setBorder(BorderFactory.createTitledBorder("QR Code Exchange"));
        qrPanel.setPreferredSize(new Dimension(280, 280));  // Bigger

        // Panel for QR code image
        qrCodeLabel = new JLabel();
        qrCodeLabel.setHorizontalAlignment(SwingConstants.CENTER);
        qrCodeLabel.setBorder(BorderFactory.createEtchedBorder());
        qrCodeLabel.setPreferredSize(new Dimension(200, 200));

        JScrollPane scrollPane = new JScrollPane(qrCodeLabel);
        scrollPane.setPreferredSize(new Dimension(200, 200));
        qrPanel.add(scrollPane, BorderLayout.CENTER);

        // Panel for buttons
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 5, 5));
        saveQRButton = new JButton("Save QR");
        scanQRButton = new JButton("Scan QR");

        // Set preferred size for buttons
        Dimension buttonSize = new Dimension(100, 30);
        saveQRButton.setPreferredSize(buttonSize);
        scanQRButton.setPreferredSize(buttonSize);

        buttonPanel.add(saveQRButton);
        buttonPanel.add(scanQRButton);
        qrPanel.add(buttonPanel, BorderLayout.SOUTH);

        // Add QR panel to the main frame on the left
        add(qrPanel, BorderLayout.WEST);

        // Add action listeners for buttons
        saveQRButton.addActionListener(e -> saveQRCodeToFile());
        scanQRButton.addActionListener(e -> scanQRCodeFromFile());
    }

    private void saveQRCodeToFile() {
        if (myPublicKeyString == null || myPublicKeyString.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "No public key available to save.",
                    "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Save QR Code");
        fileChooser.setSelectedFile(new File("public_key_qr.png"));

        int option = fileChooser.showSaveDialog(this);
        if (option == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
            try {
                QRUtil.generateQRCode(myPublicKeyString, file.getAbsolutePath(), 200, 200);
                JOptionPane.showMessageDialog(this, "QR Code saved successfully.");
            } catch (Exception e) {
                logger.severe("Error while saving QR code: " + e.getMessage());
                JOptionPane.showMessageDialog(this,
                        "Error while saving QR Code: " + e.getMessage(),
                        "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void scanQRCodeFromFile() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Select QR Code image");
        fileChooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
                "Images: (*.png, *.jpg, *.jpeg, *.gif)", "png", "jpg", "jpeg", "gif"));

        int option = fileChooser.showOpenDialog(this);
        if (option == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
            try {
                String scannedKey = QRUtil.decodeQRCode(file);
                if (scannedKey != null && !scannedKey.isEmpty()) {
                    // Set the scanned key in the CryptoManager
                    cryptoManager.setOtherUserPublicKey(scannedKey);
                    JOptionPane.showMessageDialog(this,
                            "Scanned public key imported successfully.",
                            "Success", JOptionPane.INFORMATION_MESSAGE);

                    // Visual feedback
                    chatArea.append("System: Contact public key successfully added.\n");
                } else {
                    JOptionPane.showMessageDialog(this,
                            "No valid QR code found in the image.",
                            "Warning", JOptionPane.WARNING_MESSAGE);
                }
            } catch (Exception e) {
                logger.severe("Error while scanning QR Code: " + e.getMessage());
                JOptionPane.showMessageDialog(this,
                        "Error while decoding il QR Code: " + e.getMessage(),
                        "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    public void initializeKeys() {
        try {
            this.cryptoManager = new CryptoManager();
            SecretKey sessionKey = cryptoManager.generateAESKey();
            cryptoManager.setAesSessionKey(sessionKey);

            // Get your public key as string
            myPublicKeyString = cryptoManager.getPublicKeyBase64();

            // Generate and display QR code image
            updateQRCodeImage(myPublicKeyString);

        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Failed to initialize encryption keys.");
            e.printStackTrace();
        }
    }

    private void updateQRCodeImage(String data) {
        try {
            // Use QRUtil's new helper method to get BufferedImage in memory
            BufferedImage qrBufferedImage = QRUtil.generateQRCodeImage(data, 200, 200);
            ImageIcon qrIcon = new ImageIcon(qrBufferedImage);
            qrCodeLabel.setIcon(qrIcon);
        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, "Failed to generate QR Code.");
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            GUITest client = new GUITest();
            client.setVisible(true);
            Connection connection = new Connection();
            connection.connect();
            try {
                client.initializeConnection(connection);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        });
    }
}