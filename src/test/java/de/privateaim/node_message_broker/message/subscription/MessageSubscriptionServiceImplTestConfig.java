package de.privateaim.node_message_broker.message.subscription;

import de.privateaim.node_message_broker.message.subscription.persistence.MessageSubscriptionRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.mongodb.repository.config.EnableReactiveMongoRepositories;

@TestConfiguration
@EnableReactiveMongoRepositories(basePackageClasses = MessageSubscriptionRepository.class)
public class MessageSubscriptionServiceImplTestConfig {

    @Qualifier("MESSAGE_SUBSCRIPTION_SERVICE")
    @Bean
    MessageSubscriptionService messageSubscriptionService(MessageSubscriptionRepository messageSubscriptionRepository) {
        return new MessageSubscriptionServiceImpl(messageSubscriptionRepository);
    }
}
