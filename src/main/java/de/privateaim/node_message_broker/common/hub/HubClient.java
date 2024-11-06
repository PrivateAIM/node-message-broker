package de.privateaim.node_message_broker.common.hub;

import de.privateaim.node_message_broker.common.hub.api.AnalysisNode;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Describes a client that
 */
public sealed interface HubClient permits HttpHubClient {

    /**
     * Will fetch all nodes associated with the given analysis ID from an external system (HUB).
     *
     * @param analysisId Identifies an analysis.
     * @return A set of nodes associated with an analysis.
     */
    Mono<List<AnalysisNode>> fetchAnalysisNodes(String analysisId);
}
