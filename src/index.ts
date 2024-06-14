import {Effect, Layer} from "effect";
import {AppLive} from "./server";

Effect.runFork(Layer.launch(AppLive));
