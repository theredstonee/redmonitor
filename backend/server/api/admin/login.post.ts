import { z } from 'zod'
import { verifyAdmin, createSession } from '~/server/utils/auth'

const Body = z.object({
  username: z.string().min(1).max(64),
  password: z.string().min(1).max(256)
})

// rudimentary in-memory rate limit per IP — fine for a single-instance backend
const attempts = new Map<string, { count: number; resetAt: number }>()
const WINDOW_MS = 5 * 60 * 1000
const MAX_ATTEMPTS = 10

function checkRate(ip: string): boolean {
  const now = Date.now()
  const cur = attempts.get(ip)
  if (!cur || cur.resetAt < now) {
    attempts.set(ip, { count: 1, resetAt: now + WINDOW_MS })
    return true
  }
  cur.count += 1
  return cur.count <= MAX_ATTEMPTS
}

export default defineEventHandler(async (event) => {
  const ip = getRequestIP(event, { xForwardedFor: true }) || 'unknown'
  if (!checkRate(ip)) {
    throw createError({ statusCode: 429, statusMessage: 'Too many attempts' })
  }
  const parsed = Body.safeParse(await readBody(event))
  if (!parsed.success) {
    throw createError({ statusCode: 400, statusMessage: 'Invalid body' })
  }
  const ok = await verifyAdmin(parsed.data.username, parsed.data.password)
  if (!ok) {
    throw createError({ statusCode: 401, statusMessage: 'Invalid credentials' })
  }
  await createSession(event, parsed.data.username)
  return { ok: true }
})
