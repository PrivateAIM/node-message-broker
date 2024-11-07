package de.privateaim.node_message_broker.message.subscription.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;

import java.net.URL;

@JsonIgnoreProperties(ignoreUnknown = true)
public final class AddMessageSubscriptionRequest {

    @JsonProperty("webhookUrl")
    @NotNull
    public URL webhookUrl;
}
