package org.incognito;

public class PrivateChatSession {
    private ClientHandler client1;
    private ClientHandler client2;
    private String sessionId;

    public PrivateChatSession(ClientHandler client1, ClientHandler client2, String sessionId) {
        this.client1 = client1;
        this.client2 = client2;
        this.sessionId = sessionId;
    }

    public ClientHandler getClient1() {
        return client1;
    }

    public ClientHandler getClient2() {
        return client2;
    }

    public String getSessionId() {
        return sessionId;
    }

    public ClientHandler getOtherClient(ClientHandler currentClient) {
        if (currentClient == client1) {
            return client2;
        } else if (currentClient == client2) {
            return client1;
        }
        return null; // Should not happen if currentClient is part of this session
    }
}