package de.privateaim.node_message_broker.message.subscription;

import de.privateaim.node_message_broker.message.subscription.persistence.MessageSubscription;
import de.privateaim.node_message_broker.message.subscription.persistence.MessageSubscriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.net.URL;
import java.util.UUID;

import static java.util.Objects.requireNonNull;

/**
 * Default implementation of {@link MessageSubscriptionService}.
 */
@Service
@RequiredArgsConstructor
public final class MessageSubscriptionServiceImpl implements MessageSubscriptionService {

    private final MessageSubscriptionRepository messageSubscriptionRepository;

    public Mono<MessageSubscription> addSubscription(String analysisId, URL webhookUrl) {
        requireNonNull(analysisId, "analysis ID must not be null");
        requireNonNull(webhookUrl, "webhook URL must not be null");

        var newSubscription = new MessageSubscription(UUID.randomUUID(), analysisId, webhookUrl);
        return messageSubscriptionRepository.save(newSubscription);
    }

    public Mono<MessageSubscription> getSubscription(UUID subscriptionId) {
        requireNonNull(subscriptionId, "subscription ID must not be null");

        return messageSubscriptionRepository.findById(subscriptionId);
    }

    public Mono<Void> deleteSubscription(UUID subscriptionId) {
        requireNonNull(subscriptionId, "subscription ID must not be null");

        return messageSubscriptionRepository.deleteById(subscriptionId);
    }

    public Flux<MessageSubscription> listSubscriptions(String analysisId) {
        requireNonNull(analysisId, "analysis ID must not be null");

        return messageSubscriptionRepository.findAllByAnalysisId(analysisId);
    }
}
