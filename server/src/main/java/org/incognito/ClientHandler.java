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
            inputStream = new ObjectInputStream(clientSocket.getInputStream());
            outputStream = new ObjectOutputStream(clientSocket.getOutputStream());

            // todo: authentication & initial data exchange

            String message;

            while (true) {

                message = receive();
                if (message == null) {
                    break;
                }

                logger.info("Message Received: " + message);
                if (message.equals("exit")) {
                    break;
                }

                // Echo the message back to the client
                send("Server: " + message);
            }

            outputStream.close();
            inputStream.close();
            clientSocket.close();

        } catch (Exception e) {
            logger.severe("Error handling client connection");
            e.printStackTrace();
        }
    }

    public String receive() {
        try {
            String message = (String) inputStream.readObject();
            System.err.println(message);
            return message;

        } catch (Exception e) {
            logger.severe("Error receiving message");
            e.printStackTrace();
            return null;
        }
    }

    public void send(String message) {
        try {
            outputStream.writeObject(message);
            outputStream.flush();

        } catch (Exception e) {
            logger.severe("Error sending message");
            e.printStackTrace();
        }
    }
}