package org.incognito;

import javax.swing.*;
import java.awt.*;
import java.util.logging.Logger;
import java.util.function.Consumer;

public class ErrorHandler {
    private static final Logger logger = Logger.getLogger(ErrorHandler.class.getName());
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static int currentRetryAttempt = 0;

    /**
     * Handles connection-related errors consistently
     * @param parentComponent The parent UI component
     * @param message The error message
     * @param canRetry Whether retry is allowed
     * @param retryAction The action to run on retry
     */
    public static void handleConnectionError(Component parentComponent, String message, boolean canRetry, Runnable retryAction) {
        // Log full error details
        logger.severe("Connection error: " + message);
        LocalLogger.logSevere("Connection error: " + message);
        ChatSessionLogger.logSevere("Connection error: " + message);
        
        if (currentRetryAttempt >= MAX_RETRY_ATTEMPTS) {
            handleFatalError(parentComponent, "Maximum retry attempts reached. " + message, new Exception("Max retries exceeded"));
            return;
        }

        String[] options = canRetry ? 
            new String[]{"Retry", "Exit"} :
            new String[]{"Exit"};
        
        int choice = JOptionPane.showOptionDialog(
            parentComponent,
            message + "\n\nDo you want to retry or exit?" + 
            (canRetry ? "\nAttempt " + (currentRetryAttempt + 1) + " of " + MAX_RETRY_ATTEMPTS : ""),
            "Connection Error",
            canRetry ? JOptionPane.YES_NO_OPTION : JOptionPane.DEFAULT_OPTION,
            JOptionPane.ERROR_MESSAGE,
            null,
            options,
            options[0]
        );

        if (canRetry && choice == 0 && retryAction != null) {
            currentRetryAttempt++;
            retryAction.run();
        } else {
            System.exit(1);
        }
    }

    /**
     * Handles fatal errors that require application termination
     */
    public static void handleFatalError(Component parentComponent, String message, Throwable error) {
        String fullMessage = message + "\n\nError details: " + error.getMessage();
        logger.severe("Fatal error: " + fullMessage);
        LocalLogger.logSevere("Fatal error: " + fullMessage);
        ChatSessionLogger.logSevere("Fatal error: " + fullMessage);
        error.printStackTrace();

        JOptionPane.showMessageDialog(
            parentComponent,
            fullMessage,
            "Fatal Error",
            JOptionPane.ERROR_MESSAGE
        );
        
        System.exit(1);
    }

    /**
     * Handles initialization errors with potential recovery options
     */
    public static void handleInitializationError(Component parentComponent, String message, Throwable error, Runnable recoveryAction) {
        String fullMessage = message + "\n\nError details: " + error.getMessage();
        logger.severe("Initialization error: " + fullMessage);
        LocalLogger.logSevere("Initialization error: " + fullMessage);
        
        if (error != null) {
            error.printStackTrace();
            logger.severe("Stack trace:\n" + getStackTraceAsString(error));
        }

        int choice = JOptionPane.showOptionDialog(
            parentComponent,
            fullMessage + "\n\nDo you want to try recovery options or exit?",
            "Initialization Error",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.ERROR_MESSAGE,
            null,
            new String[]{"Try Recovery", "Exit"},
            "Try Recovery"
        );

        if (choice == 0 && recoveryAction != null) {
            recoveryAction.run();
        } else {
            System.exit(1);
        }
    }

    /**
     * Handles warning-level issues that don't require application termination
     */
    public static void showWarning(Component parentComponent, String message, String suggestion) {
        logger.warning("Warning: " + message);
        LocalLogger.logWarning("Warning: " + message);
        
        String fullMessage = message + (suggestion != null ? "\n\nSuggestion: " + suggestion : "");
        JOptionPane.showMessageDialog(
            parentComponent,
            fullMessage,
            "Warning",
            JOptionPane.WARNING_MESSAGE
        );
    }

    /**
     * Resets the retry counter. Should be called when a successful connection is established.
     */
    public static void resetRetryCount() {
        currentRetryAttempt = 0;
    }

    /**
     * Handles encryption/decryption errors with improved context
     */
    public static void handleCryptoError(Component parentComponent, String message, Throwable error, Runnable retryAction) {
        String contextualMessage = "Encryption operation failed: " + message;
        String fullMessage = contextualMessage + "\n\nError details: " + (error != null ? error.getMessage() : "Unknown error");
        
        logger.severe("Crypto error: " + fullMessage);
        LocalLogger.logSevere("Crypto error: " + fullMessage);
        
        if (error != null) {
            error.printStackTrace();
            logger.severe("Stack trace:\n" + getStackTraceAsString(error));
        }

        int choice = JOptionPane.showOptionDialog(
            parentComponent,
            fullMessage + "\n\nDo you want to retry the operation?",
            "Encryption Error",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.ERROR_MESSAGE,
            null,
            new String[]{"Retry", "Cancel"},
            "Retry"
        );

        if (choice == 0 && retryAction != null) {
            retryAction.run();
        }
    }

    /**
     * Handles session-related errors
     */
    public static void handleSessionError(Component parentComponent, String message, Consumer<Boolean> reconnectAction) {
        logger.warning("Session error: " + message);
        LocalLogger.logWarning("Session error: " + message);
        
        int choice = JOptionPane.showOptionDialog(
            parentComponent,
            message + "\n\nWould you like to try reconnecting?",
            "Session Error",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.WARNING_MESSAGE,
            null,
            new String[]{"Reconnect", "Close Session"},
            "Reconnect"
        );

        if (reconnectAction != null) {
            reconnectAction.accept(choice == 0);
        }
    }

    /**
     * Converts a Throwable's stack trace to a string
     */
    private static String getStackTraceAsString(Throwable throwable) {
        if (throwable == null) return "";
        java.io.StringWriter sw = new java.io.StringWriter();
        throwable.printStackTrace(new java.io.PrintWriter(sw));
        return sw.toString();
    }
}
