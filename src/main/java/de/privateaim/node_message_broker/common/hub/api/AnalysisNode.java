package de.privateaim.node_message_broker.common.hub.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
@JsonIgnoreProperties(ignoreUnknown = true)
public final class AnalysisNode {

    @JsonProperty("id")
    public String id;

    @JsonProperty("node_id")
    public String node_id;

    @JsonProperty("node")
    public Node node;

}
