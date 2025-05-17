package org.incognito;

import org.incognito.crypto.CryptoManager;

import javax.swing.*;
import java.util.Base64;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Logger;

import java.io.ObjectInputStream;
import java.io.IOException;
import java.net.Socket;

public class ReadThread extends Thread {
    private static Logger logger = Logger.getLogger(ReadThread.class.getName());

    private CryptoManager cryptoManager;
    private ObjectInputStream inputStream;
    private Socket socket;
    private GUITest client;

    private final BlockingQueue<Object> messageQueue = new LinkedBlockingQueue<>();
    private BlockingQueue<String> loginResponseQueue;

    public ReadThread(Socket socket, GUITest client, CryptoManager cryptoManager) {
        this(socket, client, cryptoManager, null);
    }

    public ReadThread(Socket socket, GUITest client, CryptoManager cryptoManager, BlockingQueue<String> loginResponseQueue) {
        this.socket = socket;
        this.client = client;
        this.cryptoManager = cryptoManager;
        this.loginResponseQueue = loginResponseQueue;

        try {
            inputStream = new ObjectInputStream(socket.getInputStream());
        } catch (IOException ex) {
            logger.severe("Error getting input stream: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    @Override
    public void run() {
        while (!Thread.currentThread().isInterrupted()) {
            Object obj = receiveObject();
            if (obj == null) break;

            try {
                // Login system messages are expected to be Strings
                if (obj instanceof String msgStr) {
                    if (loginResponseQueue != null &&
                            (msgStr.equals("USERNAME_ACCEPTED") || msgStr.equals("USERNAME_TAKEN") || msgStr.equals("INVALID_COMMAND"))) {
                        loginResponseQueue.put(msgStr);
                        loginResponseQueue = null; // Only used once
                        continue;
                    }

                    // Store in messageQueue for blocking reads
                    messageQueue.put(msgStr);

                    // Process system or chat messages
                    if (msgStr.startsWith("USERLIST:") ||
                            msgStr.startsWith("CONNECT:") ||
                            msgStr.startsWith("DISCONNECT:") ||
                            msgStr.startsWith("SERVER:") ||
                            msgStr.startsWith("ERROR:") ||
                            msgStr.startsWith("INFO:")) {
                        processSystemMessage(msgStr);
                    } else {
                        client.appendMessage(msgStr);
                    }
                } else if (obj instanceof ChatMessage chatMsg) {
                    byte[] encrypted = Base64.getDecoder().decode(chatMsg.getEncryptedContent());
                    String decrypted = cryptoManager.decryptAES(encrypted);
                    client.appendMessage(chatMsg.getSender() + ": " + decrypted);
                    messageQueue.put(chatMsg);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                logger.severe("Error handling incoming message: " + e.getMessage());
            }
        }
        close();
    }

    private Object receiveObject() {
        try {
            return inputStream.readObject();
        } catch (IOException | ClassNotFoundException e) {
            logger.severe("Error reading from server: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    private void processSystemMessage(String message) {
        if (message.startsWith("USERLIST:")) {
            String userListStr = message.substring("USERLIST:".length());
            client.updateUsersList(userListStr);
        } else if (message.startsWith("CONNECT:")) {
            String username = message.substring("CONNECT:".length());
//            client.appendMessage(username + " has joined the chat");
        } else if (message.startsWith("DISCONNECT:")) {
            String username = message.substring("DISCONNECT:".length());
            SwingUtilities.invokeLater(() -> {
                client.removeUser(username);
            });
            client.appendMessage(username + " has left the chat");
        } else if (message.startsWith("SERVER:")) {
            String serverMessage = message.substring("SERVER:".length());
            client.appendMessage("[Server] " + serverMessage);
        } else if (message.startsWith("ERROR:")) {
            String errorMessage = message.substring("ERROR:".length());
            client.appendMessage("[Error] " + errorMessage);
        } else if (message.startsWith("INFO:")) {
            String infoMessage = message.substring("INFO:".length());
            client.appendMessage("[Info] " + infoMessage);
        }
    }

    public void close() {
        try {
            if (inputStream != null) inputStream.close();
            if (socket != null) socket.close();
        } catch (IOException e) {
            logger.severe("Error closing read thread: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Waits for and returns the next message received from the server.
     * Useful for blocking reads during login phase (e.g. username confirmation).
     */
    public Object readResponse() {
        try {
            return messageQueue.take(); // waits until a message is available
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }
}
