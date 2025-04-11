package de.privateaim.node_message_broker.message.subscription;

import de.privateaim.node_message_broker.message.subscription.persistence.MessageSubscription;
import jakarta.validation.constraints.NotNull;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.net.URL;
import java.util.UUID;

/**
 * Describes a service layer dealing with subscriptions for messages.
 */
public interface MessageSubscriptionService {

    /**
     * Adds a single new subscription for messages of a given analysis.
     *
     * @param analysisId unique identifier of the analysis that this subscription is associated with
     * @param webhookUrl will be called for every new message associated with the given analysis
     * @return the added subscription
     */
    Mono<MessageSubscription> addSubscription(@NotNull String analysisId, @NotNull URL webhookUrl);

    /**
     * Gets information of a single subscription for messages.
     *
     * @param subscriptionId unique identifier of the requested subscription
     * @return the subscription
     */
    Mono<MessageSubscription> getSubscription(@NotNull UUID subscriptionId);

    /**
     * Deletes a single subscription for message.
     *
     * @param subscriptionId unique identifier of the subscription to be deleted
     */
    Mono<Void> deleteSubscription(@NotNull UUID subscriptionId);

    /**
     * Lists all subscriptions for messages associated with a given analysis.
     *
     * @param analysisId unique identifier of the analysis that the subscriptions are associated with
     * @return stream of subscriptions
     */
    Flux<MessageSubscription> listSubscriptions(@NotNull String analysisId);
}
