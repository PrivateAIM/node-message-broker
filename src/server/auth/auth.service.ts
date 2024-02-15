import { Injectable } from '@nestjs/common';
import * as jose from 'jose';
import { JWKS } from './auth.types';

/**
 * Authentication service based on `jose`.
 */
@Injectable()
export class AuthService {
    private readonly jwks: JWKS;

    constructor(jwks: JWKS) {
        this.jwks = jwks;
    }

    /**
     * Asynchronously verifies the given authentication token.
     *
     * @param {string} token The token to verfify.
     *
     * @returns {Promise<Boolean>} A promise of the verification result.
     */
    async verifyTokenAsync(token: string): Promise<boolean> {
        return jose.jwtVerify(token, this.jwks)
            .then(() => Promise.resolve(true))
            .catch((e) => {
            // TODO: log this error properly
                console.log(e);
                return Promise.resolve(false);
            });
    }
}
