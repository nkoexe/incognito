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
            while (true) {
                String message = (String) inputStream.readObject();

                if (message.startsWith("USERLIST:")) {
                    String attemptedUsername = message.substring("USERLIST:".length());

                    if (server.isUsernameTaken(attemptedUsername)) {
                        send("USERNAME_TAKEN"); // Let client know it's taken
                        message = (String) inputStream.readObject();
                        if (!message.startsWith("USERLIST:")) {
                            send("ERROR: Invalid username message.");
                            closeConnection();
                            return;
                        }
                    } else {
                        this.username = attemptedUsername;
                        server.registerUser(username, this);
                        send("USERNAME_ACCEPTED");
                        server.broadcast("CONNECT:" + username);
                        break; // Exit loop and continue
                    }
                } else {
                    send("INVALID_COMMAND");
                }
            }

            send("Server: Welcome " + username + "!");

            // Main message handling loop
            label:
            while (!Thread.currentThread().isInterrupted()) {
                Object obj = inputStream.readObject();

                switch (obj) {
                    case null:
                        break label;
                    case String msgStr:
                        if (msgStr.startsWith("USERLIST:") ||
                                msgStr.startsWith("CONNECT:") ||
                                msgStr.startsWith("DISCONNECT:")) {
                            server.broadcast(msgStr);
                        }
                        break;
                    case ChatMessage chatMsg:
                        server.broadcast(chatMsg);
                        break;
                    default:
                        break;
                }

            }

        } catch (IOException | ClassNotFoundException e) {
            logger.info("Client disconnected: " + e.getMessage());
        } finally {
            closeConnection();
        }
    }


    public void send(Object message) {
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