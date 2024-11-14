package de.privateaim.node_message_broker.message.api.hub;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.NonNull;

import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record HubMessageMetaData(

        @JsonProperty("messageId")
        @NonNull
        UUID messageId,

        @JsonProperty("analysisId")
        @NonNull
        String analysisId
) {
}
