package de.privateaim.node_message_broker.message.api.hub;

import org.json.JSONPropertyName;

import java.util.List;

/**
 * Representation of an outgoing message that is sent to other nodes via the Hub.
 *
 * @param recipient information about the recipient
 * @param message   payload of the message
 * @param metadata  metadata associated with the message
 */
public record OutgoingHubMessage(
        @JSONPropertyName("to")
        List<HubMessageRecipient> recipient,

        @JSONPropertyName("data")
        String message,

        @JSONPropertyName("metadata")
        HubMessageMetadata metadata
) {
}
