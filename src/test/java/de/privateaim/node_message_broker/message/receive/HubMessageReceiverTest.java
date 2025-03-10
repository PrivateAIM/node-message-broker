package de.privateaim.node_message_broker.message.receive;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.privateaim.node_message_broker.message.api.hub.HubMessageMetadata;
import de.privateaim.node_message_broker.message.api.hub.HubMessageSender;
import de.privateaim.node_message_broker.message.api.hub.IncomingHubMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
public final class HubMessageReceiverTest {

    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

    private static final IncomingHubMessage TEST_MESSAGE = new IncomingHubMessage(
            new HubMessageSender("robot", "123"),
            "test-message",
            new HubMessageMetadata(
                    UUID.fromString("758be680-0300-4889-b13c-f7a9a62af6cf"),
                    "analysis-abc"
            )
    );

    private ArgumentCaptor<ReceiveMessage> messageConsumerCaptor;

    @Mock
    private MessageConsumer mockedMessageConsumerA;

    @Mock
    private MessageConsumer mockedMessageConsumerB;

    private HubMessageReceiver receiver;

    @BeforeEach
    public void setUp() {
        receiver = new HubMessageReceiver(JSON_MAPPER);
        messageConsumerCaptor = ArgumentCaptor.forClass(ReceiveMessage.class);
    }

    @AfterEach
    public void tearDown() {
        Mockito.reset(mockedMessageConsumerA, mockedMessageConsumerB);
    }

    @Test
    public void messageGetsSentToConsumersAsIsWithoutAnyRegisteredMiddlewares() throws JsonProcessingException {
        var serializedMessage = JSON_MAPPER.writeValueAsBytes(TEST_MESSAGE);

        var expectedReceivedMessage = ReceiveMessage.builder()
                .sentFrom(new ReceiveMessageSender(TEST_MESSAGE.sender().nodeRobotId()))
                .withPayload(TEST_MESSAGE.payload().getBytes())
                .inContext(new ReceiveMessageContext(
                        TEST_MESSAGE.metadata().messageId(),
                        TEST_MESSAGE.metadata().analysisId()))
                .build();

        Mockito.doReturn(Mono.empty()).when(mockedMessageConsumerA).consume(messageConsumerCaptor.capture());
        receiver.registerConsumer(mockedMessageConsumerA);

        StepVerifier.create(receiver.processMessage(serializedMessage))
                .expectNext()
                .verifyComplete();

        Mockito.verify(mockedMessageConsumerA, Mockito.times(1)).consume(Mockito.any());
        assertEquals(expectedReceivedMessage, messageConsumerCaptor.getValue());
    }

    @Test
    public void messageGetsNotSentToConsumerIfMessageParsingFails() {
        Mockito.doReturn(Mono.empty()).when(mockedMessageConsumerA).consume(messageConsumerCaptor.capture());
        receiver.registerConsumer(mockedMessageConsumerA);

        StepVerifier.create(receiver.processMessage("non-parsable-hub-message".getBytes()))
                .expectError(ProcessingReceivedMessageException.class)
                .verify();

        Mockito.verify(mockedMessageConsumerA, Mockito.never()).consume(Mockito.any());
    }

    @Test
    public void messageGetsNotSentToConsumerIfMiddlewareFails() throws JsonProcessingException, InterruptedException {
        var serializedMessage = JSON_MAPPER.writeValueAsBytes(TEST_MESSAGE);

        var latch = new CountDownLatch(1);

        Mockito.doReturn(Mono.empty()).when(mockedMessageConsumerA).consume(messageConsumerCaptor.capture());
        receiver.registerConsumer(mockedMessageConsumerA);
        receiver.registerMiddleware((msg) -> {
            latch.countDown();
            return Mono.error(new RuntimeException());
        });

        StepVerifier.create(receiver.processMessage(serializedMessage))
                .expectError(ProcessingReceivedMessageException.class)
                .verify();

        Mockito.verify(mockedMessageConsumerA, Mockito.never()).consume(Mockito.any());
        assertTrue(latch.await(5000, TimeUnit.MILLISECONDS));
    }

    @Test
    public void messageGetsSentToConsumerAfterSuccessfullyApplyingAllRegisteredMiddlewaresInOrder() throws JsonProcessingException {
        receiver.registerConsumer(mockedMessageConsumerA);
        receiver.registerMiddleware(
                msg -> Mono.just(
                        new ReceiveMessage(
                                msg.sender(),
                                "foo".getBytes(),
                                msg.context())));
        receiver.registerMiddleware(
                msg -> Mono.just(
                        new ReceiveMessage(
                                msg.sender(),
                                new String(msg.payload()).toUpperCase().getBytes(),
                                msg.context())));

        var expectedConsumerMessage = new ReceiveMessage(
                new ReceiveMessageSender(TEST_MESSAGE.sender().nodeRobotId()),
                "FOO".getBytes(),
                new ReceiveMessageContext(TEST_MESSAGE.metadata().messageId(), TEST_MESSAGE.metadata().analysisId())
        );

        Mockito.doReturn(Mono.empty()).when(mockedMessageConsumerA).consume(messageConsumerCaptor.capture());

        var serializedTestMessage = JSON_MAPPER.writeValueAsBytes(TEST_MESSAGE);
        StepVerifier.create(receiver.processMessage(serializedTestMessage))
                .expectNext()
                .verifyComplete();

        Mockito.verify(mockedMessageConsumerA, Mockito.times(1)).consume(Mockito.any());
        assertEquals(expectedConsumerMessage, messageConsumerCaptor.getValue());
    }

    @Test
    public void messageGetsSentToAdditionalConsumersIfOneOfThemFails() throws JsonProcessingException {
        receiver.registerConsumer(mockedMessageConsumerA);
        receiver.registerConsumer(mockedMessageConsumerB);

        Mockito.doReturn(Mono.error(new RuntimeException("an unexpected error occurred")))
                .when(mockedMessageConsumerA)
                .consume(Mockito.any(ReceiveMessage.class));
        Mockito.doReturn(Mono.empty())
                .when(mockedMessageConsumerB)
                .consume(Mockito.any(ReceiveMessage.class));

        var serializedTestMessage = JSON_MAPPER.writeValueAsBytes(TEST_MESSAGE);

        StepVerifier.create(receiver.processMessage(serializedTestMessage))
                .expectNext()
                .verifyComplete();

        Mockito.verify(mockedMessageConsumerA, Mockito.times(1)).consume(Mockito.any());
        Mockito.verify(mockedMessageConsumerB, Mockito.times(1)).consume(Mockito.any());
    }
}
