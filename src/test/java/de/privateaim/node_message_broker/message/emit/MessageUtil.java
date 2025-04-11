package de.privateaim.node_message_broker.message.emit;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

final class MessageUtil {

    private MessageUtil() {
    }

    public static EmitMessage generateBasicMessage(@NotNull byte[] payload) {
        return EmitMessage.builder()
                .sendTo(new EmitMessageRecipient(UUID.randomUUID().toString()))
                .withPayload(payload)
                .inContext(new EmitMessageContext(
                        UUID.randomUUID(),
                        "analysis-123"
                ))
                .build();
    }
}
