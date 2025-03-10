package de.privateaim.node_message_broker.message.emit;

/**
 * Represents recipient information of a message that shall be sent.
 *
 * @param nodeRobotId unique identifier of the robot account linked to the recipient's node
 */
public record EmitMessageRecipient(String nodeRobotId) {
}
