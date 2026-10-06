<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { NButton, NIcon } from 'naive-ui'
import { ArrowBackOutline, ShieldCheckmarkOutline } from '@vicons/ionicons5'
import { startSsoLogin } from '@/api/auth'

const route = useRoute()
const router = useRouter()
const returnTo = computed(() => typeof route.query.redirect === 'string' ? route.query.redirect : '/admin')
</script>

<template>
  <main class="admin-login-page">
    <section class="admin-login-card">
      <button class="back-link" type="button" @click="router.push('/')">
        <n-icon :component="ArrowBackOutline" />返回前台
      </button>
      <div class="login-mark"><n-icon :component="ShieldCheckmarkOutline" /></div>
      <p class="eyebrow">Para BBS / Control Room</p>
      <h1>后台管理</h1>
      <p class="intro">使用 BBS 账号登录，进入控制台需要社区管理员权限。</p>
      <n-button type="primary" block @click="startSsoLogin(returnTo, true)">继续登录</n-button>
    </section>
  </main>
</template>

<style scoped>

.admin-login-page {
  min-height: 100vh;
  display: grid;
  place-items: center;
  padding: 24px;
  background:
    var(--bg-secondary);
  color: var(--text-primary);
}

.admin-login-card {
  width: min(100%, 430px);
  padding: 38px;
  border: 1px solid var(--line-color);
  background: color-mix(in srgb, var(--bg-primary) 88%, transparent);
  box-shadow: none;
  border-radius: 28px;
}

.back-link {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--text-secondary);
  cursor: pointer;
  font: inherit;
}

.back-link:hover {
  color: var(--accent-color);
}

.login-mark {
  display: grid;
  width: 58px;
  height: 58px;
  margin: 48px 0 22px;
  place-items: center;
  border: 1px solid var(--accent-color);
  border-radius: 2px;
  color: var(--accent-color);
  font-size: 28px;
}

.eyebrow {
  margin: 0 0 9px;
  color: var(--accent-highlight);
  font-size: 0.76rem;
  letter-spacing: 0;
  text-transform: uppercase;
}

h1 {
  margin: 0;
  font-size: 28px;
  line-height: 1.1;
}

.intro {
  margin: 14px 0 32px;
  color: var(--text-secondary);
  line-height: 1.7;
}

@media (max-width: 520px) {
  .admin-login-card {
    padding: 28px 22px;
    border-radius: 28px;
  }
}

</style>
