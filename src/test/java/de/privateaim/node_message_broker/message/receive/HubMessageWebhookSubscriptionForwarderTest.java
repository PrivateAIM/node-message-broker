package de.privateaim.node_message_broker.message.receive;

import de.privateaim.node_message_broker.message.subscription.MessageSubscriptionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.util.UUID;

@ExtendWith(MockitoExtension.class)
public final class HubMessageWebhookSubscriptionForwarderTest {

    private static final String TEST_ANALYSIS_ID = "test-analysis-id";
    private static final ReceiveMessage TEST_MESSAGE = ReceiveMessage.builder()
            .sentFrom(new ReceiveMessageSender("some-id"))
            .withPayload("foo".getBytes())
            .inContext(new ReceiveMessageContext(
                    UUID.randomUUID(),
                    TEST_ANALYSIS_ID))
            .build();

    @Mock
    private WebClient webClient;

    @Mock
    private MessageSubscriptionService subscriptionService;

    private HubMessageWebhookSubscriptionForwarder hubMessageWebHookSubscriptionForwarder;

    @BeforeEach
    public void setUp() {
        hubMessageWebHookSubscriptionForwarder = new HubMessageWebhookSubscriptionForwarder(
                webClient,
                subscriptionService,
                new HubMessageWebhookSubscriptionForwarderConfig.Builder()
                        .withMaxRetries(5)
                        .withRetryDelayMs(100)
                        .build());
    }

    @AfterEach
    public void tearDown() {
        Mockito.reset(webClient, subscriptionService);
    }

    @Test
    public void messageMustNotBeNull() {
        StepVerifier.create(hubMessageWebHookSubscriptionForwarder.consume(null))
                .expectError(MessageConsumerException.class)
                .verify();
    }

    @Test
    public void messageDoesNotGetForwardedWithoutAnySubscription() {
        Mockito.doReturn(Flux.empty()).when(subscriptionService)
                .listSubscriptions(TEST_ANALYSIS_ID);

        StepVerifier.create(hubMessageWebHookSubscriptionForwarder.consume(TEST_MESSAGE))
                .expectComplete()
                .verify();

        Mockito.verify(webClient, Mockito.never()).post();
    }
}
