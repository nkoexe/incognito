package org.incognito;

import java.util.logging.Logger;

public class Server {
    private static Logger logger = Logger.getLogger(Server.class.getName());

    public static void main(String[] args) {
        // Initialize socket connection and listen for clients
        logger.fine("Starting Server...");

        for (String arg : args) {
            if (arg.equals("--dev")) {
                logger.info("Development mode enabled");
            }
        }

        Connection server = new Connection();

        logger.info("Server ready");
        server.start(); // this function handles client connectio\ns - it is blocking.
    }
}
