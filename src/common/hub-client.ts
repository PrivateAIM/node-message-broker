import {Context, Effect, Layer, Redacted} from "effect";
import {BrokerConfig, MessageBrokerConfig} from "../config";
import {mountClientResponseErrorTokenHook} from "@authup/core-http-kit";
import {Client} from "@privateaim/core-http-kit";

/**
 * Describes a client for communicating with the Hub side.
 */
export class HubClient extends Context.Tag("@app/common/HubClient")<
    HubClient,
    Client
>() {
}

/**
 * Client for communicating with the Hub side backed by the official {@link Client}.
 */
export const HubClientLive: Layer.Layer<
    HubClient,
    never,
    never
> = Layer.effect(
    HubClient,
    Effect.gen(function* () {
        const conf: MessageBrokerConfig = yield* BrokerConfig;

        let hubApiClient = new Client({
            baseURL: conf.hub.baseUrl
        });

        mountClientResponseErrorTokenHook(hubApiClient, {
            baseURL: conf.hub.auth.baseUrl,
            tokenCreator: {
                type: "robot",
                id: conf.hub.auth.robotId,
                secret: Redacted.value(conf.hub.auth.robotSecret)
            }
        });

        return hubApiClient;
    })
);


