import { Injectable } from "@nestjs/common";
import { OnEvent } from "@nestjs/event-emitter";
import { HubMessageBroadcastEvent } from "./events";

@Injectable()
export class HubMessageConsumerService {

    @OnEvent('hub.message.broadcast.received')
    handleHubMessageBroadcastEvent(payload: HubMessageBroadcastEvent) {
        // TODO: forward to subscribed clients
        console.log(payload);
    }
}
