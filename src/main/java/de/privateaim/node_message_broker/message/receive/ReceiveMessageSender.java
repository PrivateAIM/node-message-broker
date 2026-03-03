package de.privateaim.node_message_broker.message.receive;

/**
 * Represents sender information of a received message.
 *
 * @param nodeClientId unique identifier of the client account linked to the sender's node
 */
public record ReceiveMessageSender(
        String nodeClientId
) {
}
