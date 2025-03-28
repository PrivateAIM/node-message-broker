package de.privateaim.node_message_broker.message.subscription;

import de.privateaim.node_message_broker.message.subscription.persistence.MessageSubscription;
import de.privateaim.node_message_broker.message.subscription.persistence.MessageSubscriptionRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
public class MessageSubscriptionServiceImplTest {

    private static String TEST_ANALYSIS_ID;
    private static URL TEST_WEBHOOK_URL;

    @Mock
    private MessageSubscriptionRepository mockedSubscriptionRepository;

    @InjectMocks
    private MessageSubscriptionServiceImpl service;

    @BeforeAll
    public static void setUp() throws URISyntaxException, MalformedURLException {
        TEST_ANALYSIS_ID = "7495ed61-5cda-4290-aed9-dd49de1f31ee";
        TEST_WEBHOOK_URL = new URI("https://my-target.org/my-target-path").toURL();
    }

    @AfterEach
    void tearDown() {
        Mockito.reset(mockedSubscriptionRepository);
    }

    @Nested
    public class AddSubscriptionTests {
        @Test
        public void failsIfAnalysisIdIsNull() {
            StepVerifier.create(service.addSubscription(null, TEST_WEBHOOK_URL))
                    .expectError(NullPointerException.class)
                    .verify();
        }

        @Test
        public void failsIfAnalysisIdIsBlank() {
            StepVerifier.create(service.addSubscription(" ", TEST_WEBHOOK_URL))
                    .expectError(IllegalArgumentException.class)
                    .verify();
        }

        @Test
        public void failsIfWebhookUrlIsNull() {
            StepVerifier.create(service.addSubscription(TEST_ANALYSIS_ID, null))
                    .expectError(NullPointerException.class)
                    .verify();
        }

        @Test
        public void invokesRepository() {
            var addedSub = new MessageSubscription(UUID.randomUUID(), TEST_ANALYSIS_ID, TEST_WEBHOOK_URL);
            Mockito.doReturn(Mono.just(addedSub)).when(mockedSubscriptionRepository).save(any(MessageSubscription.class));

            StepVerifier.create(service.addSubscription(TEST_ANALYSIS_ID, TEST_WEBHOOK_URL))
                    .expectNext(addedSub)
                    .verifyComplete();

            verify(mockedSubscriptionRepository, times(1)).save(any(MessageSubscription.class));
        }
    }

    @Nested
    public class GetSubscriptionTests {

        @Test
        public void failsIfSubscriptionIdIsNull() {
            StepVerifier.create(service.getSubscription(null))
                    .expectError(NullPointerException.class)
                    .verify();
        }

        @Test
        public void invokesRepository() {
            var subscriptionId = UUID.randomUUID();
            var sub = new MessageSubscription(subscriptionId, TEST_ANALYSIS_ID, TEST_WEBHOOK_URL);
            Mockito.doReturn(Mono.just(sub)).when(mockedSubscriptionRepository).findById(subscriptionId);

            StepVerifier.create(service.getSubscription(subscriptionId))
                    .expectNext(sub)
                    .verifyComplete();

            verify(mockedSubscriptionRepository, times(1)).findById(subscriptionId);
        }
    }

    @Nested
    public class DeleteSubscriptionTests {

        @Test
        public void failsIfSubscriptionIdIsNull() {
            StepVerifier.create(service.deleteSubscription(null))
                    .expectError(NullPointerException.class)
                    .verify();
        }

        @Test
        public void invokesRepository() {
            var subscriptionId = UUID.randomUUID();
            Mockito.doReturn(Mono.empty()).when(mockedSubscriptionRepository).deleteById(subscriptionId);

            StepVerifier.create(service.deleteSubscription(subscriptionId))
                    .expectNext()
                    .verifyComplete();

            verify(mockedSubscriptionRepository, times(1)).deleteById(subscriptionId);
        }
    }

    @Nested
    public class ListSubscriptionsTests {

        @Test
        public void failsIfAnalysisIdIsNull() {
            StepVerifier.create(service.listSubscriptions(null))
                    .expectError(NullPointerException.class)
                    .verify();
        }

        @Test
        public void failsIfAnalysisIdIsBlank() {
            StepVerifier.create(service.listSubscriptions(" "))
                    .expectError(IllegalArgumentException.class)
                    .verify();
        }

        @Test
        public void invokesRepository() {
            var subscriptionIdA = UUID.fromString("487a373b-b3b0-48a9-8bdd-a8a663d7a95e");
            var subscriptionIdB = UUID.fromString("fb9517f1-9961-4c1e-9e41-f621389c07f5");
            var subs = List.of(
                    new MessageSubscription(subscriptionIdA, TEST_ANALYSIS_ID, TEST_WEBHOOK_URL),
                    new MessageSubscription(subscriptionIdB, TEST_ANALYSIS_ID, TEST_WEBHOOK_URL)
            );
            Mockito.doReturn(Flux.fromIterable(subs)).when(mockedSubscriptionRepository)
                    .findAllByAnalysisId(TEST_ANALYSIS_ID);

            StepVerifier.create(service.listSubscriptions(TEST_ANALYSIS_ID))
                    .expectNextCount(subs.size())
                    .verifyComplete();

            verify(mockedSubscriptionRepository, times(1)).findAllByAnalysisId(TEST_ANALYSIS_ID);
        }
    }
}
