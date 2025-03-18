package org.incognito;

import org.incognito.Connection;

public class App {

    public static void main(String[] args) {
        Connection connection = new Connection();
        connection.connect();
        connection.close();
    }

}
