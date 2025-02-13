package de.privateaim.node_message_broker.message;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.privateaim.node_message_broker.message.api.hub.IncomingHubMessage;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.function.Consumer;

import static java.util.Objects.requireNonNull;

/**
 * Represents a listener capable of listening to raw byte messages as received from other nodes via the Hub.
 */
@Slf4j
public final class HubMessageListener implements MessageListener {

    private final Consumer<IncomingHubMessage> consumer;

    private final ObjectMapper jsonMapper;

    // TODO: add private key for decryption

    public HubMessageListener(Consumer<IncomingHubMessage> consumer, ObjectMapper jsonMapper) {
        this.consumer = requireNonNull(consumer, "consumer must not be null");
        this.jsonMapper = requireNonNull(jsonMapper, "json mapper must not be null");
    }

    @Override
    public void onMessage(byte[] message) {
        try {
            consumer.accept(
                    jsonMapper.readValue(message, IncomingHubMessage.class));
        } catch (IOException e) {
            log.error("cannot parse Hub message and will drop it", e);
        }
    }
}
