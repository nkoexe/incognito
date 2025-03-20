package org.incognito;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.logging.Logger;

public class ClientHandler implements Runnable {
    private static Logger logger = Logger.getLogger(ClientHandler.class.getName());

    private Connection connection;
    private Socket clientSocket;
    private ObjectOutputStream outputStream;
    private ObjectInputStream inputStream;

    public ClientHandler(Connection connection, Socket clientSocket) {
        this.connection = connection;
        this.clientSocket = clientSocket;
    }

    @Override
    public void run() {
        try {
            outputStream = new ObjectOutputStream(clientSocket.getOutputStream());
            inputStream = new ObjectInputStream(clientSocket.getInputStream());

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

                // temp: Send the message to all connected clients
                connection.broadcast(message);
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