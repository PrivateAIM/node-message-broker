package de.privateaim.node_message_broker.common.hub;

/**
 * Signals that the public key of a node could not be obtained from the Hub core services.
 */
public final class HubNodePublicKeyNotObtainable extends RuntimeException {
    public HubNodePublicKeyNotObtainable(String message) {
        super(message);
    }
}
