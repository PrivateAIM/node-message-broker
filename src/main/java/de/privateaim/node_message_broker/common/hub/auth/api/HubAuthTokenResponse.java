package de.privateaim.node_message_broker.common.hub.auth.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * Resembles the necessary information returned from the Hub's auth service when requesting an auth token.
 */
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
@JsonIgnoreProperties(ignoreUnknown = true)
public final class HubAuthTokenResponse {

    @JsonProperty("access_token")
    public String accessToken;

}
