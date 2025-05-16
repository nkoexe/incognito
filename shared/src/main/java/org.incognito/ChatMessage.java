package org.incognito;

import java.io.Serializable;

public class ChatMessage implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String sender;
    private final String encryptedContent;

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
}
