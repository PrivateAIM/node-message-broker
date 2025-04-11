package de.privateaim.node_message_broker.message.emit;

import de.privateaim.node_message_broker.message.api.hub.HubMessageMetadata;
import de.privateaim.node_message_broker.message.api.hub.HubMessageRecipient;
import de.privateaim.node_message_broker.message.api.hub.OutgoingHubMessage;
import io.socket.client.Socket;
import jakarta.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static java.util.Objects.requireNonNull;

/**
 * An emitter for emitting messages to other nodes via the Hub.
 */
@Slf4j
public final class HubMessageEmitter implements MessageEmitter<EmitMessage> {

    private final Socket socket;
    private final List<Function<EmitMessage, Mono<EmitMessage>>> middlewares;

    // This has to be equal to what the server is listening for on the hub instance.
    private static final String SOCKET_SEND_MESSAGE_IDENTIFIER = "send";

    /**
     * Creates a new {@link HubMessageEmitter} instance.
     *
     * @param socket {@link Socket} instance for emitting messages
     */
    public HubMessageEmitter(@NotNull Socket socket) {
        this.socket = requireNonNull(socket, "socket must not be null");
        middlewares = new ArrayList<>();
    }

    /**
     * Registers a middleware. Registered middlewares are invoked in order before emitting a message via
     * {@link #emitMessage(EmitMessage)}.
     *
     * @param messageEmitterMiddleware the middleware
     */
    public void registerMiddleware(@NotNull Function<EmitMessage, Mono<EmitMessage>> messageEmitterMiddleware) {
        middlewares.add(
                requireNonNull(messageEmitterMiddleware, "middleware must not be null"));
    }

    /**
     * Attempts to emit the given message.
     *
     * @param message the message that shall get emitted
     * @return A completed {@link Mono}. Might return an {@link EmitMessageException} as an error state if a message
     * could not get emitted.
     */
    @Override
    public Mono<Void> emitMessage(EmitMessage message) {
        log.info("emitting message `{}` to node `{}`", message.context().messageId(), message.recipient().nodeRobotId());

        return generateMiddlewareStack()
                .apply(message)
                .onErrorMap(err ->
                        new EmitMessageException("failed to apply middlewares before emitting message", err))
                .map(preprocessedMessage -> new OutgoingHubMessage(
                        List.of(new HubMessageRecipient("robot", preprocessedMessage.recipient().nodeRobotId())),
                        new String(preprocessedMessage.payload()),
                        new HubMessageMetadata(
                                preprocessedMessage.context().messageId(),
                                preprocessedMessage.context().analysisId())))
                .map((msg) -> socket.emit(SOCKET_SEND_MESSAGE_IDENTIFIER, new JSONObject(msg)))
                .onErrorMap(err -> !(err instanceof EmitMessageException),
                        err -> new EmitMessageException("an unexpected error occurred", err))
                .then(Mono.empty());
    }

    // TODO: do this in the register function - improves performance since the chain does not have to be built every time
    //       it's invoked
    private Function<EmitMessage, Mono<EmitMessage>> generateMiddlewareStack() {
        // function composition for middlewares with in-between unwrapping of result values in a reactive way.
        return middlewares.stream()
                .reduce(
                        Mono::just,
                        (curr, next) ->
                                (msg) -> curr.apply(msg).flatMap(next));
    }
}
