package de.privateaim.node_message_broker.message;

import de.privateaim.node_message_broker.message.api.hub.HubReceiveMessage;

@FunctionalInterface
public interface MessageConsumer {
    void consume(HubReceiveMessage message);
}
