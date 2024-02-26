import { NestFactory } from '@nestjs/core';
import { ConfigService } from '@nestjs/config';
import { ValidationPipe } from '@nestjs/common';
import { ServerModule } from './server/server.module';

async function bootstrap() {
    const analysisCommunicationServer = await NestFactory.create(ServerModule);

    // for validating request data
    analysisCommunicationServer.useGlobalPipes(
        new ValidationPipe({
            transform: true,
            always: true,
        }),
    );
    const config = analysisCommunicationServer.get(ConfigService);
    await analysisCommunicationServer.listen(config.getOrThrow<string>('serverPort'));
}
bootstrap();
