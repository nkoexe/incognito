package org.incognito;

import java.util.logging.Logger;

public class PrivateChatSession {
    private static final Logger logger = Logger.getLogger(PrivateChatSession.class.getName());

    private final ClientHandler client1;
    private final ClientHandler client2;
    private final String sessionId;

    public PrivateChatSession(ClientHandler client1, ClientHandler client2, String sessionId) {
        this.client1 = client1;
        this.client2 = client2;
        this.sessionId = sessionId;
        logger.info("Created private chat session " + sessionId + " between " +
                client1.getUsername() + " and " + client2.getUsername());
    }

    public ClientHandler getOtherClient(ClientHandler client) {
        if (client == client1) {
            return client2;
        } else if (client == client2) {
            return client1;
        }
        return null;
    }

    public String getSessionId() {
        return sessionId;
    }
}