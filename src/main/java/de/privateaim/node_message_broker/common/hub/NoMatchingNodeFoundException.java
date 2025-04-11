package de.privateaim.node_message_broker.common.hub;

/**
 * Indicates that no matching node could be found on the Hub side.
 */
public final class NoMatchingNodeFoundException extends Exception {
    public NoMatchingNodeFoundException(String message) {
        super(message);
    }
}
