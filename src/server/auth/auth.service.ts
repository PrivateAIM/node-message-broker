import { Injectable } from '@nestjs/common';
import * as jose from 'jose';

/**
 * Describes a function that can be used to retrieve a JSON Web Key Set for token verification.
 */
export type JWKS = (protectedHeader?: jose.JWSHeaderParameters, token?: jose.FlattenedJWSInput) => Promise<jose.KeyLike>;

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
