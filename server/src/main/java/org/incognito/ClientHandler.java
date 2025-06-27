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
            // Initialize streams - important to create output stream first to avoid
            // deadlock
            outputStream = new ObjectOutputStream(socket.getOutputStream());
            outputStream.flush(); // Ensure the stream is flushed before reading
            inputStream = new ObjectInputStream(socket.getInputStream());
        } catch (IOException e) {
            ErrorHandler.handleServerError("Error creating streams for client " + socket.getRemoteSocketAddress(), e,
                    false);
            closeConnection();
        }
    }

    public Socket getSocket() {
        return socket;
    }

    @Override
    public void run() {
        try {
            // Wait for the client to send a username
            // This is a blocking call, so it will wait until the client sends a message
            // The client should send a message starting with "USERLIST:"
            // to indicate the username they want to use
            while (this.username == null) {
                if (socket.isClosed() || inputStream == null) { // Check if the connection is closed
                    logger.warning("Authentication failed: connection closed or stream unavailable.");
                    return; // exit run() if the connection is closed
                }
                Object initialMsg = inputStream.readObject();
                if (initialMsg instanceof String) {
                    String command = (String) initialMsg;
                    if (command.startsWith("USERLIST:")) { // Client sends USERLIST:username
                        String attemptedUsername = command.substring("USERLIST:".length());
                        if (server.isUsernameTaken(attemptedUsername)) {
                            send("USERNAME_TAKEN");
                            // Client should handle this response and prompt for a new username
                        } else {
                            this.username = attemptedUsername;
                            server.registerUser(this.username, this);
                            // USERNAME_ACCEPTED is sent from server.registerUser()
                            // server.broadcast("CONNECT:" + this.username);
                            break; // Exit the loop if username is accepted
                        }
                    } else {
                        send("INVALID_COMMAND:Expected USERLIST:username");
                        logger.warning("Client " + socket.getRemoteSocketAddress() + " sent invalid initial command: "
                                + command);
                        // Consider closing the connection if the command is invalid
                    }
                } else {
                    send("INVALID_COMMAND_TYPE:Expected String for username registration.");
                    logger.warning("Client " + socket.getRemoteSocketAddress() + " sent non-string initial message.");
                    // Consider closing the connection if the message is invalid
                }
            }
            if (this.username == null) { // If it was not possible to register the username
                logger.warning(
                        "Failed to register username for client " + socket.getRemoteSocketAddress()
                                + ". Closing connection.");
                closeConnection();
                return;
            }

            logger.info("User " + username + " successfully authenticated. Ready for messages.");

            // Main loop for handling messages
            label: while (!Thread.currentThread().isInterrupted() && !socket.isClosed()) {
                Object obj = inputStream.readObject();

                switch (obj) {
                    case String command:
                        if (command.startsWith("PRIVATE_CHAT:")) {
                            // Expected format: "PRIVATE_CHAT:username:sessionId"
                            String[] parts = command.split(":", 3);

                            if (parts.length == 3) {
                                String sessionId = parts[2];
                                server.handlePrivateChatRequest(this, sessionId, this.username);
                            } else {
                                send("ERROR:Invalid PRIVATE_CHAT command format. Expected PRIVATE_CHAT:yourUsername:sessionId");
                                logger.warning("User " + username + " sent invalid PRIVATE_CHAT command: " + command);
                            }
                        } else if (command.equalsIgnoreCase("REQUEST_USERLIST")) {
                            server.broadcastUserList();
                        }
                        // Other string commands could be handled here
                        else {
                            logger.info("Received unhandled String command from " + username + ": " + command);
                        }
                        break;
                    case ChatMessage chatMsg:
                        // Forward the private message instead of broadcasting
                        server.forwardPrivateMessage(this, chatMsg);
                        break;
                    case KeyExchangeMessage keyExchangeMsg:
                        // Handle automated key exchange messages
                        server.handleKeyExchange(this, keyExchangeMsg);
                        break;
                    case null: // Stream closed or null object received (sent by client)
                        logger.info("Client " + username + " closed the stream (null object received).");
                        break label; // Exit the loop if null object is received
                    default:
                        logger.warning(
                                "Received unknown object type from " + username + ": " + obj.getClass().getName());
                        break;
                }
            }

        } catch (EOFException e) {
            logger.info("Client " + (username != null ? username : socket.getRemoteSocketAddress())
                    + " disconnected (EOF).");
        } catch (IOException e) {
            if ("Connection reset".equals(e.getMessage()) || "Socket closed".equals(e.getMessage())
                    || e.getMessage().contains("Socket write error")) {
                logger.info("Client " + (username != null ? username : socket.getRemoteSocketAddress())
                        + " connection issue: "
                        + e.getMessage());
            } else {
                ErrorHandler.handleServerError(
                        "IOException for client " + (username != null ? username : socket.getRemoteSocketAddress()), e,
                        false);
            }
        } catch (ClassNotFoundException e) {
            ErrorHandler.handleServerError("ClassNotFoundException from client "
                    + (username != null ? username : socket.getRemoteSocketAddress()), e, false);
        } catch (Exception e) { // Catch-all for unexpected exceptions
            ErrorHandler.handleServerError("Unexpected error in ClientHandler for "
                    + (username != null ? username : socket.getRemoteSocketAddress()), e, false);
        } finally {
            logger.fine("ClientHandler for " + (username != null ? username : "unknown user") + " is finishing.");
            closeConnection();
        }
    }

    public void send(Object message) {
        if (socket.isClosed() || outputStream == null) {
            logger.warning("Cannot send message to " + (username != null ? username : "disconnected client")
                    + ", socket/stream closed.");
            return;
        }
        try {
            outputStream.writeObject(message);
            outputStream.flush();
        } catch (IOException e) {
            ErrorHandler.handleServerError(
                    "Error sending message to client " + (username != null ? username : "unknown"), e, false);
        }
    }

    private void closeConnection() {
        logger.info("Closing connection for client " + (username != null ? username : socket.getRemoteSocketAddress()));
        if (username != null) {
            server.removeUser(username, this);
        }

        try {
            if (inputStream != null)
                inputStream.close();
        } catch (IOException e) {
            ErrorHandler.handleServerError("Error closing input stream for " + username, e, false);
        }
        try {
            if (outputStream != null)
                outputStream.close();
        } catch (IOException e) {
            ErrorHandler.handleServerError("Error closing output stream for " + username, e, false);
        }
        try {
            if (socket != null && !socket.isClosed())
                socket.close();
        } catch (IOException e) {
            ErrorHandler.handleServerError("Error closing socket for " + username, e, false);
        }
        logger.info("Connection closed and resources released for " + (username != null ? username : "client"));
    }

    public String getUsername() {
        return username;
    }
}