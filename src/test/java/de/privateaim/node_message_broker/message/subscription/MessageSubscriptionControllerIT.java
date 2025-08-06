package de.privateaim.node_message_broker.message.subscription;


import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import de.privateaim.node_message_broker.AbstractBaseDatabaseIT;
import de.privateaim.node_message_broker.message.subscription.api.AddMessageSubscriptionRequest;
import de.privateaim.node_message_broker.message.subscription.api.MessageSubscriptionResponse;
import de.privateaim.node_message_broker.message.subscription.persistence.MessageSubscription;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.autoconfigure.security.reactive.ReactiveSecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.BodyInserters;
import org.testcontainers.shaded.com.fasterxml.jackson.core.JsonProcessingException;
import org.testcontainers.shaded.com.fasterxml.jackson.databind.ObjectMapper;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

// test with security config disabled
@WebFluxTest(controllers = MessageSubscriptionController.class,
        excludeAutoConfiguration = {ReactiveSecurityAutoConfiguration.class})
public class MessageSubscriptionControllerIT extends AbstractBaseDatabaseIT {

    private static final String ANALYSIS_ID = "ana-123";
    private static final UUID SUBSCRIPTION_ID = UUID.fromString("7c0a1126-5e2e-45c0-8177-8a6c56cb046d");
    private static final ObjectMapper JSON = new ObjectMapper();

    @MockitoBean
    private MessageSubscriptionServiceImpl mockedSubscriptionService;

    private WebTestClient client;

    @BeforeEach
    void setUp(ApplicationContext context) {
        client = WebTestClient.bindToApplicationContext(context).build();
    }

    @AfterEach
    void tearDown() {
        Mockito.reset(mockedSubscriptionService);
    }

    @Nested
    public class ListSubscriptionTests {

        @Test
        void returns400IfAnalysisIdIsBlank() {
            client.get().uri("/analyses/ /messages/subscriptions")
                    .exchange()
                    .expectStatus().isBadRequest();
        }

        @Test
        void succeeds() throws IOException {
            var analysisId = UUID.randomUUID().toString();
            var subscriptionIdA = UUID.fromString("487a373b-b3b0-48a9-8bdd-a8a663d7a95e");
            var subscriptionWebhookUrlA = URI.create("http://localhost/test")
                    .toURL();
            var subscriptionWebhookUrlB = URI.create("http://localhost/test2")
                    .toURL();
            var subscriptionIdB = UUID.fromString("fb9517f1-9961-4c1e-9e41-f621389c07f5");
            var subs = List.of(
                    new MessageSubscription(subscriptionIdA, analysisId, subscriptionWebhookUrlA),
                    new MessageSubscription(subscriptionIdB, analysisId, subscriptionWebhookUrlB)
            );
            Mockito.doReturn(Flux.fromIterable(subs)).when(mockedSubscriptionService)
                    .listSubscriptions(analysisId);

            var resp = client.get().uri("/analyses/%s/messages/subscriptions".formatted(analysisId))
                    .exchange()
                    .expectStatus().isOk()
                    .returnResult(MessageSubscriptionResponse.class);

            var clientReceivedSubs = Arrays.asList(
                    JSON.readValue(resp.getResponseBodyContent(), MessageSubscriptionResponse[].class));

            assertEquals(subs.size(), clientReceivedSubs.size());
            assertEquals(subscriptionIdA, subs.getFirst().id());
            assertEquals(analysisId, subs.getFirst().analysisId());
            assertEquals(subscriptionWebhookUrlA, subs.getFirst().webhookUrl());
            assertEquals(subscriptionIdB, subs.getLast().id());
            assertEquals(analysisId, subs.getLast().analysisId());
            assertEquals(subscriptionWebhookUrlB, subs.getLast().webhookUrl());
        }
    }

    @Nested
    public class GetSingleSubscriptionTests {

        @Test
        void returns400IfAnalysisIdIsBlank() {
            client.get().uri("/analyses/ /messages/subscriptions/%s".formatted(SUBSCRIPTION_ID))
                    .exchange()
                    .expectStatus().isBadRequest();
        }

        @Test
        void returns400IfSubscriptionDoesNotResembleAUUID() {
            client.get().uri("/analyses/%s/messages/subscriptions/%s".formatted(ANALYSIS_ID, "no-uuid"))
                    .exchange()
                    .expectStatus().isBadRequest();
        }

        @Test
        void returns400IfSubscriptionCannotBeFound() {
            Mockito.doReturn(Mono.empty())
                    .when(mockedSubscriptionService)
                    .getSubscription(SUBSCRIPTION_ID);

            client.get().uri("/analyses/%s/messages/subscriptions/%s".formatted(ANALYSIS_ID, SUBSCRIPTION_ID))
                    .exchange()
                    .expectStatus().isNotFound();

            verify(mockedSubscriptionService, times(1)).getSubscription(SUBSCRIPTION_ID);
        }

        @Test
        void succeeds() throws IOException {
            var webhookUrl = URI.create("http://localhost/test").toURL();
            var sub = new MessageSubscription(SUBSCRIPTION_ID, ANALYSIS_ID, webhookUrl);
            Mockito.doReturn(Mono.just(sub)).when(mockedSubscriptionService).getSubscription(SUBSCRIPTION_ID);

            var resp = client.get().uri("/analyses/%s/messages/subscriptions/%s".formatted(ANALYSIS_ID, SUBSCRIPTION_ID))
                    .exchange()
                    .expectStatus().isOk()
                    .returnResult(MessageSubscriptionResponse.class);

            var clientReceivedSub = JSON.readValue(resp.getResponseBodyContent(), MessageSubscriptionResponse.class);

            assertNotNull(clientReceivedSub);
            assertEquals(ANALYSIS_ID, clientReceivedSub.getAnalysisId());
            assertEquals(SUBSCRIPTION_ID, clientReceivedSub.getSubscriptionId());
            assertEquals(webhookUrl, clientReceivedSub.getWebhookUrl());
        }
    }

    @Nested
    public class AddNewSubscriptionTests {

        @Test
        void returns400IfAnalysisIdIsBlank() throws MalformedURLException, JsonProcessingException {
            var subRequest = AddMessageSubscriptionRequest.builder()
                    .webhookUrl(URI.create("http://localhost/test").toURL())
                    .build();

            client.post().uri("/analyses/ /messages/subscriptions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(BodyInserters.fromValue(JSON.writeValueAsString(subRequest)))
                    .exchange()
                    .expectStatus().isBadRequest();
        }

        @Test
        void returns400IfRequestIsMalformed() throws JsonProcessingException {
            var subRequest = JsonNodeFactory.instance.objectNode();

            client.post().uri("/analyses/%s/messages/subscriptions".formatted(ANALYSIS_ID))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(BodyInserters.fromValue(JSON.writeValueAsString(subRequest)))
                    .exchange()
                    .expectStatus().isBadRequest();
        }

        @Test
        void returns400IfRequestReferencesInvalidWebhookUrl() throws JsonProcessingException {
            var subRequest = JsonNodeFactory.instance.objectNode();
            subRequest.put("webhookUrl", "no-url");

            client.post().uri("/analyses/%s/messages/subscriptions".formatted(ANALYSIS_ID))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(BodyInserters.fromValue(JSON.writeValueAsString(subRequest)))
                    .exchange()
                    .expectStatus().isBadRequest();
        }

        @Test
        void succeedsWithLocationHeader() throws MalformedURLException, JsonProcessingException {
            var webhookUrl = URI.create("http://localhost/test").toURL();
            var subRequest = AddMessageSubscriptionRequest.builder()
                    .webhookUrl(webhookUrl)
                    .build();
            var sub = new MessageSubscription(SUBSCRIPTION_ID, ANALYSIS_ID, webhookUrl);
            Mockito.doReturn(Mono.just(sub)).when(mockedSubscriptionService).addSubscription(ANALYSIS_ID, webhookUrl);

            client.post().uri("/analyses/%s/messages/subscriptions".formatted(ANALYSIS_ID))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(BodyInserters.fromValue(JSON.writeValueAsString(subRequest)))
                    .exchange()
                    .expectStatus().isCreated()
                    .expectHeader().location("/analyses/%s/messages/subscriptions/%s"
                            .formatted(ANALYSIS_ID, SUBSCRIPTION_ID));
        }
    }

    @Nested
    public class DeleteSubscriptionTests {

        @Test
        void returns400IfAnalysisIdIsBlank() {
            client.delete().uri("/analyses/ /messages/subscriptions/%s".formatted(SUBSCRIPTION_ID))
                    .exchange()
                    .expectStatus().isBadRequest();
        }

        @Test
        void returns400IfSubscriptionIdDoesNotResembleAUUID() {
            client.delete().uri("/analyses/%s/messages/subscriptions/%s".formatted(ANALYSIS_ID, "no-uuid"))
                    .exchange()
                    .expectStatus().isBadRequest();
        }

        @Test
        void returns204AfterSuccessfulDeletion() {
            Mockito.doReturn(Mono.empty()).when(mockedSubscriptionService).deleteSubscription(SUBSCRIPTION_ID);

            client.delete().uri("/analyses/%s/messages/subscriptions/%s".formatted(ANALYSIS_ID, SUBSCRIPTION_ID))
                    .exchange()
                    .expectStatus().isNoContent();
        }
    }
}
