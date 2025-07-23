package de.privateaim.node_message_broker.discovery;

import com.github.dockerjava.zerodep.shaded.org.apache.hc.core5.http.HttpStatus;
import de.privateaim.node_message_broker.common.HttpRetryConfig;
import de.privateaim.node_message_broker.common.hub.HttpHubClient;
import de.privateaim.node_message_broker.common.hub.api.AnalysisNode;
import de.privateaim.node_message_broker.common.hub.api.HubResponseContainer;
import de.privateaim.node_message_broker.common.hub.api.Node;
import de.privateaim.node_message_broker.discovery.api.ParticipantType;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.web.reactive.function.client.WebClient;
import org.testcontainers.shaded.com.fasterxml.jackson.core.JsonProcessingException;
import org.testcontainers.shaded.com.fasterxml.jackson.databind.ObjectMapper;
import reactor.test.StepVerifier;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;

public final class DiscoveryServiceIT {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String ANALYSIS_ID = "ana-123";
    private static final String SELF_ROBOT_ID = "robot-1";

    private MockWebServer mockWebServer;

    private DiscoveryService discoveryService;

    @BeforeEach
    void setUp() {
        mockWebServer = new MockWebServer();
        var noAuthWebClient = WebClient.create(mockWebServer.url("/").toString());
        var hubClientCfg = new HttpRetryConfig(0, 0);
        var hubClient = Mockito.spy(new HttpHubClient(noAuthWebClient, hubClientCfg));
        discoveryService = new DiscoveryService(hubClient, SELF_ROBOT_ID);
    }

    @Nested
    public class DiscoverParticipantsTests {

        @Test
        void failsIfAnalysisNodeLookupFails() {
            mockWebServer.enqueue(new MockResponse().setResponseCode(HttpStatus.SC_SERVICE_UNAVAILABLE));

            StepVerifier.create(discoveryService.discoverAllParticipantsOfAnalysis(ANALYSIS_ID))
                    .expectError(AnalysisNodesLookupException.class)
                    .verify();
        }

        @Test
        void returnsParticipatingAnalysisNodes() throws JsonProcessingException {
            var participatingAnalysisNodes = List.of(
                    new AnalysisNode("123", "node-1", new Node("node-1", "default", "some-key", "robot-1")),
                    new AnalysisNode("456", "node-2", new Node("node-2", "default", "some-key", "robot-2"))
            );
            var mockedHubResponse = new HubResponseContainer<>(participatingAnalysisNodes);

            mockWebServer.enqueue(new MockResponse().setResponseCode(HttpStatus.SC_OK)
                    .setHeader("Content-Type", "application/json")
                    .setBody(JSON.writeValueAsString(mockedHubResponse)));

            var discoveredParticipants = new ArrayList<Participant>();
            StepVerifier.create(discoveryService.discoverAllParticipantsOfAnalysis(ANALYSIS_ID))
                    .recordWith(ArrayList::new)
                    .thenConsumeWhile(Objects::nonNull, discoveredParticipants::add)
                    .verifyComplete();

            assertEquals(participatingAnalysisNodes.size(), discoveredParticipants.size());
            assertEquals(participatingAnalysisNodes.getFirst().node.type,
                    discoveredParticipants.getFirst().nodeType().getRepresentation());
            assertEquals(participatingAnalysisNodes.getFirst().node.robotId,
                    discoveredParticipants.getFirst().nodeRobotId());
            assertEquals(participatingAnalysisNodes.getLast().node.type,
                    discoveredParticipants.getLast().nodeType().getRepresentation());
            assertEquals(participatingAnalysisNodes.getLast().node.robotId,
                    discoveredParticipants.getLast().nodeRobotId());
        }

        @Test
        void returnsNothingIfThereAreNoParticipantsYet() throws JsonProcessingException {
            var mockedHubResponse = new HubResponseContainer<>(Collections.emptyList());

            mockWebServer.enqueue(new MockResponse().setResponseCode(HttpStatus.SC_OK)
                    .setHeader("Content-Type", "application/json")
                    .setBody(JSON.writeValueAsString(mockedHubResponse)));

            StepVerifier.create(discoveryService.discoverAllParticipantsOfAnalysis(ANALYSIS_ID))
                    .expectNextCount(0)
                    .verifyComplete();
        }
    }

    @Nested
    public class DiscoverSelfTests {

        @Test
        void failsIfAnalysisNodeLookupFails() {
            mockWebServer.enqueue(new MockResponse().setResponseCode(HttpStatus.SC_SERVICE_UNAVAILABLE));

            StepVerifier.create(discoveryService.discoverSelfInAnalysis(ANALYSIS_ID))
                    .expectError(AnalysisNodesLookupException.class)
                    .verify();
        }

        @Test
        void failsIfMultipleAnalysisNodesShareOneRobotId() throws JsonProcessingException {
            var participatingAnalysisNodes = List.of(
                    new AnalysisNode("123", "node-1", new Node("node-1", "default", "some-key", SELF_ROBOT_ID)),
                    new AnalysisNode("456", "node-2", new Node("node-2", "default", "some-key", SELF_ROBOT_ID))
            );
            var mockedHubResponse = new HubResponseContainer<>(participatingAnalysisNodes);

            mockWebServer.enqueue(new MockResponse().setResponseCode(HttpStatus.SC_OK)
                    .setHeader("Content-Type", "application/json")
                    .setBody(JSON.writeValueAsString(mockedHubResponse)));

            StepVerifier.create(discoveryService.discoverSelfInAnalysis(ANALYSIS_ID))
                    .expectError(DiscoveryConflictException.class)
                    .verify();
        }

        @Test
        void failsIfNoParticipatingAnalysisNodesCanGetDiscovered() throws JsonProcessingException {
            var mockedHubResponse = new HubResponseContainer<>(Collections.emptyList());

            mockWebServer.enqueue(new MockResponse().setResponseCode(HttpStatus.SC_OK)
                    .setHeader("Content-Type", "application/json")
                    .setBody(JSON.writeValueAsString(mockedHubResponse)));

            StepVerifier.create(discoveryService.discoverSelfInAnalysis(ANALYSIS_ID))
                    .expectError(UndiscoverableSelfException.class)
                    .verify();
        }

        @Test
        void returnsSelfDiscoveredParticipatingAnalysisNode() throws JsonProcessingException {
            var participatingAnalysisNodes = List.of(
                    new AnalysisNode("123", "node-1", new Node("node-1", "aggregator", "some-key", SELF_ROBOT_ID)),
                    new AnalysisNode("456", "node-2", new Node("node-2", "default", "some-key", "robot-123"))
            );
            var mockedHubResponse = new HubResponseContainer<>(participatingAnalysisNodes);

            mockWebServer.enqueue(new MockResponse().setResponseCode(HttpStatus.SC_OK)
                    .setHeader("Content-Type", "application/json")
                    .setBody(JSON.writeValueAsString(mockedHubResponse)));

            StepVerifier.create(discoveryService.discoverSelfInAnalysis(ANALYSIS_ID))
                    .expectNext(new Participant(SELF_ROBOT_ID, ParticipantType.AGGREGATOR))
                    .verifyComplete();
        }
    }
}
