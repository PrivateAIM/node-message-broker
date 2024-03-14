import type { ManagerOptions, Socket, SocketOptions } from 'socket.io-client';
import {
    connect as connectSocket,
} from 'socket.io-client';
import type {
    SocketMessagesNamespaceCTSMessagesEvents,
    SocketMessagesNamespaceSTCEvents,
} from '@privateaim/core';
import type { EventEmitter2 } from '@nestjs/event-emitter';
import type { HubMessageBroadcastEvent } from './events';

export type HubMessageSocket = typeof Socket<SocketMessagesNamespaceSTCEvents, SocketMessagesNamespaceCTSMessagesEvents>;

/**
 * Supported socket namespaces when connecting to the central side (hub).
 */
export enum SocketNamespaceName {
    MESSAGES = 'messages',
}

/**
 * Basic socket connection information.
 */
export interface SocketDetails {
    hubBaseUrl: string
    namespaceName: SocketNamespaceName
}

/**
 * Registers pre-defined message event handlers on a given socket.
 *
 * @param { Socket<SocketMessagesNamespaceSTCEvents, SocketMessagesNamespaceCTSMessagesEvents> } socket the socket
 * @param { EventEmitter2 } eventEmitter an internal emitter used to forward event handling
 */
export function registerMessageEventHandlers(
    socket: Socket<SocketMessagesNamespaceSTCEvents, SocketMessagesNamespaceCTSMessagesEvents>,
    eventEmitter: EventEmitter2,
) {
    socket.on('send', (data) => {
        eventEmitter.emit(
            'hub.message.broadcast.received',
            <HubMessageBroadcastEvent>({
                analysisId: data.metadata.analysisId,
                messagePayload: data.data,
            }),
        );
    });

    socket.on('robotConnected', () => {
        console.log('robot connected');
    });

    socket.on('robotDisconnected', () => {
        console.log('robot disconnected');
    });

    socket.on('userConnected', () => {
        console.log('user connected');
    });

    socket.on('userDisconnected', () => {
        console.log('user disconnected');
    });
}

/**
 * Creates a socket.io instance to connect to the central side (hub).
 *
 * @param { SocketDetails } sd basic socket connection information
 * @param { Partial<ManagerOptions & SocketOptions> } cfg socket configuration options
 * @returns A promise of the created socket.
 */
export async function createHubMessageSocket(
    sd: SocketDetails,
    cfg: Partial<ManagerOptions & SocketOptions>,
): Promise<Socket<SocketMessagesNamespaceSTCEvents, SocketMessagesNamespaceCTSMessagesEvents>> {
    const socketUrl = new URL(`/${sd.namespaceName}`, sd.hubBaseUrl);
    const socket = connectSocket(socketUrl.toString(), cfg);

    return new Promise((resolve, reject) => {
        socket.on('connect_error', (err) => {
            console.log(err);
            reject();
        });

        socket.on('connect', () => {
            console.log('connected to hub');
            resolve(socket);
        });

        socket.on('disconnect', (reason) => {
            if (reason === 'io server disconnect') {
                console.log('manually reconnecting to hub');
                socket.connect();
            }
        });
    });
}
