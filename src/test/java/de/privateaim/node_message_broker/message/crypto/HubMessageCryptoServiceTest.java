package de.privateaim.node_message_broker.message.crypto;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.Security;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.util.Random;

import static de.privateaim.node_message_broker.message.CryptoUtil.*;
import static org.junit.jupiter.api.Assertions.*;

public final class HubMessageCryptoServiceTest {

    private static final byte[] TEST_MESSAGE = "A_rand0m Test MESSAge".getBytes(StandardCharsets.UTF_8);

    private HubMessageCryptoService cryptoService;

    @BeforeAll
    public static void setUpSecurityProvider() {
        var securityProvider = new BouncyCastleProvider();
        Security.addProvider(securityProvider);
    }

    @BeforeEach
    public void setUp() {
        cryptoService = new HubMessageCryptoService(new SecureRandom());
    }


    @Test
    public void deriveSymmetricKey__privateKeyMustNotBeNull() {
        var publicKey = (ECPublicKey) generateECDHKeyPair().getPublic();

        assertThrows(
                NullPointerException.class,
                () -> cryptoService.deriveSymmetricKey(null, publicKey, "some-info".getBytes())
        );
    }

    @Test
    public void deriveSymmetricKey__publicKeyMustNotBeNull() {
        var privateKey = (ECPrivateKey) generateECDHKeyPair().getPrivate();

        assertThrows(
                NullPointerException.class,
                () -> cryptoService.deriveSymmetricKey(privateKey, null, "some-info".getBytes())
        );
    }

    @Test
    public void deriveSymmetricKey__KDFKeyingInfoMustNotBeNull() {
        var privateKey = (ECPrivateKey) generateECDHKeyPair().getPrivate();
        var publicKey = (ECPublicKey) generateECDHKeyPair().getPublic();

        assertThrows(
                NullPointerException.class,
                () -> cryptoService.deriveSymmetricKey(privateKey, publicKey, null)
        );
    }

    @Test
    public void deriveSymmetricKey__KDFKeyingInfoMustNotBeEmpty() {
        var privateKey = (ECPrivateKey) generateECDHKeyPair().getPrivate();
        var publicKey = (ECPublicKey) generateECDHKeyPair().getPublic();

        assertThrows(
                IllegalArgumentException.class,
                () -> cryptoService.deriveSymmetricKey(privateKey, publicKey, new byte[]{})
        );
    }

    @Test
    public void deriveSymmetricKey__DifferentKeyingInfoResultsInDifferentKey() throws InvalidKeyException {
        var privateKey = (ECPrivateKey) generateECDHKeyPair().getPrivate();
        var publicKey = (ECPublicKey) generateECDHKeyPair().getPublic();

        var testKeyingInfo = "test-keying-info".getBytes();
        var otherKeyingInfo = "other-keying-info".getBytes();

        var derivedSymmetricKey = cryptoService.deriveSymmetricKey(privateKey, publicKey, testKeyingInfo);
        var otherDerivedSymmetricKey = cryptoService.deriveSymmetricKey(privateKey, publicKey, otherKeyingInfo);

        assertNotEquals(derivedSymmetricKey, otherDerivedSymmetricKey);
    }

    @Test
    public void encryptMessage__derivedSymmetricKeyMustNotBeNull() {
        assertThrows(
                NullPointerException.class,
                () -> cryptoService.encryptMessage(null, TEST_MESSAGE)
        );
    }

    @Test
    public void encryptMessage__derivedSymmetricKeyIsNoAESKey() {
        var rsaKey = generateRSAKeyPair().getPublic();

        assertThrows(
                InvalidKeyException.class,
                () -> cryptoService.encryptMessage(rsaKey, TEST_MESSAGE)
        );
    }

    @Test
    public void encryptMessage__messageMustNotBeNull() throws InvalidKeyException {
        var senderPrivateKey = (ECPrivateKey) generateECDHKeyPair().getPrivate();
        var receiverPublicKey = (ECPublicKey) generateECDHKeyPair().getPublic();
        var derivedSymmetricKey = cryptoService.deriveSymmetricKey(senderPrivateKey, receiverPublicKey,
                "test-info".getBytes());

        assertThrows(
                NullPointerException.class,
                () -> cryptoService.encryptMessage(derivedSymmetricKey, null)
        );
    }

    @Test
    public void encryptMessage__emptyMessageCanGetEncrypted() throws InvalidKeyException {
        var senderPrivateKey = (ECPrivateKey) generateECDHKeyPair().getPrivate();
        var receiverPublicKey = (ECPublicKey) generateECDHKeyPair().getPublic();
        var derivedSymmetricKey = cryptoService.deriveSymmetricKey(senderPrivateKey, receiverPublicKey,
                "test-info".getBytes());

        var msg = new byte[0];

        var encryptedMessage = assertDoesNotThrow(
                () -> cryptoService.encryptMessage(derivedSymmetricKey, msg)
        );

        // at least the IV has to be present which is 12 bytes of size
        assertTrue(encryptedMessage.length > 12);
    }


    @Test
    public void decryptMessage__derivedSymmetricKeyMustNotBeNull() {
        assertThrows(
                NullPointerException.class,
                () -> cryptoService.decryptMessage(null, TEST_MESSAGE)
        );
    }

    @Test
    public void decryptMessage__derivedSymmetricKeyIsNoAESKey() {
        var rsaKey = generateRSAKeyPair().getPublic();

        assertThrows(
                InvalidKeyException.class,
                () -> cryptoService.decryptMessage(rsaKey, TEST_MESSAGE)
        );
    }

    @Test
    public void decryptMessage__messageMustNotBeNull() throws NoSuchAlgorithmException {
        var mockedDerivedSymmetricKey = generateAESKey();
        assertThrows(
                NullPointerException.class,
                () -> cryptoService.decryptMessage(mockedDerivedSymmetricKey, null)
        );
    }

    @Test
    public void decryptMessage__messageMustBeAtLeast12BytesLong() throws NoSuchAlgorithmException {
        // This is because the IV is prepended to the actual ciphertext.
        var msg = new byte[11];
        new Random().nextBytes(msg);

        var mockedDerivedSymmetricKey = generateAESKey();

        assertThrows(
                IllegalArgumentException.class,
                () -> cryptoService.decryptMessage(mockedDerivedSymmetricKey, msg)
        );
    }

    @Test
    public void restorePlaintext() throws InvalidKeyException, MessageCryptoException {
        var senderKeyPair = generateECDHKeyPair();
        var receiverKeyPair = generateECDHKeyPair();
        var testKeyingInfo = "test-keying-info".getBytes();

        // The following 2 keys are identical.
        // However, they're kept separately to display an extended usage example.
        var symmetricKeySender = cryptoService.deriveSymmetricKey((ECPrivateKey) senderKeyPair.getPrivate(),
                (ECPublicKey) receiverKeyPair.getPublic(),
                testKeyingInfo);
        var symmetricKeyReceiver = cryptoService.deriveSymmetricKey((ECPrivateKey) receiverKeyPair.getPrivate(),
                (ECPublicKey) senderKeyPair.getPublic(),
                testKeyingInfo);

        var encryptedMessage = cryptoService.encryptMessage(symmetricKeySender, TEST_MESSAGE);
        var restoredPlaintext = cryptoService.decryptMessage(symmetricKeyReceiver, encryptedMessage);

        assertArrayEquals(TEST_MESSAGE, restoredPlaintext);
    }

    @Test
    public void restorePlaintextFailsWithDerivedKeyOfDifferentKDFKeyingInfo() throws InvalidKeyException, MessageCryptoException {
        var senderKeyPair = generateECDHKeyPair();
        var receiverKeyPair = generateECDHKeyPair();
        var keyingInfoSender = "test-keying-info".getBytes();
        var keyingInfoReceiver = "other-keying-info".getBytes();

        // The following 2 keys are NOT identical due to the usage of different keying information while
        // deriving a key.
        // However, they're kept separately to display an extended usage example.
        var symmetricKeySender = cryptoService.deriveSymmetricKey((ECPrivateKey) senderKeyPair.getPrivate(),
                (ECPublicKey) receiverKeyPair.getPublic(),
                keyingInfoSender);
        var symmetricKeyReceiver = cryptoService.deriveSymmetricKey((ECPrivateKey) receiverKeyPair.getPrivate(),
                (ECPublicKey) senderKeyPair.getPublic(),
                keyingInfoReceiver);

        var encryptedMessage = cryptoService.encryptMessage(symmetricKeySender, TEST_MESSAGE);

        assertThrows(
                MessageCryptoException.class,
                () -> cryptoService.decryptMessage(symmetricKeyReceiver, encryptedMessage)
        );
    }
}
