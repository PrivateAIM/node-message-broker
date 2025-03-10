package de.privateaim.node_message_broker.message.emit;

import de.privateaim.node_message_broker.common.hub.HubClient;
import de.privateaim.node_message_broker.common.hub.HubNodePublicKeyNotObtainable;
import de.privateaim.node_message_broker.message.crypto.HubMessageCryptoService;
import de.privateaim.node_message_broker.message.crypto.MessageCryptoException;
import de.privateaim.node_message_broker.message.crypto.MessageCryptoService;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
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
import static org.bson.assertions.Assertions.fail;
import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(MockitoExtension.class)
public final class HubMessageEncryptionMiddlewareTest {

    private static final UUID RECEIVER_NODE_ROBOT_ID = UUID.fromString("f691940a-e5ce-483c-80d7-17b9e4f682fe");
    private static final UUID MESSAGE_ID = UUID.fromString("f691940a-e5ce-483c-80d7-17b9e4f682fe");
    private static final MessageCryptoService HUB_MESSAGE_CRYPTO_SERVICE = new HubMessageCryptoService(new SecureRandom());
    private static final EmitMessage TEST_MESSAGE = new EmitMessage(
            new EmitMessageRecipient(RECEIVER_NODE_ROBOT_ID.toString()),
            "FOO".getBytes(),
            new EmitMessageContext(
                    MESSAGE_ID,
                    "analysis-123"
            )
    );

    private static final Function<EmitMessage, byte[]> KDF_KEYING_INFO_GENERATOR = message -> {
        var messageMetadata = message.context();
        return (messageMetadata.messageId() +
                messageMetadata.analysisId())
                .getBytes();
    };

    private static KeyPair nodeKeyPair;
    private static HubClient hubClient;
    private static HubMessageEncryptionMiddleware middleware;

    @BeforeAll
    public static void setUpTestEnvironment() {
        var securityProvider = new BouncyCastleProvider();
        Security.addProvider(securityProvider);

        nodeKeyPair = generateECDHKeyPair();

        hubClient = Mockito.mock(HubClient.class);

        middleware = new HubMessageEncryptionMiddleware(
                (ECPrivateKey) nodeKeyPair.getPrivate(),
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
                .expectError(EmitMiddlewareException.class)
                .verify();
    }

    @Test
    public void failsIfReceiversPublicKeyCannotGetFetched() {
        Mockito.doReturn(Mono.error(new HubNodePublicKeyNotObtainable("error")))
                .when(hubClient)
                .fetchPublicKey(RECEIVER_NODE_ROBOT_ID.toString());

        StepVerifier.create(middleware.apply(TEST_MESSAGE))
                .expectError(EmitMiddlewareException.class)
                .verify();
    }

    @Test
    public void messagePayloadGetsEncrypted() {
        var receiverKeyPair = generateECDHKeyPair();

        Mockito.doReturn(Mono.just((ECPublicKey) receiverKeyPair.getPublic()))
                .when(hubClient)
                .fetchPublicKey(RECEIVER_NODE_ROBOT_ID.toString());

        StepVerifier.create(middleware.apply(TEST_MESSAGE))
                .assertNext(encryptedMessage -> {
                    try {
                        var derivedSymmetricKey = HUB_MESSAGE_CRYPTO_SERVICE.deriveSymmetricKey(
                                (ECPrivateKey) receiverKeyPair.getPrivate(),
                                (ECPublicKey) nodeKeyPair.getPublic(),
                                KDF_KEYING_INFO_GENERATOR.apply(TEST_MESSAGE));

                        var decryptedMessagePayload = HUB_MESSAGE_CRYPTO_SERVICE.decryptMessage(derivedSymmetricKey,
                                encryptedMessage.payload());

                        var decryptedMessage = new EmitMessage(
                                TEST_MESSAGE.recipient(),
                                decryptedMessagePayload,
                                TEST_MESSAGE.context()
                        );

                        assertEquals(TEST_MESSAGE, decryptedMessage);
                    } catch (InvalidKeyException | MessageCryptoException e) {
                        fail("unexpected behavior");
                    }
                })
                .verifyComplete();
    }
}
