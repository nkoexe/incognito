package org.incognito;

import java.io.IOException;
import java.util.logging.Logger;
import java.net.Socket;
import java.net.UnknownHostException;

public class Connection {
    private static Logger logger = Logger.getLogger(Connection.class.getName());

    private String host = "incognito.njco.dev";
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
            ErrorHandler.handleConnectionError(
                    null,
                    "Server address '" + host + "' could not be resolved",
                    true,
                    () -> connect()
            );
        } catch (IOException e) {
            ErrorHandler.handleConnectionError(
                    null,
                    "Could not connect to server at " + host + ":" + port,
                    true,
                    () -> connect()
            );
        } catch (Exception e) {
            ErrorHandler.handleFatalError(
                    null,
                    "Unexpected error while connecting to server",
                    e
            );
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
            ErrorHandler.showWarning(
                    null,
                    "Error while closing connection: " + e.getMessage(),
                    "Some resources may not have been properly released"
            );
        }
    }

}
