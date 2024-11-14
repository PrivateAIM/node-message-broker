package de.privateaim.node_message_broker.message.subscription;

import de.privateaim.node_message_broker.message.subscription.persistence.MessageSubscription;
import de.privateaim.node_message_broker.message.subscription.persistence.MessageSubscriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.net.URL;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MessageSubscriptionService {

    private final MessageSubscriptionRepository messageSubscriptionRepository;

    Mono<MessageSubscription> addSubscription(String analysisId, URL webhookUrl) {
        var newSubscription = new MessageSubscription(UUID.randomUUID(), analysisId, webhookUrl);
        return messageSubscriptionRepository.save(newSubscription);
    }

    Mono<MessageSubscription> getSubscription(String analysisId, UUID subscriptionId) {
        return messageSubscriptionRepository.findById(subscriptionId)
                .map(subscription -> {
                    //if (subscription.analysisId().equals(analysisId)) {
                    return subscription;
                    //} else {
                    //    return Mono.error()
                    //}
                });
        //.doOnError(e -> throw new SubscriptionNotFoundException("", e));
    }

    Mono<Void> deleteSubscription(UUID subscriptionId) {
        return messageSubscriptionRepository.deleteById(subscriptionId);
    }

    public Flux<MessageSubscription> listSubscriptions(String analysisId) {
        return messageSubscriptionRepository.findAllByAnalysisId(analysisId);
    }
}
