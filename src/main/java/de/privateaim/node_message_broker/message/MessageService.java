package de.privateaim.node_message_broker.message;

import de.privateaim.node_message_broker.common.hub.HubClient;
import de.privateaim.node_message_broker.message.api.hub.OutgoingHubMessage;
import de.privateaim.node_message_broker.message.api.hub.HubMessageMetadata;
import de.privateaim.node_message_broker.message.api.hub.HubMessageRecipient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

import static java.util.Objects.requireNonNull;

/**
 * A service dealing with messages.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public final class MessageService {

    private final MessageEmitter<OutgoingHubMessage> messageEmitter;

    private final HubClient hubClient;

    // TODO: add function to receive public keys for encryption!

    Mono<Void> sendBroadcastMessage(String analysisId, Object message) {
        requireNonNull(analysisId, "analysis id must not be null");
        if (analysisId.isBlank()) {
            throw new IllegalArgumentException("analysis id must not be blank");
        }

        return Mono.zip(
                        generateMessageId(),
                        hubClient.fetchAnalysisNodes(analysisId)
                                .map(participants -> participants.stream()
                                        .map(participant -> new HubMessageRecipient("robot", participant.node.robotId))
                                        .toList()))
                .map(messageIdWithRecipients ->
                        new OutgoingHubMessage(
                                messageIdWithRecipients.getT2(),
                                message,
                                new HubMessageMetadata(messageIdWithRecipients.getT1(), analysisId)))
                .flatMap(messageEmitter::emitMessage);
    }

    Mono<Void> sendMessage(String analysisId, List<String> robotIdsOfReceivingNodes, Object message) {
        requireNonNull(analysisId, "analysis id must not be null");
        if (analysisId.isBlank()) {
            throw new IllegalArgumentException("analysis id must not be blank");
        }
        requireNonNull(robotIdsOfReceivingNodes, "list of robot ids of receiving nodes must not be null");
        if (robotIdsOfReceivingNodes.isEmpty()) {
            throw new IllegalArgumentException("there has to be at least a single recipient");
        }

        // TODO: check if all recipients are actually part of the analysis!!!


        var messageRecipients = robotIdsOfReceivingNodes.stream().map(id -> new HubMessageRecipient("robot", id)).toList();

        return generateMessageId()
                .map(messageId ->
                        new OutgoingHubMessage(
                                messageRecipients,
                                message,
                                new HubMessageMetadata(messageId, analysisId)))
                .flatMap(messageEmitter::emitMessage);
    }

    Mono<UUID> generateMessageId() {
        return Mono.just(UUID.randomUUID());
    }
}
