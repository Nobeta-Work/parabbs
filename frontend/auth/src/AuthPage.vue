<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ApiError, changePassword, getAccount, getCsrf, registerAccount, validatePassword } from './api'
import type { Account, CsrfToken } from './api'

const route = useRoute()
const router = useRouter()
const page = computed(() => String(route.meta.page))
const titles: Record<string, string> = {
  login: '登录 Para Auth', register: '创建 Para 账号', account: '我的账号',
  password: '修改密码', logout: '退出登录', 'not-found': '页面不存在',
}
const subtitles: Record<string, string> = {
  login: '登录后，继续访问你的应用。', register: '使用同一个账号，连接 Para 的应用。',
  account: '管理你的账号与登录状态。', password: '修改成功后，需要重新登录。',
  logout: '确认退出当前账号？', 'not-found': '这个页面不存在或已经移走。',
}
const username = ref('')
const password = ref('')
const confirmation = ref('')
const currentPassword = ref('')
const showPassword = ref(false)
const account = ref<Account | null>(null)
const csrf = ref<CsrfToken | null>(null)
const loading = ref(false)
const submitting = ref(false)
const error = ref('')
const nativeForm = ref<HTMLFormElement | null>(null)
let generation = 0
const notice = computed(() => {
  if (route.query.registered === '1') return '账号已创建，请登录。'
  if (route.query.passwordChanged === '1') return '密码已修改，请使用新密码登录。'
  if ('logout' in route.query) return '你已退出登录。'
  return ''
})

async function initialize() {
  const current = ++generation
  error.value = 'error' in route.query ? '账号或密码不正确，或账号已停用，请重新登录。' : ''
  password.value = confirmation.value = currentPassword.value = ''
  showPassword.value = false
  account.value = null
  csrf.value = null
  submitting.value = false
  loading.value = true
  const currentPage = page.value
  try {
    if (currentPage === 'account' || currentPage === 'password') {
      const result = await getAccount()
      if (current === generation) account.value = result
    } else if (currentPage === 'login' || currentPage === 'logout') {
      // Do not probe protected endpoints on the login page: preserve the saved SSO request.
      const result = await getCsrf()
      if (current === generation) csrf.value = result
    }
  } catch (cause) {
    if (current !== generation) return
    if (cause instanceof ApiError && cause.status === 401) {
      await router.replace('/login')
      return
    }
    error.value = cause instanceof Error ? cause.message : '加载失败，请重试。'
  } finally {
    if (current === generation) loading.value = false
  }
}

watch(() => route.fullPath, initialize, { immediate: true })

async function submitNative() {
  if (submitting.value) return
  error.value = ''
  submitting.value = true
  const form = nativeForm.value
  const current = generation
  try {
    csrf.value = await getCsrf()
    await nextTick()
    if (current !== generation || !form) return
    // A browser navigation follows Spring's saved-request redirect, including the RP callback.
    // An AJAX login would follow that redirect invisibly and can consume the authorization code.
    HTMLFormElement.prototype.submit.call(form)
  } catch (cause) {
    if (current !== generation) return
    error.value = cause instanceof Error ? cause.message : '提交失败，请重试。'
    submitting.value = false
  }
}

async function submitAccount() {
  if (submitting.value) return
  error.value = ''
  if (page.value === 'register' && !/^[A-Za-z0-9][A-Za-z0-9._-]{2,63}$/.test(username.value)) {
    error.value = '账号需为 3–64 个字符，以字母或数字开头，可包含 . _ -。'
    return
  }
  const validation = validatePassword(password.value)
  if (validation) { error.value = validation; return }
  if (password.value !== confirmation.value) { error.value = '两次输入的密码不一致。'; return }
  const currentPage = page.value
  const current = generation
  submitting.value = true
  try {
    if (currentPage === 'register') {
      await registerAccount(username.value, password.value)
      if (current === generation) await router.replace('/login?registered=1')
    } else {
      await changePassword(currentPassword.value, password.value)
      if (current === generation) await router.replace('/login?passwordChanged=1')
    }
  } catch (cause) {
    if (current !== generation) return
    if (cause instanceof ApiError) {
      if (cause.status === 401) { await router.replace('/login'); return }
      if (cause.status === 409) error.value = currentPage === 'register'
        ? '这个账号已被使用，请换一个账号。' : '密码已被其他请求修改，请重新登录后重试。'
      else if (cause.status === 403) error.value = currentPage === 'register'
        ? '暂时无法注册，可能已关闭注册。请刷新页面后重试。' : '会话校验失败，请刷新页面后重试。'
      else if (cause.status === 400) error.value = currentPage === 'password'
        ? '当前密码不正确，或新密码不符合要求。' : '账号或密码不符合要求，请检查后重试。'
      else error.value = cause.message
    } else error.value = cause instanceof Error ? cause.message : '请求失败，请重试。'
  } finally {
    if (current === generation) submitting.value = false
  }
}

function dateLabel(value: string) {
  // Account timestamps are UTC LocalDateTime, without a timezone suffix.
  const date = new Date(value + 'Z')
  return Number.isNaN(date.getTime()) ? '—' : date.toLocaleDateString('zh-CN')
}
</script>

<template>
  <section class="auth-page" :aria-busy="loading || submitting">
    <div class="auth-container">
      <header class="form-heading">
        <h1>{{ titles[page] }}</h1>
        <p>{{ subtitles[page] }}</p>
      </header>

      <p v-if="notice" class="message success" role="status">{{ notice }}</p>
      <p v-if="error" class="message" role="alert">{{ error }}</p>
      <p v-if="loading" class="loading" role="status">正在加载…</p>

      <div class="auth-panel">
      <form v-if="page === 'login'" ref="nativeForm" method="post" action="/auth/api/login" @submit.prevent="submitNative">
        <input v-if="csrf" type="hidden" :name="csrf.parameterName" :value="csrf.token" />
        <label for="login-username">账号</label>
        <input id="login-username" v-model="username" name="username" autocomplete="username" placeholder="输入你的账号" required :readonly="submitting" />
        <label for="login-password">密码</label>
        <div class="password-field">
          <input id="login-password" v-model="password" name="password" :type="showPassword ? 'text' : 'password'" autocomplete="current-password" placeholder="输入密码" required :readonly="submitting" />
          <button type="button" :aria-pressed="showPassword" aria-controls="login-password" @click="showPassword = !showPassword">{{ showPassword ? '隐藏' : '显示' }}</button>
        </div>
        <button class="submit-button" :disabled="loading || submitting || !csrf">{{ submitting ? '正在登录…' : '登录' }}</button>
      </form>

      <form v-else-if="page === 'register' || (page === 'password' && account)" @submit.prevent="submitAccount">
        <template v-if="page === 'register'">
          <label for="register-username">账号</label>
          <input id="register-username" v-model="username" name="username" autocomplete="username" placeholder="设置你的账号" minlength="3" maxlength="64" pattern="[A-Za-z0-9][A-Za-z0-9._\-]{2,63}" required :readonly="submitting" aria-describedby="username-help" />
          <p id="username-help" class="field-help">3–64 个字符，以字母或数字开头，可包含 . _ -</p>
        </template>
        <template v-else>
          <label for="current-password">当前密码</label>
          <input id="current-password" v-model="currentPassword" name="currentPassword" type="password" autocomplete="current-password" placeholder="输入当前密码" required :readonly="submitting" />
        </template>
        <label for="new-password">{{ page === 'register' ? '密码' : '新密码' }}</label>
        <input id="new-password" v-model="password" name="newPassword" type="password" autocomplete="new-password" placeholder="至少 12 个字符" minlength="12" maxlength="72" required :readonly="submitting" aria-describedby="password-help" />
        <p id="password-help" class="field-help">至少 12 个字符，最多 72 个 UTF-8 字节；中文通常占 3 字节。</p>
        <label for="confirm-password">确认密码</label>
        <input id="confirm-password" v-model="confirmation" name="confirmPassword" type="password" autocomplete="new-password" placeholder="再次输入密码" required :readonly="submitting" />
        <button class="submit-button" :disabled="loading || submitting">{{ submitting ? '正在提交…' : page === 'register' ? '创建账号' : '保存新密码' }}</button>
      </form>

      <div v-else-if="page === 'account' && account" class="account-summary">
        <div class="account-name"><span class="avatar" aria-hidden="true">{{ account.username.charAt(0).toUpperCase() }}</span><div><strong>{{ account.username }}</strong><span class="account-status">{{ account.enabled ? '账号正常' : '账号已停用' }}</span></div></div>
        <dl><div><dt>账号</dt><dd>{{ account.username }}</dd></div><div><dt>创建日期</dt><dd>{{ dateLabel(account.createTime) }}</dd></div></dl>
        <RouterLink to="/password" class="submit-button">修改密码</RouterLink>
        <div class="secondary-actions form-bottom"><a href="/bbs/">返回社区</a><RouterLink to="/logout">退出登录</RouterLink></div>
      </div>

      <form v-else-if="page === 'logout'" ref="nativeForm" method="post" action="/auth/api/logout" @submit.prevent="submitNative">
        <input v-if="csrf" type="hidden" :name="csrf.parameterName" :value="csrf.token" />
        <p class="logout-note">退出后，其他应用的登录状态可能仍然保留。</p>
        <button class="submit-button" :disabled="loading || submitting || !csrf">{{ submitting ? '正在退出…' : '确认退出' }}</button>
      </form>

      <RouterLink v-else-if="page === 'not-found'" to="/" class="submit-button">返回账号</RouterLink>
      <button v-if="error && !loading && (page === 'account' || page === 'password' || !csrf && (page === 'login' || page === 'logout'))" type="button" class="retry-button" @click="initialize">重新加载</button>
      </div>
      <div v-if="page === 'login'" class="switch-panel">还没有账号？<RouterLink to="/register">创建账号</RouterLink></div>
      <div v-else-if="page === 'register'" class="switch-panel">已有账号？<RouterLink to="/login">登录</RouterLink></div>
      <div v-else-if="page === 'password' || page === 'logout'" class="switch-panel"><RouterLink to="/account">返回账号</RouterLink></div>
    </div>
  </section>
</template>
