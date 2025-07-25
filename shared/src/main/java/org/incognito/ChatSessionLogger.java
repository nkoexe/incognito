package org.incognito;

import java.util.logging.Logger;
import java.util.logging.FileHandler;
import java.util.logging.SimpleFormatter;
import java.io.IOException;

public class ChatSessionLogger {
    private static final Logger logger = Logger.getLogger(ChatSessionLogger.class.getName());

    static {
        try {
            java.io.File logDir = new java.io.File("Logs");
            if (!logDir.exists()) {
                logDir.mkdirs(); // Crea la cartella se non esiste
            }
            java.io.File chatLogDir = new java.io.File("Logs/ChatLogs");
            if (!chatLogDir.exists()) {
                chatLogDir.mkdirs(); // Crea la sottocartella se non esiste
            }
            FileHandler fileHandler = new FileHandler("Logs/ChatLogs/chat_sessions.log", true);
            fileHandler.setFormatter(new SimpleFormatter());
            logger.addHandler(fileHandler);
            logger.setUseParentHandlers(false); // Disabilita il logging sulla console
        } catch (IOException e) {
            System.err.println("Error while configuring logger: " + e.getMessage());
        }
    }

    public static void logInfo(String message) {
        logger.info(message);
    }

    public static void logWarning(String message) {
        logger.warning(message);
    }

    public static void logSevere(String message) {
        logger.severe(message);
    }
}