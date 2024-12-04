package de.privateaim.node_message_broker.common.hub.auth;

/**
 * Signals that a request to obtain an access token from the hub failed.
 */
final class HubAuthException extends Exception {
    public HubAuthException(String message) {
        super(message);
    }
}
