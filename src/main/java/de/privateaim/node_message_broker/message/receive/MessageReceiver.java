package de.privateaim.node_message_broker.message.receive;

import jakarta.validation.constraints.NotNull;
import reactor.core.publisher.Mono;

/**
 * Represents a receiver capable of processing a generic message from other nodes.
 */
@FunctionalInterface
public interface MessageReceiver {

    /**
     * Processes an incoming message.
     *
     * @param message the message to be processed
     * @return A completed {@link Mono}.
     */
    Mono<Void> processMessage(@NotNull byte[] message);
}
