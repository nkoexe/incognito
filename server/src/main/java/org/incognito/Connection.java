package org.incognito;

import java.util.logging.Logger;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Connection {
    private static Logger logger = Logger.getLogger(Connection.class.getName());
    private ServerSocket socket;
    public final int PORT = 5125;
    private ExecutorService threadPool;

    public void init() {
        try {
            this.socket = new ServerSocket(PORT);
            this.threadPool = Executors.newCachedThreadPool();
            logger.fine("Initialized on port " + PORT);
        } catch (IOException e) {
            logger.severe("Could not initialize socket");
            e.printStackTrace();
        }
    }

    public void start() {
        while (true) {
            try {
                logger.fine("Listening for a new client...");
                Socket clientSocket = this.socket.accept();
                logger.fine("Connection established with " + clientSocket.getInetAddress());
                threadPool.execute(new ClientHandler(clientSocket));
            } catch (Exception e) {
                logger.severe("Error while accepting client connection");
                e.printStackTrace();
                continue;
            }

//            // After connection, handle each client in a new thread
//            // in order not to block main flow.
//            // Client authentication is also done in the dedicated thread
//            new Thread(() -> {
//                handleConnection(client);
//            }).start();
        }
    }

    public void stop() {
        try {
            logger.fine("Attempting to close socket...");
            this.socket.close();
            threadPool.shutdown();
        } catch (IOException e) {
            logger.severe("Error while closing socket");
            e.printStackTrace();
        }
    }

    public void handleConnection(Socket clientSocket) {
        try {
            ObjectInputStream clientStream = new ObjectInputStream(clientSocket.getInputStream());
            String message;

            // todo: authentication & initial data exchange

            while (true) {
                message = (String) clientStream.readObject();

                logger.info("Message Received: " + message);
                if (message.equals("exit"))
                    break;
            }

            clientStream.close();
            clientSocket.close();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
