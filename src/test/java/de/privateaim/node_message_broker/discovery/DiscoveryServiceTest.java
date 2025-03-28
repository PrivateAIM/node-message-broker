package de.privateaim.node_message_broker.discovery;

import de.privateaim.node_message_broker.common.hub.HubClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
public final class DiscoveryServiceTest {

    private static HubClient mockedHubClient;
    private static DiscoveryService discoveryService;

    @BeforeAll
    static void setUp() {
        mockedHubClient = Mockito.mock(HubClient.class);
        discoveryService = new DiscoveryService(mockedHubClient, "robot-123");
    }

    @AfterEach
    public void reset() {
        Mockito.reset(mockedHubClient);
    }

    @Nested
    public class DiscoverParticipantsTests {

        @Test
        void nullAnalysisIdIsProhibited() {
            StepVerifier.create(discoveryService.discoverAllParticipantsOfAnalysis(null))
                    .expectError(NullPointerException.class)
                    .verify();
        }

        @Test
        void blankAnalysisIdIsProhibited() {
            StepVerifier.create(discoveryService.discoverAllParticipantsOfAnalysis(" "))
                    .expectError(IllegalArgumentException.class)
                    .verify();
        }
    }

    @Nested
    public class DiscoverSelfTests {

        @Test
        void nullAnalysisIdIsProhibited() {
            StepVerifier.create(discoveryService.discoverSelfInAnalysis(null))
                    .expectError(NullPointerException.class)
                    .verify();
        }

        @Test
        void blankAnalysisIdIsProhibited() {
            StepVerifier.create(discoveryService.discoverSelfInAnalysis(" "))
                    .expectError(IllegalArgumentException.class)
                    .verify();
        }
    }
}
