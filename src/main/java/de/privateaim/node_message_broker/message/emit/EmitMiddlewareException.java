package de.privateaim.node_message_broker.message.emit;

import jakarta.validation.constraints.NotNull;

/**
 * Indicates an error while applying middleware(s) before emitting a message.
 */
public final class EmitMiddlewareException extends Exception {

    public EmitMiddlewareException(@NotNull String message) {
        super(message);
    }

    public EmitMiddlewareException(@NotNull String message, @NotNull Throwable cause) {
        super(message, cause);
    }
}
