package de.privateaim.node_message_broker.message.subscription.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;

import java.net.URL;
import java.util.UUID;

@AllArgsConstructor
public final class MessageSubscriptionResponse {

    @JsonProperty("subscriptionId")
    public UUID subscriptionId;

    @JsonProperty("analysisId")
    public String analysisId;

    @JsonProperty("webhookUrl")
    public URL webhookUrl;
}
