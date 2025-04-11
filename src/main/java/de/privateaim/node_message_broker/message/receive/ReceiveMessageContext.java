package de.privateaim.node_message_broker.message.receive;

import java.util.UUID;

/**
 * Represents meta information of a message that was received.
 *
 * @param messageId  unique identifier of the message
 * @param analysisId unique identifier of the analysis that this message belongs to
 */
public record ReceiveMessageContext(
        UUID messageId,
        String analysisId
) {
}
