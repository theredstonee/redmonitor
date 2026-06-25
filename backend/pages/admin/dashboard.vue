<template>
  <div class="wrap">
    <header>
      <div class="logo"><span class="red">Red</span>Monitor Admin</div>
      <button class="logout" @click="logout">Logout</button>
    </header>

    <div v-if="loading">Loading…</div>
    <div v-else-if="err" class="err">{{ err }}</div>
    <div v-else-if="stats" class="grid">
      <div class="card">
        <div class="label">Total Devices</div>
        <div class="big">{{ stats.totalDevices }}</div>
      </div>
      <div class="card">
        <div class="label">DAU (24h)</div>
        <div class="big">{{ stats.dau }}</div>
      </div>
      <div class="card">
        <div class="label">MAU (30d)</div>
        <div class="big">{{ stats.mau }}</div>
      </div>
      <div class="card">
        <div class="label">Backups Stored</div>
        <div class="big">{{ stats.backups }}</div>
      </div>

      <div class="card wide">
        <div class="label">Active by Hour (24h)</div>
        <div class="bars">
          <div v-for="(h, i) in stats.hourly24" :key="i" class="bar"
               :style="{ height: ((h.count / maxHourly) * 100) + '%' }"
               :title="`${h.hour}: ${h.count}`"></div>
        </div>
      </div>

      <div class="card">
        <div class="label">By Brand (30d)</div>
        <ul>
          <li v-for="b in stats.byBrand" :key="b.brand">
            <span>{{ b.brand }}</span><span class="num">{{ b.count }}</span>
          </li>
        </ul>
      </div>
      <div class="card">
        <div class="label">By App Version (30d)</div>
        <ul>
          <li v-for="v in stats.byVersion" :key="v.version">
            <span>{{ v.version }}</span><span class="num">{{ v.count }}</span>
          </li>
        </ul>
      </div>
      <div class="card">
        <div class="label">By Android SDK (30d)</div>
        <ul>
          <li v-for="s in stats.bySdk" :key="s.sdk">
            <span>API {{ s.sdk }}</span><span class="num">{{ s.count }}</span>
          </li>
        </ul>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
const loading = ref(true)
const err = ref('')
const stats = ref<any>(null)
const maxHourly = computed(() => Math.max(1, ...(stats.value?.hourly24 ?? []).map((h: any) => h.count)))

onMounted(async () => {
  try {
    stats.value = await $fetch('/api/admin/stats')
  } catch (e: any) {
    if (e?.status === 401) {
      navigateTo('/admin/login')
      return
    }
    err.value = e?.data?.statusMessage || e?.message || 'Fehler'
  } finally {
    loading.value = false
  }
})

async function logout() {
  await $fetch('/api/admin/logout', { method: 'POST' }).catch(() => {})
  navigateTo('/admin/login')
}
</script>

<style scoped>
:global(html), :global(body) { margin: 0; padding: 0; background: #0a0a0a; color: #e5e7eb; font-family: -apple-system, system-ui, sans-serif; }
.wrap { max-width: 1100px; margin: 2rem auto; padding: 1rem 2rem; }
header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 1.5rem; }
.logo { font-size: 22px; font-weight: 700; }
.red { color: #dc2626; }
.logout { background: transparent; color: #9ca3af; border: 1px solid #2a2a2a; padding: 0.4rem 0.8rem; border-radius: 6px; cursor: pointer; }
.logout:hover { color: #f87171; border-color: #f87171; }
.grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(220px, 1fr)); gap: 1rem; }
.card { background: #14141a; border: 1px solid #2a2a2a; border-radius: 10px; padding: 1rem; }
.card.wide { grid-column: 1 / -1; }
.label { color: #9ca3af; font-size: 11px; text-transform: uppercase; letter-spacing: 1px; margin-bottom: 0.5rem; }
.big { font-size: 32px; font-weight: 700; color: #dc2626; font-family: ui-monospace, monospace; }
ul { margin: 0; padding: 0; list-style: none; }
li { display: flex; justify-content: space-between; padding: 4px 0; font-size: 13px; border-bottom: 1px solid #1f1f24; }
li:last-child { border: none; }
.num { color: #dc2626; font-family: ui-monospace, monospace; font-weight: 600; }
.bars { display: flex; align-items: flex-end; gap: 2px; height: 100px; }
.bar { flex: 1; background: linear-gradient(to top, #7f1d1d, #dc2626); border-radius: 2px 2px 0 0; min-height: 1px; }
.err { color: #f87171; }
</style>
