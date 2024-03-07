import type {
    AnalysisNode, CollectionResourceResponse, SocketMessagesNamespaceCTSMessagesEvents, SocketMessagesNamespaceSTCEvents,
} from '@privateaim/core';
import { APIClient as HubApiClient } from '@privateaim/core';
import { Injectable } from '@nestjs/common';
import { Socket } from 'socket.io-client';

export type NodeBroadcastMessageMetadata = {
    analysisId: string
    projectId?: string
};

export type NodeBroadcastMessageHeader = {
    nodeIds: string[]
    metadata: NodeBroadcastMessageMetadata
};

@Injectable()
export class HubClient {
    private readonly hubApiClient: HubApiClient;

    private readonly hubMessageSocket: Socket<SocketMessagesNamespaceSTCEvents, SocketMessagesNamespaceCTSMessagesEvents>;

    constructor(hubApiClient: HubApiClient, hubMessageSocket: Socket<SocketMessagesNamespaceSTCEvents, SocketMessagesNamespaceCTSMessagesEvents>) {
        this.hubApiClient = hubApiClient;
        this.hubMessageSocket = hubMessageSocket;
    }

    async getAnalysisNodes(analysisId: string): Promise<AnalysisNode[]> {
        // TODO: add pagination later on!
        return this.hubApiClient.analysisNode.getMany({
            filter: {
                'analysis_id': analysisId,
            },
            include: {
                node: true,
            },
        }).then((res: CollectionResourceResponse<AnalysisNode>) => res.data);
    }

    produceNodeBroadcastMessage(header: NodeBroadcastMessageHeader, payload: Record<string, any>) {
        this.hubMessageSocket.emit('send', {
            to: header.nodeIds.map((nid) => ({ type: 'robot', id: nid })),
            data: payload,
            metadata: header.metadata,
        });
    }
}
