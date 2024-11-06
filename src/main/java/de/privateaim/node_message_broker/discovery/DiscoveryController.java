package de.privateaim.node_message_broker.discovery;

import de.privateaim.node_message_broker.discovery.api.Participant;
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

@RestController
@RequestMapping("/analyses/{analysisId}/participants")
public final class DiscoveryController {

    private final DiscoveryService discoveryService;

    public DiscoveryController(DiscoveryService discoveryService) {
        this.discoveryService = requireNonNull(discoveryService, "discovery service must not be null");
    }

    @GetMapping()
    ResponseEntity<Mono<List<Participant>>> discoverAllParticipants(@PathVariable String analysisId) {
        if (analysisId.isBlank()) {
            // TODO: use ControllerAdvice annotation instead later on...
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "analysis id must not be blank");
        }

        return ResponseEntity.ok(discoveryService.discoverAllParticipantsOfAnalysis(analysisId));
    }

    @GetMapping("/self")
    ResponseEntity<Mono<Participant>> discoverSelf(@PathVariable String analysisId) {
        if (analysisId.isBlank()) {
            // TODO: use ControllerAdvice annotation instead later on...
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "analysis id must not be blank");
        }

        return ResponseEntity.ok(discoveryService.discoverSelfInAnalysis(analysisId));
    }
}
