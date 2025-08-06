package de.privateaim.node_message_broker.message;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import de.privateaim.node_message_broker.message.api.MessageBroadcastRequest;
import de.privateaim.node_message_broker.message.api.MessageRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.autoconfigure.security.reactive.ReactiveSecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.BodyInserters;
import reactor.core.publisher.Mono;

import java.util.List;

import static org.mockito.Mockito.when;

// test with security config disabled
@WebFluxTest(controllers = MessageController.class, excludeAutoConfiguration = {ReactiveSecurityAutoConfiguration.class})
public final class MessageControllerIT {

    private static final ObjectMapper JSON = new ObjectMapper();

    @MockitoBean
    private MessageService service;

    private WebTestClient client;

    @BeforeEach
    void setUp(ApplicationContext context) {
        client = WebTestClient.bindToApplicationContext(context).build();
    }

    @Nested
    public class SendMessageTests {

        private static final MessageRequest TEST_MESSAGE_REQUEST = MessageRequest.builder()
                .recipients(List.of("foo"))
                .message(JsonNodeFactory.instance.objectNode())
                .build();

        @Test
        void failsIfAnalysisIdIsBlank() throws Exception {
            client.post().uri("/analyses/ /messages")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(BodyInserters.fromValue(JSON.writeValueAsString(TEST_MESSAGE_REQUEST)))
                    .exchange()
                    .expectStatus().isBadRequest();
        }

        @Test
        void failsIfAnalysisNodesCannotGetLookedUp() throws JsonProcessingException {
            when(service.sendMessageToSelectedRecipients(Mockito.eq("ana-123"), Mockito.eq(TEST_MESSAGE_REQUEST)))
                    .thenReturn(Mono.error(new AnalysisNodesLookupException("foo", null)));

            client.post().uri("/analyses/ana-123/messages")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(BodyInserters.fromValue(JSON.writeValueAsString(TEST_MESSAGE_REQUEST)))
                    .exchange()
                    .expectStatus().isEqualTo(HttpStatus.BAD_GATEWAY);
        }

        @Test
        void failsIfMessageRecipientsAreNotPartOfTheAnalysis() throws JsonProcessingException {
            when(service.sendMessageToSelectedRecipients(Mockito.eq("ana-123"), Mockito.eq(TEST_MESSAGE_REQUEST)))
                    .thenReturn(Mono.error(new InvalidMessageRecipientsException("foo")));

            client.post().uri("/analyses/ana-123/messages")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(BodyInserters.fromValue(JSON.writeValueAsString(TEST_MESSAGE_REQUEST)))
                    .exchange()
                    .expectStatus().isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test
        void failsOnUnexpectedErrors() throws JsonProcessingException {
            when(service.sendMessageToSelectedRecipients(Mockito.eq("ana-123"), Mockito.eq(TEST_MESSAGE_REQUEST)))
                    .thenReturn(Mono.error(new RuntimeException()));

            client.post().uri("/analyses/ana-123/messages")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(BodyInserters.fromValue(JSON.writeValueAsString(TEST_MESSAGE_REQUEST)))
                    .exchange()
                    .expectStatus().isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        }

        @Test
        void failsOnInvalidRequestBody() throws JsonProcessingException {
            var messageReq = JsonNodeFactory.instance.objectNode();

            client.post().uri("/analyses/ana-123/messages")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(BodyInserters.fromValue(JSON.writeValueAsString(messageReq)))
                    .exchange()
                    .expectStatus().isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test
        void succeeds() throws JsonProcessingException {
            when(service.sendMessageToSelectedRecipients(Mockito.eq("ana-123"), Mockito.eq(TEST_MESSAGE_REQUEST)))
                    .thenReturn(Mono.empty());

            client.post().uri("/analyses/ana-123/messages")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(BodyInserters.fromValue(JSON.writeValueAsString(TEST_MESSAGE_REQUEST)))
                    .exchange()
                    .expectStatus().isEqualTo(HttpStatus.ACCEPTED);
        }
    }

    @Nested
    public class SendBroadcastTests {

        private static final MessageBroadcastRequest TEST_MESSAGE_BROADCAST_REQUEST = MessageBroadcastRequest.builder()
                .message(JsonNodeFactory.instance.objectNode())
                .build();

        @Test
        void failsIfAnalysisIdIsBlank() throws Exception {
            client.post().uri("/analyses/ /messages/broadcast")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(BodyInserters.fromValue(JSON.writeValueAsString(TEST_MESSAGE_BROADCAST_REQUEST)))
                    .exchange()
                    .expectStatus().isBadRequest();
        }

        @Test
        void failsIfAnalysisNodesCannotGetLookedUp() throws JsonProcessingException {
            when(service.sendBroadcastMessage(Mockito.eq("ana-123"), Mockito.eq(TEST_MESSAGE_BROADCAST_REQUEST)))
                    .thenReturn(Mono.error(new AnalysisNodesLookupException("foo", null)));

            client.post().uri("/analyses/ana-123/messages/broadcast")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(BodyInserters.fromValue(JSON.writeValueAsString(TEST_MESSAGE_BROADCAST_REQUEST)))
                    .exchange()
                    .expectStatus().isEqualTo(HttpStatus.BAD_GATEWAY);
        }

        @Test
        void failsOnUnexpectedErrors() throws JsonProcessingException {
            when(service.sendBroadcastMessage(Mockito.eq("ana-123"), Mockito.eq(TEST_MESSAGE_BROADCAST_REQUEST)))
                    .thenReturn(Mono.error(new RuntimeException()));

            client.post().uri("/analyses/ana-123/messages/broadcast")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(BodyInserters.fromValue(JSON.writeValueAsString(TEST_MESSAGE_BROADCAST_REQUEST)))
                    .exchange()
                    .expectStatus().isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        }

        @Test
        void failsOnInvalidRequestBody() throws JsonProcessingException {
            var messageBroadcastReq = JsonNodeFactory.instance.objectNode();

            client.post().uri("/analyses/ana-123/messages/broadcast")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(BodyInserters.fromValue(JSON.writeValueAsString(messageBroadcastReq)))
                    .exchange()
                    .expectStatus().isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test
        void succeeds() throws JsonProcessingException {
            when(service.sendBroadcastMessage(Mockito.eq("ana-123"), Mockito.eq(TEST_MESSAGE_BROADCAST_REQUEST)))
                    .thenReturn(Mono.empty());

            client.post().uri("/analyses/ana-123/messages/broadcast")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(BodyInserters.fromValue(JSON.writeValueAsString(TEST_MESSAGE_BROADCAST_REQUEST)))
                    .exchange()
                    .expectStatus().isEqualTo(HttpStatus.ACCEPTED);
        }
    }
}
