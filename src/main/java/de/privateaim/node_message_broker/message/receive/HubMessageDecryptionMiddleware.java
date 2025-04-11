package de.privateaim.node_message_broker.message.receive;

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
 * A middleware that decrypts a message before it's forwarded to the caller.
 */
public final class HubMessageDecryptionMiddleware implements Function<ReceiveMessage, Mono<ReceiveMessage>> {

    private final ECPrivateKey nodePrivateKey; // this node's ECDH private key
    private final MessageCryptoService messageCryptoService;
    private final HubClient hubClient;
    private final Function<ReceiveMessage, byte[]> kdfKeyingInfoGenerator;

    /**
     * Constructs a new {@link HubMessageDecryptionMiddleware} instance.
     *
     * @param nodePrivateKey         the receiver's private key. The key must be an ECDH key.
     * @param messageCryptoService   service offering cryptographic functionality
     * @param hubClient              client for communicating with the Hub
     * @param kdfKeyingInfoGenerator generator for generating additional keying information based on a single message to
     *                               derive a symmetric key for encryption.
     *                               See {@link MessageCryptoService#deriveSymmetricKey(ECPrivateKey, ECPublicKey, byte[])}
     *                               for more information on how to choose appropriate values.
     */
    public HubMessageDecryptionMiddleware(
            @NotNull ECPrivateKey nodePrivateKey,
            @NotNull MessageCryptoService messageCryptoService,
            @NotNull HubClient hubClient,
            @NotNull Function<ReceiveMessage, byte[]> kdfKeyingInfoGenerator) {
        this.nodePrivateKey = requireNonNull(nodePrivateKey, "node private key must not be null");
        this.messageCryptoService = requireNonNull(messageCryptoService, "message crypto service must not be null");
        this.hubClient = requireNonNull(hubClient, "hub client must not be null");
        this.kdfKeyingInfoGenerator = requireNonNull(kdfKeyingInfoGenerator, "KDF keying info generator must not be null");
    }

    /**
     * Decrypts the given message.
     * <p>
     * Decryption is done by using a derived symmetric key based on parts of the sender's and recipient's key
     * information. The sender's key information is obtained from the Hub.
     *
     * @param message the message that shall get decrypted
     * @return The decrypted message. Might return qn {@link ReceiveMiddlewareException} as an error state is applying
     * this middleware fails.
     */
    @Override
    public Mono<ReceiveMessage> apply(@NotNull ReceiveMessage message) {
        if (message == null) {
            return Mono.error(new ReceiveMiddlewareException("message must not be null"));
        }

        return Mono.zip(
                        hubClient.fetchPublicKey(message.sender().nodeRobotId()),
                        Mono.just(kdfKeyingInfoGenerator.apply(message)))
                .onErrorMap(err -> new ReceiveMiddlewareException("failed to retrieve key material", err))
                .flatMap(keyMaterial -> {
                    try {
                        return Mono.just(messageCryptoService.deriveSymmetricKey(nodePrivateKey, keyMaterial.getT1(),
                                keyMaterial.getT2()));
                    } catch (InvalidKeyException e) {
                        return Mono.error(
                                new ReceiveMiddlewareException("failed to derive symmetric key for message encryption", e)
                        );
                    }
                })
                .flatMap(symmetricKey -> {
                    try {
                        return Mono.just(new ReceiveMessage(
                                message.sender(),
                                messageCryptoService.decryptMessage(symmetricKey, message.payload()),
                                message.context()
                        ));
                    } catch (InvalidKeyException | MessageCryptoException e) {
                        return Mono.error(
                                new ReceiveMiddlewareException("failed to decrypt message", e));
                    }
                })
                .onErrorMap(err -> !(err instanceof ReceiveMiddlewareException),
                        err -> new ReceiveMiddlewareException("an unexpected error occurred", err));
    }
}
