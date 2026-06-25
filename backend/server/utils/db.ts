import pg from 'pg'

let pool: pg.Pool | null = null

export function getPool(): pg.Pool {
  if (pool) return pool
  const cfg = useRuntimeConfig()
  pool = new pg.Pool({
    host: cfg.dbHost,
    port: cfg.dbPort,
    database: cfg.dbName,
    user: cfg.dbUser,
    password: cfg.dbPass,
    max: 10,
    idleTimeoutMillis: 30_000
  })
  return pool
}

export async function q<T = any>(text: string, params?: any[]): Promise<T[]> {
  const res = await getPool().query(text, params)
  return res.rows as T[]
}

export async function q1<T = any>(text: string, params?: any[]): Promise<T | null> {
  const rows = await q<T>(text, params)
  return rows[0] ?? null
}
