import { HttpException, HttpStatus, Injectable } from '@nestjs/common';
import { APIClient as HubApiClient } from '@privateaim/core';
import type { AnalysisNode, CollectionResourceResponse } from '@privateaim/core';

/**
 * Describes the type of a participating analysis node.
 */
export enum NodeType {
    DEFAULT = 'default',
    AGGREGATOR = 'aggregator',
    UNKNOWN = 'unknown',
}

/**
 * Describes the discovery result for participating analysis nodes.
 */
export type AnalysisNodeDiscoveryResult = {
    nodeId: string
    nodeType: NodeType
};

export const DISCOVERY_SERVICE = 'DISCOVERY SERVICE';

/**
 * Describes a service that offer discovery functionality.
 */
export interface DiscoveryService {
    discoverParticipatingAnalysisNodes(analysisId: string): Promise<AnalysisNodeDiscoveryResult[]>;
}

function extractNodeType(analysisNode: AnalysisNode): NodeType {
    const nodeTypeIndex = Object.values(NodeType)
        .findIndex((v) => v === analysisNode.node.type);

    if (nodeTypeIndex >= 0) {
        return Object.values(NodeType)[nodeTypeIndex] as NodeType;
    }
    return NodeType.UNKNOWN;
}

/**
 * A service for discovering analysis related information.
 * The discovery is backed by information present on the central side (hub).
 */
@Injectable()
export class HubBackedDiscoveryService implements DiscoveryService {
    private readonly hubApiClient: HubApiClient;

    constructor(hubApiClient: HubApiClient) {
        this.hubApiClient = hubApiClient;
    }

    /**
     * Discovers all participating nodes within an analysis.
     *
     * @param analysisId Identifies the analysis.
     * @returns A promise of all participating analysis nodes.
     */
    async discoverParticipatingAnalysisNodes(analysisId: string): Promise<AnalysisNodeDiscoveryResult[]> {
        return this.hubApiClient.analysisNode.getMany({
            filter: {
                analysis_id: analysisId,
            },
            include: {
                node: true,
            },
        })
            .then((res: CollectionResourceResponse<AnalysisNode>) => res.data)
            .then((analysisNodes: AnalysisNode[]) => analysisNodes.filter((n) => n.node.robot_id != null && n.node.robot_id !== undefined)
                .flatMap((analysisNode) => ({
                    nodeId: analysisNode.node.robot_id as string,
                    nodeType: extractNodeType(analysisNode),
                })))
            .catch((error) => {
                // TODO: maybe introduce another error type and translate to an HTTP error at the controller level.
                throw new HttpException('analysis nodes cannot be fetched from central side (hub)', HttpStatus.BAD_GATEWAY, {
                    cause: error,
                });
            });
    }
}
