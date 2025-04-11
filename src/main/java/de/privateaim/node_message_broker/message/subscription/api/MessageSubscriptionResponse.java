package de.privateaim.node_message_broker.message.subscription.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.net.URL;
import java.util.UUID;

/**
 * Response schema for a single message subscription.
 */
@Getter
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
@Builder
public final class MessageSubscriptionResponse {

    @JsonProperty("subscriptionId")
    public UUID subscriptionId;

    @JsonProperty("analysisId")
    public String analysisId;

    @JsonProperty("webhookUrl")
    public URL webhookUrl;
}
