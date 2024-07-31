import {Context, Effect, Layer} from "effect";
import {Agent as HttpAgent} from 'node:http';
import {Agent as HttpsAgent} from 'node:https';
import {EventSystem, EventType, IncomingNodeMessageEvent} from "../common/event-system";
import {Schema} from "@effect/schema";
import {MongoDbBasedSubscriptionServiceLive, SubscriptionService} from "../common/node-subscription";
import axios, {AxiosError, AxiosRequestConfig} from "axios";

/**
 * Describes an HTTP client for communicating with the message receiving server of an analysis.
 */
class AnalysisMessageHttpClient extends Context.Tag("@app/message/AnalysisMessageHttpClient")<
    AnalysisMessageHttpClient,
    HttpAgent
>() {
}

/**
 * Describes an HTTPS client for communicating with the message receiving server of an analysis.
 */
class AnalysisMessageHttpsClient extends Context.Tag("@app/message/AnalysisMessageHttpsClient")<
    AnalysisMessageHttpsClient,
    HttpsAgent
>() {
}

/**
 * Production related implementation of {@link AnalysisMessageHttpClient}.
 */
const AnalysisMessageHttpClientLive = Layer.sync(AnalysisMessageHttpClient, () => new HttpAgent({
    keepAlive: true
}));

/**
 * Production related implementation of {@link AnalysisMessageHttpsClient}.
 */
const AnalysisMessageHttpsClientLive = Layer.sync(AnalysisMessageHttpsClient, () => new HttpsAgent({
    keepAlive: true
}));

/**
 * Indicates that a message got delivered to the subscriber but the subscriber responded with an error.
 */
export class DistributionRequestFailedError {
    readonly _tag = "DistributionRequestFailedError";

    constructor(readonly message: string, readonly cause: Error) {
    }
}

/**
 * Indicates that the distribution subscriber is not reachable.
 */
export class DistributionSubscriberNotReachableError {
    readonly _tag = "DistributionSubscriberNotReachableError";

    constructor(readonly message: string, readonly cause: Error) {
    }
}

/**
 * Indicates that the request to forward a message to a subscriber could not be set up.
 */
export class DistributionRequestSetupError {
    readonly _tag = "DistributionRequestSetupError";

    constructor(readonly message: string, readonly cause: Error) {
    }
}

/**
 * Describes a service for distributing incoming messages to an analysis.
 */
class AnalysisMessageDistributionService extends Context.Tag("@app/message/AnalysisMessageDistributionService")<
    AnalysisMessageDistributionService,
    {
        readonly sendMessage: (targets: URL[], message: typeof IncomingNodeMessageEvent.Type) => Effect.Effect<
            void,
            (DistributionRequestFailedError | DistributionSubscriberNotReachableError | DistributionRequestSetupError)[],
            never
        >
    }
>() {
}

/**
 * Production related implementation of {@link AnalysisMessageDistributionService}.
 */
const AnalysisMessageDistributionServiceLive = Layer.effect(
    AnalysisMessageDistributionService,
    Effect.gen(function* () {
        const httpAgent = yield* AnalysisMessageHttpClient;
        const httpsAgent = yield* AnalysisMessageHttpsClient;

        const sendMessage = (subscribers: URL[], message: typeof IncomingNodeMessageEvent.Type) => Effect.gen(function* () {
            // TODO: add retries later on...
            const subscriberRequestConfigs: AxiosRequestConfig<any>[] = subscribers.map((s) => ({
                method: 'POST',
                url: s.toString(),
                headers: {
                    'Content-Type': 'appliction/json',
                },
                data: message.data,
                httpAgent: httpAgent,
                httpsAgent: httpsAgent,
            }));

            yield* Effect.validateAll(subscriberRequestConfigs, (src: AxiosRequestConfig<any>) =>
                 Effect.tryPromise({
                    try: () => {
                        Effect.runSync(Effect.logInfo(`distributing message with id '${message.metadata.messageId}' to '${src.url}'`));
                        return axios.request(src);
                    },
                    catch: (err) => {
                        const error = err as AxiosError;
                        if (error.response) {
                            return new DistributionRequestFailedError(`trying to send message with id '${message.metadata.messageId}' to '${src.url}' failed with status code '${error.response.status}': ${error.response.data}`, error);
                        } else if (error.request) {
                            return new DistributionSubscriberNotReachableError(`could not get a response from '${src.url}' when sending message with id '${message.metadata.messageId}'`, error);
                        } else {
                            return new DistributionRequestSetupError(`setting up request for message with id '${message.metadata.messageId}' to '${src.url}' failed`, error);
                        }
                    }
                })
            );

            return;
        });

        return {
            sendMessage
        }
    })
).pipe(
    Layer.provide(Layer.mergeAll(
        AnalysisMessageHttpClientLive,
        AnalysisMessageHttpsClientLive
    ))
);

/**
 * Indicates that a message could not be read.
 */
class MalformedMessageError {
    readonly _tag = "MalformedMessageError";

    constructor(readonly message: string, readonly cause: Error) {
    }
}

/**
 * Production related distributor which is hooking into the {@link EventSystem} to receive events used to forward
 * incoming messages to their associated subscribers.
 */
export const AnalysisMessageDistributorLive: Layer.Layer<
    never,
    never,
    EventSystem
> = Layer.effectDiscard(
    Effect.gen(function* () {
        const es = yield* EventSystem;
        const messageDistributionSvc = yield* AnalysisMessageDistributionService;
        const subscriptionService = yield* SubscriptionService;

        yield* Effect.logInfo(`registering event handler for event '${EventType.INCOMING_NODE_MESSAGE}'`);
        es.on(EventType.INCOMING_NODE_MESSAGE, (msg: any) => Effect.runCallback(Effect.gen(function* () {
                const message = yield* Effect.try({
                    try: () => Schema.decodeUnknownSync(IncomingNodeMessageEvent, {errors: "all"})(msg),
                    catch: (err) => new MalformedMessageError("cannot read incoming message", err as Error)
                });

                yield* Effect.logInfo(`received message '${message.metadata.messageId}' from node '${message.from.id}`);

                const subscribers = (yield* subscriptionService.findSubscriptionsByAnalysis(message.metadata.analysisId))
                    .flatMap(s => s.webhookUrl);

                if (subscribers.length == 0) {
                    yield* Effect.logInfo(`there are no subscribers for received message with id '${message.metadata.messageId}'`);
                }

                yield* messageDistributionSvc.sendMessage(subscribers, message);
            }).pipe(
                Effect.catchAll((e) => {
                    return Effect.logError(e);
                })
            )
        ))
        ;
    })
).pipe(
    Layer.provide(Layer.mergeAll(
        AnalysisMessageHttpClientLive,
        AnalysisMessageHttpsClientLive,
        AnalysisMessageDistributionServiceLive,
        MongoDbBasedSubscriptionServiceLive
    ))
);
