package de.privateaim.node_message_broker.common.hub.auth;

/**
 * Signals that the response from the Hub's auth service is malformed.
 */
public class HubAuthMalformedResponseException extends Exception {
    public HubAuthMalformedResponseException(String message, Throwable cause) {
        super(message, cause);
    }
}
