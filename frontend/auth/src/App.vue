<script setup lang="ts">
import { ref } from 'vue'

const dark = ref(document.documentElement.classList.contains('dark'))
function toggleTheme() {
  dark.value = !dark.value
  document.documentElement.classList.toggle('dark', dark.value)
  document.querySelector('meta[name="theme-color"]')?.setAttribute('content', dark.value ? '#0d1117' : '#ffffff')
  try { localStorage.setItem('theme', dark.value ? 'dark' : 'light') } catch { /* Storage may be unavailable. */ }
}
</script>

<template>
  <header class="site-header">
    <RouterLink to="/" class="brand" aria-label="Para Auth 首页"><span aria-hidden="true">P</span></RouterLink>
  </header>
  <main id="main-content"><RouterView /></main>
  <footer class="site-footer">
    <nav aria-label="页脚导航">
      <a href="/bbs/">返回社区</a>
      <button type="button" class="text-button" :aria-pressed="dark" @click="toggleTheme">{{ dark ? '浅色模式' : '深色模式' }}</button>
      <span>Para Auth</span>
    </nav>
  </footer>
</template>
