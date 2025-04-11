package de.privateaim.node_message_broker.message.crypto;

/**
 * Indicates that an error occurred in the cryptographic context.
 */
public final class MessageCryptoException extends Exception {
    public MessageCryptoException(String message, Throwable cause) {
        super(message, cause);
    }
}
