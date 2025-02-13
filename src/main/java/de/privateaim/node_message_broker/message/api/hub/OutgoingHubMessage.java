package de.privateaim.node_message_broker.message.api.hub;

import org.json.JSONPropertyName;

import java.util.List;

/**
 * Representation of an outgoing message that is sent to other nodes via the Hub.
 *
 * @param recipients information about the recipients
 * @param message    payload of the message
 * @param metadata   metadata associated with the message
 */
public record OutgoingHubMessage(
        @JSONPropertyName("to")
        List<HubMessageRecipient> recipients,

        @JSONPropertyName("data")
        Object message,

        @JSONPropertyName("metadata")
        HubMessageMetadata metadata
) {
}
