package de.privateaim.node_message_broker.message.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;

@JsonIgnoreProperties(ignoreUnknown = true)
public final class MessageBroadcastRequest {
    
    @JsonProperty("message")
    @NotNull
    // TODO: add custom validation annotation checking for a message's size
    public byte[] message;
}
