import { z } from 'zod'
import { q1 } from '~/server/utils/db'

/**
 * Vergleichswerte fuer ein Benchmark-Ergebnis. Bewusst ohne deviceId und ohne
 * Schreibzugriff: Lesen darf jeder, auch wer selbst nichts hochlaedt.
 *
 * Statistik-Detail: gruppiert wird auf **das beste Ergebnis pro Geraet**. Sonst
 * wuerde ein einzelner Nutzer, der zwanzigmal benchmarkt, die Verteilung kippen.
 */

const MIN_SAMPLES = 5

const Query = z.object({
  benchType: z.string().min(1).max(32),
  soc: z.string().min(1).max(64),
  score: z.coerce.number().int().min(1).max(10_000_000)
})

export default defineEventHandler(async (event) => {
  const parsed = Query.safeParse(getQuery(event))
  if (!parsed.success) {
    throw createError({ statusCode: 400, statusMessage: 'Invalid query' })
  }
  const { benchType, soc, score } = parsed.data

  const row = await q1<{
    below: string
    total: string
    avg_score: string | null
    max_score: string | null
  }>(
    `WITH best AS (
       SELECT device_id, MAX(total_score) AS score
         FROM benchmark_scores
        WHERE bench_type = $1 AND soc = $2
        GROUP BY device_id
     )
     SELECT COUNT(*) FILTER (WHERE score < $3)::text AS below,
            COUNT(*)::text                           AS total,
            ROUND(AVG(score))::text                  AS avg_score,
            MAX(score)::text                         AS max_score
       FROM best`,
    [benchType, soc, score]
  )

  const total = Number(row?.total ?? 0)
  if (total < MIN_SAMPLES) {
    // Bei zu wenig Daten waere ein Percentil Zahlenmystik statt Aussage
    return { ok: true, enoughData: false, sampleCount: total, minSamples: MIN_SAMPLES }
  }

  const below = Number(row?.below ?? 0)
  return {
    ok: true,
    enoughData: true,
    sampleCount: total,
    percentile: Math.round((below / total) * 100),
    averageScore: row?.avg_score ? Number(row.avg_score) : null,
    bestScore: row?.max_score ? Number(row.max_score) : null
  }
})
