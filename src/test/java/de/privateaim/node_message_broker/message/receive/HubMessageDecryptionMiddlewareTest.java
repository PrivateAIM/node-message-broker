package de.privateaim.node_message_broker.message.receive;

import de.privateaim.node_message_broker.common.hub.HubClient;
import de.privateaim.node_message_broker.common.hub.HubNodePublicKeyNotObtainable;
import de.privateaim.node_message_broker.message.crypto.HubMessageCryptoService;
import de.privateaim.node_message_broker.message.crypto.MessageCryptoException;
import de.privateaim.node_message_broker.message.crypto.MessageCryptoService;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.SecureRandom;
import java.security.Security;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.util.UUID;
import java.util.function.Function;

import static de.privateaim.node_message_broker.message.CryptoUtil.generateECDHKeyPair;
import static org.junit.jupiter.api.Assertions.assertEquals;

public final class HubMessageDecryptionMiddlewareTest {

    private static final UUID SENDER_ROBOT_ID = UUID.fromString("5242c219-4eeb-4b60-95a9-e95fab73714c");
    private static final UUID MESSAGE_ID = UUID.fromString("f691940a-e5ce-483c-80d7-17b9e4f682fe");
    private static final String ANALYSIS_ID = "analysis-123";
    private static final String TEST_MESSAGE_PAYLOAD = "{\"foo\": \"bar\"}";
    private static final MessageCryptoService HUB_MESSAGE_CRYPTO_SERVICE = new HubMessageCryptoService(new SecureRandom());
    private static final Function<ReceiveMessage, byte[]> KDF_KEYING_INFO_GENERATOR = message -> {
        var messageMetadata = message.context();
        return (messageMetadata.messageId() +
                messageMetadata.analysisId())
                .getBytes();
    };

    private static KeyPair senderKeyPair;
    private static KeyPair receiverKeyPair;
    private static HubClient hubClient;
    private static HubMessageDecryptionMiddleware middleware;

    @BeforeAll
    public static void setUpTestEnvironment() {
        var securityProvider = new BouncyCastleProvider();
        Security.addProvider(securityProvider);

        senderKeyPair = generateECDHKeyPair();
        receiverKeyPair = generateECDHKeyPair();

        hubClient = Mockito.mock(HubClient.class);

        middleware = new HubMessageDecryptionMiddleware(
                (ECPrivateKey) receiverKeyPair.getPrivate(),
                HUB_MESSAGE_CRYPTO_SERVICE,
                hubClient,
                KDF_KEYING_INFO_GENERATOR
        );
    }

    @AfterEach
    public void resetMocks() {
        Mockito.reset(hubClient);
    }

    @Test
    public void incomingMessageMustNotBeNull() {
        StepVerifier.create(middleware.apply(null))
                .expectError(ReceiveMiddlewareException.class)
                .verify();
    }

    @Test
    public void failsIfSenderPublicKeyCannotGetFetched() {
        var testMessage = ReceiveMessage.builder()
                .sentFrom(new ReceiveMessageSender(SENDER_ROBOT_ID.toString()))
                .withPayload("does-not-matter".getBytes())
                .inContext(new ReceiveMessageContext(
                        MESSAGE_ID,
                        ANALYSIS_ID))
                .build();

        Mockito.doReturn(Mono.error(new HubNodePublicKeyNotObtainable("error")))
                .when(hubClient)
                .fetchPublicKey(SENDER_ROBOT_ID.toString());

        StepVerifier.create(middleware.apply(testMessage))
                .expectError(ReceiveMiddlewareException.class)
                .verify();
    }

    @Test
    public void messagePayloadGetsDecrypted() throws InvalidKeyException, MessageCryptoException {
        var derivedSymmetricKey = HUB_MESSAGE_CRYPTO_SERVICE.deriveSymmetricKey(
                (ECPrivateKey) senderKeyPair.getPrivate(),
                (ECPublicKey) receiverKeyPair.getPublic(),
                (MESSAGE_ID + ANALYSIS_ID).getBytes()
        );

        var encryptedMessagePayload = HUB_MESSAGE_CRYPTO_SERVICE
                .encryptMessage(derivedSymmetricKey, TEST_MESSAGE_PAYLOAD.getBytes());

        var encryptedTestMessage = ReceiveMessage.builder()
                .sentFrom(new ReceiveMessageSender(SENDER_ROBOT_ID.toString()))
                .withPayload(encryptedMessagePayload)
                .inContext(new ReceiveMessageContext(
                        MESSAGE_ID,
                        ANALYSIS_ID))
                .build();

        var expectedPlaintextTestMessage = ReceiveMessage.builder()
                .sentFrom(new ReceiveMessageSender(SENDER_ROBOT_ID.toString()))
                .withPayload(TEST_MESSAGE_PAYLOAD.getBytes())
                .inContext(new ReceiveMessageContext(
                        MESSAGE_ID,
                        ANALYSIS_ID))
                .build();

        Mockito.doReturn(Mono.just((ECPublicKey) senderKeyPair.getPublic()))
                .when(hubClient)
                .fetchPublicKey(SENDER_ROBOT_ID.toString());

        StepVerifier.create(middleware.apply(encryptedTestMessage))
                .assertNext(decryptedMessage ->
                        assertEquals(expectedPlaintextTestMessage, decryptedMessage))
                .verifyComplete();
    }
}
