import {Layer} from "effect";
import {NodeRuntime} from "@effect/platform-node"
import {AppLive} from "./server";

NodeRuntime.runMain(Layer.launch(AppLive));
