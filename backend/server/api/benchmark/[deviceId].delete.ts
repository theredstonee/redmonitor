import { z } from 'zod'
import { q1 } from '~/server/utils/db'

/**
 * Loescht alle hochgeladenen Benchmark-Scores eines Geraets (DSGVO Art. 17).
 * Sub-Scores gehen per ON DELETE CASCADE mit.
 *
 * Die Device-ID ist selbst schon ein Hash aus dem Hardware-Fingerprint — wer sie
 * kennt, sitzt am Geraet. Ein zusaetzliches Auth-Token gibt es hier bewusst nicht:
 * es waere derselbe Wert unter anderem Namen.
 */

const Params = z.object({
  deviceId: z.string().regex(/^[0-9a-f]{64}$/i)
})

export default defineEventHandler(async (event) => {
  const parsed = Params.safeParse({ deviceId: getRouterParam(event, 'deviceId') })
  if (!parsed.success) {
    throw createError({ statusCode: 400, statusMessage: 'Invalid device id' })
  }

  const row = await q1<{ count: string }>(
    `WITH d AS (DELETE FROM benchmark_scores WHERE device_id = $1 RETURNING 1)
     SELECT COUNT(*)::text AS count FROM d`,
    [parsed.data.deviceId]
  )

  return { ok: true, deleted: Number(row?.count ?? 0) }
})
