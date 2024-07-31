import {Context, Data, Effect, Layer, Runtime} from "effect";
import express, {IRouter, Request, Response} from "express";
import {Schema} from "@effect/schema";
import {SocketIoMessagingClient, SocketIoMessagingClientLive} from "./message-socket";
import {
    DiscoveryService,
    HubBasedDiscoveryServiceLive,
    HubFetchError,
    HubUnexpectedResultError
} from "../common/hub-discovery";
import {CTSMessagingEventName} from "@privateaim/messenger-kit";
import {v4} from "uuid";

/**
 * Describes an express based router for message related endpoints.
 */
export class MessageRouter extends Context.Tag("@app/MessageRouter")<
    MessageRouter,
    IRouter
>() {
}

/**
 * Indicates that a message broadcast request is malformed and cannot be processed.
 */
class MalformedMessageBroadcastRequest {
    readonly _tag = "MalformedMessageBroadcastRequest";

    constructor(readonly message: string, readonly cause: Error) {
    }
}

/**
 * Indicates that a message request is malformed and cannot be processed.
 */
class MalformedMessageRequest {
    readonly _tag = "MalformedMessageRequest";

    constructor(readonly message: string, readonly cause: Error) {
    }
}

/**
 * Indicates that a message request contains invalid message recipients.
 */
class InvalidMessageRecipientsError extends Data.TaggedError("InvalidMessageRecipientsError")<{
    message: string
}> {
}

/**
 * Describes the request necessary for sending a broadcast message.
 */
const MessageBroadcastRequest = Schema.required(
    Schema.Struct({
        analysisId: Schema.String.pipe(Schema.minLength(1)),
        message: Schema.Struct({})
    })
)

/**
 * Describes the request necessary for sending a message to specific recipients.
 */
const MessageRequest = Schema.required(
    Schema.Struct({
        analysisId: Schema.String.pipe(Schema.minLength(1)),
        recipients: Schema.NonEmptyArray(Schema.String).pipe(
            Schema.filter((recipients) =>
                recipients.reduce((acc, r) => {
                    return acc && r.length > 0
                }, true)
            )
        ),
        message: Schema.Struct({})
    })
);

/**
 * Describes a service for HUB based messaging purposes.
 */
class MessageService extends Context.Tag("@app/MessageService")<
    MessageService,
    {
        /**
         * Sends a broadcast message to all participants of an analysis.
         *
         * @param messageBroadcastRequest information about the message that shall be sent.
         */
        readonly sendBroadcastMessage: (messageBroadcastRequest: typeof MessageBroadcastRequest.Type) => Effect.Effect<
            void,
            MalformedMessageBroadcastRequest | InvalidMessageRecipientsError | HubFetchError | HubUnexpectedResultError,
            never
        >

        /**
         * Sends a message to selected recipients.
         *
         * @param messageRequest information about the message that shall be sent.
         */
        readonly sendMessage: (messageRequest: typeof MessageRequest.Type) => Effect.Effect<
            void,
            MalformedMessageRequest | InvalidMessageRecipientsError | HubFetchError | HubUnexpectedResultError,
            never
        >

    }
>() {
}

/**
 * Production related implementation of {@link MessageService} based on a Socket.io socket.
 */
const SocketBasedMessageServiceLive = Layer.effect(
    MessageService,
    Effect.gen(function* () {
        const messageSocketClient = yield* SocketIoMessagingClient;
        const discoverySvc = yield* DiscoveryService;

        /**
         * Sends a broadcast message to all participants of an analysis.
         *
         * @param messageBroadcastRequest information about the message that shall be sent.
         */
        const sendBroadcastMessage = (messageBroadcastRequest: typeof MessageBroadcastRequest.Type) => Effect.gen(function* () {
            const parsedMessageRequest = yield* Effect.try({
                try: () => Schema.decodeUnknownSync(MessageBroadcastRequest, {errors: "all"})(messageBroadcastRequest),
                catch: (err) => new MalformedMessageBroadcastRequest("received malformed message broadcast request data", err as Error)
            });

            const participatingAnalysisNodes = yield* discoverySvc.discoverParticipatingAnalysisNodes(parsedMessageRequest.analysisId);

            const messageId = v4();
            messageSocketClient.emit(CTSMessagingEventName.SEND,
                {
                    to: participatingAnalysisNodes.map((pan) => ({
                        type: "robot",
                        id: pan.nodeId
                    })),
                    data: parsedMessageRequest.message,
                    metadata: {
                        messageId: messageId,
                        analysisId: parsedMessageRequest.analysisId
                    }
                }
            );
            yield* Effect.logInfo(`sent broadcast message with id '${messageId}' to nodes [${participatingAnalysisNodes.flatMap(pan => pan.nodeId).join(",")}]`)

            return Effect.void
        });

        /**
         * Sends a message to selected recipients.
         *
         * @param messageRequest information about the message that shall be sent.
         */
        const sendMessage = (messageRequest: typeof MessageRequest.Type) => Effect.gen(function* () {
            const parsedMessageRequest = yield* Effect.try({
                try: () => Schema.decodeUnknownSync(MessageRequest, {errors: "all"})(messageRequest),
                catch: (err) => new MalformedMessageRequest("received malformed message request data", err as Error)
            });

            const participatingAnalysisNodes = yield* discoverySvc.discoverParticipatingAnalysisNodes(parsedMessageRequest.analysisId);

            const invalidRecipients = parsedMessageRequest.recipients.filter((rn) =>
                !participatingAnalysisNodes.flatMap((pan) => pan.nodeId)
                    .includes(rn));
            if (invalidRecipients.length > 0) {
                yield* new InvalidMessageRecipientsError({message: `recipient node ids '[${invalidRecipients}]' are invalid for analysis '${parsedMessageRequest.analysisId}' since they are no participants`});
            }

            const messageId = v4();
            messageSocketClient.emit(CTSMessagingEventName.SEND,
                {
                    to: parsedMessageRequest.recipients.map((r) => ({
                        type: "robot",
                        id: r
                    })),
                    data: parsedMessageRequest.message,
                    metadata: {
                        messageId: messageId,
                        analysisId: parsedMessageRequest.analysisId
                    }
                }
            );
            yield* Effect.logInfo(`sent message with id '${messageId}' to nodes [${parsedMessageRequest.recipients.join(",")}]`)

            return Effect.void
        });

        return {
            sendBroadcastMessage,
            sendMessage
        }
    })
).pipe(
    Layer.provide(
        Layer.mergeAll(
            SocketIoMessagingClientLive,
            HubBasedDiscoveryServiceLive
        )
    )
);

/**
 * Controller for messaging related requests.
 */
const MessageControllerLive: Layer.Layer<
    MessageRouter,
    never,
    MessageRouter
> = Layer.effect(
    MessageRouter,
    Effect.gen(function* () {
        let router = yield* MessageRouter;
        const messageSvc = yield* MessageService;
        const runFork = Runtime.runFork(yield* Effect.runtime<never>())

        yield* SocketIoMessagingClient;

        router.post("/analyses/:analysisId/messages/broadcasts", (req: Request, res: Response) => {
            const {analysisId} = req.params;

            runFork(
                messageSvc.sendBroadcastMessage({
                    analysisId: analysisId,
                    message: req.body
                })
                    .pipe(
                        Effect.map(() =>
                            res.status(201).send()
                        ),
                        Effect.catchTags({
                            MalformedMessageBroadcastRequest: (e: MalformedMessageBroadcastRequest) => Effect.succeed(
                                res.status(400).send(
                                    JSON.stringify(e)
                                )
                            ),
                            InvalidMessageRecipientsError: (e: InvalidMessageRecipientsError) => Effect.succeed(
                                res.status(400).send(
                                    JSON.stringify(e)
                                )
                            ),
                            HubFetchError: (e: HubFetchError) => Effect.succeed(
                                res.status(502).send(
                                    JSON.stringify(e)
                                )
                            ),
                            HubUnexpectedResultError: (e: HubUnexpectedResultError) => Effect.succeed(
                                res.status(502).send(
                                    JSON.stringify(e)
                                )
                            )
                        }),
                        Effect.catchAll((e: Error) => Effect.succeed(
                            res.status(500).send(
                                JSON.stringify(e)
                            )
                        ))
                    )
            )
        });

        router.post("/analyses/:analysisId/messages", (req: Request, res: Response) => {
            const {analysisId} = req.params;

            runFork(
                messageSvc.sendMessage({
                    analysisId: analysisId,
                    recipients: req.body.recipients,
                    message: req.body.message
                })
                    .pipe(
                        Effect.map(() =>
                            res.status(201).send()
                        ),
                        Effect.catchTags({
                            MalformedMessageRequest: (e: MalformedMessageRequest) => Effect.succeed(
                                res.status(400).send(
                                    JSON.stringify(e)
                                )
                            ),
                            InvalidMessageRecipientsError: (e: InvalidMessageRecipientsError) => Effect.succeed(
                                res.status(400).send(
                                    JSON.stringify(e)
                                )
                            ),
                            HubFetchError: (e: HubFetchError) => Effect.succeed(
                                res.status(502).send(
                                    JSON.stringify(e)
                                )
                            ),
                            HubUnexpectedResultError: (e: HubUnexpectedResultError) => Effect.succeed(
                                res.status(502).send(
                                    JSON.stringify(e)
                                )
                            )
                        }),
                        Effect.catchAll((e: Error) => Effect.succeed(
                            res.status(500).send(
                                JSON.stringify(e)
                            )
                        ))
                    )
            )
        })

        return router
    })
).pipe(
    Layer.provide(
        Layer.mergeAll(
            SocketBasedMessageServiceLive,
            SocketIoMessagingClientLive
        )
    )
);

/**
 * Express router for messaging related endpoints.
 */
export const MessageRouterLive = MessageControllerLive.pipe(
    Layer.provide(Layer.sync(MessageRouter, () => express.Router()))
)

