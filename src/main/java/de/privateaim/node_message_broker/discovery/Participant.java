package de.privateaim.node_message_broker.discovery;

import de.privateaim.node_message_broker.discovery.api.ParticipantType;

/**
 * Represents a participant in its internal form.
 *
 * @param nodeId   unique identifier of a node
 * @param clientId  unique identifier of a node's client account (used for internal communications & authentication)
 * @param nodeType type of the node (default | aggregator)
 */
public record Participant(
        String nodeId,
        String clientId,
        ParticipantType nodeType
) {
}
