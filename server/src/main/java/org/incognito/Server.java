package org.incognito;

import java.util.logging.Logger;

import org.incognito.Connection;

public class Server {
    private static Logger logger = Logger.getLogger(Server.class.getName());

    public static void main(String[] args) {
        // Initialize socket connection and listen for clients
        logger.fine("Starting Server...");
        Connection server = new Connection();
        server.init();
        logger.info("Server ready");
        server.start(); // this function handles client connections - it is blocking.
    }
}
