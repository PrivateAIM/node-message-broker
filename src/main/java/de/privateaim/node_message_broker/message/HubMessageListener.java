package de.privateaim.node_message_broker.message;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.privateaim.node_message_broker.message.api.hub.HubReceiveMessage;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;

import static java.util.Objects.requireNonNull;

@Slf4j
public final class HubMessageListener implements MessageListener {

    private final MessageConsumer consumer;

    private final ObjectMapper jsonMapper;

    public HubMessageListener(MessageConsumer consumer, ObjectMapper jsonMapper) {
        this.consumer = requireNonNull(consumer, "consumer must not be null");
        this.jsonMapper = requireNonNull(jsonMapper, "json mapper must not be null");
    }

    @Override
    public void onMessage(byte[] message) {
        try {
            consumer.consume(
                    jsonMapper.readValue(message, HubReceiveMessage.class));
        } catch (IOException e) {
            log.error("cannot parse Hub message and will drop it", e);
        }
    }
}
