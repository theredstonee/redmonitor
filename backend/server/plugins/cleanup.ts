import { q } from '../utils/db'

/**
 * Auto-Purge: jede 24h löscht alles älter als 90 Tage.
 * - Heartbeats > 90 d
 * - Backups   > 90 d
 * - Devices, die seit > 90 d kein Heartbeat hatten (CASCADE killt restliche FKs)
 * - Abgelaufene Admin-Sessions
 *
 * Läuft als simpler setInterval — kein Cron, kein pg_cron nötig.
 */
export default async () => {
  async function purge() {
    try {
      const r1 = await q<{ count: string }>(
        `WITH d AS (DELETE FROM heartbeats WHERE reported_at < NOW() - INTERVAL '90 days' RETURNING 1)
         SELECT COUNT(*)::text AS count FROM d`
      )
      const r2 = await q<{ count: string }>(
        `WITH d AS (DELETE FROM backups WHERE created_at < NOW() - INTERVAL '90 days' RETURNING 1)
         SELECT COUNT(*)::text AS count FROM d`
      )
      const r3 = await q<{ count: string }>(
        `WITH d AS (DELETE FROM devices WHERE last_seen < NOW() - INTERVAL '90 days' RETURNING 1)
         SELECT COUNT(*)::text AS count FROM d`
      )
      const r4 = await q<{ count: string }>(
        `WITH d AS (DELETE FROM admin_sessions WHERE expires_at < NOW() RETURNING 1)
         SELECT COUNT(*)::text AS count FROM d`
      )
      console.log(`[cleanup] purged hb=${r1[0]?.count ?? 0} bk=${r2[0]?.count ?? 0} dev=${r3[0]?.count ?? 0} sess=${r4[0]?.count ?? 0}`)
    } catch (e: any) {
      console.warn('[cleanup] failed:', e?.message ?? e)
    }
  }
  await purge()
  setInterval(purge, 24 * 60 * 60 * 1000)
}
