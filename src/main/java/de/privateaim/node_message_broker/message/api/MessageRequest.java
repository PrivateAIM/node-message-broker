package de.privateaim.node_message_broker.message.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public final class MessageRequest {

    @JsonProperty("recipients")
    @NotNull
    @Min(1)
    public List<String> recipients;

    @JsonProperty("message")
    @NotNull
    public byte[] message;
}
