package de.privateaim.node_message_broker.message.subscription;

import de.privateaim.node_message_broker.message.subscription.persistence.MessageSubscription;
import de.privateaim.node_message_broker.message.subscription.persistence.MessageSubscriptionRepository;
import jakarta.validation.constraints.NotNull;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.net.URL;
import java.util.UUID;

import static java.util.Objects.requireNonNull;

/**
 * A service dealing with subscriptions for messages.
 */
@Service
public final class MessageSubscriptionServiceImpl implements MessageSubscriptionService {

    private final MessageSubscriptionRepository messageSubscriptionRepository;

    public MessageSubscriptionServiceImpl(@NotNull MessageSubscriptionRepository messageSubscriptionRepository) {
        this.messageSubscriptionRepository = requireNonNull(messageSubscriptionRepository,
                "message subscription repository cannot be null");
    }

    /**
     * {@inheritDoc}
     */
    public Mono<MessageSubscription> addSubscription(@NotNull String analysisId, @NotNull URL webhookUrl) {
        if (analysisId == null) {
            return Mono.error(new NullPointerException("analysis id must not be null"));
        }
        if (analysisId.isBlank()) {
            return Mono.error(new IllegalArgumentException("analysis id must not be blank"));
        }
        if (webhookUrl == null) {
            return Mono.error(new NullPointerException("webhook url must not be null"));
        }

        return Mono.just(new MessageSubscription(UUID.randomUUID(), analysisId, webhookUrl))
                .flatMap(messageSubscriptionRepository::save);
    }

    /**
     * {@inheritDoc}
     */
    public Mono<MessageSubscription> getSubscription(@NotNull UUID subscriptionId) {
        if (subscriptionId == null) {
            return Mono.error(new NullPointerException("subscription id must not be null"));
        }

        return messageSubscriptionRepository.findById(subscriptionId);
    }

    /**
     * {@inheritDoc}
     */
    public Mono<Void> deleteSubscription(@NotNull UUID subscriptionId) {
        if (subscriptionId == null) {
            return Mono.error(new NullPointerException("subscription id must not be null"));
        }

        return messageSubscriptionRepository.deleteById(subscriptionId);
    }

    /**
     * {@inheritDoc}
     */
    public Flux<MessageSubscription> listSubscriptions(@NotNull String analysisId) {
        if (analysisId == null) {
            return Flux.error(new NullPointerException("analysis id must not be null"));
        }
        if (analysisId.isBlank()) {
            return Flux.error(new IllegalArgumentException("analysis id must not be blank"));
        }

        return messageSubscriptionRepository.findAllByAnalysisId(analysisId);
    }
}
