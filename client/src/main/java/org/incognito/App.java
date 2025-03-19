package org.incognito;

import java.util.logging.ConsoleHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.incognito.Connection;
import org.incognito.GUITest;

public class App {
    private static Logger logger = Logger.getLogger(App.class.getName());

    public static void main(String[] args) {
        GUITest window = new GUITest();

        Connection connection = new Connection();
        connection.connect();

        window.initializeConnection(connection);
    }
}
