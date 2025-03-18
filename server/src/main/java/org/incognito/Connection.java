package org.incognito;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.net.ServerSocket;
import java.net.Socket;

public class Connection {
    private ServerSocket socket;
    public final int PORT = 5125;

    public void init() {
        try {
            this.socket = new ServerSocket(PORT);
        } catch (IOException e) {
            System.err.println("Could not initialize socket");
            e.printStackTrace();
        }
    }

    public void stop() {
        try {
            this.socket.close();
        } catch (IOException e) {
            System.err.println("Error while closing socket");
            e.printStackTrace();

        }
    }

    public void start() {
        while (true) {
            Socket client;
            try {
                client = this.socket.accept();
            } catch (Exception e) {
                continue;
            }

            // After connection, handle each client in a new thread
            // in order not to block main flow.
            // Client authentication is also done in the dedicated thread
            new Thread(() -> {
                handleConnection(client);
            }).start();
        }
    }

    public void handleConnection(Socket clientSocket) {
        try {
            ObjectInputStream clientStream = new ObjectInputStream(clientSocket.getInputStream());

            // todo: authentication & initial data exchange

            while (true) {
                String message = (String) clientStream.readObject();
                System.out.println("Message Received: " + message);
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
