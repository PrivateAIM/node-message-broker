import {Effect, Layer, Runtime} from "effect";
import {Express} from "./server";

export const IndexRouteLive = Layer.effectDiscard(
    Effect.gen(function* () {
        const app = yield* Express
        const runFork = Runtime.runFork(yield* Effect.runtime<never>())

        app.get("/", (_, res) => {
            runFork(Effect.sync(() => res.send("Hello World!")))
        });
    })
);
