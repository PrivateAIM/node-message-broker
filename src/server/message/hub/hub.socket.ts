import type { ManagerOptions, Socket, SocketOptions } from 'socket.io-client';
import {
    connect as connectSocket
} from 'socket.io-client';
import type {
    SocketMessagesNamespaceCTSMessagesEvents,
    SocketMessagesNamespaceSTCEvents,
} from '@privateaim/core';
import type { EventEmitter2 } from '@nestjs/event-emitter';
import { HubMessageBroadcastEvent } from './events';

export type HubMessageSocket = typeof Socket<SocketMessagesNamespaceSTCEvents, SocketMessagesNamespaceCTSMessagesEvents>;

export enum SocketNamespaceName {
    MESSAGES = 'messages',
}

export interface SocketDetails {
    hubBaseUrl: string
    namespaceName: SocketNamespaceName
}

export function registerMessageEventHandlers(
    socket: Socket<SocketMessagesNamespaceSTCEvents, SocketMessagesNamespaceCTSMessagesEvents>,
    eventEmitter: EventEmitter2
) {
    socket.on("send", (data) => {
        eventEmitter.emit('hub.message.broadcast.received',
            <HubMessageBroadcastEvent>({
                analysisId: data.metadata.analysisId,
                messagePayload: data.data
            })
        );
    })

    socket.on('robotConnected', () => {
        console.log("robot connected");
    });

    socket.on('robotDisconnected', () => {
        console.log("robot disconnected");
    });

    socket.on('userConnected', () => {
        console.log("user connected");
    });

    socket.on('userDisconnected', () => {
        console.log("user disconnected");
    });
}

export async function createHubMessageSocket(
    sd: SocketDetails,
    cfg: Partial<ManagerOptions & SocketOptions>
): Promise<Socket<SocketMessagesNamespaceSTCEvents, SocketMessagesNamespaceCTSMessagesEvents>> {
    const socketUrl = new URL(`/${sd.namespaceName}`, sd.hubBaseUrl);
    const socket = connectSocket(socketUrl.toString(), cfg);

    return new Promise((resolve, reject) => {
        socket.on('connect_error', (err) => {
            console.log(err);
            reject();
        })

        socket.on("connect", () => {
            console.log('connected to hub');
            resolve(socket);
        });

        socket.on("disconnect", (reason) => {
            if (reason === "io server disconnect") {
                console.log('manually reconnecting to hub');
                socket.connect();
            }
        });
    });
}
