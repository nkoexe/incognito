package org.incognito;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.logging.Logger;

public class ClientHandler implements Runnable {
    private static Logger logger = Logger.getLogger(ClientHandler.class.getName());

    private Connection connection;
    private Socket clientSocket;
    private ObjectOutputStream outputStream;
    private ObjectInputStream inputStream;
    private String username;

    public ClientHandler(Connection connection, Socket clientSocket) {
        this.connection = connection;
        this.clientSocket = clientSocket;

        try {
            // Initialize streams - important to create output stream first to avoid deadlock
            outputStream = new ObjectOutputStream(clientSocket.getOutputStream());
            inputStream = new ObjectInputStream(clientSocket.getInputStream());
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

            // Check if it's a proper username registration message
            if (firstMessage.startsWith("USERLIST:")) {
                username = firstMessage.substring("USERLIST:".length());

                // Check if username is already taken
                if (connection.isUsernameTaken(username)) {
                    send("Server: Username '" + username + "' is already taken. Please reconnect with a different name.");
                    closeConnection();
                    return;
                }

                // Register the user with the server
                connection.registerUser(username, this);

                // Process messages in a loop
                while (!Thread.currentThread().isInterrupted()) {
                    String message = (String) inputStream.readObject();
                    if (message == null) {
                        break;
                    }

                    // Broadcast the message to all clients
                    connection.broadcast(message);
                }
            } else {
                send("Server: Invalid connection sequence. Please reconnect.");
            }
        } catch (Exception e) {
            logger.severe("Error handling client connection");
            e.printStackTrace();
        } finally {
            closeConnection();
        }
    }

    public String receive() {
        try {
            String message = (String) inputStream.readObject();
            return message;

        } catch (Exception e) {
            logger.severe("Error receiving message");
            e.printStackTrace();
            return null;
        }
    }

    public void send(String message) {
        try {
            outputStream.writeObject(message);
            outputStream.flush();
        } catch (Exception e) {
            logger.severe("Error sending message to client: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void closeConnection() {
        try {
            // If username was set, remove from connected users
            if (username != null) {
                connection.removeUser(username, this);
            }

            // Close streams and socket
            if (inputStream != null) inputStream.close();
            if (outputStream != null) outputStream.close();
            if (clientSocket != null) clientSocket.close();
        } catch (IOException e) {
            logger.severe("Error closing client connection: " + e.getMessage());
            e.printStackTrace();
        }
    }
}