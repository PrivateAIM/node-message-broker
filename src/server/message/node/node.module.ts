import { Module } from '@nestjs/common';
import { MessageNodeEmitterService } from './node.emitter.service';

@Module({
    providers: [
        {
            provide: MessageNodeEmitterService,
            useFactory: () => MessageNodeEmitterService.create(),
        },
    ],
    exports: [MessageNodeEmitterService],
})
export class MessageNodeModule { }
