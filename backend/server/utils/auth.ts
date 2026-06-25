import { randomBytes, createHash } from 'node:crypto'
import bcrypt from 'bcryptjs'
import { q, q1 } from './db'
import type { H3Event } from 'h3'

const SESSION_TTL_SECONDS = 60 * 60 * 24 * 14  // 14 days
const COOKIE_NAME = 'rm_admin'

export async function bootstrapAdmin(username: string, plaintext: string) {
  const existing = await q1<{ username: string }>('SELECT username FROM admins WHERE username=$1', [username])
  if (existing) return
  const hash = await bcrypt.hash(plaintext, 12)
  await q('INSERT INTO admins (username, pwhash) VALUES ($1, $2)', [username, hash])
}

export async function verifyAdmin(username: string, plaintext: string): Promise<boolean> {
  const row = await q1<{ pwhash: string }>('SELECT pwhash FROM admins WHERE username=$1', [username])
  if (!row) {
    // constant-time dummy compare to avoid user-existence timing leak
    await bcrypt.compare(plaintext, '$2a$12$0000000000000000000000.0000000000000000000000000000000')
    return false
  }
  const ok = await bcrypt.compare(plaintext, row.pwhash)
  if (ok) await q('UPDATE admins SET last_login_at=NOW() WHERE username=$1', [username])
  return ok
}

export async function createSession(event: H3Event, username: string): Promise<string> {
  const token = randomBytes(32).toString('hex')
  const ip = getRequestIP(event, { xForwardedFor: true }) || null
  await q(
    `INSERT INTO admin_sessions (token, username, expires_at, ip)
     VALUES ($1, $2, NOW() + INTERVAL '${SESSION_TTL_SECONDS} seconds', $3)`,
    [token, username, ip]
  )
  setCookie(event, COOKIE_NAME, token, {
    httpOnly: true,
    secure: true,
    sameSite: 'strict',
    path: '/',
    maxAge: SESSION_TTL_SECONDS
  })
  return token
}

export async function destroySession(event: H3Event) {
  const token = getCookie(event, COOKIE_NAME)
  if (token) await q('DELETE FROM admin_sessions WHERE token=$1', [token])
  deleteCookie(event, COOKIE_NAME, { path: '/' })
}

export async function getAdminUsername(event: H3Event): Promise<string | null> {
  const token = getCookie(event, COOKIE_NAME)
  if (!token) return null
  const row = await q1<{ username: string }>(
    'SELECT username FROM admin_sessions WHERE token=$1 AND expires_at > NOW()',
    [token]
  )
  return row?.username ?? null
}

export async function requireAdmin(event: H3Event): Promise<string> {
  const u = await getAdminUsername(event)
  if (!u) throw createError({ statusCode: 401, statusMessage: 'Unauthorized' })
  return u
}

/** Hash a device-id-source string the same way the Android client should. */
export function hashDeviceFingerprint(fingerprint: string, salt: string): string {
  return createHash('sha256').update(fingerprint + '|' + salt).digest('hex')
}
