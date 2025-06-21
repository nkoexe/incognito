package org.incognito;

import javax.swing.*;
import java.awt.*;
import java.util.logging.Logger;
import java.util.function.Consumer;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ErrorHandler {
    private static final Logger logger = Logger.getLogger(ErrorHandler.class.getName());
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final long RETRY_TIMEOUT_MS = 5000; // 5 seconds timeout between retries
    private static final Map<String, RetryContext> retryContexts = new ConcurrentHashMap<>();

    private static class RetryContext {
        int attempts;
        long lastAttemptTime;

        RetryContext() {
            this.attempts = 0;
            this.lastAttemptTime = 0;
        }

        boolean canRetry() {
            return attempts < MAX_RETRY_ATTEMPTS && 
                   (System.currentTimeMillis() - lastAttemptTime) >= RETRY_TIMEOUT_MS;
        }

        void incrementAttempt() {
            attempts++;
            lastAttemptTime = System.currentTimeMillis();
        }
    }

    /**
     * Handles connection-related errors consistently
     * @param parentComponent The parent UI component
     * @param message The error message
     * @param canRetry Whether retry is allowed
     * @param retryAction The action to run on retry
     */
    public static void handleConnectionError(Component parentComponent, String message, boolean canRetry, Runnable retryAction) {
        String contextKey = Thread.currentThread().getName();
        RetryContext context = retryContexts.computeIfAbsent(contextKey, k -> new RetryContext());
        
        // Log full error details
        logger.severe("Connection error: " + message);
        LocalLogger.logSevere("Connection error: " + message);
        ChatSessionLogger.logSevere("Connection error: " + message);
        
        if (!context.canRetry()) {
            handleFatalError(parentComponent, "Maximum retry attempts reached or too frequent retries. " + message, 
                           new Exception("Retry limits exceeded"));
            return;
        }

        String[] options = canRetry ? 
            new String[]{"Retry", "Exit"} :
            new String[]{"Exit"};
        
        int choice = JOptionPane.showOptionDialog(
            parentComponent,
            message + "\n\nDo you want to retry or exit?" + 
            (canRetry ? "\nAttempt " + (context.attempts + 1) + " of " + MAX_RETRY_ATTEMPTS : ""),
            "Connection Error",
            canRetry ? JOptionPane.YES_NO_OPTION : JOptionPane.DEFAULT_OPTION,
            JOptionPane.ERROR_MESSAGE,
            null,
            options,
            options[0]
        );

        if (canRetry && choice == 0 && retryAction != null) {
            context.incrementAttempt();
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

    public enum InitErrorSeverity {
        FATAL,      // No recovery possible, must exit
        SEVERE,     // Recovery possible but risky
        MODERATE,   // Recovery likely to succeed
        MILD       // Recovery should be straightforward
    }

    /**
     * Handles initialization errors with context-aware recovery options
     */
    public static void handleInitializationError(Component parentComponent, String message, Throwable error, 
                                               InitErrorSeverity severity, Runnable recoveryAction,
                                               Runnable alternativeAction) {
        String fullMessage = message + "\n\nError details: " + error.getMessage();
        logger.severe("Initialization error (" + severity + "): " + fullMessage);
        LocalLogger.logSevere("Initialization error (" + severity + "): " + fullMessage);
        
        if (error != null) {
            error.printStackTrace();
            logger.severe("Stack trace:\n" + getStackTraceAsString(error));
        }

        String[] options;
        String defaultOption;
        switch (severity) {
            case FATAL:
                options = new String[]{"Exit"};
                defaultOption = "Exit";
                break;
            case SEVERE:
                options = alternativeAction != null ? 
                    new String[]{"Try Recovery", "Alternative Action", "Exit"} :
                    new String[]{"Try Recovery", "Exit"};
                defaultOption = "Exit";
                break;
            case MODERATE:
            case MILD:
                options = alternativeAction != null ?
                    new String[]{"Try Recovery", "Alternative Action", "Exit"} :
                    new String[]{"Try Recovery", "Exit"};
                defaultOption = "Try Recovery";
                break;
            default:
                options = new String[]{"Try Recovery", "Exit"};
                defaultOption = "Try Recovery";
        }

        int choice = JOptionPane.showOptionDialog(
            parentComponent,
            fullMessage + "\n\nHow would you like to proceed?",
            "Initialization Error",
            options.length > 2 ? JOptionPane.YES_NO_CANCEL_OPTION : JOptionPane.YES_NO_OPTION,
            JOptionPane.ERROR_MESSAGE,
            null,
            options,
            defaultOption
        );

        if (severity == InitErrorSeverity.FATAL || 
            (choice == options.length - 1 && severity == InitErrorSeverity.SEVERE)) {
            System.exit(1);
        } else if (choice == 0 && recoveryAction != null) {
            recoveryAction.run();
        } else if (choice == 1 && alternativeAction != null && options.length > 2) {
            alternativeAction.run();
        } else if (choice != 0 || recoveryAction == null) {
            // If no recovery action is available or user didn't choose recovery
            System.exit(1);
        }
    }

    // Overload for backward compatibility
    public static void handleInitializationError(Component parentComponent, String message, Throwable error, Runnable recoveryAction) {
        handleInitializationError(parentComponent, message, error, InitErrorSeverity.MODERATE, recoveryAction, null);
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
        String contextKey = Thread.currentThread().getName();
        retryContexts.remove(contextKey);
    }

    public enum CryptoErrorType {
        KEY_GENERATION_ERROR,
        ENCRYPTION_ERROR,
        DECRYPTION_ERROR,
        KEY_EXCHANGE_ERROR,
        UNKNOWN_ERROR
    }

    /**
     * Handles encryption/decryption errors with improved context and specific recovery options
     */
    public static void handleCryptoError(Component parentComponent, String message, Throwable error, 
                                       CryptoErrorType errorType, Runnable retryAction, Runnable regenerateKeyAction) {
        String contextualMessage = "Encryption operation failed: " + message;
        String fullMessage = contextualMessage + "\n\nError details: " + (error != null ? error.getMessage() : "Unknown error");
        
        logger.severe("Crypto error (" + errorType + "): " + fullMessage);
        LocalLogger.logSevere("Crypto error (" + errorType + "): " + fullMessage);
        
        if (error != null) {
            error.printStackTrace();
            logger.severe("Stack trace:\n" + getStackTraceAsString(error));
        }

        String[] options;
        if (errorType == CryptoErrorType.KEY_GENERATION_ERROR || 
            errorType == CryptoErrorType.KEY_EXCHANGE_ERROR) {
            options = new String[]{"Regenerate Keys", "Retry", "Cancel"};
        } else {
            options = new String[]{"Retry", "Cancel"};
        }

        int choice = JOptionPane.showOptionDialog(
            parentComponent,
            fullMessage + "\n\nHow would you like to proceed?",
            "Encryption Error",
            JOptionPane.YES_NO_CANCEL_OPTION,
            JOptionPane.ERROR_MESSAGE,
            null,
            options,
            options[0]
        );

        if (choice == 0) {
            if (options.length == 3 && regenerateKeyAction != null) {
                regenerateKeyAction.run();
            } else if (retryAction != null) {
                retryAction.run();
            }
        } else if (choice == 1 && options.length == 3 && retryAction != null) {
            retryAction.run();
        }
    }

    // Overload for backward compatibility
    public static void handleCryptoError(Component parentComponent, String message, Throwable error, Runnable retryAction) {
        handleCryptoError(parentComponent, message, error, CryptoErrorType.UNKNOWN_ERROR, retryAction, null);
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
     * Handles server-side errors (no UI, just logging and escalation)
     */
    public static void handleServerError(String context, Throwable error, boolean fatal) {
        String message = (context != null ? context + ": " : "") + (error != null ? error.getMessage() : "Unknown error");
        logger.severe("Server error: " + message);
        LocalLogger.logSevere("Server error: " + message);
        ChatSessionLogger.logSevere("Server error: " + message);
        if (error != null) {
            error.printStackTrace();
            logger.severe("Stack trace:\n" + getStackTraceAsString(error));
        }
        if (fatal) {
            logger.severe("Fatal server error, shutting down application.");
            System.exit(1);
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
