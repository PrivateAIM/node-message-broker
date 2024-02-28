/**
 * Configuration options for this application.
 */
export default () => ({
    serverPort: parseInt(process.env.SERVER_PORT as string, 10) || 3000,
    auth: {
        jwksUrl: process.env.AUTH_JWKS_URL,
    },
    persistence: {
        dbUrl: process.env.MONGO_DB_URL,
        dbName: process.env.MONGO_DB_NAME,
    },
});
