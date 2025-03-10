package de.privateaim.node_message_broker.message.receive;

import jakarta.validation.constraints.NotNull;

/**
 * Indicates that a final error occurred while forwarding a message to an external downstream system. The error is
 * final since all countermeasures (e.g. retrying to forward the message) eventually didn't succeed.
 */
public class HubMessageForwardUndeliverableException extends RuntimeException {
    public HubMessageForwardUndeliverableException(@NotNull String message) {
        super(message);
    }
}
