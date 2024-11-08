package de.privateaim.node_message_broker.message.api.hub;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.NonNull;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record HubSendMessage(
        @JsonProperty("to")
        @NonNull
        List<HubSendMessageRecipient> recipients,

        @JsonProperty("data")
        Object message,

        @JsonProperty("metadata")
        HubSendMessageMetaData metaData
) {
}
