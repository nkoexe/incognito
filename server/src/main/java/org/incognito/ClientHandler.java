package org.incognito;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.logging.Logger;

public class ClientHandler implements Runnable {
    private static Logger logger = Logger.getLogger(ClientHandler.class.getName());
    private Socket clientSocket;
    private ObjectOutputStream outputStream;
    private ObjectInputStream inputStream;

    public ClientHandler(Socket clientSocket) {
        this.clientSocket = clientSocket;
    }

    @Override
    public void run() {
        try {
            outputStream = new ObjectOutputStream(clientSocket.getOutputStream());
            inputStream = new ObjectInputStream(clientSocket.getInputStream());

            String message;
            while ((message = (String) inputStream.readObject()) != null) {
                logger.info("Message Received: " + message);
                if (message.equals("exit")) {
                    break;
                }
                // Echo the message back to the client
                outputStream.writeObject("Server: " + message);
                outputStream.flush();
            }

            inputStream.close();
            outputStream.close();
            clientSocket.close();
        } catch (Exception e) {
            logger.severe("Error handling client connection");
            e.printStackTrace();
        }
    }
}