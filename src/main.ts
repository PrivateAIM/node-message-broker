import { NestFactory } from '@nestjs/core';
import { ServerModule } from './server/server.module';

async function bootstrap() {
    const serverPort = process.env.SERVER_PORT || 3000;

    const analysisCommunicationServer = await NestFactory.create(ServerModule);
    await analysisCommunicationServer.listen(serverPort);
}
bootstrap();
