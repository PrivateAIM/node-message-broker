package de.privateaim.node_message_broker.message;

import org.bouncycastle.jce.provider.BouncyCastleProvider;

import javax.crypto.KeyGenerator;
import java.security.Key;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;

public final class CryptoUtil {

    private CryptoUtil() {
    }

    public static KeyPair generateECDHKeyPair() {
        try {
            var generator = KeyPairGenerator.getInstance("EC", new BouncyCastleProvider());
            generator.initialize(256); // for secp256r1
            return generator.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    public static Key generateAESKey() throws NoSuchAlgorithmException {
        var keyGen = KeyGenerator.getInstance("AES");
        keyGen.init(256);
        return keyGen.generateKey();
    }

    public static KeyPair generateRSAKeyPair() {
        try {
            var generator = KeyPairGenerator.getInstance("RSA", new BouncyCastleProvider());
            generator.initialize(1024);
            return generator.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }
}
