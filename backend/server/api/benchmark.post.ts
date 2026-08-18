import { z } from 'zod'
import { q, q1 } from '~/server/utils/db'

/**
 * Freiwilliger Upload eines Benchmark-Ergebnisses fuer den Geraete-Vergleich.
 *
 * Der Client fragt vor JEDEM Upload nach — hier gibt es deshalb bewusst keinen
 * serverseitigen "Einwilligung gemerkt"-Zustand. Gespeichert wird nur, was fuer
 * den Vergleich noetig ist: Score, Sub-Scores und die Geraeteklasse.
 */

const MAX_SUB_SCORES = 32

const SubScore = z.object({
  name: z.string().min(1).max(64),
  singleScore: z.number().int().min(0).max(10_000_000),
  multiScore: z.number().int().min(0).max(10_000_000),
  unit: z.string().max(16).optional()
})

const Body = z.object({
  deviceId: z.string().regex(/^[0-9a-f]{64}$/i),
  benchType: z.string().min(1).max(32),
  totalScore: z.number().int().min(1).max(10_000_000),
  singleScore: z.number().int().min(0).max(10_000_000).optional(),
  multiScore: z.number().int().min(0).max(10_000_000).optional(),
  soc: z.string().max(64).optional(),
  brand: z.string().max(64).optional(),
  model: z.string().max(64).optional(),
  androidSdk: z.number().int().min(1).max(100).optional(),
  appVersion: z.string().max(32).optional(),
  subScores: z.array(SubScore).max(MAX_SUB_SCORES).optional()
})

export default defineEventHandler(async (event) => {
  const parsed = Body.safeParse(await readBody(event))
  if (!parsed.success) {
    throw createError({ statusCode: 400, statusMessage: 'Invalid body' })
  }
  const b = parsed.data

  // Device-Zeile sicherstellen (FK)
  await q(
    `INSERT INTO devices (device_id) VALUES ($1) ON CONFLICT (device_id) DO NOTHING`,
    [b.deviceId]
  )

  const row = await q1<{ id: string }>(
    `INSERT INTO benchmark_scores (
       device_id, bench_type, total_score, single_score, multi_score,
       soc, brand, model, android_sdk, app_version
     ) VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10)
     RETURNING id::text AS id`,
    [
      b.deviceId, b.benchType, b.totalScore, b.singleScore ?? null, b.multiScore ?? null,
      b.soc ?? null, b.brand ?? null, b.model ?? null, b.androidSdk ?? null, b.appVersion ?? null
    ]
  )
  if (!row) throw createError({ statusCode: 500, statusMessage: 'Insert failed' })

  for (const sub of b.subScores ?? []) {
    await q(
      `INSERT INTO benchmark_sub_scores (score_id, name, single_score, multi_score, unit)
       VALUES ($1,$2,$3,$4,$5)`,
      [row.id, sub.name, sub.singleScore, sub.multiScore, sub.unit ?? null]
    )
  }

  // Pro Geraet und Benchmark-Typ nur die letzten 20 Laeufe behalten
  await q(
    `DELETE FROM benchmark_scores
      WHERE device_id = $1 AND bench_type = $2
        AND id NOT IN (
          SELECT id FROM benchmark_scores
           WHERE device_id = $1 AND bench_type = $2
           ORDER BY created_at DESC LIMIT 20
        )`,
    [b.deviceId, b.benchType]
  )

  return { ok: true }
})
