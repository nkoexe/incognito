package org.incognito;

import org.incognito.Connection;

public class Server {

    public static void main(String[] args) {
        // Initialize socket connection and listen for clients
        Connection server = new Connection();
        server.init();
        server.start(); // this function handles client connections - it is blocking.
    }
}
