package de.privateaim.node_message_broker.message.emit;

import de.privateaim.node_message_broker.common.hub.HubClient;
import de.privateaim.node_message_broker.message.crypto.MessageCryptoException;
import de.privateaim.node_message_broker.message.crypto.MessageCryptoService;
import jakarta.validation.constraints.NotNull;
import reactor.core.publisher.Mono;

import java.security.InvalidKeyException;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.util.function.Function;

import static java.util.Objects.requireNonNull;

/**
 * A middleware that encrypts a message before it's sent to its recipient.
 */
public final class HubMessageEncryptionMiddleware implements Function<EmitMessage, Mono<EmitMessage>> {

    private final ECPrivateKey nodePrivateKey; // this node's ECDH private key
    private final MessageCryptoService messageCryptoService;
    private final HubClient hubClient;
    private final Function<EmitMessage, byte[]> kdfKeyingInfoGenerator;

    /**
     * Constructs a new {@link HubMessageEncryptionMiddleware} instance.
     *
     * @param nodePrivateKey         the sender's private key. The key must be an ECDH key.
     * @param messageCryptoService   service offering cryptographic functionality
     * @param hubClient              client for communicating with the Hub
     * @param kdfKeyingInfoGenerator generator for generating additional keying information based on a single message to
     *                               derive a symmetric key for encryption.
     *                               See {@link MessageCryptoService#deriveSymmetricKey(ECPrivateKey, ECPublicKey, byte[])}
     *                               for more information on how to choose appropriate values.
     */
    public HubMessageEncryptionMiddleware(
            @NotNull ECPrivateKey nodePrivateKey,
            @NotNull MessageCryptoService messageCryptoService,
            @NotNull HubClient hubClient,
            @NotNull Function<EmitMessage, byte[]> kdfKeyingInfoGenerator) {
        this.nodePrivateKey = requireNonNull(nodePrivateKey, "node private key must not be null");
        this.messageCryptoService = requireNonNull(messageCryptoService, "message crypto service must not be null");
        this.hubClient = requireNonNull(hubClient, "hub client must not be null");
        this.kdfKeyingInfoGenerator = requireNonNull(kdfKeyingInfoGenerator, "KDF keying info generator must not be null");
    }

    /**
     * Encrypts the given message.
     * <p>
     * Encryption is done by using a derived symmetric key based on parts of the sender's and recipient's key
     * information. The recipient's key information is obtained from the Hub.
     *
     * @param message the message that shall get encrypted
     * @return The encrypted message. Might return an {@link EmitMiddlewareException} as an error state if applying this
     * middleware fails.
     */
    @Override
    public Mono<EmitMessage> apply(@NotNull EmitMessage message) {
        if (message == null) {
            return Mono.error(new EmitMiddlewareException("outgoing hub message must not be null"));
        }

        return Mono.zip(
                        hubClient.fetchPublicKey(message.recipient().nodeRobotId()),
                        Mono.just(kdfKeyingInfoGenerator.apply(message)))
                .onErrorMap(err -> new EmitMiddlewareException("failed to retrieve key material", err))
                .flatMap(keyMaterial ->
                {
                    try {
                        return Mono.just(messageCryptoService.deriveSymmetricKey(nodePrivateKey, keyMaterial.getT1(),
                                keyMaterial.getT2()));
                    } catch (InvalidKeyException e) {
                        return Mono.error(
                                new EmitMiddlewareException("failed to derive symmetric key for message encryption", e)
                        );
                    }
                })
                .flatMap(symmetricKey -> {
                    try {
                        return Mono.just(new EmitMessage(
                                message.recipient(),
                                messageCryptoService.encryptMessage(symmetricKey, message.payload()),
                                message.context()
                        ));
                    } catch (InvalidKeyException | MessageCryptoException e) {
                        return Mono.error(
                                new EmitMiddlewareException("failed to encrypt message for message encryption", e));
                    }
                })
                .onErrorMap((err) -> !(err instanceof EmitMiddlewareException),
                        err -> new EmitMiddlewareException("an unexpected error occurred", err));
    }
}
