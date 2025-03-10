package de.privateaim.node_message_broker.message;

import jakarta.validation.constraints.NotNull;

/**
 * Indicates that an error occurred while trying to look up analysis nodes on the Hub.
 */
public class AnalysisNodesLookupException extends Exception {
    public AnalysisNodesLookupException(@NotNull String message, @NotNull Throwable cause) {
        super(message, cause);
    }
}
