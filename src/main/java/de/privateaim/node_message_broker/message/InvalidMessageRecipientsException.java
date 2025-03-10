package de.privateaim.node_message_broker.message;

import jakarta.validation.constraints.NotNull;

/**
 * Indicates that given message recipients are invalid.
 */
public class InvalidMessageRecipientsException extends Exception {
    public InvalidMessageRecipientsException(@NotNull String message) {
        super(message);
    }
}
