package org.incognito;

import java.net.Socket;
import java.net.UnknownHostException;

public class Connection {
    // localhost for testing
    private String host = "127.0.0.1";
    private int port = 5125;
    Socket socket;

    public void connect() {
        try {
            socket = new Socket(host, port);
        } catch (UnknownHostException e) {
            // todo: use offline local mode only
            System.err.println("Unable to connect to server");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void close() {
        try {
            socket.close();
        } catch (Exception e) {
            System.err.println("Error in closing the connection");
        }
    }

}
