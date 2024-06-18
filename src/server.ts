import {Context, Effect, Layer} from "effect";
import express from "express";
import {IndexRouteLive} from "./router";
import {HealthRouteLive} from "./health/health";

export class Express extends Context.Tag("Express")<
    Express,
    ReturnType<typeof express>
>() {
}

// Server Setup
const ServerLive = Layer.scopedDiscard(
    Effect.gen(function* () {
        const port = 3001
        const app = yield* Express

        yield* Effect.acquireRelease(
            Effect.sync(() =>
                app.listen(port, () =>
                    console.log(`Example app listening on port ${port}`)
                )
            ),
            (server) => Effect.sync(() => server.close())
        );
    })
);

const ExpressLive = Layer.sync(Express, () => express());

export const AppLive = ServerLive.pipe(
    Layer.provide(IndexRouteLive),
    Layer.provide(HealthRouteLive),
    Layer.provide(ExpressLive)
);
