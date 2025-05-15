package org.incognito;

import java.io.*;
import java.net.Socket;
import java.util.logging.Logger;

public class ClientHandler implements Runnable {
    private static Logger logger = Logger.getLogger(ClientHandler.class.getName());

    private Socket socket;
    private Connection server;
    private ObjectInputStream inputStream;
    private ObjectOutputStream outputStream;
    private String username;

    public ClientHandler(Connection server, Socket socket) {
        this.server = server;
        this.socket = socket;

        try {
            // Initialize streams - important to create output stream first to avoid deadlock
            outputStream = new ObjectOutputStream(socket.getOutputStream());
            inputStream = new ObjectInputStream(socket.getInputStream());
        } catch (IOException e) {
            logger.severe("Error creating streams: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public void run() {
        try {
            // Wait for the first message which should contain the username
            String firstMessage = (String) inputStream.readObject();

            // Check if it starts with USERLIST: prefix for username registration
            if (firstMessage.startsWith("USERLIST:")) {
                username = firstMessage.substring("USERLIST:".length());

                // Check if username is already taken
                if (server.isUsernameTaken(username)) {
                    send("Server: Username '" + username + "' is already taken. Please reconnect with a different name.");
                    closeConnection();
                    return;
                }

                // Register the user with the server
                server.registerUser(username, this);

                // Welcome message
                send("Server: Welcome " + username + "!");

                // Process messages in a loop
                while (!Thread.currentThread().isInterrupted()) {
                    String message = (String) inputStream.readObject();
                    if (message == null) {
                        break;
                    }

                    // Format message with username prefix
                    String formattedMessage = username + ": " + message;

                    // Broadcast the message to all clients
                    server.broadcast(formattedMessage);
                }
            } else {
                send("Server: Invalid connection sequence. Please reconnect.");
            }
        } catch (IOException | ClassNotFoundException e) {
            logger.info("Client disconnected: " + e.getMessage());
        } finally {
            closeConnection();
        }
    }

    public void send(String message) {
        try {
            outputStream.writeObject(message);
            outputStream.flush();
        } catch (IOException e) {
            logger.severe("Error sending message to client: " + e.getMessage());
        }
    }

    private void closeConnection() {
        try {
            // If username was set, remove from connected users
            if (username != null) {
                server.removeUser(username, this);
            }

            // Close streams and socket
            if (inputStream != null) inputStream.close();
            if (outputStream != null) outputStream.close();
            if (socket != null && !socket.isClosed()) socket.close();

        } catch (IOException e) {
            logger.severe("Error closing connection: " + e.getMessage());
        }
    }

    public String getUsername() {
        return username;
    }
}