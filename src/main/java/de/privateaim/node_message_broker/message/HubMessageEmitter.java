package de.privateaim.node_message_broker.message;

import de.privateaim.node_message_broker.message.api.hub.OutgoingHubMessage;
import io.socket.client.Socket;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import reactor.core.publisher.Mono;

import java.util.Objects;

/**
 * An emitter for emitting messages to other nodes via the Hub.
 */
@Slf4j
public final class HubMessageEmitter implements MessageEmitter<OutgoingHubMessage> {

    private final Socket socket;

    // This has to be equal to what the server is listening for on the hub instance.
    private static final String SOCKET_SEND_MESSAGE_IDENTIFIER = "send";

    public HubMessageEmitter(Socket socket) {
        this.socket = Objects.requireNonNull(socket, "socket must not be null");
    }

    @Override
    public Mono<Void> emitMessage(OutgoingHubMessage message) {
        var serializedMessage = new JSONObject(message);

        log.info("emitting message");
        log.info(serializedMessage.toString());

        return Mono.fromCallable(() -> socket.emit(SOCKET_SEND_MESSAGE_IDENTIFIER, serializedMessage))
                .then(Mono.empty());
    }
}
