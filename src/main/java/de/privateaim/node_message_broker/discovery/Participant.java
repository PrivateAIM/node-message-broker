package de.privateaim.node_message_broker.discovery;

import de.privateaim.node_message_broker.discovery.api.ParticipantType;

/**
 * Represents a participant in its internal form.
 *
 * @param nodeRobotId unique identifier of a node's robot account associated with it
 * @param nodeType    type of the node (default | aggregator)
 */
public record Participant(
        String nodeRobotId,
        ParticipantType nodeType
) {
}
