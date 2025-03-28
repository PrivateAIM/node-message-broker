package de.privateaim.node_message_broker.discovery.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

/**
 * Response schema for a single discovered participant.
 */
@Getter
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
@Builder
public final class ParticipantResponse {

    @JsonProperty("nodeId")
    public String nodeId;

    @JsonProperty("nodeType")
    public ParticipantType nodeType;
}
