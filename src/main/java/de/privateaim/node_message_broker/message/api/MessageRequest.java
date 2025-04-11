package de.privateaim.node_message_broker.message.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.util.List;

/**
 * Schema for an HTTP request's body for sending a message to specific nodes (recipients) of an analysis.
 */
@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
@Builder
public final class MessageRequest {

    @JsonProperty("recipients")
    @NotNull
    @NotEmpty
    public List<String> recipients;

    @JsonProperty("message")
    @NotNull
    public JsonNode message;
}
