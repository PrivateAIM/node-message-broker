package de.privateaim.node_message_broker.message.receive;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.privateaim.node_message_broker.message.api.hub.IncomingHubMessage;
import jakarta.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static java.util.Objects.requireNonNull;

/**
 * A receiver for processing incoming messages sent by other nodes via the Hub.
 */
@Slf4j
public final class HubMessageReceiver implements MessageReceiver {

    private final ObjectMapper jsonMapper;
    private final List<Function<ReceiveMessage, Mono<ReceiveMessage>>> middlewares;
    private final List<MessageConsumer> consumers;

    /**
     * Creates a new {@link HubMessageReceiver} instance.
     *
     * @param jsonMapper for deserializing JSON based messages
     */
    public HubMessageReceiver(@NotNull ObjectMapper jsonMapper) {
        this.jsonMapper = requireNonNull(jsonMapper, "json mapper must not be null");
        this.middlewares = new ArrayList<>();
        this.consumers = new ArrayList<>();
    }

    /**
     * Registers a middleware. Registered middlewares are invoked in order before processing a message via
     * {@link #processMessage(byte[])}.
     *
     * @param middleware the middleware
     */
    public void registerMiddleware(Function<ReceiveMessage, Mono<ReceiveMessage>> middleware) {
        middlewares.add(
                requireNonNull(middleware, "middleware must not be null"));
    }

    /**
     * Registers a consumer for processed messages. Registered consumers are invoked for every processed message in
     * order.
     *
     * @param messageConsumer the consumer
     */
    public void registerConsumer(MessageConsumer messageConsumer) {
        consumers.add(
                requireNonNull(messageConsumer, "message consumer must not be null"));
    }

    /**
     * Attempts to process the given message and forward it to any registered consumers.
     *
     * @param message the message to be processed
     * @return A completed {@link Mono}.
     */
    @Override
    public Mono<Void> processMessage(byte[] message) {
        var internalMessage = convertIncomingMessageToInternal(message)
                .flatMap(msg -> generateMiddlewareStack().apply(msg))
                .cache()
                .repeat(consumers.size());

        return Flux.zip(
                        internalMessage,
                        Flux.fromIterable(consumers))
                .map(messageAndConsumer ->
                        messageAndConsumer.getT2()
                                .consume(messageAndConsumer.getT1())
                                .onErrorResume(err -> {
                                    log.error("consumer encountered an error while processing message", err);
                                    return Mono.empty();
                                }))
                .onErrorMap(err -> !(err instanceof ProcessingReceivedMessageException),
                        err -> new ProcessingReceivedMessageException("could not process message from hub", err))
                .doOnError(err -> log.error("message not processable", err))
                .then(Mono.empty());
    }

    private Mono<ReceiveMessage> convertIncomingMessageToInternal(byte[] message) {
        return Mono.defer(() -> {
                    try {
                        return Mono.just(jsonMapper.readValue(message, IncomingHubMessage.class));
                    } catch (IOException e) {
                        return Mono.error(new ProcessingReceivedMessageException("cannot parse received message", e));
                    }
                })
                .map(msg -> ReceiveMessage.builder()
                        .sentFrom(new ReceiveMessageSender(msg.sender().nodeRobotId()))
                        .withPayload(msg.payload().getBytes())
                        .inContext(new ReceiveMessageContext(
                                msg.metadata().messageId(),
                                msg.metadata().analysisId()))
                        .build());
    }

    // TODO: do this in the register function - improves performance since the chain does not have to be built every time
    //       it's invoked
    private Function<ReceiveMessage, Mono<ReceiveMessage>> generateMiddlewareStack() {
        // function composition for middlewares with in-between unwrapping of result values in a reactive way.
        return middlewares.stream()
                .reduce(
                        Mono::just,
                        (curr, next) ->
                                (msg) -> curr.apply(msg).flatMap(next));
    }
}
