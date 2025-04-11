package de.privateaim.node_message_broker.common.hub.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * Response container for everything returned from the Hub.
 *
 * @param <T> type of the embedded data
 */
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
@JsonIgnoreProperties(ignoreUnknown = true)
public final class HubResponseContainer<T> {

    @JsonProperty("data")
    public T data;
}
