import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { getPool } from '../utils/db'
import { bootstrapAdmin } from '../utils/auth'

export default async () => {
  const pool = getPool()
  const schemaPath = resolve(process.cwd(), 'server/init/schema.sql')
  const ddl = readFileSync(schemaPath, 'utf-8')
  await pool.query(ddl)
  console.log('[init-db] schema applied')

  // Bootstrap admin from env if not present yet
  const u = process.env.ADMIN_USERNAME
  const p = process.env.ADMIN_PASSWORD_INITIAL
  if (u && p) {
    await bootstrapAdmin(u, p)
    console.log(`[init-db] admin '${u}' ensured`)
  }
}
