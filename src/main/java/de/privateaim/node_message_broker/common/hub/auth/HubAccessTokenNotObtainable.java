package de.privateaim.node_message_broker.common.hub.auth;

/**
 * Signals that an access token could not be obtained from the hub's auth service.
 */
public final class HubAccessTokenNotObtainable extends Exception {
    public HubAccessTokenNotObtainable(String message) {
        super(message);
    }
}
