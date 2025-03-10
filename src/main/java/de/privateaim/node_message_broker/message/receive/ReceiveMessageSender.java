package de.privateaim.node_message_broker.message.receive;

/**
 * Represents sender information of a received message.
 *
 * @param nodeRobotId unique identifier of the robot account linked to the sender's node
 */
public record ReceiveMessageSender(
        String nodeRobotId
) {
}
