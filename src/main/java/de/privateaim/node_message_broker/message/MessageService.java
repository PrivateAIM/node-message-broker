package de.privateaim.node_message_broker.message;

import com.fasterxml.jackson.databind.JsonNode;
import de.privateaim.node_message_broker.common.hub.HubClient;
import de.privateaim.node_message_broker.message.api.MessageBroadcastRequest;
import de.privateaim.node_message_broker.message.api.MessageRequest;
import de.privateaim.node_message_broker.message.emit.EmitMessage;
import de.privateaim.node_message_broker.message.emit.EmitMessageContext;
import de.privateaim.node_message_broker.message.emit.EmitMessageRecipient;
import de.privateaim.node_message_broker.message.emit.MessageEmitter;
import jakarta.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;

/**
 * A service dealing with messages.
 */
@Slf4j
@Service
public final class MessageService {

    private final MessageEmitter<EmitMessage> messageEmitter;
    private final HubClient hubClient;
    private final String selfClientId;

    public MessageService(MessageEmitter<EmitMessage> messageEmitter, HubClient hubClient, String selfClientId) {
        this.messageEmitter = requireNonNull(messageEmitter, "message emitter must not be null");
        this.hubClient = requireNonNull(hubClient, "hub client must not be null");
        requireNonNull(selfClientId, "self client id must not be null");
        if (selfClientId.isBlank()) {
            throw new IllegalArgumentException("self client id must not be blank");
        }
        this.selfClientId = selfClientId;
    }

    /**
     * Sends a message as a broadcast to all eligible recipients. That is, every node participating within the analysis
     * that this message is associated with.
     * <p>
     * This function does NOT guarantee that messages are sent to all recipients. If a message cannot be sent then
     * sending it not retried!
     *
     * @param analysisId unique identifier of the analysis that this message belongs to
     * @param messageReq request describing the message to be sent
     * @return A completed {@link Mono}.
     */
    Mono<Void> sendBroadcastMessage(@NotNull String analysisId, @NotNull MessageBroadcastRequest messageReq) {
        if (analysisId == null) {
            return Mono.error(new NullPointerException("analysis id must not be null"));
        }
        if (analysisId.isBlank()) {
            return Mono.error(new IllegalArgumentException("analysis id must not be blank"));
        }
        if (messageReq == null) {
            return Mono.error(new NullPointerException("message request must not be null"));
        }

        return getParticipantsOffAllOtherParticipatingAnalysisNodes(analysisId)
                .onErrorMap(err -> new AnalysisNodesLookupException("could not look up analysis nodes for analysis `%s`"
                        .formatted(analysisId), err))
                .flatMap(participants -> {
                    var participantsClientIds = participants.stream().map(p -> p.clientId).toList();
                    var messages = buildIndividualMessages(analysisId, messageReq.message, participantsClientIds);
                    return sendIndividualMessages(messages);
                });
    }

    /**
     * Sends a message to selected recipients. These recipients are described within the given request and MUST be part
     * of the analysis that this message belongs to.
     * <p>
     * This function does NOT guarantee that messages are sent to all selected recipients. If a message cannot be sent
     * then sending is not retried!
     *
     * @param analysisId unique identifier of the analysis that this message belongs to
     * @param messageReq request describing the message to be sent
     * @return A completed {@link Mono}.
     */
    Mono<Void> sendMessageToSelectedRecipients(@NotNull String analysisId, @NotNull MessageRequest messageReq) {
        if (analysisId == null) {
            return Mono.error(new NullPointerException("analysis id must not be null"));
        }
        if (analysisId.isBlank()) {
            return Mono.error(new IllegalArgumentException("analysis id must not be blank"));
        }
        if (messageReq == null) {
            return Mono.error(new NullPointerException("message request must not be null"));
        }
        if (messageReq.recipients.isEmpty()) {
            return Mono.error(new IllegalArgumentException("recipients must not be empty"));
        }

        return getParticipantsOffAllOtherParticipatingAnalysisNodes(analysisId)
                .onErrorMap(err -> new AnalysisNodesLookupException("could not look up analysis nodes", err))
                .flatMap(participants -> {
                    var participantsNodeIds = participants.stream().map(p -> p.nodeId).toList();
                    if (participantsNodeIds.containsAll(messageReq.recipients)) {
                        return sendIndividualMessages(buildIndividualMessages(analysisId, messageReq.message,
                                mapNodeIdsToClientIds(participants, messageReq.recipients)));
                    } else {
                        return Mono.error(new InvalidMessageRecipientsException("list of recipients contains at least " +
                                "one recipients that is not part of the analysis"));
                    }
                });
    }

    private Mono<Void> sendIndividualMessages(Flux<EmitMessage> messages) {
        return messages.flatMap(msg ->
                        messageEmitter.emitMessage(msg)
                                .doOnError(err -> log.error("emitting message `{}` to node `{}` failed",
                                        msg.context().messageId(), msg.recipient().nodeClientId(), err))
                                .onErrorResume(err -> Mono.empty()))
                .then(Mono.empty());
    }

    private Mono<Set<AnalysisParticipant>> getParticipantsOffAllOtherParticipatingAnalysisNodes(String analysisId) {
        return hubClient.fetchAnalysisNodes(analysisId)
                .map(nodes -> nodes.stream()
                        .map(n -> new AnalysisParticipant(n.node.id, n.node.clientId))
                        .filter(participant -> !participant.clientId.equals(selfClientId))
                        .collect(Collectors.toSet()));
    }

    private Flux<EmitMessage> buildIndividualMessages(String analysisId, JsonNode message,
                                                      List<String> recipientClientIds) {
        var messageId = UUID.randomUUID();
        return Flux.fromIterable(recipientClientIds.stream().map(clientId -> EmitMessage.builder()
                                .sendTo(new EmitMessageRecipient(clientId))
                                .withPayload(message.toString().getBytes(StandardCharsets.UTF_8))
                                .inContext(new EmitMessageContext(
                                        messageId,
                                        analysisId))
                                .build())
                        .toList())
                .onErrorMap(err -> new RuntimeException("could not prepare individual messages", err));
    }

    private List<String> mapNodeIdsToClientIds(Set<AnalysisParticipant> participants, List<String> nodeIds) {
        var nodeIdClientIdMapping = participants.stream()
                .map(p -> Map.entry(p.nodeId(), p.clientId()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        return nodeIds.stream()
                .map(nodeIdClientIdMapping::get)
                .toList();
    }

    private record AnalysisParticipant(
            String nodeId,
            String clientId // for internal communication usage
    ) {
    }
}
