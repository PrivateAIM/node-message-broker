package de.privateaim.node_message_broker.message.receive;

import de.privateaim.node_message_broker.message.subscription.MessageSubscriptionService;
import de.privateaim.node_message_broker.message.subscription.MessageSubscriptionServiceImpl;
import de.privateaim.node_message_broker.message.subscription.persistence.MessageSubscriptionRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.mongodb.repository.config.EnableReactiveMongoRepositories;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;

@TestConfiguration
@EnableReactiveMongoRepositories(basePackageClasses = MessageSubscriptionRepository.class)
class HubMessageWebhookSubscriptionForwarderTestConfig {

    @Qualifier("MESSAGE_FORWARD_CTX_FORWARDER_CONFIG")
    @Bean
    HubMessageWebhookSubscriptionForwarderConfig config() {
        return new HubMessageWebhookSubscriptionForwarderConfig.Builder()
                .withMaxRetries(5)
                .withRetryDelayMs(10) // keeping it short for tests
                .build();
    }

    @Qualifier("MESSAGE_FORWARD_CTX_WEB_CLIENT")
    @Bean
    WebClient webClient() {
        return WebClient.builder()
                .defaultHeaders(httpHeaders -> httpHeaders.setAccept(List.of(MediaType.APPLICATION_JSON)))
                .build();
    }

    @Qualifier("MESSAGE_FORWARD_CTX_SUB_SERVICE")
    @Bean
    MessageSubscriptionService messageSubscriptionService(MessageSubscriptionRepository messageSubscriptionRepository) {
        return new MessageSubscriptionServiceImpl(messageSubscriptionRepository);
    }

    @Qualifier("MESSAGE_FORWARD_CTX_FORWARDER")
    @Bean
    MessageConsumer hubMessageSubscriptionForwarder(
            @Qualifier("MESSAGE_FORWARD_CTX_WEB_CLIENT") WebClient webClient,
            @Qualifier("MESSAGE_FORWARD_CTX_SUB_SERVICE") MessageSubscriptionService messageSubscriptionService,
            @Qualifier("MESSAGE_FORWARD_CTX_FORWARDER_CONFIG") HubMessageWebhookSubscriptionForwarderConfig config
    ) {
        return new HubMessageWebhookSubscriptionForwarder(webClient, messageSubscriptionService, config);
    }
}
