<template>
  <div class="wrap">
    <div class="logo"><span class="red">Red</span>Monitor Admin</div>
    <form @submit.prevent="submit" class="form">
      <input v-model="username" placeholder="Username" autocomplete="username" required />
      <input v-model="password" type="password" placeholder="Password" autocomplete="current-password" required />
      <button :disabled="loading">{{ loading ? '…' : 'Login' }}</button>
      <div v-if="err" class="err">{{ err }}</div>
    </form>
  </div>
</template>

<script setup lang="ts">
const username = ref('')
const password = ref('')
const loading = ref(false)
const err = ref('')

async function submit() {
  loading.value = true
  err.value = ''
  try {
    await $fetch('/api/admin/login', {
      method: 'POST',
      body: { username: username.value, password: password.value }
    })
    navigateTo('/admin/dashboard')
  } catch (e: any) {
    err.value = e?.data?.statusMessage || e?.message || 'Login fehlgeschlagen'
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
:global(html), :global(body) { margin: 0; padding: 0; background: #0a0a0a; color: #e5e7eb; font-family: -apple-system, system-ui, sans-serif; }
.wrap { max-width: 360px; margin: 12vh auto; padding: 2rem; }
.logo { font-size: 24px; font-weight: 700; margin-bottom: 1.5rem; text-align: center; }
.red { color: #dc2626; }
.form { display: flex; flex-direction: column; gap: 0.75rem; }
input { background: #1a1a1a; border: 1px solid #2a2a2a; color: #e5e7eb; padding: 0.75rem; border-radius: 6px; font-size: 14px; }
input:focus { outline: none; border-color: #dc2626; }
button { background: #dc2626; color: #fff; border: none; padding: 0.75rem; border-radius: 6px; font-size: 14px; font-weight: 600; cursor: pointer; }
button:hover { background: #ef4444; }
button:disabled { opacity: 0.5; cursor: wait; }
.err { color: #f87171; font-size: 12px; }
</style>
