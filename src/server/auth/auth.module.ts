import type { DynamicModule } from '@nestjs/common';
import { Module } from '@nestjs/common';
import * as jose from 'jose';
import { ConfigService } from '@nestjs/config';
import { AuthService } from './auth.service';

/**
 * A Nest.js module that takes care of different authentication concerns.
 */
@Module({})
export class AuthModule {
    /**
     * Creates a dynamic Nest.js module for registration at the root module.
     *
     * @param {boolean} isGlobal Whether this module should be registered globally. If set to true other dependant
     *                           modules do not have to import this module again.
     * @returns {DynamicModule} A dynamic module for registration at the root module.
     */
    static forRoot(isGlobal?: boolean): DynamicModule {
        return {
            module: AuthModule,
            global: isGlobal || false,
            providers: [
                {
                    provide: AuthService,
                    useFactory: (config: ConfigService) => {
                        const jwks = jose.createRemoteJWKSet(new URL(config.getOrThrow<string>('auth.jwksUrl')));
                        return new AuthService(jwks);
                    },
                    inject: [ConfigService],
                },
            ],
            exports: [AuthService],
        };
    }
}
