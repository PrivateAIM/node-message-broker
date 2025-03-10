package de.privateaim.node_message_broker.message.emit;

import reactor.core.publisher.Mono;

/**
 * Represents an emitter capable of emitting generic messages to other nodes.
 *
 * @param <T> message type
 */
@FunctionalInterface
public interface MessageEmitter<T> {

    /**
     * Emits a given message.
     *
     * @param message the message that shall get emitted
     * @return A completed {@link Mono} in case the message got emitted successfully, or a {@link Mono} in an error
     * state represented by an {@link EmitMessageException}.
     */
    Mono<Void> emitMessage(T message);
}
