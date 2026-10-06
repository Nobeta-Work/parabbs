<script setup lang="ts">
import { computed, ref, watch, onUnmounted } from 'vue'
import { useRoute } from 'vue-router'
import { ApiError, getAccount, getAuthRoles, listClients, listAccounts, getClient, getManagedAccount,
  createClient, updateClient, setClientStatus, resetClientSecret, createManagedAccount,
  setAccountStatus, setAccountRoles, resetAccountPassword, validatePassword } from './api'
import type { Account, Client, ClientInput } from './api'

const route = useRoute()
const clientsTab = computed(() => route.path.endsWith('/clients'))
const actor = ref<Account | null>(null), allowed = ref(false), loading = ref(false), busy = ref(false)
const error = ref(''), notice = ref(''), query = ref(''), status = ref(''), page = ref(1), total = ref(0)
const clients = ref<Client[]>([]), accounts = ref<Account[]>([]), availableRoles = ref<string[]>([])
const panel = ref<'client' | 'account' | 'roles' | 'password' | ''>('')
const selectedClient = ref<Client | null>(null), selectedAccount = ref<Account | null>(null)
const adminRole = ref(false), username = ref(''), password = ref(''), confirmation = ref('')
const secret = ref(''), secretClientId = ref(''), copied = ref(false)
const redirectUris = ref(''), logoutUris = ref(''), expiry = ref(''), refresh = ref(true)
const newInput = (): ClientInput => ({ clientId: '', clientName: '', redirectUris: [], postLogoutRedirectUris: [],
  scopes: ['openid'], authorizationGrantTypes: ['authorization_code', 'refresh_token'], requireAuthorizationConsent: true,
  requireProofKey: false, authorizationCodeTimeToLive: 300, accessTokenTimeToLive: 900,
  refreshTokenTimeToLive: 604800, clientSecretExpiresAt: null })
const input = ref<ClientInput>(newInput())
let generation = 0

function clearSecret() { secret.value = ''; secretClientId.value = ''; copied.value = false }
function closePanel() { panel.value = ''; password.value = confirmation.value = ''; selectedAccount.value = null; selectedClient.value = null }
function message(cause: unknown) {
  if (cause instanceof ApiError) {
    if (cause.status === 401) return '会话已失效，请重新登录。'
    if (cause.status === 403) return '没有管理权限，或会话校验已失效。请刷新页面。'
    if (cause.status === 409) return '操作冲突：标识已存在、账号被并发修改，或操作将移除最后一个管理员。'
    if (cause.status === 400) return '参数不符合要求，请检查账号、密码、回调地址及有效期。'
    if (cause.status === 404) return '记录不存在，请刷新列表。'
  }
  return cause instanceof Error ? cause.message : '请求失败，请重试。'
}
async function load() {
  const current = ++generation
  loading.value = true; error.value = ''
  try {
    const profile = await getAccount()
    if (current !== generation) return
    actor.value = profile; allowed.value = profile.roles.includes('ROLE_AUTH_ADMIN')
    if (!allowed.value) { clients.value = []; accounts.value = []; return }
    if (clientsTab.value) {
      const result = await listClients(page.value, query.value, status.value)
      if (current === generation) { clients.value = result.items; total.value = result.total }
    } else {
      const [result, roles] = await Promise.all([listAccounts(page.value, query.value, status.value), getAuthRoles()])
      if (current === generation) { accounts.value = result.items; total.value = result.total; availableRoles.value = roles }
    }
  } catch (cause) {
    if (current !== generation) return
    error.value = message(cause)
    if (cause instanceof ApiError && (cause.status === 401 || cause.status === 403)) allowed.value = false
  } finally { if (current === generation) loading.value = false }
}
watch(() => route.path, () => {
  clearSecret(); closePanel(); notice.value = ''; query.value = ''; status.value = ''; page.value = 1
  void load()
}, { immediate: true })
onUnmounted(() => { ++generation; clearSecret(); password.value = confirmation.value = '' })
function search() { page.value = 1; void load() }
function turn(delta: number) { page.value += delta; void load() }

async function operation(action: () => Promise<void>) {
  if (busy.value) return
  const current = generation
  busy.value = true; error.value = ''; notice.value = ''
  try { await action() }
  catch (cause) { if (current === generation) error.value = message(cause) }
  finally { busy.value = false }
}
function openNewClient() {
  clearSecret(); selectedClient.value = null; input.value = newInput(); redirectUris.value = ''; logoutUris.value = ''
  expiry.value = ''; refresh.value = true; panel.value = 'client'
}
async function editClient(client: Client) {
  await operation(async () => {
    const current = generation, detail = await getClient(client.id)
    if (current !== generation) return
    clearSecret(); selectedClient.value = detail; input.value = { ...detail }
    redirectUris.value = detail.redirectUris.join('\n'); logoutUris.value = detail.postLogoutRedirectUris.join('\n')
    // UTC input avoids silently converting secret expiration between time zones.
    expiry.value = detail.clientSecretExpiresAt?.slice(0, 16) ?? ''
    refresh.value = detail.authorizationGrantTypes.includes('refresh_token'); panel.value = 'client'
  })
}
const lines = (text: string) => [...new Set(text.split(/\r?\n/).map(value => value.trim()).filter(Boolean))]
async function saveClient() {
  await operation(async () => {
    const current = generation
    const body: ClientInput = { ...input.value, redirectUris: lines(redirectUris.value), postLogoutRedirectUris: lines(logoutUris.value),
      scopes: ['openid'], authorizationGrantTypes: refresh.value ? ['authorization_code', 'refresh_token'] : ['authorization_code'],
      clientSecretExpiresAt: expiry.value ? new Date(expiry.value + 'Z').toISOString() : null }
    if (selectedClient.value) await updateClient(selectedClient.value.id, body)
    else {
      const result = await createClient(body)
      if (current !== generation) return
      secret.value = result.clientSecret; secretClientId.value = result.client.clientId
    }
    if (current !== generation) return
    closePanel(); notice.value = '客户端已保存。'; await load()
  })
}
async function toggleClient(client: Client) {
  if (!window.confirm(`${client.enabled ? '停用' : '启用'}客户端 ${client.clientId}？${client.enabled ? '停用将撤销该客户端的授权记录。' : ''}`)) return
  await operation(async () => { await setClientStatus(client.id, !client.enabled); await load() })
}
async function resetSecret(client: Client) {
  if (!window.confirm(`重置 ${client.clientId} 的密钥？应用需同步更新密钥才能继续兑换令牌。`)) return
  await operation(async () => {
    const current = generation, result = await resetClientSecret(client.id)
    if (current !== generation) return
    clearSecret(); secret.value = result.clientSecret; secretClientId.value = client.clientId
  })
}
async function copySecret() {
  try { await navigator.clipboard.writeText(secret.value); copied.value = true }
  catch { error.value = '无法复制，请手动选择密钥并复制。' }
}
function newAccount() { clearSecret(); username.value = ''; password.value = confirmation.value = ''; panel.value = 'account' }
async function openAccount(account: Account, kind: 'roles' | 'password') {
  await operation(async () => {
    const current = generation, detail = await getManagedAccount(account.id)
    if (current !== generation) return
    selectedAccount.value = detail; adminRole.value = detail.roles.includes('ROLE_AUTH_ADMIN')
    password.value = confirmation.value = ''; panel.value = kind
  })
}
async function saveAccount() {
  const kind = panel.value, account = selectedAccount.value
  if (kind !== 'roles') {
    const validation = validatePassword(password.value)
    if (validation) { error.value = validation; return }
    if (password.value !== confirmation.value) { error.value = '两次输入的密码不一致。'; return }
  }
  await operation(async () => {
    const current = generation
    if (kind === 'account') await createManagedAccount(username.value, password.value)
    else if (account && kind === 'roles') await setAccountRoles(account.id, adminRole.value ? ['ROLE_AUTH_ADMIN'] : [])
    else if (account && kind === 'password') await resetAccountPassword(account.id, password.value)
    else return
    if (current !== generation) return
    closePanel(); notice.value = '账号设置已保存。'; await load()
  })
}
async function toggleAccount(account: Account) {
  if (!window.confirm(`${account.enabled ? '停用' : '启用'}账号 ${account.username}？${account.enabled ? '停用将撤销 Auth 会话和授权记录。' : ''}`)) return
  await operation(async () => { await setAccountStatus(account.id, !account.enabled); await load() })
}
function date(value: string | null) { return value ? new Date(value.endsWith('Z') ? value : value + 'Z').toLocaleString('zh-CN') : '不设到期时间' }
</script>

<template>
  <section class="admin-page" :aria-busy="loading || busy">
    <header class="admin-heading"><div><p class="admin-eyebrow">Para Auth</p><h1>认证中心管理</h1><p>管理接入应用、用户账号与认证中心权限。</p></div><RouterLink to="/account">我的账号</RouterLink></header>
    <nav class="admin-tabs" aria-label="管理导航"><RouterLink to="/admin/clients">客户端</RouterLink><RouterLink to="/admin/accounts">用户账号与权限</RouterLink></nav>
    <p v-if="error" class="message" role="alert">{{ error }}</p>
    <p v-if="notice" class="message success" role="status">{{ notice }}</p>
    <p v-if="loading" role="status">正在加载…</p>
    <div v-if="!allowed && !loading" class="admin-empty"><h2>需要管理员权限</h2><p>只有认证中心管理员可以访问这里。使用普通账号登录时，可联系现有管理员分配权限。</p><RouterLink to="/login">前往登录</RouterLink><button type="button" @click="load">重新加载</button></div>
    <template v-if="allowed">
      <aside v-if="secret" class="admin-secret" aria-label="新客户端密钥"><h2>{{ secretClientId }} 的新密钥</h2><p>仅本次展示。请保存到应用的服务端配置，关闭后无法再次查询。</p><code>{{ secret }}</code><div class="admin-actions"><button type="button" @click="copySecret">{{ copied ? '已复制' : '复制密钥' }}</button><button type="button" @click="clearSecret">已保存，关闭</button></div></aside>
      <form class="admin-toolbar" @submit.prevent="search"><label class="sr-only" for="admin-query">搜索</label><input id="admin-query" v-model="query" :maxlength="clientsTab ? 100 : 64" :placeholder="clientsTab ? '搜索客户端' : '搜索账号名'" /><label class="sr-only" for="admin-status">状态</label><select id="admin-status" v-model="status"><option value="">全部状态</option><option value="true">已启用</option><option value="false">已停用</option></select><button :disabled="loading || busy">查询</button><button type="button" class="primary" :disabled="loading || busy" @click="clientsTab ? openNewClient() : newAccount()">{{ clientsTab ? '创建客户端' : '创建账号' }}</button></form>
      <div v-if="panel" class="admin-editor">
        <form v-if="panel === 'client'" @submit.prevent="saveClient">
          <h2>{{ selectedClient ? '编辑客户端' : '创建客户端' }}</h2><p class="field-help">授权码模式的机密客户端，客户端认证方式为 client_secret_basic。</p>
          <fieldset :disabled="busy"><div class="admin-grid">
            <label>客户端标识<input v-model="input.clientId" required maxlength="100" pattern="[A-Za-z0-9._\-]+" :readonly="!!selectedClient" /></label>
            <label>应用名称<input v-model="input.clientName" required maxlength="200" /></label>
            <label>回调地址（每行一个）<textarea v-model="redirectUris" required rows="4" placeholder="https://example.com/login/callback" /></label>
            <label>退出回调地址（每行一个，可为空）<textarea v-model="logoutUris" rows="4" /></label>
            <label>授权码有效期（秒）<input v-model.number="input.authorizationCodeTimeToLive" type="number" min="30" max="600" required /></label>
            <label>访问令牌有效期（秒）<input v-model.number="input.accessTokenTimeToLive" type="number" min="60" max="86400" required /></label>
            <label>刷新令牌有效期（秒）<input v-model.number="input.refreshTokenTimeToLive" type="number" min="300" max="2592000" required /></label>
            <label>客户端密钥到期时间（UTC，可为空）<input v-model="expiry" type="datetime-local" /></label>
          </div><div class="admin-checks"><label><input v-model="input.requireAuthorizationConsent" type="checkbox" />要求用户确认授权</label><label><input v-model="input.requireProofKey" type="checkbox" />要求 PKCE</label><label><input v-model="refresh" type="checkbox" />允许刷新令牌</label></div><p class="field-help">身份范围为 openid。客户端标识创建后不可修改；回调地址需完整匹配。</p></fieldset>
          <div class="admin-actions"><button class="primary" :disabled="busy">{{ busy ? '正在保存…' : '保存客户端' }}</button><button type="button" :disabled="busy" @click="closePanel">取消</button></div>
        </form>
        <form v-else @submit.prevent="saveAccount">
          <h2>{{ panel === 'account' ? '创建账号' : panel === 'roles' ? '管理账号权限' : '重置账号密码' }}</h2>
          <p v-if="selectedAccount">{{ selectedAccount.username }} · {{ selectedAccount.subject }}</p>
          <fieldset :disabled="busy"><template v-if="panel === 'roles'"><label v-if="availableRoles.includes('ROLE_AUTH_ADMIN')" class="admin-checkbox"><input v-model="adminRole" type="checkbox" :disabled="selectedAccount?.id === actor?.id" />认证中心管理员</label><p class="field-help">管理员可管理所有客户端和账号。取消后该账号恢复普通用户权限；修改会撤销其 Auth 会话和授权记录。</p></template>
          <template v-else><label v-if="panel === 'account'">账号名<input v-model="username" required minlength="3" maxlength="64" pattern="[A-Za-z0-9][A-Za-z0-9._\-]{2,63}" autocomplete="off" /></label><label>新密码<input v-model="password" type="password" required minlength="12" maxlength="72" autocomplete="new-password" /></label><label>确认新密码<input v-model="confirmation" type="password" required minlength="12" maxlength="72" autocomplete="new-password" /></label><p class="field-help">至少 12 个字符，最多 72 个 UTF-8 字节。重置密码会撤销该账号的 Auth 会话和授权记录。</p></template></fieldset>
          <div class="admin-actions"><button class="primary" :disabled="busy">{{ busy ? '正在保存…' : '保存' }}</button><button type="button" :disabled="busy" @click="closePanel">取消</button></div>
        </form>
      </div>
      <div class="admin-table-wrap"><table v-if="clientsTab"><caption class="sr-only">客户端列表</caption><thead><tr><th>应用</th><th>状态</th><th>授权设置</th><th>操作</th></tr></thead><tbody><tr v-for="client in clients" :key="client.id"><td><strong>{{ client.clientName }}</strong><small>{{ client.clientId }}</small><small>密钥到期：{{ date(client.clientSecretExpiresAt) }}</small></td><td>{{ client.enabled ? '已启用' : '已停用' }}</td><td><small>{{ client.requireProofKey ? '要求 PKCE' : '可选 PKCE' }}</small><small>{{ client.requireAuthorizationConsent ? '确认授权' : '无需确认' }}</small></td><td><div class="admin-actions"><button :disabled="busy || loading" @click="editClient(client)">详情 / 编辑</button><button :disabled="busy || loading" @click="toggleClient(client)">{{ client.enabled ? '停用' : '启用' }}</button><button :disabled="busy || loading" @click="resetSecret(client)">重置密钥</button></div></td></tr><tr v-if="!clients.length && !loading"><td colspan="4">没有符合条件的客户端。</td></tr></tbody></table>
      <table v-else><caption class="sr-only">用户账号列表</caption><thead><tr><th>账号</th><th>状态</th><th>权限</th><th>操作</th></tr></thead><tbody><tr v-for="account in accounts" :key="account.id"><td><strong>{{ account.username }}</strong><small>{{ account.subject }}</small><small>创建于 {{ date(account.createTime) }}</small></td><td>{{ account.enabled ? '已启用' : '已停用' }}</td><td>{{ account.roles.includes('ROLE_AUTH_ADMIN') ? '认证中心管理员' : '普通用户' }}<small v-if="account.id === actor?.id">当前账号</small></td><td><div class="admin-actions"><button :disabled="busy || loading" @click="openAccount(account, 'roles')">权限</button><button :disabled="busy || loading || account.id === actor?.id" @click="toggleAccount(account)">{{ account.enabled ? '停用' : '启用' }}</button><button :disabled="busy || loading || account.id === actor?.id" @click="openAccount(account, 'password')">重置密码</button></div></td></tr><tr v-if="!accounts.length && !loading"><td colspan="4">没有符合条件的账号。</td></tr></tbody></table></div>
      <footer class="admin-pagination"><span>共 {{ total }} 条 · 第 {{ page }} 页</span><div class="admin-actions"><button :disabled="page <= 1 || busy || loading" @click="turn(-1)">上一页</button><button :disabled="page * 20 >= total || busy || loading" @click="turn(1)">下一页</button></div></footer>
    </template>
  </section>
</template>
