package de.privateaim.node_message_broker.common.hub;

/**
 * Signals that a request to a central Hub service has failed due to a server error.
 * An operation causing this error might be retryable.
 */
public class HubCoreServerException extends RuntimeException {
    public HubCoreServerException(String message) {
        super(message);
    }
}
