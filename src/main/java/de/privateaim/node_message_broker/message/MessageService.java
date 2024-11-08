package de.privateaim.node_message_broker.message;

import de.privateaim.node_message_broker.common.hub.HubClient;
import de.privateaim.node_message_broker.message.api.hub.HubSendMessage;
import de.privateaim.node_message_broker.message.api.hub.HubSendMessageMetaData;
import de.privateaim.node_message_broker.message.api.hub.HubSendMessageRecipient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

import static java.util.Objects.requireNonNull;

@Service
@RequiredArgsConstructor
public final class MessageService {

    private final MessageEmitter messageEmitter;

    private final HubClient hubClient;

    Mono<Void> sendBroadcastMessage(String analysisId, byte[] message) {
        requireNonNull(analysisId, "analysis id must not be null");
        if (analysisId.isBlank()) {
            throw new IllegalArgumentException("analysis id must not be blank");
        }

        return Mono.zip(
                        generateMessageId(),
                        hubClient.fetchAnalysisNodes(analysisId)
                                .map(participants -> participants.stream()
                                        .map(participant -> new HubSendMessageRecipient("robot", participant.node.robotId))
                                        .toList()))
                .map(messageIdWithRecipients ->
                        new HubSendMessage(
                                messageIdWithRecipients.getT2(),
                                message,
                                new HubSendMessageMetaData(messageIdWithRecipients.getT1(), analysisId)))
                .flatMap(messageEmitter::emitMessage);
    }

    Mono<Void> sendMessage(String analysisId, List<String> robotIdsOfReceivingNodes, byte[] message) {
        requireNonNull(analysisId, "analysis id must not be null");
        if (analysisId.isBlank()) {
            throw new IllegalArgumentException("analysis id must not be blank");
        }
        requireNonNull(robotIdsOfReceivingNodes, "list of robot ids of receiving nodes must not be null");
        if (robotIdsOfReceivingNodes.isEmpty()) {
            throw new IllegalArgumentException("there has to be at least a single recipient");
        }

        // TODO: check if all recipients are actually part of the analysis!!!


        var messageRecipients = robotIdsOfReceivingNodes.stream().map(id -> new HubSendMessageRecipient("robot", id)).toList();

        return generateMessageId()
                .map(messageId ->
                        new HubSendMessage(
                                messageRecipients,
                                message,
                                new HubSendMessageMetaData(messageId, analysisId)))
                .flatMap(messageEmitter::emitMessage);
    }

    Mono<UUID> generateMessageId() {
        return Mono.just(UUID.randomUUID());
    }
}
