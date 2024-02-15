/**
 * Configuration options for this application.
 */
export default () => ({
    serverPort: parseInt(process.env.SERVER_PORT as string, 10) || 3000,
    auth: {
        jwksUrl: process.env.AUTH_JWKS_URL,
    },
});
