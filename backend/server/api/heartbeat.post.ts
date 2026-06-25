import { z } from 'zod'
import { q } from '~/server/utils/db'

const Body = z.object({
  deviceId: z.string().regex(/^[0-9a-f]{64}$/i),  // SHA-256 hex
  appVersion: z.string().max(32).optional(),
  appVersionCode: z.number().int().optional(),
  androidSdk: z.number().int().optional(),
  androidRelease: z.string().max(32).optional(),
  brand: z.string().max(64).optional(),
  model: z.string().max(64).optional(),
  soc: z.string().max(64).optional(),
  networkType: z.enum(['wifi', 'mobile', 'none']).optional(),
  batteryPct: z.number().int().min(0).max(100).optional(),
  uptimeSeconds: z.number().int().optional(),
  extras: z.record(z.any()).optional()
})

export default defineEventHandler(async (event) => {
  const raw = await readBody(event)
  const parsed = Body.safeParse(raw)
  if (!parsed.success) {
    throw createError({ statusCode: 400, statusMessage: 'Invalid body' })
  }
  const b = parsed.data
  const cfg = useRuntimeConfig()
  const encKey = cfg.encKey

  // Upsert device — bump install_count if first_seen exists but we see a fresh install signal
  await q(
    `INSERT INTO devices (
       device_id, app_version, app_version_code, android_sdk, android_release,
       brand, model, soc, device_extras_enc
     ) VALUES ($1, $2, $3, $4, $5, $6, $7, $8,
       CASE WHEN $9::text IS NULL THEN NULL ELSE pgp_sym_encrypt($9::text, $10) END)
     ON CONFLICT (device_id) DO UPDATE SET
       last_seen        = NOW(),
       app_version      = EXCLUDED.app_version,
       app_version_code = EXCLUDED.app_version_code,
       android_sdk      = EXCLUDED.android_sdk,
       android_release  = EXCLUDED.android_release,
       brand            = EXCLUDED.brand,
       model            = EXCLUDED.model,
       soc              = EXCLUDED.soc,
       device_extras_enc= COALESCE(EXCLUDED.device_extras_enc, devices.device_extras_enc)`,
    [
      b.deviceId, b.appVersion, b.appVersionCode, b.androidSdk, b.androidRelease,
      b.brand, b.model, b.soc,
      b.extras ? JSON.stringify(b.extras) : null,
      encKey
    ]
  )

  await q(
    `INSERT INTO heartbeats (device_id, app_version, network_type, battery_pct, uptime_seconds)
     VALUES ($1, $2, $3, $4, $5)`,
    [b.deviceId, b.appVersion, b.networkType, b.batteryPct, b.uptimeSeconds]
  )

  return { ok: true }
})
