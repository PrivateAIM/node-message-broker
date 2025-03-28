package de.privateaim.node_message_broker.discovery;

import de.privateaim.node_message_broker.discovery.api.ParticipantResponse;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.util.List;

import static java.util.Objects.requireNonNull;

/**
 * REST controller for discovery functionality.
 */
@RestController
@RequestMapping("/analyses/{analysisId}/participants")
public final class DiscoveryController {

    private final DiscoveryService discoveryService;

    public DiscoveryController(@NotNull DiscoveryService discoveryService) {
        this.discoveryService = requireNonNull(discoveryService, "discovery service must not be null");
    }

    @GetMapping()
    Mono<ResponseEntity<List<ParticipantResponse>>> discoverAllParticipants(@PathVariable String analysisId) {
        if (analysisId.isBlank()) {
            return Mono.just(ResponseEntity.badRequest().build());
        }

        return discoveryService.discoverAllParticipantsOfAnalysis(analysisId)
                .collectList()
                .map(participants -> participants.stream()
                        .map(p -> new ParticipantResponse(p.nodeRobotId(), p.nodeType()))
                        .toList())
                .map(ResponseEntity::ok)
                .onErrorMap(AnalysisNodesLookupException.class, err ->
                        new ResponseStatusException(HttpStatus.BAD_GATEWAY, err.getMessage(), err));
    }

    @GetMapping("/self")
    Mono<ResponseEntity<ParticipantResponse>> discoverSelf(@PathVariable String analysisId) {
        if (analysisId.isBlank()) {
            return Mono.just(ResponseEntity.badRequest().build());
        }

        return discoveryService.discoverSelfInAnalysis(analysisId)
                .map(p -> new ParticipantResponse(p.nodeRobotId(), p.nodeType()))
                .map(ResponseEntity::ok)
                .onErrorMap(AnalysisNodesLookupException.class, err ->
                        new ResponseStatusException(HttpStatus.BAD_GATEWAY, err.getMessage(), err))
                .onErrorMap(DiscoveryConflictException.class, err ->
                        new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, err.getMessage(), err))
                .onErrorMap(UndiscoverableSelfException.class, err ->
                        new ResponseStatusException(HttpStatus.NOT_FOUND, err.getMessage(), err))
                .switchIfEmpty(Mono.just(ResponseEntity.notFound().build()));
    }
}
