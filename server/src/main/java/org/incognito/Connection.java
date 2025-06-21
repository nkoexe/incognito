package org.incognito;

import org.incognito.ChatSessionLogger;

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

public class Connection {
    private static Logger logger = Logger.getLogger(Connection.class.getName());

    private ServerSocket socket;
    public final int PORT = 58239;

    // threadPool that will handle user connections
    private ExecutorService clientHandlerPool;
    private ArrayList<ClientHandler> connectedClients = new ArrayList<>();
    private Map<String, ClientHandler> usersClientMap = new ConcurrentHashMap<>();
    private Set<String> connectedUsers = ConcurrentHashMap.newKeySet();

    // Data structure for private chat
    private Map<String, ClientHandler> pendingPrivateChats = new ConcurrentHashMap<>(); // sessionId ->
                                                                                        // clientHandlerInAttesa
    private Map<String, PrivateChatSession> activePrivateSessions = new ConcurrentHashMap<>(); // sessionId ->
                                                                                               // PrivateChatSession
    private Map<ClientHandler, String> clientToSessionIdMap = new ConcurrentHashMap<>(); // clientHandler -> sessionId
                                                                                         // (for active sessions)

    public Connection() {
        try {
            this.socket = new ServerSocket(PORT);
            this.clientHandlerPool = Executors.newCachedThreadPool();
            logger.fine("Initialized on port " + PORT);
        } catch (IOException e) {
            ErrorHandler.handleServerError("Could not initialize socket", e, true);
        }
    }

    public void start() {
        if (this.socket == null) {
            ErrorHandler.handleServerError("Socket is unavailable. Unable to start server.", null, true);
            return;
        }

        logger.info("Server ready and listening on port " + PORT);
        while (true) {
            try {
                logger.fine("Listening for a new client...");

                Socket clientSocket = this.socket.accept();
                logger.fine("Connection established with " + clientSocket.getInetAddress());

                ClientHandler clientHandler = new ClientHandler(this, clientSocket);
                clientHandlerPool.execute(clientHandler);

            } catch (IOException e) {
                if (socket.isClosed()) {
                    logger.info("Server socket closed, stopping listener.");
                    break;
                }
                ErrorHandler.handleServerError("Error while accepting client connection", e, false);
            } catch (Exception e) {
                ErrorHandler.handleServerError("Unexpected error in server accept loop", e, true);
            }
        }
    }

    public void stop() {
        logger.info("Attempting to stop server...");
        broadcast("SERVER_SHUTDOWN"); // Notify all clients about server shutdown
        try {
            if (this.socket != null && !this.socket.isClosed()) {
                this.socket.close();
            }
        } catch (IOException e) {
            ErrorHandler.handleServerError("Error while closing server socket", e, false);
        } finally {
            clientHandlerPool.shutdown();
            logger.info("Server stopped.");
        }
    }

    public void broadcast(Object message) {
        for (ClientHandler client : new ArrayList<>(usersClientMap.values())) {
            client.send(message);
        }
    }    // add user to the list of connected users
    public void registerUser(String username, ClientHandler clientHandler) {
        // Store the username in its original case but check case-insensitively
        if (isUsernameTaken(username)) {
            clientHandler.send("USERNAME_TAKEN");
            return;
        }
        
        usersClientMap.put(username, clientHandler);
        connectedUsers.add(username);

        clientHandler.send("USERNAME_ACCEPTED");
        logger.info("User " + username + " registered from " + clientHandler.getSocket().getRemoteSocketAddress());

        broadcastUserList(); // Send updated user list to all clients
        broadcast("CONNECT:" + username); // Notify all clients about the new user
    }

    public void removeUser(String username, ClientHandler handler) {
        if (username == null)
            return; // Avoid null pointer exception

        usersClientMap.remove(username);
        connectedUsers.remove(username);

        logger.info("User " + username + " removed.");
        broadcast("DISCONNECT:" + username);
        broadcastUserList();

        // To close private chat sessions
        String sessionId = clientToSessionIdMap.remove(handler);
        if (sessionId != null) {
            PrivateChatSession session = activePrivateSessions.remove(sessionId);
            if (session != null) {
                ClientHandler peer = session.getOtherClient(handler);
                if (peer != null) {
                    clientToSessionIdMap.remove(peer);
                    peer.send("PEER_DISCONNECTED:" + username); // Notify peer about disconnection
                    logger.info("Closed private session " + sessionId + " due to disconnect of " + username);
                }
            }
        }
        // Remove even from pending private chats
        pendingPrivateChats.values().remove(handler);
    }

    public void broadcastUserList() {
        if (connectedUsers.isEmpty()) {
            broadcast("USERLIST:");
        } else {
            String userListStr = String.join(",", connectedUsers);
            broadcast("USERLIST:" + userListStr);
        }
        logger.fine("Broadcasting user list: " + String.join(",", connectedUsers));
    }

    // Get client handler by username
    public ClientHandler getClientByUsername(String username) {
        return usersClientMap.get(username);
    }    // Check if username is already taken (case-insensitive)
    public boolean isUsernameTaken(String username) {
        return connectedUsers.stream()
            .anyMatch(existingUser -> existingUser.equalsIgnoreCase(username));
    }

    // Methods for private chat session handling
    public synchronized void handlePrivateChatRequest(ClientHandler requester, String sessionId,
            String requesterUsername) {
        if (clientToSessionIdMap.containsKey(requester)) {
            requester.send("ERROR:Already in a session or pending request.");
            logger.warning("User " + requesterUsername + " tried to start a new private chat while already in one.");
            return;
        }

        if (pendingPrivateChats.containsKey(sessionId)) {
            ClientHandler peerHandler = pendingPrivateChats.remove(sessionId);

            if (peerHandler == requester) { // Same client sent the request again
                pendingPrivateChats.put(sessionId, requester); // Re-add to pending
                requester.send("WAITING_FOR_PEER");
                logger.info("User " + requesterUsername + " re-initiated wait for session " + sessionId);
                return;
            }
            PrivateChatSession newSession = new PrivateChatSession(requester, peerHandler, sessionId);
            activePrivateSessions.put(sessionId, newSession);
            clientToSessionIdMap.put(requester, sessionId);
            clientToSessionIdMap.put(peerHandler, sessionId);

            ChatSessionLogger
                    .logInfo("Private chat session " + sessionId + " created between " + requesterUsername + " and "
                            + peerHandler.getUsername());

            requester.send("PEER_CONNECTED:" + peerHandler.getUsername() + ":" + sessionId);
            peerHandler.send("PEER_CONNECTED:" + requesterUsername + ":" + sessionId);
            logger.info("Private session " + sessionId + " started between " + requesterUsername + " and "
                    + peerHandler.getUsername());
        } else {
            pendingPrivateChats.put(sessionId, requester);
            requester.send("WAITING_FOR_PEER:" + sessionId);
            ChatSessionLogger.logInfo("User " + requesterUsername + " is waiting for a peer for session " + sessionId);
            logger.info("User " + requesterUsername + " is waiting for a peer for session " + sessionId);
        }
    }

    public void forwardPrivateMessage(ClientHandler sender, ChatMessage message) {
        String senderUsername = sender.getUsername();
        if (senderUsername == null && sender.getSocket() != null) {
            senderUsername = "[NoUsername:" + sender.getSocket().getRemoteSocketAddress() + "]";
        } else if (senderUsername == null) {
            senderUsername = "[NoUsername:SocketInfoUnavailable]";
        }

        String sessionId = clientToSessionIdMap.get(sender);
        if (sessionId == null) {
            sender.send("ERROR:You are not in an active private chat session.");
            ChatSessionLogger
                    .logWarning(
                            "User " + senderUsername + " tried to send a private message without being in a session.");
            logger.warning(
                    "User " + sender.getUsername() + " tried to send a private message without being in a session.");
            return;
        }

        PrivateChatSession session = activePrivateSessions.get(sessionId);
        if (session == null) {
            sender.send("ERROR:Private chat session not found.");
            logger.severe("Session " + sessionId + " not found for user " + sender.getUsername()
                    + " but clientToSessionIdMap had an entry.");
            clientToSessionIdMap.remove(sender); // Clean up stale entry
            return;
        }

        ClientHandler recipient = session.getOtherClient(sender);
        if (recipient != null) {
            // IMPORTANTE!!!!!
            // Assicurati che ChatMessage contenga il sessionId se il client deve conoscerlo
            // o che il client possa dedurlo dal contesto.
            // Per ora, il server inoltra semplicemente il messaggio.
            recipient.send(message);
            ChatSessionLogger
                    .logInfo("Forwarded private message from " + senderUsername + " to " + recipient.getUsername()
                            + " in session " + sessionId);
            logger.fine("Forwarded private message from " + sender.getUsername() + " to " + recipient.getUsername()
                    + " in session " + sessionId);
        } else {
            sender.send("ERROR:Peer not found in your session.");
            logger.warning("Peer not found for " + sender.getUsername() + " in session " + sessionId);
        }
    }

    public void handleKeyExchange(ClientHandler sender, KeyExchangeMessage message) {
        String senderUsername = sender.getUsername();
        String targetUsername = message.getTargetUsername();

        logger.info("Handling key exchange: " + message);

        ClientHandler targetClient = usersClientMap.get(targetUsername);
        if (targetClient == null) {
            KeyExchangeMessage errorMsg = new KeyExchangeMessage(
                    KeyExchangeMessage.Type.EXCHANGE_ERROR,
                    "system", senderUsername);
            errorMsg.setPayload("Target user not found or offline");
            sender.send(errorMsg);
            return;
        }

        switch (message.getType()) {
            case INITIATE_EXCHANGE:
                // Forward initiation request to target user
                KeyExchangeMessage initiateMsg = new KeyExchangeMessage(
                        KeyExchangeMessage.Type.INITIATE_EXCHANGE,
                        senderUsername, targetUsername);
                targetClient.send(initiateMsg);

                logger.info("Key exchange initiated between " + senderUsername + " and " + targetUsername);
                break;

            case PUBLIC_KEY_OFFER:
                // Forward public key to target
                targetClient.send(message);
                break;

            case SESSION_KEY_OFFER:
                // Forward encrypted session key to target
                targetClient.send(message);
                break;

            case EXCHANGE_COMPLETE:
                // Both users confirm - create private chat session if not exists
                String sessionId = message.getSessionId();
                if (!activePrivateSessions.containsKey(sessionId)) {
                    PrivateChatSession session = new PrivateChatSession(sender, targetClient, sessionId);
                    activePrivateSessions.put(sessionId, session);
                    clientToSessionIdMap.put(sender, sessionId);
                    clientToSessionIdMap.put(targetClient, sessionId);

                    // Notify both clients that session is ready
                    sender.send("PEER_CONNECTED:" + targetUsername + ":" + sessionId);
                    targetClient.send("PEER_CONNECTED:" + senderUsername + ":" + sessionId);

                    logger.info("Key exchange completed and chat session created for " + sessionId);
                }
                break;

            case EXCHANGE_ERROR:
                // Forward error to target and clean up
                targetClient.send(message);
                break;
        }
    }
}
