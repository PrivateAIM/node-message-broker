package de.privateaim.node_message_broker.discovery;

import de.privateaim.node_message_broker.common.hub.HubClient;
import de.privateaim.node_message_broker.discovery.api.ParticipantType;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import static java.util.Objects.requireNonNull;

/**
 * A service dealing with analysis participant discovery.
 */
@Service
public final class DiscoveryService {

    private final HubClient hubClient;
    private final String selfClientId; // client id of the node running this instance of the message broker

    public DiscoveryService(@NotNull HubClient hubClient,
                            @Qualifier("DISCOVERY_SELF_CLIENT_ID") @NotNull @NotEmpty String selfClientId) {
        this.hubClient = requireNonNull(hubClient, "hub client must not be null");
        requireNonNull(selfClientId, "self client id must not be null");
        if (selfClientId.isBlank()) {
            throw new IllegalArgumentException("self client id must not be blank");
        }
        this.selfClientId = selfClientId;
    }

    /**
     * Discovers all analysis nodes which are participating within an analysis identified by the given analysis
     * identifier.
     *
     * @param analysisId unique identifier of the analysis whose participating nodes shall get discovered
     * @return All participants of the analysis if there are any.
     */
    Flux<Participant> discoverAllParticipantsOfAnalysis(@NotNull String analysisId) {
        if (analysisId == null) {
            return Flux.error(new NullPointerException("analysis id must not be null"));
        }
        if (analysisId.isBlank()) {
            return Flux.error(new IllegalArgumentException("analysis id must not be blank"));
        }

        return hubClient.fetchAnalysisNodes(analysisId)
                .onErrorMap(err -> new AnalysisNodesLookupException("could not look up participating " +
                        "analysis nodes", err))
                .flatMapIterable(analysisNodes -> analysisNodes.stream()
                        .map(analysisNode -> new Participant(
                                analysisNode.node.id,
                                analysisNode.node.clientId,
                                ParticipantType.fromRepresentation(analysisNode.node.type)
                        )).toList())
                .switchIfEmpty(Flux.empty());
    }

    /**
     * Discovers the node running the broker within the list of analysis nodes which are participating within an
     * analysis identified by the given analysis identifier.
     *
     * @param analysisId unique identifier of the analysis within which the node shall get discovered
     * @return The participant representing this node if there is any.
     */
    Mono<Participant> discoverSelfInAnalysis(@NotNull String analysisId) {
        if (analysisId == null) {
            return Mono.error(new NullPointerException("analysis id must not be null"));
        }
        if (analysisId.isBlank()) {
            return Mono.error(new IllegalArgumentException("analysis id must not be blank"));
        }

        return discoverAllParticipantsOfAnalysis(analysisId)
                .onErrorMap(err -> new AnalysisNodesLookupException("could not look up participating " +
                        "analysis nodes", err))
                .collectList()
                .flatMap(participants -> {
                    var selfParticipants = participants.stream()
                            .filter(p -> p.clientId().equals(selfClientId))
                            .toList();

                    if (selfParticipants.isEmpty()) {
                        return Mono.error(new UndiscoverableSelfException("could not discover self since analysis " +
                                "does not have any participants yet"));
                    } else if (selfParticipants.size() > 1) {
                        return Mono.error(new DiscoveryConflictException("there is more than one node that would " +
                                "match the client id `%s`".formatted(selfClientId)));
                    } else {
                        return Mono.just(selfParticipants.getFirst());
                    }
                });
    }
}
