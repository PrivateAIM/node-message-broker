package de.privateaim.node_message_broker.discovery;

import de.privateaim.node_message_broker.common.hub.HubClient;
import de.privateaim.node_message_broker.discovery.api.Participant;
import de.privateaim.node_message_broker.discovery.api.ParticipantType;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;

@Service
@RequiredArgsConstructor
public final class DiscoveryService {

    @NonNull
    private final HubClient hubClient;

    @Value("${app.hub.auth.robotId}")
    private String selfRobotId; // robot id of the node running this instance of the message broker

    Mono<List<Participant>> discoverAllParticipantsOfAnalysis(String analysisId) {
        return hubClient.fetchAnalysisNodes(analysisId)
                .map(analysisNodes -> analysisNodes.stream().map(analysisNode -> new Participant(
                        analysisNode.node.robotId,
                        ParticipantType.fromRepresentation(analysisNode.node.type)
                )).toList());
    }

    Mono<Participant> discoverSelfInAnalysis(String analysisId) {
        return discoverAllParticipantsOfAnalysis(analysisId)
                .map(participants -> participants.stream()
                        .filter(p -> p.nodeId.equals(selfRobotId))
                        .reduce((p1, p2) -> {
                            throw new IllegalStateException("more than one participant after filtering");
                        })
                        .orElseThrow(
                                () -> new IllegalStateException("no participant found")));
        // TODO: use proper exceptions here
    }
}
