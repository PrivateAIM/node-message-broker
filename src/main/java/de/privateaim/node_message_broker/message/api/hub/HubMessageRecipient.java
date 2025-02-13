package de.privateaim.node_message_broker.message.api.hub;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.NonNull;
import org.json.JSONPropertyName;

/**
 * Representation of a message's recipient.
 *
 * @param recipientType type of the recipient (user, robot)
 * @param nodeRobotId   unique identifier of the receiving node's account
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record HubMessageRecipient(

        // Makes use of annotations from 2 different libraries (org.json and jackson).
        // This is intended since the socket.io client that is eventually using an instance of this record requires
        // it to be serialized using org.json. However, since this record is also used for reading and socket.io not
        // mandating the use of a specific deserializer, jackson is used for simplicity and its richer feature set.

        // TODO: check making the recipient type an enum
        @JsonProperty("type")
        @NonNull
        @JSONPropertyName("type")
        String recipientType,

        @JsonProperty("id")
        @NonNull
        @JSONPropertyName("id")
        String nodeRobotId
) {
}
