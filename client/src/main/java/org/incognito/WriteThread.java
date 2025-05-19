package org.incognito;


import org.incognito.crypto.CryptoManager;
import java.util.Base64;
import org.incognito.ChatMessage;

import java.io.ObjectOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Logger;

public class WriteThread extends Thread {
    private static Logger logger = Logger.getLogger(WriteThread.class.getName());

    private CryptoManager cryptoManager;

    private BlockingQueue<String> messageQueue = new LinkedBlockingQueue<>();
    private ObjectOutputStream outputStream;
    private Socket socket;
    private GUITest client;

    public WriteThread(Socket socket, GUITest client, CryptoManager cryptoManager) {
        this.socket = socket;
        this.client = client;
        this.cryptoManager = cryptoManager;

        try {
            outputStream = new ObjectOutputStream(socket.getOutputStream());
        } catch (IOException e) {
            logger.severe("Error getting output stream: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public void run() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                String message = messageQueue.take();

                if (outputStream == null) {
                    logger.severe("Cannot send message - outputStream is null");
                    continue;
                }

                if (message.startsWith("USERLIST:") ||
                        message.startsWith("CONNECT:") ||
                        message.startsWith("DISCONNECT:")) {

                    // Send system messages as plain strings (no encryption)
                    outputStream.writeObject(message);
                } else {
                    if (cryptoManager.getAesSessionKey() == null) {
                        logger.severe("AES session key is null. Cannot encrypt message.");
                        client.appendMessage("[SYSTEM] AES session key is null. Cannot encrypt message.");
                        continue;
                    }
                    // Encrypt message, encode to base64
                    byte[] encrypted = cryptoManager.encryptAES(message);
                    String encoded = Base64.getEncoder().encodeToString(encrypted);

                    // Wrap in ChatMessage object
                    ChatMessage chatMsg = new ChatMessage(client.getUserName(), encoded);

                    // Send ChatMessage object
                    outputStream.writeObject(chatMsg);
                }

                outputStream.flush();

            } catch (Exception ex) {
                logger.severe("Error sending message: " + ex.getMessage());
                client.appendMessage("[SYSTEM] Error sending message: " + ex.getMessage());
                ex.printStackTrace();
            }
        }
        close();
    }

    private void processSystemMessage(String message) {
        try {
            if (outputStream != null) {
                outputStream.writeObject(message);
            } else {
                logger.severe("Cannot send system message - outputStream is null");
            }
        } catch (IOException e) {
            logger.severe("Error sending system message: " + e.getMessage());
            e.printStackTrace();
        }
    }


    public void close() {
        try {
            if (outputStream != null) {
                outputStream.close();
            }
            if (socket != null) {
                socket.close();
            }
        } catch (IOException e) {
            logger.severe("Error closing write thread: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void sendMessage(String message) {
        messageQueue.offer(message);
    }
}