export default defineNuxtConfig({
  compatibilityDate: '2026-06-01',
  ssr: true,
  devtools: { enabled: false },
  nitro: {
    // bind to 127.0.0.1 via env in production (see systemd unit), here just config
    plugins: ['~/server/plugins/init-db.ts', '~/server/plugins/cleanup.ts']
  },
  runtimeConfig: {
    // server-only — all set via env
    dbHost: process.env.PG_HOST || '127.0.0.1',
    dbPort: Number(process.env.PG_PORT || 5432),
    dbName: process.env.PG_DB || 'redmonitor_backend',
    dbUser: process.env.PG_USER || 'redmonitor',
    dbPass: process.env.PG_PASS || '',
    // server-side encryption key for pgcrypto pgp_sym_encrypt (at-rest layer)
    encKey: process.env.SERVER_ENC_KEY || '',
    // device-id derivation salt (mixed in with hardware-fingerprint client-side)
    idSalt: process.env.DEVICE_ID_SALT || '',
    // admin session token signing
    sessionSecret: process.env.SESSION_SECRET || '',
    public: {
      // nothing — backend has no client-side secrets
    }
  },
  app: {
    head: {
      title: 'RedMonitor Backend',
      meta: [
        { name: 'viewport', content: 'width=device-width, initial-scale=1' },
        { name: 'robots', content: 'noindex,nofollow' }
      ]
    }
  }
})
