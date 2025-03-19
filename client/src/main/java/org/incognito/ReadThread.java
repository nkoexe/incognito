package org.incognito;

import java.io.ObjectInputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.logging.Logger;

public class ReadThread extends Thread {
    private static Logger logger = Logger.getLogger(ReadThread.class.getName());

    private ObjectInputStream inputStream;
    private Socket socket;
    private GUITest client;

    public ReadThread(Socket socket, GUITest client) {
        this.socket = socket;
        this.client = client;

        try {
            inputStream = new ObjectInputStream(socket.getInputStream());
        } catch (IOException ex) {
            logger.severe("Error getting input stream: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    @Override
    public void run() {
        while (!Thread.currentThread().isInterrupted()) {
            String message = receive();
            if (message == null)
                break;

            client.appendMessage(message);
        }

        close();
    }

    public void close() {
        try {
            if (inputStream != null) {
                inputStream.close();
            }
            if (socket != null) {
                socket.close();
            }
        } catch (IOException e) {
            logger.severe("Error closing read thread: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public String receive() {
        try {
            String message = (String) inputStream.readObject();
            return message;
        } catch (IOException | ClassNotFoundException e) {
            logger.severe("Error reading from server: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }
}