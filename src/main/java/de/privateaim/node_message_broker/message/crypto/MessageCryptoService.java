package de.privateaim.node_message_broker.message.crypto;

import jakarta.validation.constraints.NotNull;

import java.security.InvalidKeyException;
import java.security.Key;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;

/**
 * Represents a service offering cryptographic functionality for messages.
 */
public interface MessageCryptoService {

    /**
     * Encrypts a message using a symmetric key. For obtaining the symmetric key
     * {@link #deriveSymmetricKey(ECPrivateKey, ECPublicKey, byte[]) use a function} to derive it from key information
     * of 2 parties exchanging the message.
     *
     * @param derivedSymmetricKey a symmetric key for encrypting the message
     * @param message             the message itself that shall get encrypted
     * @return The encrypted message.
     * @throws InvalidKeyException    If the key is not usable by the implementation.
     * @throws MessageCryptoException If an error occurs while encrypting the message.
     */
    byte[] encryptMessage(@NotNull Key derivedSymmetricKey, @NotNull byte[] message)
            throws InvalidKeyException, MessageCryptoException;

    /**
     * Decrypts a message using a symmetric key. For obtaining the symmetric key
     * {@link #deriveSymmetricKey(ECPrivateKey, ECPublicKey, byte[]) use a function} to derive it from key information
     * of 2 parties exchanging the message.
     *
     * @param derivedSymmetricKey a symmetric key for decrypting the message
     * @param message             the message itself that shall get decrypted
     * @return The decrypted message.
     * @throws InvalidKeyException    If the key is not usable by the implementation.
     * @throws MessageCryptoException If an error occurs while decrypting the message.
     */
    byte[] decryptMessage(@NotNull Key derivedSymmetricKey, @NotNull byte[] message)
            throws InvalidKeyException, MessageCryptoException;

    /**
     * Derives a symmetric key from key information of 2 parties that are trying to exchange sensitive information.
     *
     * @param privateKey    the private key of one party. The key needs to be an ECDH key.
     * @param publicKey     the public key of the other party. The key needs to be an ECDH key.
     * @param KDFKeyingInfo additional keying information for deriving a symmetric key. This will affect the derivation
     *                      so chose information that is known by both communicating parties but different for every
     *                      message.
     * @return A derived symmetric key.
     * @throws InvalidKeyException If one of the keys is not usable by the implementation.
     */
    Key deriveSymmetricKey(@NotNull ECPrivateKey privateKey, @NotNull ECPublicKey publicKey,
                           @NotNull byte[] KDFKeyingInfo)
            throws InvalidKeyException;
}
