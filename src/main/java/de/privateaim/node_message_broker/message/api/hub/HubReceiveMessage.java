package de.privateaim.node_message_broker.message.api.hub;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.NonNull;

@JsonIgnoreProperties(ignoreUnknown = true)
public record HubReceiveMessage(
        @JsonProperty("from")
        @NonNull
        HubReceiveMessageSender sender,

        @JsonProperty("data")
        Object message,

        @JsonProperty("metadata")
        @NonNull
        HubMessageMetaData metaData
) {
}
