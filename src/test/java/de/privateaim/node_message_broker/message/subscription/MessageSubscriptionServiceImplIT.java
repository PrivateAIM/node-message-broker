package de.privateaim.node_message_broker.message.subscription;

import de.privateaim.node_message_broker.AbstractBaseDatabaseIT;
import de.privateaim.node_message_broker.message.subscription.persistence.MessageSubscription;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import reactor.test.StepVerifier;

import java.net.MalformedURLException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Objects;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

@ExtendWith(SpringExtension.class)
@DataMongoTest
@ContextConfiguration(classes = MessageSubscriptionServiceImplTestConfig.class)
public class MessageSubscriptionServiceImplIT extends AbstractBaseDatabaseIT {

    private static final String ANALYSIS_ID = "ana-123";
    private static final URI WEBHOOK_URI = URI.create("http://localhost:12345/foo");

    @Autowired
    private MessageSubscriptionServiceImpl messageSubscriptionService;

    @BeforeAll
    static void setUpEnvironment() {
        mongo.start();
    }

    @AfterAll
    static void tearDownEnvironment() {
        mongo.stop();
    }

    @AfterEach
    void reset() {
        wipeDatabase();
    }

    @Test
    void addSubscription_ReturnsNewlyAddedSubscription() throws MalformedURLException {
        StepVerifier.create(messageSubscriptionService.addSubscription(ANALYSIS_ID, WEBHOOK_URI.toURL()))
                .expectNextMatches(sub -> sub.analysisId().equals(ANALYSIS_ID)
                        && sub.webhookUrl().toString().equals(WEBHOOK_URI.toString()))
                .verifyComplete();
    }

    @Test
    void addSubscription_AddingTheSameSubscriptionTwiceCreatedSeparateSubscriptions() throws MalformedURLException {
        var subs = new ArrayList<MessageSubscription>();

        var addCalls = 2;
        for (int i = 0; i < addCalls; i++) {
            StepVerifier.create(messageSubscriptionService.addSubscription(ANALYSIS_ID, WEBHOOK_URI.toURL()))
                    .recordWith(ArrayList::new)
                    .consumeRecordedWith(subs::addAll)
                    .expectNextCount(1)
                    .verifyComplete();
        }

        assertEquals(addCalls, subs.size());
        assertEquals(ANALYSIS_ID, subs.getFirst().analysisId());
        assertEquals(WEBHOOK_URI.toString(), subs.getFirst().webhookUrl().toString());
        assertEquals(ANALYSIS_ID, subs.getLast().analysisId());
        assertEquals(WEBHOOK_URI.toString(), subs.getLast().webhookUrl().toString());
        assertNotEquals(subs.getFirst().id(), subs.getLast().id());
    }

    @Test
    void getSubscription_ReturnsEmptyMonoIfSubscriptionDoesNotExist() {
        StepVerifier.create(messageSubscriptionService.getSubscription(
                        UUID.fromString("fc9243cc-97bb-422c-9c2c-57165ed85754")))
                .expectNext()
                .verifyComplete();
    }

    @Test
    void getSubscription_ReturnsSubscriptionIfItExists() throws MalformedURLException {
        var subs = new ArrayList<MessageSubscription>();

        StepVerifier.create(messageSubscriptionService.addSubscription(ANALYSIS_ID, WEBHOOK_URI.toURL()))
                .recordWith(ArrayList::new)
                .consumeRecordedWith(subs::addAll)
                .expectNextCount(1)
                .verifyComplete();

        StepVerifier.create(messageSubscriptionService.getSubscription(subs.getFirst().id()))
                .expectNext(subs.getFirst())
                .verifyComplete();
    }

    @Test
    void deleteSubscription_ReturnsEmptyMonoIfSubscriptionDoesNotExist() {
        StepVerifier.create(messageSubscriptionService.deleteSubscription(
                        UUID.fromString("f0794951-4230-43a2-810b-5870c4b71853")))
                .expectNext()
                .verifyComplete();
    }

    @Test
    void deleteSubscription_ReturnsEmptyMonoIfSubscriptionGetsDeletedMoreThanOnce() throws MalformedURLException {
        var subs = new ArrayList<MessageSubscription>();
        StepVerifier.create(messageSubscriptionService.addSubscription(ANALYSIS_ID, WEBHOOK_URI.toURL()))
                .recordWith(ArrayList::new)
                .consumeRecordedWith(subs::addAll)
                .expectNextCount(1)
                .verifyComplete();

        var deleteCalls = 2;
        for (int i = 0; i < deleteCalls; i++) {
            StepVerifier.create(messageSubscriptionService.deleteSubscription(subs.getFirst().id()))
                    .expectNext()
                    .verifyComplete();
        }
    }

    @Test
    void listSubscriptions_ReturnsEmptyFluxIfNoSubscriptionExistsForAnalysis() {
        StepVerifier.create(messageSubscriptionService.listSubscriptions("does-not-exist"))
                .expectNextCount(0)
                .verifyComplete();
    }

    @Test
    void listSubscriptions_ReturnsAllSubscriptionsForAnalysis() throws MalformedURLException {
        var addedSubs = new ArrayList<MessageSubscription>();

        var addCalls = 2;
        for (int i = 0; i < addCalls; i++) {
            StepVerifier.create(messageSubscriptionService.addSubscription(ANALYSIS_ID, WEBHOOK_URI.toURL()))
                    .recordWith(ArrayList::new)
                    .consumeRecordedWith(addedSubs::addAll)
                    .expectNextCount(1)
                    .verifyComplete();
        }

        var queriedSubs = new ArrayList<MessageSubscription>();
        StepVerifier.create(messageSubscriptionService.listSubscriptions(ANALYSIS_ID))
                .recordWith(ArrayList::new)
                .thenConsumeWhile(Objects::nonNull, queriedSubs::add)
                .verifyComplete();

        assertEquals(addCalls, queriedSubs.size());
        assertEquals(addedSubs, queriedSubs);
    }
}
