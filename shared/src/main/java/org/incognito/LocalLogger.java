package org.incognito;

public class LocalLogger {
    private static final String LOG_FILE_PATH = "Logs/LocalLogger/local.log";

    public static void logInfo(String message) {
        log("INFO", message);
    }

    public static void logWarning(String message) {
        log("WARNING", message);
    }

    public static void logSevere(String message) {
        log("SEVERE", message);
    }

    private static void log(String level, String message) {
        try {
            java.io.File logDir = new java.io.File("Logs/LocalLogger");
            if (!logDir.exists()) {
                logDir.mkdirs(); // Create the directory if it does not exist
            }
            java.nio.file.Files.write(java.nio.file.Paths.get(LOG_FILE_PATH),
                    (level + ": " + message + System.lineSeparator()).getBytes(),
                    java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
        } catch (java.io.IOException e) {
            System.err.println("Error while logging: " + e.getMessage());
        }
    }
}