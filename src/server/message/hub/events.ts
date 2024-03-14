/**
 * Describes an event that is fired when a message from the central side (hub)
 * is received.
 */
export class HubMessageBroadcastEvent {
    analysisId: string;

    messagePayload: Record<string, any>;
}
