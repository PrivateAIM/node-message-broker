package de.privateaim.node_message_broker.discovery.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;

@AllArgsConstructor
public final class Participant {

    @JsonProperty("nodeId")
    public String nodeId;

    @JsonProperty("nodeType")
    public ParticipantType nodeType;
}
