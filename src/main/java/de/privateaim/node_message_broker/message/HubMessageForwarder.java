package de.privateaim.node_message_broker.message;

import de.privateaim.node_message_broker.message.api.hub.HubReceiveMessage;
import de.privateaim.node_message_broker.message.subscription.MessageSubscriptionService;
import de.privateaim.node_message_broker.message.subscription.persistence.MessageSubscription;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.net.URI;
import java.time.Duration;

import static java.util.Objects.requireNonNull;

@Slf4j
public final class HubMessageForwarder implements MessageConsumer {

    private final WebClient webClient;

    private final MessageSubscriptionService subscriptionService;

    public HubMessageForwarder(WebClient webClient, MessageSubscriptionService subscriptionService) {
        this.webClient = requireNonNull(webClient, "web client must not be null");
        this.subscriptionService = requireNonNull(subscriptionService, "subscription service must not be null");
    }

    @Override
    public void consume(HubReceiveMessage message) {
        subscriptionService.listSubscriptions(message.metaData().analysisId())
                .flatMap(subscription -> forwardMessageToSubscriber(subscription, message)
                        .onErrorResume(err -> {
                            log.error("failed to forward message with id '{}' to subscription target at {}",
                                    message.metaData().messageId(), subscription.webhookUrl(), err);
                            return Mono.empty();
                        })
                )
                .subscribe();
    }

    private Mono<Void> forwardMessageToSubscriber(MessageSubscription subscription, HubReceiveMessage message) {
        return webClient.post()
                .uri(URI.create(subscription.webhookUrl().toString()))
                .contentType(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromValue(message.message()))
                .retrieve()
                .onStatus(HttpStatusCode::is5xxServerError,
                        response -> {
                            var err = new HubMessageForwarderServiceException(("subscription target at '%s' encountered" +
                                    " an issue while forwarding message with id '%s'")
                                    .formatted(subscription.webhookUrl(), message.metaData().messageId()));

                            log.warn("retrying message delivery due to: %s", err);
                            return Mono.error(err);
                        })
                .bodyToMono(Void.class)
                .retryWhen(Retry.backoff(5, Duration.ofSeconds(2)).jitter(0.75)
                        .filter(err -> err instanceof HubMessageForwarderServiceException)
                        .onRetryExhaustedThrow(((retryBackoffSpec, retrySignal) ->
                                new HubMessageForwardUndeliverableException(("exhausted maximum retries of '%d'")
                                        .formatted(5)))))
                .doOnSuccess(_void -> log.info("message with id '{}' delivered to subscription target at '{}'",
                        message.metaData().messageId(), subscription.webhookUrl()));
    }
}
