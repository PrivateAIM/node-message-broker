package de.privateaim.node_message_broker.message;

import de.privateaim.node_message_broker.message.api.hub.HubSendMessage;
import reactor.core.publisher.Mono;

public sealed interface MessageEmitter permits HubMessageEmitter {
    Mono<Void> emitMessage(HubSendMessage message);
}
