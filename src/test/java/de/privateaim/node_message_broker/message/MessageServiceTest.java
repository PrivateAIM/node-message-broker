package de.privateaim.node_message_broker.message;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import de.privateaim.node_message_broker.common.hub.HubClient;
import de.privateaim.node_message_broker.message.api.MessageBroadcastRequest;
import de.privateaim.node_message_broker.message.api.MessageRequest;
import de.privateaim.node_message_broker.message.emit.EmitMessage;
import de.privateaim.node_message_broker.message.emit.MessageEmitter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.test.StepVerifier;

import java.util.List;

@ExtendWith(MockitoExtension.class)
public final class MessageServiceTest {

    @Mock
    private MessageEmitter<EmitMessage> messageEmitter;

    @Mock
    private HubClient hubClient;

    private MessageService messageService;

    @BeforeEach
    public void setUp() {
        messageService = new MessageService(messageEmitter, hubClient);
    }

    @AfterEach
    public void tearDown() {
        Mockito.reset(messageEmitter, hubClient);
    }

    @Nested
    public class SendMessageTests {

        @Test
        public void nullAnalysisIdIsProhibited() {
            var req = new MessageRequest();
            req.recipients = List.of("foo");
            req.message = JsonNodeFactory.instance.arrayNode();

            StepVerifier.create(messageService.sendMessageToSelectedRecipients(null, req))
                    .expectError(NullPointerException.class)
                    .verify();
        }

        @Test
        public void blankAnalysisIdIsProhibited() {
            var req = new MessageRequest();
            req.recipients = List.of("foo");
            req.message = JsonNodeFactory.instance.arrayNode();

            StepVerifier.create(messageService.sendMessageToSelectedRecipients(" ", req))
                    .expectError(IllegalArgumentException.class)
                    .verify();
        }

        @Test
        public void nullMessageRequestIsProhibited() {
            StepVerifier.create(messageService.sendMessageToSelectedRecipients("test-123", null))
                    .expectError(NullPointerException.class)
                    .verify();
        }

        @Test
        public void noRecipientsInRequestIsProhibited() {
            var req = new MessageRequest();
            req.recipients = List.of();
            req.message = JsonNodeFactory.instance.arrayNode();

            StepVerifier.create(messageService.sendMessageToSelectedRecipients("test-123", req))
                    .expectError(IllegalArgumentException.class)
                    .verify();
        }
    }

    @Nested
    public class SendBroadcastMessageTests {
        @Test
        public void nullAnalysisIdIsProhibited() {
            var req = new MessageBroadcastRequest();
            req.message = JsonNodeFactory.instance.objectNode();

            StepVerifier.create(messageService.sendBroadcastMessage(null, req))
                    .expectError(NullPointerException.class)
                    .verify();
        }

        @Test
        public void blankAnalysisIdIsProhibited() {
            var req = new MessageBroadcastRequest();
            req.message = JsonNodeFactory.instance.objectNode();

            StepVerifier.create(messageService.sendBroadcastMessage(" ", req))
                    .expectError(IllegalArgumentException.class)
                    .verify();
        }

        @Test
        public void nullMessageRequestIsProhibited() {
            StepVerifier.create(messageService.sendBroadcastMessage("test-123", null))
                    .expectError(NullPointerException.class)
                    .verify();
        }
    }
}
