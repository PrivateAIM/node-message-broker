import type {
    AnalysisNode, CollectionResourceResponse, SocketMessagesNamespaceCTSMessagesEvents, SocketMessagesNamespaceSTCEvents,
} from '@privateaim/core';
import { APIClient as HubApiClient } from '@privateaim/core';
import { Injectable } from '@nestjs/common';
import { Socket } from 'socket.io-client';

/**
 * Metadata of a message.
 */
export type NodeBroadcastMessageMetadata = {
    analysisId: string
    projectId?: string
};

/**
 * Message information that also includes routing information.
 */
export type NodeBroadcastMessageHeader = {
    nodeIds: string[]
    metadata: NodeBroadcastMessageMetadata
};

/**
 * A simple client that offers functionality for the central side (hub) communication.
 * This includes API and message socket communication.
 */
@Injectable()
export class HubClient {
    private readonly hubApiClient: HubApiClient;

    private readonly hubMessageSocket: Socket<SocketMessagesNamespaceSTCEvents, SocketMessagesNamespaceCTSMessagesEvents>;

    constructor(hubApiClient: HubApiClient, hubMessageSocket: Socket<SocketMessagesNamespaceSTCEvents, SocketMessagesNamespaceCTSMessagesEvents>) {
        this.hubApiClient = hubApiClient;
        this.hubMessageSocket = hubMessageSocket;
    }

    /**
     * Gets all nodes that are associated with a specific analysis.
     *
     * @param analysisId identifies the analysis
     * @returns A promise of all associated nodes.
     */
    async getAnalysisNodes(analysisId: string): Promise<AnalysisNode[]> {
        // TODO: add pagination later on!
        return this.hubApiClient.analysisNode.getMany({
            filter: {
                analysis_id: analysisId,
            },
            include: {
                node: true,
            },
        }).then((res: CollectionResourceResponse<AnalysisNode>) => res.data);
    }

    /**
     * Sends a message to the central side (hub).
     *
     * @param header message information (also used for routing)
     * @param payload the actual message
     */
    sendMessage(header: NodeBroadcastMessageHeader, payload: Record<string, any>) {
        this.hubMessageSocket.emit('send', {
            to: header.nodeIds.map((nid) => ({ type: 'robot', id: nid })),
            data: payload,
            metadata: header.metadata,
        });
    }
}
