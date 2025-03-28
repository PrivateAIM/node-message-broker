package de.privateaim.node_message_broker.discovery;

import jakarta.validation.constraints.NotNull;

/**
 * Indicates that this node (self) could not get discovered.
 */
public class UndiscoverableSelfException extends Exception {
    public UndiscoverableSelfException(@NotNull String message) {
        super(message);
    }
}
