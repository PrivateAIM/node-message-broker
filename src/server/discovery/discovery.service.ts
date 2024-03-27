/* eslint-disable max-classes-per-file */
import { Injectable } from '@nestjs/common';
import { APIClient as HubApiClient } from '@privateaim/core';
import type { AnalysisNode, CollectionResourceResponse } from '@privateaim/core';

type ErrorName =
    | 'FAILED_TO_FETCH_ANALYSIS_NODES'
    | 'SELF_NOT_FOUND';

export class DiscoveryError extends Error {
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
    discoverSelf(analysisId: string): Promise<AnalysisNodeDiscoveryResult>;
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

    private readonly myNodeId: string;

    constructor(hubApiClient: HubApiClient, myNodeId: string) {
        this.hubApiClient = hubApiClient;
        this.myNodeId = myNodeId;
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
                throw new DiscoveryError({
                    name: 'FAILED_TO_FETCH_ANALYSIS_NODES',
                    message: `nodes for analysis '${analysisId}' cannot be fetched from central side (hub)`,
                    cause: error,
                });
            });
    }

    /**
     * Discovers the participating node within an analysis that this message broker belongs to.
     *
     * @param analysisId Identifies the analysis.
     * @returns A promise of the participating analysis node that this message broker belongs to.
     */
    async discoverSelf(analysisId: string): Promise<AnalysisNodeDiscoveryResult> {
        return this.discoverParticipatingAnalysisNodes(analysisId)
            .then((nodes) => {
                const selfNode = nodes.find((n) => n.nodeId === this.myNodeId);
                if (selfNode !== undefined) {
                    return selfNode;
                }
                throw new DiscoveryError({
                    name: 'SELF_NOT_FOUND',
                    message: `cannot determine own identity for analysis '${analysisId}'`,
                });
            });
    }
}
