package de.privateaim.node_message_broker.message.receive;

import jakarta.validation.constraints.NotNull;

/**
 * Indicates that an error occurred while applying middleware(s) before consuming a received message.
 */
public class ReceiveMiddlewareException extends Exception {

    public ReceiveMiddlewareException(@NotNull String message, @NotNull Throwable cause) {
        super(message, cause);
    }

    public ReceiveMiddlewareException(@NotNull String message) {
        super(message);
    }
}
