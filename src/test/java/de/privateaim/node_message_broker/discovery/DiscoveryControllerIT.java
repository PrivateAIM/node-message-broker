package de.privateaim.node_message_broker.discovery;

import de.privateaim.node_message_broker.discovery.api.ParticipantResponse;
import de.privateaim.node_message_broker.discovery.api.ParticipantType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.autoconfigure.security.reactive.ReactiveSecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.shaded.com.fasterxml.jackson.databind.ObjectMapper;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@WebFluxTest(controllers = DiscoveryController.class,
        excludeAutoConfiguration = {ReactiveSecurityAutoConfiguration.class})
public final class DiscoveryControllerIT {

    private static final String ANALYSIS_ID = "ana-123";
    private static final ObjectMapper JSON = new ObjectMapper();

    @MockitoBean
    private DiscoveryService mockedDiscoveryService;

    private WebTestClient client;

    @BeforeEach
    void setUp(ApplicationContext context) {
        client = WebTestClient.bindToApplicationContext(context).build();
    }

    @AfterEach
    void tearDown() {
        Mockito.reset(mockedDiscoveryService);
    }

    @Nested
    public class DiscoverParticipantsTests {

        @Test
        void returns400IfAnalysisIdIsBlank() {
            client.get().uri("/analyses/ /participants")
                    .exchange()
                    .expectStatus().isBadRequest();
        }

        @Test
        void returns200IfNoParticipantsCouldGetFound() throws IOException {
            Mockito.doReturn(Flux.empty()).when(mockedDiscoveryService)
                    .discoverAllParticipantsOfAnalysis(ANALYSIS_ID);

            var resp = client.get().uri("/analyses/%s/participants".formatted(ANALYSIS_ID))
                    .exchange()
                    .expectStatus().isOk()
                    .returnResult(ParticipantResponse.class);

            var clientReceivedParticipants = Arrays.asList(
                    JSON.readValue(resp.getResponseBodyContent(), ParticipantResponse[].class));

            assertTrue(clientReceivedParticipants.isEmpty());
        }

        @Test
        void returns502IfAnalysisNodesLookupFails() {
            Mockito.doReturn(Flux.error(new AnalysisNodesLookupException("foo", new RuntimeException("bar"))))
                    .when(mockedDiscoveryService)
                    .discoverAllParticipantsOfAnalysis(ANALYSIS_ID);

            client.get().uri("/analyses/%s/participants".formatted(ANALYSIS_ID))
                    .exchange()
                    .expectStatus().isEqualTo(HttpStatus.BAD_GATEWAY);
        }

        @Test
        void returns500OnUnknownError() {
            Mockito.doReturn(Flux.error(new RuntimeException("foo")))
                    .when(mockedDiscoveryService)
                    .discoverAllParticipantsOfAnalysis(ANALYSIS_ID);

            client.get().uri("/analyses/%s/participants".formatted(ANALYSIS_ID))
                    .exchange()
                    .expectStatus().isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        }

        @Test
        void returns200WithAllDiscoveredParticipants() throws IOException {
            var participants = List.of(
                    new Participant("123", "abc", ParticipantType.AGGREGATOR),
                    new Participant("456", "def", ParticipantType.DEFAULT)
            );
            Mockito.doReturn(Flux.fromIterable(participants))
                    .when(mockedDiscoveryService)
                    .discoverAllParticipantsOfAnalysis(ANALYSIS_ID);

            var resp = client.get().uri("/analyses/%s/participants".formatted(ANALYSIS_ID))
                    .exchange()
                    .expectStatus().isOk()
                    .returnResult(ParticipantResponse.class);

            var clientReceivedParticipants = Arrays.asList(
                    JSON.readValue(resp.getResponseBodyContent(), ParticipantResponse[].class));

            assertEquals(participants.size(), clientReceivedParticipants.size());
            assertEquals(participants.getFirst().nodeType(), clientReceivedParticipants.getFirst().getNodeType());
            assertEquals(participants.getFirst().nodeId(), clientReceivedParticipants.getFirst().nodeId);
            assertEquals(participants.getLast().nodeType(), clientReceivedParticipants.getLast().getNodeType());
            assertEquals(participants.getLast().nodeId(), clientReceivedParticipants.getLast().nodeId);
        }
    }

    @Nested
    public class DiscoverSelfTests {

        @Test
        void returns400IfAnalysisIdIsBlank() {
            client.get().uri("/analyses/ /participants/self")
                    .exchange()
                    .expectStatus().isBadRequest();
        }

        @Test
        void returns502IfAnalysisNodesLookupFails() {
            Mockito.doReturn(Mono.error(new AnalysisNodesLookupException("foo", new RuntimeException("bar"))))
                    .when(mockedDiscoveryService)
                    .discoverSelfInAnalysis(ANALYSIS_ID);

            client.get().uri("/analyses/%s/participants/self".formatted(ANALYSIS_ID))
                    .exchange()
                    .expectStatus().isEqualTo(HttpStatus.BAD_GATEWAY);
        }

        @Test
        void returns500IfMultipleParticipantsResembleTheSameSelfNode() {
            Mockito.doReturn(Mono.error(new DiscoveryConflictException("foo")))
                    .when(mockedDiscoveryService)
                    .discoverSelfInAnalysis(ANALYSIS_ID);

            client.get().uri("/analyses/%s/participants/self".formatted(ANALYSIS_ID))
                    .exchange()
                    .expectStatus().isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        }

        @Test
        void returns404IfNoParticipantResemblesTheSelfNode() {
            Mockito.doReturn(Mono.error(new UndiscoverableSelfException("foo")))
                    .when(mockedDiscoveryService)
                    .discoverSelfInAnalysis(ANALYSIS_ID);

            client.get().uri("/analyses/%s/participants/self".formatted(ANALYSIS_ID))
                    .exchange()
                    .expectStatus().isNotFound();
        }

        @Test
        void returns200WithTheDiscoveredSelfNode() throws IOException {
            var self = new Participant("node-123", "robot-123", ParticipantType.AGGREGATOR);
            Mockito.doReturn(Mono.just(self))
                    .when(mockedDiscoveryService)
                    .discoverSelfInAnalysis(ANALYSIS_ID);

            var resp = client.get().uri("/analyses/%s/participants/self".formatted(ANALYSIS_ID))
                    .exchange()
                    .expectStatus().isOk()
                    .returnResult(ParticipantResponse.class);

            var clientReceivedSelf = JSON.readValue(resp.getResponseBodyContent(), ParticipantResponse.class);

            assertNotNull(clientReceivedSelf);
            assertEquals(self.nodeType(), clientReceivedSelf.getNodeType());
            assertEquals(self.nodeId(), clientReceivedSelf.getNodeId());
        }
    }
}
