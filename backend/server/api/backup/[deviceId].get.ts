import { q1 } from '~/server/utils/db'

export default defineEventHandler(async (event) => {
  const deviceId = getRouterParam(event, 'deviceId') || ''
  if (!/^[0-9a-f]{64}$/i.test(deviceId)) {
    throw createError({ statusCode: 400, statusMessage: 'Invalid device id' })
  }
  const cfg = useRuntimeConfig()
  const row = await q1<{ blob_version: number; payload_size: number; payload: Buffer; created_at: Date }>(
    `SELECT blob_version, payload_size, pgp_sym_decrypt_bytea(encrypted_blob, $1) AS payload, created_at
     FROM backups
     WHERE device_id = $2
     ORDER BY created_at DESC
     LIMIT 1`,
    [cfg.encKey, deviceId]
  )
  if (!row) {
    return { ok: false, error: 'no_backup' }
  }
  return {
    ok: true,
    blobVersion: row.blob_version,
    size: row.payload_size,
    createdAt: row.created_at.toISOString(),
    payloadB64: Buffer.from(row.payload).toString('base64')
  }
})
