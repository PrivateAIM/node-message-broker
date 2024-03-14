import { Injectable } from '@nestjs/common';
import { HubClient } from './hub.client';

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
}
