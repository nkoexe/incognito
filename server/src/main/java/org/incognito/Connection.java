package org.incognito;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.incognito.ClientHandler;

public class Connection {
    private static Logger logger = Logger.getLogger(Connection.class.getName());

    private ServerSocket socket;
    public final int PORT = 58239;

    // threadPool that will handle user connections
    private ExecutorService clientHandlerPool;
    private ArrayList<ClientHandler> connectedClients = new ArrayList<>();
    private  Map<String, ClientHandler> usersClientMap = new ConcurrentHashMap<>();
    private Set<String> connectedUsers = ConcurrentHashMap.newKeySet();

    public Connection() {
        try {
            this.socket = new ServerSocket(PORT);
            this.clientHandlerPool = Executors.newCachedThreadPool();
            logger.fine("Initialized on port " + PORT);
        } catch (IOException e) {
            logger.severe("Could not initialize socket");
            e.printStackTrace();
        }
    }

    public void start() {
        if (this.socket == null) {
            logger.severe("Socket is unavailable. Unable to start server.");
            return;
        }

        while (true) {
            try {
                logger.fine("Listening for a new client...");

                Socket clientSocket = this.socket.accept();
                logger.fine("Connection established with " + clientSocket.getInetAddress());

                // After connection, handle each client in a new thread
                // in order not to block main flow.
                // Client authentication is also done in the dedicated thread
                ClientHandler clientHandler = new ClientHandler(this, clientSocket);
                connectedClients.add(clientHandler);
                clientHandlerPool.execute(clientHandler);

            } catch (Exception e) {
                logger.severe("Error while accepting client connection");
                e.printStackTrace();
                continue;
            }
        }
    }

    public void stop() {
        // todo: send closing connection message to clients
        try {
            logger.fine("Attempting to close socket...");
            this.socket.close();
            clientHandlerPool.shutdown();
        } catch (IOException e) {
            logger.severe("Error while closing socket");
            e.printStackTrace();
        }
    }

    public void broadcast(String message) {
        for (ClientHandler client : connectedClients) {
            client.send(message);
        }
    }

    //add user to the list of connected users
    public void registerUser(String username, ClientHandler client) {
        connectedUsers.add(username);
        usersClientMap.put(username, client);

        // Notify all clients about the new user
        broadcast("CONNECT:" + username);

        // Send updated user list to all clients
        broadcastUserList();

        logger.info("User " + username + " registered");
    }

    public void removeUser(String username, ClientHandler handler) {
        usersClientMap.remove(username);
        connectedUsers.remove(username);
        connectedClients.remove(handler);

        //Notify all client about the user that left
        broadcast("DISCONNECT:" + username);

        //Send updated user list to all clients
        broadcastUserList();

        logger.info("User " + username + " removed");
    }

    private void broadcastUserList() {
        String userListStr = String.join(",", connectedUsers);
        broadcast("USERLIST:" + userListStr);
        logger.fine("Broadcasting user list: " + userListStr);
    }

    // Get client handler by username
    public ClientHandler getClientByUsername(String username) {
        return usersClientMap.get(username);
    }

    // Check if username is already taken
    public boolean isUsernameTaken(String username) {
        return connectedUsers.contains(username);
    }
}
