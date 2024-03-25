/* eslint-disable max-classes-per-file */
import { Injectable } from '@nestjs/common';
import { HubClient } from './hub.client';

type ErrorName =
    | 'INVALID_RECIPIENTS';

/**
 * Describes an error that occured while producing a message.
 */
export class MessageProducerError extends Error {
    override name: ErrorName;

    override message: string;

    override cause: any;

    constructor({
        name,
        message,
        cause,
    }: {
        name: ErrorName;
        message: string;
        cause?: any;
    }) {
        super();
        this.name = name;
        this.message = message;
        this.cause = cause;
    }
}

/**
 * A service for sending out messages to the central side (hub).
 */
@Injectable()
export class HubMessageProducerService {
    private readonly hubClient: HubClient;

    constructor(hubClient: HubClient) {
        this.hubClient = hubClient;
    }

    /**
     * Produces a broadcast message that is sent to all nodes associated with a given analysis ID.
     *
     * @param { string } analysisId identifies the analysis for which a broadcast message shall be sent
     * @param { Record<string, any> } payload the actual message that is sent
     * @returns
     */
    async produceNodeBroadcastMessage(analysisId: string, payload: Record<string, any>): Promise<void> {
        // Note: If an analysis does not exist this will still output an empty array of nodes!
        return this.hubClient.getAnalysisNodes(analysisId)
            .then((nodes) => ({
                nodeIds: nodes
                    .filter((n) => n.node.robot_id !== undefined && n.node.robot_id !== null)
                    .map((n) => n.node.robot_id || ''),
                metadata: {
                    analysisId,
                },
            }))
            .then((header) => this.hubClient.sendMessage(header, payload));
    }

    /**
     * Produces a message that is sent to all given recipients. The message will be associated with the given analysis ID.
     *
     * @param recipientNodeIds identifies all recipients of this message
     * @param analysisId identifies the analysis that this message is associated with
     * @param payload the actual message that is sent
     * @returns
     */
    async produceMessage(recipientNodeIds: Array<string>, analysisId: string, payload: Record<string, any>): Promise<void> {
        // Note: If an analysis does not exist this will still output an empty array of nodes!
        return this.hubClient.getAnalysisNodes(analysisId)
            .then((nodes) => nodes
                .filter((n) => n.node.robot_id !== undefined && n.node.robot_id !== null)
                .map((n) => n.node.robot_id || ''))
            .then((paticipatingNodeIds) => {
                const invalidRecipients = recipientNodeIds.filter((rn) => !paticipatingNodeIds.includes(rn));
                if (invalidRecipients.length > 0) {
                    throw new MessageProducerError({
                        name: 'INVALID_RECIPIENTS',
                        message: `recipient node ids '[${invalidRecipients}]' are invalid for analysis '${analysisId}' since they are no participants`,
                    });
                } else {
                    return recipientNodeIds;
                }
            })
            .then((recipientNodeIds) => this.hubClient.sendMessage({
                nodeIds: recipientNodeIds,
                metadata: {
                    analysisId,
                },
            }, payload));
    }
}
