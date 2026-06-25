<template>
  <div class="wrap">
    <div class="logo">
      <span class="red">Red</span>Monitor <span class="muted">Backend</span>
    </div>
    <p class="muted">This service backs the RedMonitor Android app.</p>
    <p class="muted small">Privacy: device data is hardware-derived hash, end-to-end encrypted blob, at-rest encrypted column. No PII collected.</p>
    <NuxtLink to="/admin/login" class="link">Admin →</NuxtLink>
  </div>
</template>

<style scoped>
:global(html), :global(body) { margin: 0; padding: 0; background: #0a0a0a; color: #e5e7eb; font-family: -apple-system, system-ui, sans-serif; }
.wrap { max-width: 540px; margin: 8vh auto; padding: 2rem; }
.logo { font-size: 28px; font-weight: 700; margin-bottom: 0.5rem; }
.red { color: #dc2626; }
.muted { color: #9ca3af; }
.small { font-size: 12px; }
.link { color: #dc2626; text-decoration: none; font-weight: 600; display: inline-block; margin-top: 1rem; }
.link:hover { text-decoration: underline; }
</style>
