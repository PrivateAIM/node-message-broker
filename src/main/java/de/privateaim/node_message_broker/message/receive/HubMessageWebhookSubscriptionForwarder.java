package de.privateaim.node_message_broker.message.receive;

import de.privateaim.node_message_broker.message.subscription.MessageSubscriptionService;
import de.privateaim.node_message_broker.message.subscription.persistence.MessageSubscription;
import jakarta.validation.constraints.NotNull;
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
import static java.util.logging.Level.WARNING;
import static reactor.core.publisher.SignalType.ON_ERROR;

/**
 * A consumer for messages received from another node via the Hub.
 * This consumer forwards the received message to subscribed downstream systems using webhooks via HTTP.
 */
@Slf4j
public final class HubMessageWebhookSubscriptionForwarder implements MessageConsumer {

    private final WebClient webClient;
    private final MessageSubscriptionService subscriptionService;
    private final HubMessageWebhookSubscriptionForwarderConfig config;

    /**
     * Constructs a new {@link HubMessageWebhookSubscriptionForwarder} instance.
     *
     * @param webClient           client capable of carrying out HTTP requests
     * @param subscriptionService service for managing message subscriptions
     * @param config              additional configuration for request behavior
     */
    public HubMessageWebhookSubscriptionForwarder(
            @NotNull WebClient webClient,
            @NotNull MessageSubscriptionService subscriptionService,
            @NotNull HubMessageWebhookSubscriptionForwarderConfig config) {
        this.webClient = requireNonNull(webClient, "web client must not be null");
        this.subscriptionService = requireNonNull(subscriptionService, "subscription service must not be null");
        this.config = requireNonNull(config, "config must not be null");
    }

    /**
     * Attempts to consume the given message by forwarding it to all subscribers.
     *
     * @param message the message
     * @return A completed {@link Mono} if the message got consumed successfully, or a {@link Mono} in an error state
     * represented by a {@link MessageConsumerException}.
     */
    @Override
    public Mono<Void> consume(@NotNull ReceiveMessage message) {
        if (message == null) {
            return Mono.error(new MessageConsumerException("message must not be null"));
        }

        return subscriptionService.listSubscriptions(message.context().analysisId())
                .flatMap(subscription -> forwardMessageToSubscriber(subscription, message)
                        .onErrorResume(err -> {
                            log.error("failed to forward message with id '{}' to subscription target at {}",
                                    message.context().messageId(), subscription.webhookUrl(), err);
                            return Mono.empty();
                        }))
                .onErrorMap(err -> !(err instanceof MessageConsumerException),
                        err -> new MessageConsumerException("failed to forward message with id `%s`"
                                .formatted(message.context().messageId()), err))
                .then(Mono.empty());
    }

    private Mono<Void> forwardMessageToSubscriber(MessageSubscription subscription, ReceiveMessage message) {
        return webClient.post()
                .uri(URI.create(subscription.webhookUrl().toString()))
                .contentType(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromValue(message.payload()))
                .retrieve()
                .onStatus(HttpStatusCode::is5xxServerError,
                        response ->
                                response.bodyToMono(String.class)
                                        .or(Mono.just(""))
                                        .map(respMessage ->
                                                new HubMessageForwarderServiceException(("subscription target at '%s' encountered" +
                                                        " an issue while forwarding message with id '%s' and responded with status code" +
                                                        " '%d' | response: '%s'").formatted(
                                                        subscription.webhookUrl(),
                                                        message.context().messageId(),
                                                        response.statusCode().value(),
                                                        respMessage
                                                )))
                                        .log("message.forwarder", WARNING, ON_ERROR)
                                        .flatMap(Mono::error))
                .bodyToMono(Void.class)
                .retryWhen(Retry.backoff(config.maxRetries(), Duration.ofMillis(config.retryDelayMs()))
                        .jitter(0.75)
                        .filter(err -> err instanceof HubMessageForwarderServiceException)
                        .onRetryExhaustedThrow(((retryBackoffSpec, retrySignal) ->
                                new HubMessageForwardUndeliverableException(("exhausted maximum retries of '%d'")
                                        .formatted(config.maxRetries())))))
                .doOnSuccess(_void -> log.info("message with id '{}' delivered to subscription target at '{}'",
                        message.context().messageId(), subscription.webhookUrl()));
    }
}
