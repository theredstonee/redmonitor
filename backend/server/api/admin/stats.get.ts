import { requireAdmin } from '~/server/utils/auth'
import { q, q1 } from '~/server/utils/db'

export default defineEventHandler(async (event) => {
  await requireAdmin(event)
  const total = await q1<{ count: string }>('SELECT COUNT(*)::text AS count FROM devices')
  const dau = await q1<{ count: string }>(
    `SELECT COUNT(DISTINCT device_id)::text AS count FROM heartbeats WHERE reported_at > NOW() - INTERVAL '24 hours'`
  )
  const mau = await q1<{ count: string }>(
    `SELECT COUNT(DISTINCT device_id)::text AS count FROM heartbeats WHERE reported_at > NOW() - INTERVAL '30 days'`
  )
  const byBrand = await q<{ brand: string | null; count: string }>(
    `SELECT brand, COUNT(*)::text AS count FROM devices
     WHERE last_seen > NOW() - INTERVAL '30 days' AND brand IS NOT NULL
     GROUP BY brand ORDER BY COUNT(*) DESC LIMIT 20`
  )
  const byVersion = await q<{ app_version: string | null; count: string }>(
    `SELECT app_version, COUNT(*)::text AS count FROM devices
     WHERE last_seen > NOW() - INTERVAL '30 days' AND app_version IS NOT NULL
     GROUP BY app_version ORDER BY app_version DESC LIMIT 20`
  )
  const bySdk = await q<{ android_sdk: number | null; count: string }>(
    `SELECT android_sdk, COUNT(*)::text AS count FROM devices
     WHERE last_seen > NOW() - INTERVAL '30 days' AND android_sdk IS NOT NULL
     GROUP BY android_sdk ORDER BY android_sdk DESC`
  )
  const hourly24 = await q<{ hour: string; count: string }>(
    `SELECT date_trunc('hour', reported_at) AS hour, COUNT(DISTINCT device_id)::text AS count
     FROM heartbeats WHERE reported_at > NOW() - INTERVAL '24 hours'
     GROUP BY hour ORDER BY hour`
  )
  const backupCount = await q1<{ count: string }>('SELECT COUNT(*)::text AS count FROM backups')
  return {
    totalDevices: Number(total?.count ?? 0),
    dau: Number(dau?.count ?? 0),
    mau: Number(mau?.count ?? 0),
    byBrand: byBrand.map(r => ({ brand: r.brand, count: Number(r.count) })),
    byVersion: byVersion.map(r => ({ version: r.app_version, count: Number(r.count) })),
    bySdk: bySdk.map(r => ({ sdk: r.android_sdk, count: Number(r.count) })),
    hourly24: hourly24.map(r => ({ hour: r.hour, count: Number(r.count) })),
    backups: Number(backupCount?.count ?? 0)
  }
})
