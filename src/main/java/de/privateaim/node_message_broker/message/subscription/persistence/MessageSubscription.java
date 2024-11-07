package de.privateaim.node_message_broker.message.subscription.persistence;

import org.springframework.data.mongodb.core.index.TextIndexed;
import org.springframework.data.mongodb.core.mapping.MongoId;

import java.net.URL;
import java.util.UUID;

public record MessageSubscription(@MongoId UUID id, @TextIndexed String analysisId, URL webhookUrl) {
}
