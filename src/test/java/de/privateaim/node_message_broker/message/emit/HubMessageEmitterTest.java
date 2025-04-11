package de.privateaim.node_message_broker.message.emit;

import de.privateaim.node_message_broker.message.api.hub.HubMessageMetadata;
import de.privateaim.node_message_broker.message.api.hub.HubMessageRecipient;
import de.privateaim.node_message_broker.message.api.hub.OutgoingHubMessage;
import io.socket.client.Socket;
import org.json.JSONObject;
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

import java.util.List;

import static de.privateaim.node_message_broker.message.emit.MessageUtil.generateBasicMessage;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
public final class HubMessageEmitterTest {

    private ArgumentCaptor<JSONObject> messageCaptor;

    @Mock
    private Socket socket;

    private HubMessageEmitter emitter;

    @BeforeEach
    public void setUp() {
        emitter = new HubMessageEmitter(socket);
        messageCaptor = ArgumentCaptor.forClass(JSONObject.class);
    }

    @AfterEach
    public void tearDown() {
        Mockito.reset(socket);
    }

    @Test
    public void messageGetsEmittedAsIsWithoutAnyRegisteredMiddlewares() {
        var testMessage = generateBasicMessage("randome-test-message".getBytes());
        var expectedEmitMessage = new OutgoingHubMessage(
                List.of(new HubMessageRecipient("robot", testMessage.recipient().nodeRobotId())),
                new String(testMessage.payload()),
                new HubMessageMetadata(
                        testMessage.context().messageId(),
                        testMessage.context().analysisId()
                )
        );

        Mockito.doReturn(socket).when(socket).emit(Mockito.anyString(), messageCaptor.capture());

        StepVerifier.create(emitter.emitMessage(testMessage))
                .expectNext()
                .verifyComplete();

        Mockito.verify(socket, Mockito.times(1)).emit(Mockito.anyString(), Mockito.any(JSONObject.class));
        assertTrue(messageCaptor.getValue().similar(new JSONObject(expectedEmitMessage)));
    }

    @Test
    public void messageGetsNotEmittedIfMiddlewareFails() {
        emitter.registerMiddleware(
                (outgoingHubMessage) -> Mono.error(new Exception("random error"))
        );

        var testMessage = generateBasicMessage("randome-test-message".getBytes());
        StepVerifier.create(emitter.emitMessage(testMessage))
                .verifyError(EmitMessageException.class);

        Mockito.verifyNoInteractions(socket);
    }

    @Test
    public void messageGetsEmittedAfterSuccessfullyApplyingAllRegisteredMiddlewaresInOrder() {
        var testMessage = generateBasicMessage("foobar".getBytes());

        emitter.registerMiddleware(
                msg -> Mono.just(new EmitMessage(
                        msg.recipient(),
                        "foo".getBytes(),
                        msg.context()
                ))
        );
        emitter.registerMiddleware(
                msg -> Mono.just(new EmitMessage(
                        msg.recipient(),
                        new String(msg.payload()).toUpperCase().getBytes(),
                        msg.context()
                ))
        );

        var expectedEmitMessage = new OutgoingHubMessage(
                List.of(new HubMessageRecipient("robot", testMessage.recipient().nodeRobotId())),
                "FOO",
                new HubMessageMetadata(
                        testMessage.context().messageId(),
                        testMessage.context().analysisId()
                )
        );

        Mockito.doReturn(socket).when(socket).emit(Mockito.anyString(), messageCaptor.capture());

        StepVerifier.create(emitter.emitMessage(testMessage))
                .expectNext()
                .verifyComplete();

        Mockito.verify(socket, Mockito.times(1)).emit(Mockito.anyString(), Mockito.any(JSONObject.class));
        assertTrue(messageCaptor.getValue().similar(new JSONObject(expectedEmitMessage)));
    }
}
