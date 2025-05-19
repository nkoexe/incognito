package org.incognito;

import java.io.Serializable;

public class ChatMessage implements Serializable {
    private static final long serialVersionUID = 1L;

    private String sender;
    private String encryptedContent;

    public ChatMessage(String sender, String encryptedContent) {
        this.sender = sender;
        this.encryptedContent = encryptedContent;
    }

    public String getSender() {
        return sender;
    }

    public String getEncryptedContent() {
        return encryptedContent;
    }

    @Override
    public String toString() {
        return "ChatMessage{sender='" + sender + "'}";
    }
}