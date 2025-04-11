package de.privateaim.node_message_broker.common.hub;

/**
 * Indicates that an error occurred regarding the public key of a node.
 */
public final class NoPublicKeyException extends Exception {

    public NoPublicKeyException(String message) {
        super(message);
    }
}
