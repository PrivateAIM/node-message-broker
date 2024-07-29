import {Context, Effect, Layer} from "effect";
import express from "express";
import {ServerRouter, ServerRouterLive} from "./router";
import {BrokerConfig, MessageBrokerConfig} from "./config";
import {AnalysisMessageDistributorLive} from "./message/message-distributor";
import {EventSystemLive} from "./common/event-system";

export class Server extends Context.Tag("Server")<
    Server,
    ReturnType<typeof express>
>() {
}

// Server Setup
const ServerLive: Layer.Layer<
    never,
    never,
    Server | ServerRouter
> = Layer.scopedDiscard(
    Effect.gen(function* () {
        const conf: MessageBrokerConfig = yield* BrokerConfig;
        const server = yield* Server
        const router = yield* ServerRouter

        server.use(router);

        yield* Effect.acquireRelease(
            Effect.sync(() =>
                server.listen(conf.serverPort, () =>
                    Effect.runSync(Effect.logInfo(`message broker listening on port ${conf.serverPort}`))
                )
            ),
            (server) => Effect.sync(() => server.close())
        );
    })
);

const ExpressLive = Layer.sync(Server, () => express());

export const AppLive = Layer.mergeAll(
    AnalysisMessageDistributorLive.pipe(
        Layer.provide(EventSystemLive)
    ),
    ServerLive.pipe(
        Layer.provide(Layer.merge(ExpressLive, ServerRouterLive))
    )
);
