package org.incognito;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.logging.Logger;
import java.net.Socket;
import java.net.UnknownHostException;

public class Connection {
    private static Logger logger = Logger.getLogger(Connection.class.getName());

    // localhost for testing
    private String host = "127.0.0.1";
    private int port = 58239;

    private Socket socket;
    private ObjectOutputStream outputStream;
    private ObjectInputStream inputStream;

    public Socket getSocket() {
        return socket;
    }

    public void connect() {
        try {
            socket = new Socket(host, port);
            logger.info("Connected to server at " + host + ":" + port);

            outputStream = new ObjectOutputStream(socket.getOutputStream());
            inputStream = new ObjectInputStream(socket.getInputStream());

        } catch (UnknownHostException e) {
            logger.severe("Unknown host: " + host);
            e.printStackTrace();
        } catch (IOException e) {
            logger.severe("IOException while connecting to server");
            e.printStackTrace();
        } catch (Exception e) {
            logger.severe("Unexpected error while connecting");
            e.printStackTrace();
        }
    }

    public void close() {
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
                logger.info("Connection closed");
            }
        } catch (IOException e) {
            logger.severe("Error while closing connection");
            e.printStackTrace();
        }
    }

    public String receive() {
        return "";
    }

}
