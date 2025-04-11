package de.privateaim.node_message_broker.discovery;

import jakarta.validation.constraints.NotNull;

/**
 * Indicates that there was a logical conflict during discovery.
 */
public class DiscoveryConflictException extends Exception {
    public DiscoveryConflictException(@NotNull String message) {
        super(message);
    }
}
