package de.privateaim.node_message_broker.message.emit;

import java.util.UUID;

/**
 * Represents meta information of a message that shall be sent to a recipient.
 *
 * @param messageId  unique identifier of the message
 * @param analysisId unique identifier of the analysis that this message belongs to
 */
public record EmitMessageContext(
        UUID messageId,
        String analysisId
) {
}
