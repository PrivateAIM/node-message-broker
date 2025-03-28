package de.privateaim.node_message_broker.message.subscription.persistence;

import org.springframework.data.mongodb.core.index.TextIndexed;
import org.springframework.data.mongodb.core.mapping.MongoId;

import java.net.URL;
import java.util.UUID;

/**
 * Represents a single message subscription.
 *
 * @param id         unique identifier of a subscription
 * @param analysisId unique identifier of an analysis whose messages are part of the subscription
 * @param webhookUrl target URL to forward any received message to
 */
public record MessageSubscription(@MongoId UUID id, @TextIndexed String analysisId, URL webhookUrl) {
}
