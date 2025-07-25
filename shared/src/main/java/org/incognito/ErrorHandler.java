package org.incognito;

import javax.swing.*;
import java.awt.*;
import java.util.logging.Logger;
import java.util.function.Consumer;

public class ErrorHandler {
    private static final Logger logger = Logger.getLogger(ErrorHandler.class.getName());

    // --- Helper for logging errors to all loggers ---
    private static void logError(String prefix, String message, Throwable error) {
        String fullMessage = prefix + (message != null ? message : "");
        logger.severe(fullMessage);
        LocalLogger.logSevere(fullMessage);
        ChatSessionLogger.logSevere(fullMessage);
        if (error != null) {
            error.printStackTrace();
            logger.severe("Stack trace:\n" + getStackTraceAsString(error));
        }
    }

    // --- Helper for showing option dialogs ---
    private static int showOptionDialog(Component parent, String message, String title, int optionType, int messageType, String[] options, String defaultOption) {
        return JOptionPane.showOptionDialog(
                parent,
                message,
                title,
                optionType,
                messageType,
                null,
                options,
                defaultOption
        );
    }

    /**
     * Handles connection-related errors consistently
     *
     * @param parentComponent The parent UI component
     * @param message         The error message
     * @param canRetry        Whether retry is allowed
     * @param retryAction     The action to run on retry
     */
    public static void handleConnectionError(Component parentComponent, String message, boolean canRetry, Runnable retryAction) {
        logError("Connection error: ", message, null);
        String[] options = new String[]{"Exit"};
        showOptionDialog(
                parentComponent,
                message,
                "Connection Error",
                JOptionPane.DEFAULT_OPTION,
                JOptionPane.ERROR_MESSAGE,
                options,
                options[0]
        );
        System.exit(1);
    }

    /**
     * Handles fatal errors that require application termination
     */
    public static void handleFatalError(Component parentComponent, String message, Throwable error) {
        logError("Fatal error: ", message, error);
        JOptionPane.showMessageDialog(
                parentComponent,
                message + "\n\nError details: " + (error != null ? error.getMessage() : "Unknown error"),
                "Fatal Error",
                JOptionPane.ERROR_MESSAGE
        );
        System.exit(1);
    }

    public enum InitErrorSeverity {
        FATAL,      // No recovery possible, must exit
        SEVERE,     // Recovery possible but risky
        MODERATE,   // Recovery likely to succeed
        MILD        // Recovery should be straightforward
    }


    public enum CryptoErrorType {
        UNKNOWN_ERROR,
        KEY_GENERATION_ERROR,
        KEY_EXCHANGE_ERROR
    }


    /**
     * Handles initialization errors with context-aware recovery options
     */
    public static void handleInitializationError(Component parentComponent, String message, Throwable error, InitErrorSeverity severity, Runnable recoveryAction, Runnable alternativeAction) {
        logError("Initialization error (" + severity + "): ", message, error);
        String[] options;
        String defaultOption;
        switch (severity) {
            case FATAL:
                options = new String[]{"Exit"};
                defaultOption = "Exit";
                break;
            case SEVERE:
                options = alternativeAction != null ? new String[]{"Try Recovery", "Alternative Action", "Exit"} : new String[]{"Try Recovery", "Exit"};
                defaultOption = "Exit";
                break;
            case MODERATE:
            case MILD:
                options = alternativeAction != null ? new String[]{"Try Recovery", "Alternative Action", "Exit"} : new String[]{"Try Recovery", "Exit"};
                defaultOption = "Try Recovery";
                break;
            default:
                options = new String[]{"Try Recovery", "Exit"};
                defaultOption = "Try Recovery";
        }
        int choice = showOptionDialog(
                parentComponent,
                message + "\n\nError details: " + (error != null ? error.getMessage() : "Unknown error") + "\n\nHow would you like to proceed?",
                "Initialization Error",
                options.length > 2 ? JOptionPane.YES_NO_CANCEL_OPTION : JOptionPane.YES_NO_OPTION,
                JOptionPane.ERROR_MESSAGE,
                options,
                defaultOption
        );
        if (severity == InitErrorSeverity.FATAL || (choice == options.length - 1 && severity == InitErrorSeverity.SEVERE)) {
            System.exit(1);
        } else if (choice == 0 && recoveryAction != null) {
            recoveryAction.run();
        } else if (choice == 1 && alternativeAction != null && options.length > 2) {
            alternativeAction.run();
        } else if (choice != 0 || recoveryAction == null) {
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
     * Handles encryption/decryption errors with improved context and specific recovery options
     */
    public static void handleCryptoError(Component parentComponent, String message, Throwable error, CryptoErrorType errorType, Runnable retryAction, Runnable regenerateKeyAction) {
        logError("Crypto error (" + errorType + "): ", message, error);
        String[] options;
        if (errorType == CryptoErrorType.KEY_GENERATION_ERROR || errorType == CryptoErrorType.KEY_EXCHANGE_ERROR) {
            options = new String[]{"Regenerate Keys", "Retry", "Cancel"};
        } else {
            options = new String[]{"Retry", "Cancel"};
        }
        int choice = showOptionDialog(
                parentComponent,
                "Encryption operation failed: " + message + "\n\nError details: " + (error != null ? error.getMessage() : "Unknown error") + "\n\nHow would you like to proceed?",
                "Encryption Error",
                JOptionPane.YES_NO_CANCEL_OPTION,
                JOptionPane.ERROR_MESSAGE,
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
        int choice = showOptionDialog(
                parentComponent,
                message + "\n\nWould you like to try reconnecting?",
                "Session Error",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE,
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
        logError("Server error: " + (context != null ? context + ": " : ""), null, error);
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
