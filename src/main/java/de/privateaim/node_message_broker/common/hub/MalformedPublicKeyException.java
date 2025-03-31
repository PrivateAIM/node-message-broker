package de.privateaim.node_message_broker.common.hub;

/**
 * Indicates that the obtained public key is malformed and thus cannot be read.
 */
public final class MalformedPublicKeyException extends Exception {
    public MalformedPublicKeyException(String message, Throwable cause) {
        super(message, cause);
    }
}
