package de.privateaim.node_message_broker.message.receive;

import jakarta.validation.constraints.NotNull;

/**
 * Indicates that an error occurred while forwarding a message to an external downstream system.
 */
public class HubMessageForwarderServiceException extends RuntimeException {
    public HubMessageForwarderServiceException(@NotNull String message) {
        super(message);
    }
}
