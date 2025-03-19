package org.incognito;

import java.util.logging.Logger;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.incognito.ClientHandler;

public class Connection {
    private static Logger logger = Logger.getLogger(Connection.class.getName());

    private ServerSocket socket;
    public final int PORT = 58239;

    // threadPool that will handle user connections
    private ExecutorService clientHandlerPool;

    public void init() {
        try {
            this.socket = new ServerSocket(PORT);
            this.clientHandlerPool = Executors.newCachedThreadPool();
            logger.fine("Initialized on port " + PORT);
        } catch (IOException e) {
            logger.severe("Could not initialize socket");
            e.printStackTrace();
        }
    }

    public void start() {
        if (this.socket == null) {
            logger.severe("Socket is unavailable. Unable to start server.");
            return;
        }

        while (true) {
            try {
                logger.fine("Listening for a new client...");

                Socket clientSocket = this.socket.accept();
                logger.fine("Connection established with " + clientSocket.getInetAddress());

                // After connection, handle each client in a new thread
                // in order not to block main flow.
                // Client authentication is also done in the dedicated thread
                clientHandlerPool.execute(new ClientHandler(clientSocket));

            } catch (Exception e) {
                logger.severe("Error while accepting client connection");
                e.printStackTrace();
                continue;
            }
        }
    }

    public void stop() {
        // todo: send closing connection message to clients
        try {
            logger.fine("Attempting to close socket...");
            this.socket.close();
            clientHandlerPool.shutdown();
        } catch (IOException e) {
            logger.severe("Error while closing socket");
            e.printStackTrace();
        }
    }
}
