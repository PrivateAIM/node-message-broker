package de.privateaim.node_message_broker.message.subscription;

import de.privateaim.node_message_broker.message.subscription.api.AddMessageSubscriptionRequest;
import de.privateaim.node_message_broker.message.subscription.api.MessageSubscriptionResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.UUID;

import static java.util.Objects.requireNonNull;

@RestController
@RequestMapping("/analyses/{analysisId}/messages/subscriptions")
public final class MessageSubscriptionController {

    private final MessageSubscriptionService messageSubscriptionService;

    public MessageSubscriptionController(MessageSubscriptionService messageSubscriptionService) {
        this.messageSubscriptionService = requireNonNull(messageSubscriptionService, "subscription service must not be null");
    }

    @GetMapping
    ResponseEntity<Flux<MessageSubscriptionResponse>> listSubscriptions(@PathVariable String analysisId) {
        var responseBody = messageSubscriptionService.listSubscriptions(analysisId)
                .map(subscription -> new MessageSubscriptionResponse(
                        subscription.id(),
                        subscription.analysisId(),
                        subscription.webhookUrl()
                ));

        return ResponseEntity.ok(responseBody);
    }

    @GetMapping("/{subscriptionId}")
    Mono<ResponseEntity<MessageSubscriptionResponse>> getSingleSubscription(
            @PathVariable String analysisId,
            @PathVariable UUID subscriptionId) {

        return messageSubscriptionService.getSubscription(analysisId, subscriptionId)
                .map(subscription -> {
                    var response = new MessageSubscriptionResponse(
                            subscription.id(),
                            subscription.analysisId(),
                            subscription.webhookUrl()
                    );
                    return ResponseEntity.ok(response);
                });
    }

    @PostMapping
    Mono<ResponseEntity<MessageSubscriptionResponse>> addNewSubscription(
            UriComponentsBuilder uriComponentsBuilder,
            @PathVariable String analysisId,
            @Valid @RequestBody AddMessageSubscriptionRequest subscriptionRequest) {
        return messageSubscriptionService.addSubscription(analysisId, subscriptionRequest.webhookUrl)
                .map(subscription -> {
                    var response = new MessageSubscriptionResponse(
                            subscription.id(),
                            subscription.analysisId(),
                            subscription.webhookUrl()
                    );

                    var subscriptionResourceUri = uriComponentsBuilder.replacePath(null)
                            .replaceQuery(null)
                            .path("/analyses/%s/messages/subscriptions/%s".formatted(analysisId, subscription.id()))
                            .build().toString();

                    return ResponseEntity.created(URI.create(subscriptionResourceUri))
                            .body(response);
                });
    }

    @DeleteMapping("/{subscriptionId}")
    Mono<ResponseEntity<Void>> deleteSubscription(@PathVariable String analysisId, @PathVariable UUID subscriptionId) {
        return messageSubscriptionService.deleteSubscription(subscriptionId)
                .then(Mono.fromCallable(() -> ResponseEntity.noContent().build()));
    }
}
