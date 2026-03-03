package de.privateaim.node_message_broker.message.emit;

/**
 * Represents recipient information of a message that shall be sent.
 *
 * @param nodeClientId unique identifier of the client account linked to the recipient's node
 */
public record EmitMessageRecipient(String nodeClientId) {
}
