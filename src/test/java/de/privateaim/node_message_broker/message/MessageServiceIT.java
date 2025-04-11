package de.privateaim.node_message_broker.message;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.github.dockerjava.zerodep.shaded.org.apache.hc.core5.http.HttpStatus;
import de.privateaim.node_message_broker.common.hub.HttpHubClient;
import de.privateaim.node_message_broker.common.hub.HttpHubClientConfig;
import de.privateaim.node_message_broker.common.hub.api.AnalysisNode;
import de.privateaim.node_message_broker.common.hub.api.HubResponseContainer;
import de.privateaim.node_message_broker.common.hub.api.Node;
import de.privateaim.node_message_broker.message.api.MessageBroadcastRequest;
import de.privateaim.node_message_broker.message.api.MessageRequest;
import de.privateaim.node_message_broker.message.emit.EmitMessage;
import de.privateaim.node_message_broker.message.emit.HubMessageEmitter;
import de.privateaim.node_message_broker.message.emit.MessageEmitter;
import io.socket.client.Socket;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.io.IOException;
import java.util.List;

import static com.mongodb.assertions.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

public final class MessageServiceIT {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String SELF_ROBOT_ID = "robot-123";

    private MockWebServer mockWebServer;

    private ArgumentCaptor<EmitMessage> emitMessageCaptor;

    // mocking this here since there's no current Java version of the server side and spinning up a custom JS solution
    // during tests is too much of a stretch at this point. Might get replaced later on with a real server used for
    // testing.
    private Socket mockSocket;

    private MessageEmitter<EmitMessage> spyMessageEmitter;

    private MessageService messageService;

    @BeforeEach
    void setUp() {
        mockWebServer = new MockWebServer();
        mockSocket = Mockito.mock(Socket.class);
        spyMessageEmitter = Mockito.spy(new HubMessageEmitter(mockSocket));
        var webClient = WebClient.create(mockWebServer.url("/").toString());
        var httpHubClient = new HttpHubClient(webClient, new HttpHubClientConfig.Builder()
                .withMaxRetries(0)
                .withRetryDelayMs(0)
                .build());

        messageService = new MessageService(spyMessageEmitter, httpHubClient, SELF_ROBOT_ID);
        emitMessageCaptor = ArgumentCaptor.forClass(EmitMessage.class);
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
        Mockito.reset(mockSocket, spyMessageEmitter);
    }

    @Nested
    public class SendMessageTests {

        @Test
        public void failsIfAnalysisNodeLookupFails() {
            mockWebServer.enqueue(new MockResponse().setResponseCode(HttpStatus.SC_SERVICE_UNAVAILABLE));

            var messageRequest = new MessageRequest();
            messageRequest.recipients = List.of("abc");
            messageRequest.message = JsonNodeFactory.instance.objectNode();

            StepVerifier.create(messageService.sendMessageToSelectedRecipients("123", messageRequest))
                    .expectError(AnalysisNodesLookupException.class)
                    .verify();
        }

        @Test
        public void failsIfMessageRequestsRecipientsThatAreNotPartOfTheAnalysis() throws JsonProcessingException {
            var testAnalysisNodes = List.of(
                    new AnalysisNode("123", "node-1", new Node("node-1", "default", "pub123", "robot-1")),
                    new AnalysisNode("456", "node-2", new Node("node-2", "default", "pub456", "robot-2"))
            );
            var mockedHubResponse = new HubResponseContainer<>(testAnalysisNodes);

            mockWebServer.enqueue(new MockResponse().setResponseCode(HttpStatus.SC_OK)
                    .setHeader("Content-Type", "application/json")
                    .setBody(JSON.writeValueAsString(mockedHubResponse)));

            var messageRequest = new MessageRequest();
            messageRequest.recipients = List.of("robot-not-in-list");
            messageRequest.message = JsonNodeFactory.instance.objectNode();

            StepVerifier.create(messageService.sendMessageToSelectedRecipients("123", messageRequest))
                    .expectError(InvalidMessageRecipientsException.class)
                    .verify();
        }

        @Test
        public void stillSucceedsIfSingleMessageCannotGetEmitted() throws JsonProcessingException {
            var testAnalysisNodes = List.of(
                    new AnalysisNode("123", "node-1", new Node("node-1", "default", "pub123", "robot-1")),
                    new AnalysisNode("456", "node-2", new Node("node-2", "default", "pub456", "robot-2"))
            );
            var mockedHubResponse = new HubResponseContainer<>(testAnalysisNodes);

            var messageRequest = new MessageRequest();
            messageRequest.recipients = List.of("robot-1", "robot-2");
            messageRequest.message = JsonNodeFactory.instance.objectNode();

            mockWebServer.enqueue(new MockResponse().setResponseCode(HttpStatus.SC_OK)
                    .setHeader("Content-Type", "application/json")
                    .setBody(JSON.writeValueAsString(mockedHubResponse)));

            Mockito.when(mockSocket.emit(Mockito.any(), Mockito.any()))
                    .thenReturn(mockSocket)
                    .thenThrow(new RuntimeException("some error"));

            StepVerifier.create(messageService.sendMessageToSelectedRecipients("analysis-123", messageRequest))
                    .expectNext()
                    .verifyComplete();

            Mockito.verify(mockSocket, Mockito.times(testAnalysisNodes.size())).emit(Mockito.any(), Mockito.any());
        }

        @Test
        public void remainingMessagesAreTriedToGetEmittedAfterPreviousOneFails() throws JsonProcessingException {
            var testAnalysisNodes = List.of(
                    new AnalysisNode("123", "node-1", new Node("node-1", "default", "pub123", "robot-1")),
                    new AnalysisNode("456", "node-2", new Node("node-2", "default", "pub456", "robot-2")),
                    new AnalysisNode("789", "node-3", new Node("node-3", "default", "pub789", "robot-3"))
            );
            var mockedHubResponse = new HubResponseContainer<>(testAnalysisNodes);

            var messageRequest = new MessageRequest();
            messageRequest.recipients = List.of("robot-1", "robot-2", "robot-3");
            messageRequest.message = JsonNodeFactory.instance.objectNode();

            mockWebServer.enqueue(new MockResponse().setResponseCode(HttpStatus.SC_OK)
                    .setHeader("Content-Type", "application/json")
                    .setBody(JSON.writeValueAsString(mockedHubResponse)));

            Mockito.when(mockSocket.emit(Mockito.any(), Mockito.any()))
                    .thenReturn(mockSocket)
                    .thenThrow(new RuntimeException("some error"))
                    .thenReturn(mockSocket);

            StepVerifier.create(messageService.sendMessageToSelectedRecipients("analysis-123", messageRequest))
                    .expectNext()
                    .verifyComplete();

            Mockito.verify(mockSocket, Mockito.times(testAnalysisNodes.size())).emit(Mockito.any(), Mockito.any());
        }

        @Test
        public void unableToSendMessageToSelf() throws JsonProcessingException {
            var testAnalysisNodes = List.of(
                    new AnalysisNode("123", "node-1", new Node("node-1", "default", "pub123", "robot-1")),
                    new AnalysisNode("456", "node-2", new Node("node-2", "default", "pub456", "robot-2")),
                    new AnalysisNode("789", "node-3", new Node("node-3", "default", "pub789", SELF_ROBOT_ID))
            );
            var mockedHubResponse = new HubResponseContainer<>(testAnalysisNodes);

            var messageRequest = new MessageRequest();
            messageRequest.recipients = List.of("robot-1", "robot-2", SELF_ROBOT_ID);
            messageRequest.message = JsonNodeFactory.instance.objectNode();

            mockWebServer.enqueue(new MockResponse().setResponseCode(HttpStatus.SC_OK)
                    .setHeader("Content-Type", "application/json")
                    .setBody(JSON.writeValueAsString(mockedHubResponse)));

            StepVerifier.create(messageService.sendMessageToSelectedRecipients("analysis-123", messageRequest))
                    .expectError(InvalidMessageRecipientsException.class)
                    .verify();
        }
    }

    @Nested
    public class BroadcastMessageTests {
        @Test
        public void failsIfAnalysisNodeLookupFails() {
            mockWebServer.enqueue(new MockResponse().setResponseCode(HttpStatus.SC_SERVICE_UNAVAILABLE));

            var messageBroadcastRequest = new MessageBroadcastRequest();
            messageBroadcastRequest.message = JsonNodeFactory.instance.objectNode();

            StepVerifier.create(messageService.sendBroadcastMessage("123", messageBroadcastRequest))
                    .expectError(AnalysisNodesLookupException.class)
                    .verify();
        }

        @Test
        public void stillSucceedsIfSingleMessageCannotGetEmitted() throws JsonProcessingException {
            var testAnalysisNodes = List.of(
                    new AnalysisNode("123", "node-1", new Node("node-1", "default", "pub123", "robot-1")),
                    new AnalysisNode("456", "node-2", new Node("node-2", "default", "pub456", "robot-2"))
            );
            var mockedHubResponse = new HubResponseContainer<>(testAnalysisNodes);

            mockWebServer.enqueue(new MockResponse().setResponseCode(HttpStatus.SC_OK)
                    .setHeader("Content-Type", "application/json")
                    .setBody(JSON.writeValueAsString(mockedHubResponse)));

            Mockito.when(mockSocket.emit(Mockito.any(), Mockito.any()))
                    .thenReturn(mockSocket)
                    .thenThrow(new RuntimeException("some error"));

            var messageBroadcastRequest = new MessageBroadcastRequest();
            messageBroadcastRequest.message = JsonNodeFactory.instance.objectNode();

            StepVerifier.create(messageService.sendBroadcastMessage("123", messageBroadcastRequest))
                    .expectNext()
                    .verifyComplete();

            Mockito.verify(mockSocket, Mockito.times(testAnalysisNodes.size())).emit(Mockito.any(), Mockito.any());
        }

        @Test
        public void remainingMessagesAreTriedToGetEmittedAfterPreviousOneFails() throws JsonProcessingException {
            var testAnalysisNodes = List.of(
                    new AnalysisNode("123", "node-1", new Node("node-1", "default", "pub123", "robot-1")),
                    new AnalysisNode("456", "node-2", new Node("node-2", "default", "pub456", "robot-2")),
                    new AnalysisNode("789", "node-3", new Node("node-3", "default", "pub789", "robot-3"))
            );
            var mockedHubResponse = new HubResponseContainer<>(testAnalysisNodes);

            mockWebServer.enqueue(new MockResponse().setResponseCode(HttpStatus.SC_OK)
                    .setHeader("Content-Type", "application/json")
                    .setBody(JSON.writeValueAsString(mockedHubResponse)));

            Mockito.when(mockSocket.emit(Mockito.any(), Mockito.any()))
                    .thenReturn(mockSocket)
                    .thenThrow(new RuntimeException("some error"))
                    .thenReturn(mockSocket);

            var messageBroadcastRequest = new MessageBroadcastRequest();
            messageBroadcastRequest.message = JsonNodeFactory.instance.objectNode();

            StepVerifier.create(messageService.sendBroadcastMessage("123", messageBroadcastRequest))
                    .expectNext()
                    .verifyComplete();

            Mockito.verify(mockSocket, Mockito.times(testAnalysisNodes.size())).emit(Mockito.any(), Mockito.any());
        }

        @Test
        public void messageGetsEmittedToAllNodesBeingPartOfTheAnalysisExceptTheSender() throws JsonProcessingException {
            var testAnalysisNodes = List.of(
                    new AnalysisNode("123", "node-1", new Node("node-1", "default", "pub123", SELF_ROBOT_ID)),
                    new AnalysisNode("456", "node-2", new Node("node-2", "default", "pub456", "robot-2")),
                    new AnalysisNode("789", "node-3", new Node("node-3", "default", "pub789", "robot-3"))

            );
            var mockedHubResponse = new HubResponseContainer<>(testAnalysisNodes);

            mockWebServer.enqueue(new MockResponse().setResponseCode(HttpStatus.SC_OK)
                    .setHeader("Content-Type", "application/json")
                    .setBody(JSON.writeValueAsString(mockedHubResponse)));

            var messageBroadcastRequest = new MessageBroadcastRequest();
            messageBroadcastRequest.message = JsonNodeFactory.instance.objectNode();

            Mockito.doReturn(Mono.empty()).when(spyMessageEmitter).emitMessage(emitMessageCaptor.capture());

            StepVerifier.create(messageService.sendBroadcastMessage("123", messageBroadcastRequest))
                    .expectNext()
                    .verifyComplete();

            var emittedMessages = emitMessageCaptor.getAllValues();
            assertEquals(testAnalysisNodes.size() - 1, emittedMessages.size());
            assertTrue(emittedMessages.stream().map(msg -> msg.recipient().nodeRobotId())
                    .toList()
                    .containsAll(testAnalysisNodes.stream()
                            .filter(an -> !an.node.robotId.equals(SELF_ROBOT_ID))
                            .map(analysisNode -> analysisNode.node.robotId)
                            .toList()));
        }
    }
}
