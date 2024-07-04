import {Context, Effect, Layer} from "effect";
import express from "express";
import {ServerRouter, ServerRouterLive} from "./router";

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
        const port = 3001
        const server = yield* Server
        const router = yield* ServerRouter

        server.use(router);

        yield* Effect.acquireRelease(
            Effect.sync(() =>
                server.listen(port, () =>
                    console.log(`Example app listening on port ${port}`)
                )
            ),
            (server) => Effect.sync(() => server.close())
        );
    })
);

const ExpressLive = Layer.sync(Server, () => express());

export const AppLive = ServerLive.pipe(
    Layer.provide(Layer.merge(ExpressLive, ServerRouterLive))
);
