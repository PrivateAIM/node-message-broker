package de.privateaim.node_message_broker.message;

import de.privateaim.node_message_broker.message.api.hub.HubSendMessage;
import io.socket.client.Socket;
import reactor.core.publisher.Mono;

import java.util.Objects;

public final class HubMessageEmitter implements MessageEmitter {

    private final Socket socket;

    // This has to be equal to what the server is listening for on the hub instance.
    private static final String SOCKET_SEND_MESSAGE_IDENTIFIER = "send";

    public HubMessageEmitter(Socket socket) {
        this.socket = Objects.requireNonNull(socket, "socket must not be null");
    }

    @Override
    public Mono<Void> emitMessage(HubSendMessage message) {
        return Mono.fromCallable(() -> socket.emit(SOCKET_SEND_MESSAGE_IDENTIFIER, message))
                .then(Mono.empty());
    }
}
