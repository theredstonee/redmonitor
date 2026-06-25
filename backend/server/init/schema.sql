-- RedMonitor Backend schema.
-- Re-runnable: CREATE IF NOT EXISTS / no destructive ops.

CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE IF NOT EXISTS devices (
  device_id            TEXT PRIMARY KEY,        -- hex(SHA-256(hardware-fingerprint || server_salt))
  first_seen           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  last_seen            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  install_count        INT NOT NULL DEFAULT 1,  -- bump every time a fresh install reports
  app_version          TEXT,
  app_version_code     INT,
  android_sdk          INT,
  android_release      TEXT,
  brand                TEXT,
  model                TEXT,
  soc                  TEXT,
  -- additional non-PII telemetry, encrypted at rest with pgp_sym_encrypt
  device_extras_enc    BYTEA
);

CREATE INDEX IF NOT EXISTS idx_devices_last_seen ON devices(last_seen DESC);
CREATE INDEX IF NOT EXISTS idx_devices_brand ON devices(brand);

CREATE TABLE IF NOT EXISTS heartbeats (
  id                   BIGSERIAL PRIMARY KEY,
  device_id            TEXT NOT NULL REFERENCES devices(device_id) ON DELETE CASCADE,
  reported_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  app_version          TEXT,
  network_type         TEXT,        -- wifi / mobile / none
  battery_pct          SMALLINT,
  uptime_seconds       BIGINT
);

CREATE INDEX IF NOT EXISTS idx_heartbeats_device ON heartbeats(device_id, reported_at DESC);
CREATE INDEX IF NOT EXISTS idx_heartbeats_reported ON heartbeats(reported_at DESC);

CREATE TABLE IF NOT EXISTS backups (
  id                   BIGSERIAL PRIMARY KEY,
  device_id            TEXT NOT NULL REFERENCES devices(device_id) ON DELETE CASCADE,
  created_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  blob_version         INT NOT NULL DEFAULT 1,
  payload_size         INT NOT NULL,
  -- Client-blob is ALREADY E2E-encrypted with HKDF(hardware-fingerprint || server_salt).
  -- We then wrap it with pgp_sym_encrypt(client_blob, server_key) for at-rest defense-in-depth.
  encrypted_blob       BYTEA NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_backups_device ON backups(device_id, created_at DESC);

CREATE TABLE IF NOT EXISTS admins (
  username             TEXT PRIMARY KEY,
  pwhash               TEXT NOT NULL,
  created_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  last_login_at        TIMESTAMPTZ
);

CREATE TABLE IF NOT EXISTS admin_sessions (
  token                TEXT PRIMARY KEY,
  username             TEXT NOT NULL REFERENCES admins(username) ON DELETE CASCADE,
  created_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  expires_at           TIMESTAMPTZ NOT NULL,
  ip                   INET
);

CREATE INDEX IF NOT EXISTS idx_admin_sessions_expires ON admin_sessions(expires_at);
