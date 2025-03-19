package org.incognito;

import java.util.logging.Logger;

import org.incognito.Connection;

public class App {
    private static Logger logger = Logger.getLogger(App.class.getName());

    public static void main(String[] args) {
        Connection connection = new Connection();
        connection.connect();
        connection.close();
    }

}
