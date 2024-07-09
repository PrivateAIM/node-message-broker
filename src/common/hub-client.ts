import {Context, Effect, Layer, Redacted} from "effect";
import {APIClient} from "@privateaim/core";
import {mountClientResponseErrorTokenHook} from "@authup/core";
import {BrokerConfig, MessageBrokerConfig} from "../config";

/**
 * Describes a client for communicating with the Hub side.
 */
export class HubClient extends Context.Tag("@app/common/HubClient")<
    HubClient,
    APIClient
>() {
}

/**
 * Client for communicating with the Hub side backed by the official {@link APIClient}.
 */
export const HubClientLive: Layer.Layer<
    HubClient,
    never,
    never
> = Layer.effect(
    HubClient,
    Effect.gen(function* () {
        const conf: MessageBrokerConfig = yield* BrokerConfig;

        let hubApiClient = new APIClient({
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


