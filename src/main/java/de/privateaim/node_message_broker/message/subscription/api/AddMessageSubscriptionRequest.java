package de.privateaim.node_message_broker.message.subscription.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.net.URL;

/**
 * Schema for an HTTP request's body for adding a single message subscription.
 */
@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
@Builder
public final class AddMessageSubscriptionRequest {

    @JsonProperty("webhookUrl")
    @NotNull
    public URL webhookUrl;
}
