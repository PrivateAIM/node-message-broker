import type { CanActivate, ExecutionContext } from '@nestjs/common';
import { Injectable, UnauthorizedException } from '@nestjs/common';
import type { Request } from 'express';
import { AuthService } from './auth.service';

/**
 * Extracts the authentication token from the headers of the given request. Expects the request to use
 * a bearer token authentication type.
 *
 * @param {Request} request Tries to extract the authentication token from this request's headers.
 *
 * @returns {string | undefined} The extracted token or undefined.
 */
function extractTokenFromHeader(request: Request): string | undefined {
    const [type, token] = request.headers.authorization?.split(' ') ?? [];
    return type === 'Bearer' ? token : undefined;
}

/**
 * A Nest.js guard that handles authentication. Use this guard in combination with the @useGuard decorator
 * to secure endpoints. Using this guard requires the client to use the bearer token authentication type.
 */
@Injectable()
export class AuthGuard implements CanActivate {
    private readonly authService: AuthService;

    constructor(authService: AuthService) {
        this.authService = authService;
    }

    async canActivate(context: ExecutionContext): Promise<boolean> {
        const request = context.switchToHttp().getRequest();
        const token = extractTokenFromHeader(request);
        if (!token) {
            throw new UnauthorizedException();
        }

        return this.authService.verifyTokenAsync(token)
            .then(
                (verificationResult) => {
                    if (!verificationResult) {
                        throw new UnauthorizedException();
                    }
                    return verificationResult;
                },
                () => false,
            )
            .catch(() => false);
    }
}
