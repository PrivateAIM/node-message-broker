import {Context, Layer} from "effect";
import EventEmitter from "node:events";
import {Schema} from "@effect/schema";

/**
 * Event type as used throughout this application.
 */
export enum EventType {
    INCOMING_NODE_MESSAGE = "INCOMING_NODE_MESSAGE",
}

/**
 * Defines the structure of an event of type {@link EventType.INCOMING_NODE_MESSAGE}.
 */
export const IncomingNodeMessageEvent = Schema.required(
    Schema.Struct({
        from: Schema.required(Schema.Struct({
            type: Schema.String.pipe(Schema.filter(t => ["robot", "user"].includes(t))),
            id: Schema.String.pipe(Schema.minLength(1))
        })),
        data: Schema.Struct({}),
        metadata: Schema.required(Schema.Struct({
            messageId: Schema.String.pipe(Schema.minLength(1)),
            analysisId: Schema.String.pipe(Schema.minLength(1))
        }))
    })
);

/**
 * Describes the event system used throughout this application.
 */
export class EventSystem extends Context.Tag("@app/common/EventSystem")<
    EventSystem,
    EventEmitter
>() {
}

/**
 * Production related implementation of {@link EventSystem}.
 */
export const EventSystemLive = Layer.sync(EventSystem, () => new EventEmitter());
