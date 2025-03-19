package org.incognito;

import java.io.*;
import java.net.Socket;
import java.util.logging.Logger;

public class WriteThread extends Thread {
    private static Logger logger = Logger.getLogger(WriteThread.class.getName());
    private PrintWriter writer;
    private Socket socket;
    private GUITest client;

    public WriteThread(Socket socket, GUITest client) {
        this.socket = socket;
        this.client = client;

        try {
            OutputStream output = socket.getOutputStream();
            writer = new PrintWriter(output, true);
        } catch (IOException ex) {
            logger.severe("Error getting output stream: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    public void run() {
        BufferedReader consoleReader = new BufferedReader(new InputStreamReader(System.in));

        while (!Thread.currentThread().isInterrupted()) {
            try {
                String message = consoleReader.readLine();
                writer.println(message);

                if (message.equalsIgnoreCase("exit")) {
                    break;
                }
            } catch (IOException ex) {
                logger.severe("Error writing to server: " + ex.getMessage());
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

    public void sendMessage(String message) {
        writer.println(message);
    }
}