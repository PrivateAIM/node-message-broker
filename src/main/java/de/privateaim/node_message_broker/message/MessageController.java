package de.privateaim.node_message_broker.message;

import de.privateaim.node_message_broker.message.api.MessageBroadcastRequest;
import de.privateaim.node_message_broker.message.api.MessageRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import static java.util.Objects.requireNonNull;

/**
 * REST controller for sending messages. Either to selected recipients or to all of them in a broadcast fashion.
 */
@RestController
@RequestMapping("/analyses/{analysisId}/messages")
public final class MessageController {

    private final MessageService messageService;

    public MessageController(@NotNull MessageService messageService) {
        this.messageService = requireNonNull(messageService, "message service must not be null");
    }

    @PostMapping("broadcast")
    Mono<ResponseEntity<Void>> sendBroadcastMessage(@PathVariable String analysisId,
                                                    @Valid @RequestBody MessageBroadcastRequest messageBroadcastRequest) {
        if (analysisId.isBlank()) {
            return Mono.just(ResponseEntity.badRequest().build());
        }

        return messageService.sendBroadcastMessage(analysisId, messageBroadcastRequest)
                .onErrorMap(AnalysisNodesLookupException.class, err ->
                        new ResponseStatusException(HttpStatus.BAD_GATEWAY, err.getMessage(), err))
                // other errors are handled by Spring's default exception handler which will return
                // a 500 status code.
                .thenReturn(ResponseEntity.accepted().build());
    }

    @PostMapping
    Mono<ResponseEntity<Void>> sendMessage(@PathVariable String analysisId,
                                           @Valid @RequestBody MessageRequest messageRequest) {
        if (analysisId.isBlank()) {
            return Mono.just(ResponseEntity.badRequest().build());
        }

        return messageService.sendMessageToSelectedRecipients(analysisId, messageRequest)
                .onErrorMap(AnalysisNodesLookupException.class, err ->
                        new ResponseStatusException(HttpStatus.BAD_GATEWAY, err.getMessage(), err))
                .onErrorMap(InvalidMessageRecipientsException.class, err ->
                        new ResponseStatusException(HttpStatus.BAD_REQUEST, err.getMessage(), err))
                // other errors are handled by Spring's default exception handler which will return
                // a 500 status code.
                .thenReturn(ResponseEntity.accepted().build());
    }
}
