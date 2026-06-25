import { requireAdmin } from '~/server/utils/auth'
import { q } from '~/server/utils/db'

export default defineEventHandler(async (event) => {
  await requireAdmin(event)
  const qry = getQuery(event)
  const limit = Math.min(Number(qry.limit ?? 100), 500)
  const rows = await q<any>(
    `SELECT device_id, first_seen, last_seen, app_version, android_sdk, brand, model, soc
     FROM devices ORDER BY last_seen DESC LIMIT $1`,
    [limit]
  )
  return { devices: rows }
})
