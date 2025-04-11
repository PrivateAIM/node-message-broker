package de.privateaim.node_message_broker.message.subscription;

import de.privateaim.node_message_broker.message.subscription.api.AddMessageSubscriptionRequest;
import de.privateaim.node_message_broker.message.subscription.api.MessageSubscriptionResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

import static java.util.Objects.requireNonNull;

/**
 * REST controller for managing message subscriptions.
 */
@RestController
@RequestMapping("/analyses/{analysisId}/messages/subscriptions")
public final class MessageSubscriptionController {

    private final MessageSubscriptionServiceImpl messageSubscriptionService;

    public MessageSubscriptionController(@NotNull MessageSubscriptionServiceImpl messageSubscriptionService) {
        this.messageSubscriptionService = requireNonNull(messageSubscriptionService, "subscription service must not be null");
    }

    @GetMapping
    Mono<ResponseEntity<List<MessageSubscriptionResponse>>> listSubscriptions(@PathVariable String analysisId) {
        if (analysisId.isBlank()) {
            return Mono.just(ResponseEntity.badRequest().build());
        }

        return messageSubscriptionService.listSubscriptions(analysisId)
                .map(subscription -> new MessageSubscriptionResponse(
                        subscription.id(),
                        subscription.analysisId(),
                        subscription.webhookUrl()
                ))
                .collectList()
                .map(ResponseEntity::ok);
    }

    @GetMapping("/{subscriptionId}")
    Mono<ResponseEntity<MessageSubscriptionResponse>> getSingleSubscription(
            @PathVariable String analysisId,
            @PathVariable UUID subscriptionId) {

        if (analysisId.isBlank()) {
            return Mono.just(ResponseEntity.badRequest().build());
        }

        return messageSubscriptionService.getSubscription(subscriptionId)
                .map(subscription -> new MessageSubscriptionResponse(
                        subscription.id(),
                        subscription.analysisId(),
                        subscription.webhookUrl()))
                .map(ResponseEntity::ok)
                .switchIfEmpty(Mono.just(ResponseEntity.notFound().build()));
    }

    @PostMapping
    Mono<ResponseEntity<MessageSubscriptionResponse>> addNewSubscription(
            UriComponentsBuilder uriComponentsBuilder,
            @PathVariable String analysisId,
            @Valid @RequestBody AddMessageSubscriptionRequest subscriptionRequest) {

        if (analysisId.isBlank()) {
            return Mono.just(ResponseEntity.badRequest().build());
        }

        return messageSubscriptionService.addSubscription(analysisId, subscriptionRequest.getWebhookUrl())
                .map(subscription -> new MessageSubscriptionResponse(
                        subscription.id(),
                        subscription.analysisId(),
                        subscription.webhookUrl()))
                .map(subscription -> {
                    var subscriptionResourceUri = uriComponentsBuilder.replacePath(null)
                            .replaceQuery(null)
                            .path("/analyses/%s/messages/subscriptions/%s".formatted(analysisId,
                                    subscription.getSubscriptionId()))
                            .build().toUri();

                    return ResponseEntity.created(subscriptionResourceUri)
                            .body(subscription);
                });
    }

    @DeleteMapping("/{subscriptionId}")
    Mono<ResponseEntity<Void>> deleteSubscription(@PathVariable String analysisId, @PathVariable UUID subscriptionId) {
        if (analysisId.isBlank()) {
            return Mono.just(ResponseEntity.badRequest().build());
        }

        return messageSubscriptionService.deleteSubscription(subscriptionId)
                .then(Mono.just(ResponseEntity.noContent().build()));
    }
}
