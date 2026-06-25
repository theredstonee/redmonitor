import { destroySession } from '~/server/utils/auth'

export default defineEventHandler(async (event) => {
  await destroySession(event)
  return { ok: true }
})
