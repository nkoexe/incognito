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

    public Socket getSocket() {
        return socket;
    }

    public boolean connect() {
        try {
            socket = new Socket(host, port);
            LocalLogger.logInfo("Connecting to server at " + host + ":" + port);
            logger.info("Connected to server at " + host + ":" + port);
            return true;
        } catch (UnknownHostException e) {
            LocalLogger.logSevere("Unknown host: " + host);
            logger.severe("Unknown host: " + host);
            e.printStackTrace();
        } catch (IOException e) {
            LocalLogger.logSevere("IOException while connecting to server");
            logger.severe("IOException while connecting to server");
            e.printStackTrace();
        } catch (Exception e) {
            LocalLogger.logSevere("Unexpected error while connecting");
            logger.severe("Unexpected error while connecting");
            e.printStackTrace();
        }
        return false;
    }

    public void close() {
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
                LocalLogger.logInfo("Connection closed");
                logger.info("Connection closed");
            }
        } catch (IOException e) {
            LocalLogger.logSevere("Error while closing connection");
            logger.severe("Error while closing connection");
            e.printStackTrace();
        }
    }

    public String receive() {
        return "";
    }

}
