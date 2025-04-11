package de.privateaim.node_message_broker.common.hub;

import de.privateaim.node_message_broker.common.hub.api.AnalysisNode;
import reactor.core.publisher.Mono;

import java.security.interfaces.ECPublicKey;
import java.util.List;

/**
 * Describes a client able to carry out Hub related operations.
 */
public interface HubClient {

    /**
     * Fetches all nodes associated with the given analysis ID from an external system (HUB).
     *
     * @param analysisId Identifies an analysis.
     * @return A set of nodes associated with an analysis.
     */
    Mono<List<AnalysisNode>> fetchAnalysisNodes(String analysisId);

    /**
     * Fetches the public key of a node identified by its ID.
     *
     * @param nodeRobotId unique identifier of a node's robot
     * @return The node's public key.
     */
    Mono<ECPublicKey> fetchPublicKey(String nodeRobotId);
}
