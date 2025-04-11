package de.privateaim.node_message_broker.discovery.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

@Getter
public enum ParticipantType {

    @JsonProperty("default")
    DEFAULT("default"),

    @JsonProperty("aggregator")
    AGGREGATOR("aggregator"),

    @JsonProperty("unknown")
    UNKNOWN("unknown");

    private final String representation;

    ParticipantType(String representation) {
        this.representation = representation;
    }

    public static ParticipantType fromRepresentation(String representation) {
        for (ParticipantType type : values()) {
            if (type.getRepresentation().equals(representation)) {
                return type;
            }
        }
        return UNKNOWN;
    }
}
