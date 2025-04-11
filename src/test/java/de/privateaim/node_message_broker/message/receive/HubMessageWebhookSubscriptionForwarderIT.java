package de.privateaim.node_message_broker.message.receive;

import com.github.dockerjava.zerodep.shaded.org.apache.hc.core5.http.HttpStatus;
import de.privateaim.node_message_broker.AbstractBaseDatabaseIT;
import de.privateaim.node_message_broker.message.subscription.MessageSubscriptionService;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import reactor.test.StepVerifier;

import java.io.IOException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(SpringExtension.class)
@DataMongoTest
@ContextConfiguration(classes = HubMessageWebhookSubscriptionForwarderTestConfig.class)
public final class HubMessageWebhookSubscriptionForwarderIT extends AbstractBaseDatabaseIT {

    private static final String ANALYSIS_ID = UUID.randomUUID().toString();
    private static final String TEST_MESSAGE_PAYLOAD = "foo";
    private static final ReceiveMessage TEST_MESSAGE = ReceiveMessage.builder()
            .sentFrom(new ReceiveMessageSender("node-123"))
            .withPayload(TEST_MESSAGE_PAYLOAD.getBytes())
            .inContext(new ReceiveMessageContext(
                    UUID.randomUUID(),
                    ANALYSIS_ID))
            .build();

    @Qualifier("MESSAGE_FORWARD_CTX_SUB_SERVICE")
    @Autowired
    private MessageSubscriptionService subscriptionService;

    @Qualifier("MESSAGE_FORWARD_CTX_FORWARDER_CONFIG")
    @Autowired
    private HubMessageWebhookSubscriptionForwarderConfig config;

    @Qualifier("MESSAGE_FORWARD_CTX_FORWARDER")
    @Autowired
    private MessageConsumer subscriptionForwarder;

    private MockWebServer forwardHttpTargetServerA;
    private MockWebServer forwardHttpTargetServerB;

    @BeforeAll
    static void setUpEnvironment() {
        mongo.start();
    }

    @AfterAll
    static void tearDownEnvironment() {
        mongo.stop();
    }

    @BeforeEach
    void setUp() {
        forwardHttpTargetServerA = new MockWebServer();
        forwardHttpTargetServerB = new MockWebServer();
    }

    @AfterEach
    void tearDown() throws IOException {
        forwardHttpTargetServerA.shutdown();
        forwardHttpTargetServerB.shutdown();
        wipeDatabase();
    }

    @Test
    public void messageGetsForwardedToAllTargetsEvenIfDeliveryToOneFails() throws InterruptedException {
        subscriptionService.addSubscription(
                        ANALYSIS_ID,
                        forwardHttpTargetServerA.url("/").url())
                .block();

        subscriptionService.addSubscription(
                        ANALYSIS_ID,
                        forwardHttpTargetServerB.url("/").url())
                .block();

        for (int i = 0; i < config.maxRetries() + 1; i++) {
            forwardHttpTargetServerA.enqueue(new MockResponse().setResponseCode(HttpStatus.SC_SERVICE_UNAVAILABLE));
        }
        forwardHttpTargetServerB.enqueue(new MockResponse().setResponseCode(HttpStatus.SC_OK));

        StepVerifier.create(subscriptionForwarder.consume(TEST_MESSAGE))
                .expectComplete()
                .verify();

        assertEquals(config.maxRetries() + 1, forwardHttpTargetServerA.getRequestCount());
        for (int i = 0; i < config.maxRetries() + 1; i++) {
            var recordedRequestTargetA = forwardHttpTargetServerA.takeRequest();
            assertEquals(TEST_MESSAGE_PAYLOAD, recordedRequestTargetA.getBody().readUtf8());
            assertEquals(MediaType.APPLICATION_JSON.toString(),
                    recordedRequestTargetA.getHeader(HttpHeaders.CONTENT_TYPE));
        }
        assertEquals(1, forwardHttpTargetServerB.getRequestCount());
        var recordedRequestTargetB = forwardHttpTargetServerB.takeRequest();
        assertEquals(TEST_MESSAGE_PAYLOAD, recordedRequestTargetB.getBody().readUtf8());
        assertEquals(MediaType.APPLICATION_JSON.toString(),
                recordedRequestTargetB.getHeader(HttpHeaders.CONTENT_TYPE));
    }
}
