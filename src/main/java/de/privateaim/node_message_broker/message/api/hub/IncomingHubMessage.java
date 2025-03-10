package de.privateaim.node_message_broker.message.api.hub;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.NonNull;

/**
 * Representation of an incoming message that was sent by another node via the Hub.
 *
 * @param sender   information about the sender
 * @param payload  encrypted of the message
 * @param metadata metadata associated with the message
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record IncomingHubMessage(
        @JsonProperty("from")
        @NonNull
        HubMessageSender sender,

        @JsonProperty("data")
        @NonNull
        String payload,

        @JsonProperty("metadata")
        @NonNull
        HubMessageMetadata metadata
) {
}
