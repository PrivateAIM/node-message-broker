import { MiddlewareConsumer, Module, NestModule, RequestMethod, ValidationPipe } from '@nestjs/common';
import { ConfigModule, ConfigService } from '@nestjs/config';
import { MongooseModule } from '@nestjs/mongoose';
import { APP_PIPE } from '@nestjs/core';
import { HealthModule } from './health/health.module';
import configuration from './config/configuration';
import { AuthModule } from './auth/auth.module';
import { MessageModule } from './message/message.module';
import { DiscoveryModule } from './discovery/discovery.module';
import { RequestLoggerMiddleware } from './middleware/request.logger';

@Module({
    imports: [
        ConfigModule.forRoot({
            isGlobal: true,
            load: [configuration],
        }),
        MongooseModule.forRootAsync({
            imports: [ConfigModule],
            useFactory: async (config: ConfigService) => ({
                uri: config.get<string>('persistence.dbUrl'),
                dbName: config.get<string>('persistence.dbName'),
            }),
            inject: [ConfigService],
        }),
        AuthModule.forRoot(true),
        HealthModule,
        MessageModule,
        DiscoveryModule,
    ],
    providers: [
        {
            provide: APP_PIPE,
            useClass: ValidationPipe,
        },
    ],
})
export class ServerModule implements NestModule {
    configure(consumer: MiddlewareConsumer) {
        consumer.apply(RequestLoggerMiddleware)
            .forRoutes({ path: '*', method: RequestMethod.ALL });
    }
}
