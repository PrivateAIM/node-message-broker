package de.privateaim.node_message_broker.message;

import de.privateaim.node_message_broker.message.api.MessageBroadcastRequest;
import de.privateaim.node_message_broker.message.api.MessageRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import static java.util.Objects.requireNonNull;

@RestController
@RequestMapping("/analyses/{analysisId}/messages")
public final class MessageController {

    private final MessageService messageService;

    public MessageController(MessageService messageService) {
        this.messageService = requireNonNull(messageService, "message service must not be null");
    }

    @PostMapping("broadcast")
    Mono<ResponseEntity<Void>> sendBroadcastMessage(@PathVariable String analysisId,
                                                    @Valid @RequestBody MessageBroadcastRequest messageBroadcastRequest) {
        return messageService.sendBroadcastMessage(analysisId, messageBroadcastRequest.message)
                .then(Mono.fromCallable(() -> ResponseEntity.accepted().build()));
    }

    @PostMapping
    Mono<ResponseEntity<Void>> sendMessage(@PathVariable String analysisId,
                                           @Valid @RequestBody MessageRequest messageRequest) {
        return messageService.sendMessage(analysisId, messageRequest.recipients, messageRequest.message)
                .then(Mono.fromCallable(() -> ResponseEntity.accepted().build()));
    }
}
