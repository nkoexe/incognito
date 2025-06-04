package org.incognito;

import java.io.Serializable;

public class KeyExchangeMessage implements Serializable {
  private static final long serialVersionUID = 1L;

  public enum Type {
    INITIATE_EXCHANGE, // Start key exchange with a specific user
    PUBLIC_KEY_OFFER, // Send public key to peer
    SESSION_KEY_OFFER, // Send encrypted AES key to peer
    EXCHANGE_COMPLETE, // Confirm exchange completion
    EXCHANGE_ERROR // Error during exchange
  }

  private Type type;
  private String senderUsername;
  private String targetUsername;
  private String sessionId;
  private String payload; // Contains keys or error messages

  public KeyExchangeMessage(Type type, String senderUsername, String targetUsername) {
    this.type = type;
    this.senderUsername = senderUsername;
    this.targetUsername = targetUsername;
    this.sessionId = generateSessionId(senderUsername, targetUsername);
  }

  private String generateSessionId(String user1, String user2) {
    // Deterministic session ID based on usernames
    String combined = user1.compareTo(user2) < 0 ? user1 + user2 : user2 + user1;
    return "session_" + Math.abs(combined.hashCode());
  }

  // Getters and setters
  public Type getType() {
    return type;
  }

  public String getSenderUsername() {
    return senderUsername;
  }

  public String getTargetUsername() {
    return targetUsername;
  }

  public String getSessionId() {
    return sessionId;
  }

  public String getPayload() {
    return payload;
  }

  public void setPayload(String payload) {
    this.payload = payload;
  }

  @Override
  public String toString() {
    return "KeyExchangeMessage{type=" + type + ", from=" + senderUsername +
        ", to=" + targetUsername + ", session=" + sessionId + "}";
  }
}
