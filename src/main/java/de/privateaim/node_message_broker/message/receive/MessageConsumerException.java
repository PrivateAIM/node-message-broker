package de.privateaim.node_message_broker.message.receive;

import jakarta.validation.constraints.NotNull;

/**
 * Indicates that an error occurred while trying to consume a message.
 */
public class MessageConsumerException extends Exception {
    public MessageConsumerException(@NotNull String message) {
        super(message);
    }

    public MessageConsumerException(@NotNull String message, @NotNull Throwable cause) {
        super(message, cause);
    }
}
