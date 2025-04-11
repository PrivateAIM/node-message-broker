package de.privateaim.node_message_broker.message.emit;

import jakarta.validation.constraints.NotNull;

/**
 * Indicates an error while emitting a message.
 */
public final class EmitMessageException extends Exception {
    public EmitMessageException(@NotNull String message, @NotNull Throwable cause) {
        super(message, cause);
    }
}
