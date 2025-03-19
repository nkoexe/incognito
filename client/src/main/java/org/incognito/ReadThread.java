package org.incognito;

import java.io.*;
import java.net.Socket;
import java.util.logging.Logger;

public class ReadThread extends Thread {
    private static Logger logger = Logger.getLogger(ReadThread.class.getName());
    private BufferedReader reader;
    private Socket socket;
    private GUITest client;

    public ReadThread(Socket socket, GUITest client) {
        this.socket = socket;
        this.client = client;

        try {
            InputStream input = socket.getInputStream();
            reader = new BufferedReader(new InputStreamReader(input));
        } catch (IOException ex) {
            logger.severe("Error getting input stream: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    public void run() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                String response = reader.readLine();
                if (response == null) break;

                client.appendMessage("Server: " + response);
            } catch (IOException ex) {
                logger.severe("Error reading from server: " + ex.getMessage());
                ex.printStackTrace();
                break;
            }
        }

        try {
            socket.close();
        } catch (IOException ex) {
            logger.severe("Error closing socket: " + ex.getMessage());
            ex.printStackTrace();
        }
    }
}