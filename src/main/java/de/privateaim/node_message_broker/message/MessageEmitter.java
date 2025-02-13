package de.privateaim.node_message_broker.message;

import reactor.core.publisher.Mono;

/**
 * Represents an emitter capable of emitting generic messages to other nodes.
 *
 * @param <T> message type
 */
@FunctionalInterface
public interface MessageEmitter<T> {
    Mono<Void> emitMessage(T message);
}
