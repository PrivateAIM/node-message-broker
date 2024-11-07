package de.privateaim.node_message_broker.message.subscription.persistence;

import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import reactor.core.publisher.Flux;

import java.util.UUID;

public interface MessageSubscriptionRepository extends ReactiveMongoRepository<MessageSubscription, UUID> {

    Flux<MessageSubscription> findAllByAnalysisId(String analysisId);

}
