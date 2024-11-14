package de.privateaim.node_message_broker.message;

@FunctionalInterface
public interface MessageListener {
    void onMessage(byte[] message);
}
