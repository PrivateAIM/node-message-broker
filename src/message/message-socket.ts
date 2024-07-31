import {Context, Effect, Layer, Redacted} from "effect";
import {Socket} from "socket.io-client";
import {CTSMessagingEvents, STCMessagingEventName, STCMessagingEvents,} from '@privateaim/messenger-kit';
import {BrokerConfig, MessageBrokerConfig} from "../config";
import {HubAuthClient, HubAuthClientLive} from "../common/hub-auth-client";
import {HubClientLive} from "../common/hub-client";
import {ClientManager} from '@authup/core-realtime-kit';
import {EventSystem, EventSystemLive, EventType} from "../common/event-system";

/**
 * Describes a client for communicating with other nodes via Hub's message socket.
 */
export class SocketIoMessagingClient extends Context.Tag("@app/message/SocketIoMessagingClient")<
    SocketIoMessagingClient,
    Socket<STCMessagingEvents, CTSMessagingEvents>
>() {
}

/**
 * Indicates that there was an error while trying to connect to the Hub's message socket.
 */
class HubSocketConnectionError {
    readonly _tag = "HubConnectionError";

    constructor(readonly message: string, readonly cause: Error) {
    }
}

/**
 * Client for communicating with other nodes via Hub's message socket using Socket.io's socket implementation.
 */
export const SocketIoMessagingClientLive: Layer.Layer<
    SocketIoMessagingClient,
    never,
    never
> = Layer.effect(
    SocketIoMessagingClient,
    Effect.gen(function* () {
        const conf: MessageBrokerConfig = yield* BrokerConfig;
        const hubAuthClient = yield* HubAuthClient;
        const es = yield* EventSystem;

        const accessToken = yield* Effect.tryPromise({
            try: () => hubAuthClient.token.createWithRobotCredentials({
                id: conf.hub.auth.robotId,
                secret: Redacted.value(conf.hub.auth.robotSecret)
            }),
            catch: (err) => err
        }).pipe(
            Effect.map((res) => res.access_token),
            Effect.catchAll((defect) => {
                Effect.runSync(Effect.logError(defect));
                return Effect.dieMessage("cannot obtain access token from Hub's auth system");
            })
        );

        const socketUrl = new URL('/', conf.hub.messengerBaseUrl);
        const client = new ClientManager({
            url: socketUrl.toString(),
            token: accessToken
        });

        yield* Effect.logInfo("trying to connect to hub via socket at " + socketUrl);
        const socket = yield* Effect.tryPromise({
            try: () => client.connect(),
            catch: (err) => new HubSocketConnectionError("error while trying to connect to hub socket", err as Error)
        }).pipe(
            Effect.tap(() => Effect.logInfo("connected to hub socket")),
            Effect.catchAll((defect) => {
                Effect.runSync(Effect.logError(defect));
                return Effect.dieMessage("cannot create socket to communicate with the Hub side");
            })
        );

        socket.on('disconnect', (reason) => {
            if (reason === 'io server disconnect') {
                Effect.runSync(Effect.logInfo("manually reconnecting to hub"));
                socket.connect();
            }
        });

        socket.on(STCMessagingEventName.SEND, (data) => {
            es.emit(EventType.INCOMING_NODE_MESSAGE, data);
        });

        return socket;
    })
).pipe(
    Layer.provide(
        Layer.mergeAll(
            HubClientLive,
            HubAuthClientLive,
            EventSystemLive
        )
    )
);

