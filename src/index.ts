import { NestFactory } from '@nestjs/core';
import { ConfigService } from '@nestjs/config';
import { ServerModule } from './server/server.module';

async function bootstrap() {
    const analysisCommunicationServer = await NestFactory.create(ServerModule);
    const config = analysisCommunicationServer.get(ConfigService);
    await analysisCommunicationServer.listen(config.getOrThrow<string>('serverPort'));
}
bootstrap();
