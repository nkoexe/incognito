package org.incognito;

import java.io.ObjectOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Logger;

public class WriteThread extends Thread {
    private static Logger logger = Logger.getLogger(WriteThread.class.getName());

    private BlockingQueue<String> messageQueue = new LinkedBlockingQueue<>();
    private ObjectOutputStream outputStream;
    private Socket socket;
    private GUITest client;

    public WriteThread(Socket socket, GUITest client) {
        this.socket = socket;
        this.client = client;

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
                // Blocks until a message is available
                String message = messageQueue.take();

                try {
                    if (outputStream == null) {
                        logger.severe("Cannot send message - outputStream is null");
                        continue;
                    }

                    outputStream.writeObject(message);
                    outputStream.flush();
                } catch (IOException e) {
                    logger.severe("Error sending message: " + e.getMessage());
                    e.printStackTrace();
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        close();
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