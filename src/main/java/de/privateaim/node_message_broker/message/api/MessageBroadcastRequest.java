package de.privateaim.node_message_broker.message.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;

/**
 * Schema for an HTTP request's body for sending a message to all recipients using a broadcast.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class MessageBroadcastRequest {

    @JsonProperty("message")
    @NotNull
    public Object message;
}
