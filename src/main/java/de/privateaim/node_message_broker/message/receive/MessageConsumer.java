package de.privateaim.node_message_broker.message.receive;

import jakarta.validation.constraints.NotNull;
import reactor.core.publisher.Mono;

/**
 * Represents a consumer capable of consuming a received message.
 */
@FunctionalInterface
public interface MessageConsumer {

    /**
     * Consumes a given message.
     *
     * @param message the message
     * @return A completed {@link Mono} if the message got consumed successfully, or a {@link Mono} in an error state
     * represented by a {@link MessageConsumerException}.
     */
    Mono<Void> consume(@NotNull ReceiveMessage message);
}
