import {Context, Effect, Layer} from "effect";
import {HubClient, HubClientLive} from "./hub-client";
import {BrokerConfig, MessageBrokerConfig} from "../config";
import {Schema} from "@effect/schema";
import {CollectionResourceResponse} from "@authup/core-http-kit";
import {AnalysisNode} from "@privateaim/core-kit";

/**
 * Describes the expected single node data sent from the Hub side when requesting analysis nodes.
 */
const HubNode = Schema.Struct({
    robot_id: Schema.String,
    type: Schema.String.pipe(Schema.filter(t => ["default", "aggregator"].includes(t)))
})

/**
 * Describes the expected data sent from the Hub side when requesting analysis nodes.
 */
const HubAnalysisNodes = Schema.Array(
    Schema.Struct({
        node: HubNode
    })
);

/**
 * Describes participating analysis node's type.
 */
export enum NodeType {
    DEFAULT = 'default',
    AGGREGATOR = 'aggregator',
    UNKNOWN = 'unknown',
}

/**
 * Describes a single node that has been discovered as a participant within an analysis.
 */
export type ParticipatingNode = {
    nodeId: string
    nodeType: NodeType
};

/**
 * Indicates that there was an error fetching data from the Hub side.
 */
export class HubFetchError {
    readonly _tag = "HubFetchError";

    constructor(readonly message: string, readonly cause: Error) {
    }
}

/**
 * Indicates that the Hub side returned unexpected data that cannot be processed.
 */
export class HubUnexpectedResultError {
    readonly _tag = "HubUnexpectedResultError";

    constructor(readonly message: string, readonly cause: Error) {
    }
}

/**
 * Indicates that the own node could not get identified within a set of analysis nodes.
 */
export class SelfNotFoundError {
    readonly _tag = "SelfNotFoundError";

    constructor(readonly message: string) {
    }
}

/**
 * Describes a service for discovery purposes.
 */
export class DiscoveryService extends Context.Tag("@app/DiscoveryService")<
    DiscoveryService,
    {
        /**
         * Discovers all participants of an analysis.
         *
         * @param analysisId identifies the analysis
         * @returns All participants associated with the analysis.
         */
        readonly discoverParticipatingAnalysisNodes: (analysisId: string) => Effect.Effect<
            ParticipatingNode[],
            HubFetchError | HubUnexpectedResultError,
            never>,

        /**
         * Discovers the requester's node within a set of analyses.
         *
         * @param analysisId identifies the analysis
         * @returns The requester's node.
         */
        readonly discoverSelf: (analysisId: string) => Effect.Effect<
            ParticipatingNode,
            HubFetchError | HubUnexpectedResultError | SelfNotFoundError,
            never>
    }
>() {
}

/**
 * {@link DiscoveryService} implementation that makes use of the {@link HubClient} to communicate with the Hub side.
 */
export const HubBasedDiscoveryServiceLive = Layer.effect(
    DiscoveryService,
    Effect.gen(function* () {
        const hubClient = yield* HubClient;
        const conf: MessageBrokerConfig = yield* BrokerConfig;

        /**
         * Extracts the {@link NodeType} from a single analysis node.
         *
         * @param node Single node as returned from the Hub side.
         */
        const extractNodeType = (node: typeof HubNode.Type): NodeType => {
            const nodeTypeIndex = Object.values(NodeType)
                .findIndex((v) => v === node.type.toString());

            if (nodeTypeIndex >= 0) {
                return Object.values(NodeType)[nodeTypeIndex] as NodeType;
            }
            return NodeType.UNKNOWN;
        }

        /**
         * Filters for only nodes that have a robot id defined.
         *
         * @param nodes Nodes as returned from the Hub side.
         */
        const filterForNodesWithRobotId = (nodes: typeof HubAnalysisNodes.Type): typeof HubAnalysisNodes.Type => {
            return nodes.filter(n => n.node.robot_id !== undefined && n.node.robot_id != null);
        }

        /**
         * Converts the nodes into a discovery result that is exposed to potential clients of the broker.
         *
         * @param nodes Nodes as returned from the Hub side.
         */
        const convertToDiscoveryResult = (nodes: typeof HubAnalysisNodes.Type): ParticipatingNode[] => {
            return nodes.flatMap(n => ({
                nodeId: n.node.robot_id.toString(),
                nodeType: extractNodeType(n.node)
            }));
        }

        /**
         * Discovers all participants of an analysis.
         *
         * @param analysisId identifies the analysis
         * @returns All participants associated with the analysis.
         */
        const discoverParticipatingAnalysisNodes = (analysisId: string) => Effect.gen(function* () {
            const fetchedAnalysisNodes = yield* Effect.tryPromise({
                try: () => hubClient.analysisNode.getMany({
                    filter: {
                        analysis_id: analysisId
                    },
                    include: {
                        node: true
                    }
                }).then((res: CollectionResourceResponse<AnalysisNode>) => res.data),
                catch: (err) => new HubFetchError("could not fetch analysis nodes from hub", err as Error)
            });

            const parsedAnalysisNodes = yield* Effect.try({
                try: () => Schema.decodeUnknownSync(HubAnalysisNodes, {errors: "all"})(fetchedAnalysisNodes),
                catch: (err) => new HubUnexpectedResultError("hub returned unexpected data", err as Error)
            });

            const nodesWithRobotId = filterForNodesWithRobotId(parsedAnalysisNodes);
            return convertToDiscoveryResult(nodesWithRobotId);
        });

        /**
         * Discovers the requester's node within a set of analyses.
         *
         * @param analysisId identifies the analysis
         * @returns The requester's node.
         */
        const discoverSelf = (analysisId: string) => Effect.gen(function* () {
            const participatingAnalysisNodes = yield* discoverParticipatingAnalysisNodes(analysisId);

            const self = participatingAnalysisNodes.find(n => n.nodeId === conf.hub.auth.robotId);
            if (self === undefined) {
                yield* Effect.fail(new SelfNotFoundError("self could not be found for analysis"));
            }
            return self!;
        });

        return {
            discoverParticipatingAnalysisNodes,
            discoverSelf
        }
    })
).pipe(
    Layer.provide(HubClientLive)
);
