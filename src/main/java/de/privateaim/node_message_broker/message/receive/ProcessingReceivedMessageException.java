package de.privateaim.node_message_broker.message.receive;

import jakarta.validation.constraints.NotNull;

/**
 * Indicates that an error occurred while processing a received message.
 */
public class ProcessingReceivedMessageException extends Exception {
    public ProcessingReceivedMessageException(@NotNull String message, @NotNull Throwable cause) {
        super(message, cause);
    }
}
