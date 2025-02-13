package de.privateaim.node_message_broker.message;

/**
 * Represents a listener capable of listening to raw byte messages as received from other nodes.
 */
@FunctionalInterface
public interface MessageListener {
    void onMessage(byte[] message);
}
