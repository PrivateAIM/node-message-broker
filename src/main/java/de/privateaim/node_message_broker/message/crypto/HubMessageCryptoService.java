package de.privateaim.node_message_broker.message.crypto;

import jakarta.validation.constraints.NotNull;
import org.bouncycastle.jcajce.spec.UserKeyingMaterialSpec;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.GCMParameterSpec;
import java.security.*;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;

import static java.util.Objects.requireNonNull;

/**
 * A service that offers cryptographic functionality tailored to be used when exchanging messages via the Hub.
 * It makes use of an AES 256-bit algorithm using GCM without any padding and makes use of ECDH keys created by the Hub
 * when registering a node.
 */
public final class HubMessageCryptoService implements MessageCryptoService {

    private static final int IV_LENGTH_BYTES = 12; // NIST recommends 12 Byte IVs
    private static final int AUTHENTICATION_TAG_LENGTH_BYTES = 16;
    private static final String KEY_AGREEMENT_ALGORITHM = "ECCDHwithSHA384CKDF";
    private static final String SECRET_KEY_ALGORITHM = "AES[256]";
    private static final String TRANSFORMATION_ALGORITHM = "AES_256/GCM/NoPadding";

    private final SecureRandom randomGenerator;

    public HubMessageCryptoService(@NotNull SecureRandom randomGenerator) {
        this.randomGenerator = requireNonNull(randomGenerator, "random generator must not be null");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Key deriveSymmetricKey(
            ECPrivateKey privateKey,
            ECPublicKey publicKey,
            byte[] KDFKeyingInfo
    ) throws InvalidKeyException {
        requireNonNull(privateKey, "private key must not be null");
        requireNonNull(publicKey, "public key must not be null");
        requireNonNull(KDFKeyingInfo, "KDF keying info must not be null");
        if (KDFKeyingInfo.length == 0) {
            // Check for avoiding insecure usage
            throw new IllegalArgumentException("KDF keying info must not be empty");
        }

        try {
            var agreement = KeyAgreement.getInstance(KEY_AGREEMENT_ALGORITHM);
            agreement.init(privateKey, new UserKeyingMaterialSpec(KDFKeyingInfo));
            agreement.doPhase(publicKey, true);
            return agreement.generateSecret(SECRET_KEY_ALGORITHM);
        } catch (NoSuchAlgorithmException e) {
            // can not happen -> ensured by provider
            throw new RuntimeException("check security provider for support of algorithms: '%s' and '%s'"
                    .formatted(KEY_AGREEMENT_ALGORITHM, SECRET_KEY_ALGORITHM), e);
        } catch (InvalidAlgorithmParameterException e) {
            throw new RuntimeException("unintended behavior", e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public byte[] encryptMessage(Key derivedSymmetricKey, byte[] message) throws InvalidKeyException, MessageCryptoException {
        requireNonNull(derivedSymmetricKey, "derived symmetric key must not be null");
        // NIST mandates the following requirement regarding a message 'P':
        //      len(P) ≤ (2^39)-256;
        // However, we are not enforcing this boundary here since Java arrays can only hold up to 2^31 elements.
        // Since this is a byte array that would equal to roughly 2GB which is way lower than the enforced requirement.
        requireNonNull(message, "message must not be null");

        var gcmParameterSpec = getGCMParameterSpec(generateGcmInitializationVector());

        try {
            var cipher = Cipher.getInstance(TRANSFORMATION_ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, derivedSymmetricKey, gcmParameterSpec);
            return prependIVToCiphertext(gcmParameterSpec.getIV(), cipher.doFinal(message));
        } catch (NoSuchAlgorithmException e) {
            // can not happen -> ensured by provider
            throw new RuntimeException("check security provider for support of algorithm: '%s'"
                    .formatted(TRANSFORMATION_ALGORITHM), e);
        } catch (NoSuchPaddingException e) {
            throw new RuntimeException("check security provider for support of a 'NoPadding' option", e);
        } catch (InvalidAlgorithmParameterException e) {
            throw new RuntimeException("unintended behavior", e);
        } catch (InvalidKeyException e) {
            throw e;
        } catch (Exception e) {
            throw new MessageCryptoException("an unexpected error occurred", e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public byte[] decryptMessage(Key derivedSymmetricKey, byte[] message) throws InvalidKeyException, MessageCryptoException {
        requireNonNull(derivedSymmetricKey, "derived symmetric key must not be null");
        requireNonNull(message, "message must not be null");
        if (message.length < IV_LENGTH_BYTES) {
            throw new IllegalArgumentException("message length must be greater than '%d' bytes but is only '%d' bytes"
                    .formatted(IV_LENGTH_BYTES, message.length));
        }

        var gcmParameterSpec = getGCMParameterSpec(extractIVFromCiphertext(message));

        try {
            var cipher = Cipher.getInstance(TRANSFORMATION_ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, derivedSymmetricKey, gcmParameterSpec);
            return cipher.doFinal(removeIVFromCiphertext(message));
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("check security provider for support of algorithm: '%s'"
                    .formatted(TRANSFORMATION_ALGORITHM), e);
        } catch (NoSuchPaddingException e) {
            throw new RuntimeException("check security provider for support of a 'NoPadding' option", e);
        } catch (InvalidAlgorithmParameterException e) {
            throw new RuntimeException("unintended behavior", e);
        } catch (InvalidKeyException e) {
            throw e;
        } catch (Exception e) {
            throw new MessageCryptoException("an unexpected error occurred", e);
        }
    }

    private byte[] generateGcmInitializationVector() {
        byte[] initializationVector = new byte[IV_LENGTH_BYTES];
        randomGenerator.nextBytes(initializationVector);
        return initializationVector;
    }

    private GCMParameterSpec getGCMParameterSpec(byte[] initializationVector) {
        return new GCMParameterSpec(AUTHENTICATION_TAG_LENGTH_BYTES * 8, initializationVector);
    }

    private byte[] prependIVToCiphertext(byte[] iv, byte[] ciphertext) {
        var encryptedMessageWithIV = new byte[IV_LENGTH_BYTES + ciphertext.length];
        System.arraycopy(iv, 0, encryptedMessageWithIV, 0, IV_LENGTH_BYTES);
        System.arraycopy(ciphertext, 0, encryptedMessageWithIV, IV_LENGTH_BYTES, ciphertext.length);
        return encryptedMessageWithIV;
    }

    private byte[] extractIVFromCiphertext(byte[] ciphertext) {
        var iv = new byte[IV_LENGTH_BYTES];
        System.arraycopy(ciphertext, 0, iv, 0, IV_LENGTH_BYTES);
        return iv;
    }

    private byte[] removeIVFromCiphertext(byte[] ciphertext) {
        var ciphertextWithoutIV = new byte[ciphertext.length - IV_LENGTH_BYTES];
        System.arraycopy(ciphertext, IV_LENGTH_BYTES, ciphertextWithoutIV, 0, ciphertextWithoutIV.length);
        return ciphertextWithoutIV;
    }
}
