package de.privateaim.node_message_broker.message.api.hub;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.NonNull;
import org.json.JSONPropertyName;

import java.util.UUID;

/**
 * Representation of metadata as part of a message.
 *
 * @param messageId  unique identifier of the message for tracing purposes
 * @param analysisId unique identifier of the analysis that the message is associated with
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record HubMessageMetadata(

        // Makes use of annotations from 2 different libraries (org.json and jackson).
        // This is intended since the socket.io client that is eventually using an instance of this record requires
        // it to be serialized using org.json. However, since this record is also used for reading and socket.io not
        // mandating the use of a specific deserializer, jackson is used for simplicity and its richer feature set.

        @JsonProperty("messageId")
        @NonNull
        @JSONPropertyName("messageId")
        UUID messageId,

        @JsonProperty("analysisId")
        @NonNull
        @JSONPropertyName("analysisId")
        String analysisId
) {
}
