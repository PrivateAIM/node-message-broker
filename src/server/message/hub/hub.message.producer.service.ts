import { Injectable } from "@nestjs/common";
import { HubClient } from "./hub.client";

@Injectable()
export class HubMessageProducerService {

    private readonly hubClient: HubClient;

    constructor(hubClient: HubClient) {
        this.hubClient = hubClient;
    }

    async produceNodeBroadcastMessage(analysisId: string, payload: Record<string, any>): Promise<any> {
        return this.hubClient.getAnalysisNodes(analysisId)
            .then(nodes => ({
                nodeIds: nodes
                    .filter(n => n.node.robot_id !== undefined && n.node.robot_id !== null)
                    .map(n => n.node.robot_id || ""),
                metadata: {
                    analysisId: analysisId
                }
            }))
            .then(header => this.hubClient.produceNodeBroadcastMessage(header, payload));
    }
}
