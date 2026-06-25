import { z } from 'zod'
import { q } from '~/server/utils/db'

const MAX_BACKUP_SIZE = 2 * 1024 * 1024  // 2 MB cap

const Body = z.object({
  deviceId: z.string().regex(/^[0-9a-f]{64}$/i),
  blobVersion: z.number().int().min(1).default(1),
  // Base64-encoded client-side encrypted blob (AES-256-GCM with HKDF-derived key)
  payloadB64: z.string()
})

export default defineEventHandler(async (event) => {
  const raw = await readBody(event)
  const parsed = Body.safeParse(raw)
  if (!parsed.success) {
    throw createError({ statusCode: 400, statusMessage: 'Invalid body' })
  }
  const b = parsed.data

  const blob = Buffer.from(b.payloadB64, 'base64')
  if (blob.length > MAX_BACKUP_SIZE) {
    throw createError({ statusCode: 413, statusMessage: 'Backup too large' })
  }
  if (blob.length < 16) {
    throw createError({ statusCode: 400, statusMessage: 'Backup too small / invalid' })
  }

  // Ensure device row exists (FK)
  await q(
    `INSERT INTO devices (device_id) VALUES ($1) ON CONFLICT (device_id) DO NOTHING`,
    [b.deviceId]
  )

  const cfg = useRuntimeConfig()
  // Wrap the (already E2E-encrypted) client blob with pgp_sym_encrypt for at-rest defense
  await q(
    `INSERT INTO backups (device_id, blob_version, payload_size, encrypted_blob)
     VALUES ($1, $2, $3, pgp_sym_encrypt_bytea($4, $5))`,
    [b.deviceId, b.blobVersion, blob.length, blob, cfg.encKey]
  )

  // Keep only the latest 5 backups per device — older purged
  await q(
    `DELETE FROM backups
     WHERE device_id = $1
       AND id NOT IN (
         SELECT id FROM backups WHERE device_id = $1 ORDER BY created_at DESC LIMIT 5
       )`,
    [b.deviceId]
  )

  return { ok: true, size: blob.length }
})
