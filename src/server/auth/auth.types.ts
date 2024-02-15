import type * as jose from 'jose';

export type JWKS = (protectedHeader?: jose.JWSHeaderParameters, token?: jose.FlattenedJWSInput) => Promise<jose.KeyLike>;
