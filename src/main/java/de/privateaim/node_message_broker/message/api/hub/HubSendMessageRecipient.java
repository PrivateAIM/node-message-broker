package de.privateaim.node_message_broker.message.api.hub;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.NonNull;

@JsonIgnoreProperties(ignoreUnknown = true)
public record HubSendMessageRecipient(

        @JsonProperty("type")
        @NonNull
        String recipientType,

        @JsonProperty("id")
        @NonNull
        String nodeRobotId
) {
}
