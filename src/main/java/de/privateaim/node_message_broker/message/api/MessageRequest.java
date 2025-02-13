package de.privateaim.node_message_broker.message.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * Schema for an HTTP request's body for sending a message to specific recipients.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class MessageRequest {

    @JsonProperty("recipients")
    @NotNull
    @NotEmpty
    public List<String> recipients;

    @JsonProperty("message")
    @NotNull
    public Object message;
}
