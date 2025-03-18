package org.incognito;

import java.util.logging.Logger;
import java.net.Socket;
import java.net.UnknownHostException;

public class Connection {
    private static Logger logger = Logger.getLogger(Connection.class.getName());

    // localhost for testing
    private String host = "127.0.0.1";
    private int port = 5125;
    Socket socket;

    public void connect() {
        try {
            socket = new Socket(host, port);
        } catch (UnknownHostException e) {
            // todo: use offline local mode only
            logger.warning("Unable to connect to server");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void close() {
        try {
            socket.close();
        } catch (Exception e) {
            logger.warning("Error while attempting to close the connection");
        }
    }

}
