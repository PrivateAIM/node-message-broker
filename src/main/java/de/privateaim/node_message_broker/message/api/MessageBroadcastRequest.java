package de.privateaim.node_message_broker.message.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotNull;
import lombok.*;

/**
 * Schema for an HTTP request's body for sending a message to all nodes (recipients) of an analysis using a broadcast.
 */
@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
@Builder
public final class MessageBroadcastRequest {

    @JsonProperty("message")
    @NotNull
    public JsonNode message;
}
