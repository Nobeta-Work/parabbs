<script setup lang="ts">
import { computed, ref, h } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { NIcon, NAvatar, NDrawer, NDrawerContent, NDropdown, NSwitch } from 'naive-ui'
import { CompassOutline, DocumentTextOutline, BriefcaseOutline, PersonOutline, AddOutline, MenuOutline, ChevronBackOutline, ChevronForwardOutline, ShieldCheckmarkOutline, LogOutOutline } from '@vicons/ionicons5'
import { useUserStore } from '@/stores/user'
import { useThemeStore } from '@/stores/theme'
import { resolveAvatarUrl } from '@/utils/avatar'

const brandLogo = `${import.meta.env.BASE_URL.replace(/\/?$/, '/')}webIcon.png`
const collapsed = defineModel<boolean>('collapsed', { default: false })
const user = useUserStore()
const theme = useThemeStore()
const route = useRoute()
const router = useRouter()
const open = ref(false)
const avatar = computed(() => resolveAvatarUrl(user.userInfo?.avatar))
const profile = computed(() => user.userInfo?.id ? '/' + user.userInfo.id : '/login')
const links = computed(() => [
  { label: '发现', path: '/', icon: CompassOutline },
  { label: '文章', path: '/blog', icon: DocumentTextOutline },
  { label: '工作台', path: '/workspace', icon: BriefcaseOutline },
  ...(user.isAuthenticated ? [{ label: '我的主页', path: profile.value, icon: PersonOutline }] : []),
  ...(user.hasAnyRole(['ROLE_ADMIN']) ? [{ label: '管理', path: '/admin', icon: ShieldCheckmarkOutline }] : []),
])
const accountOptions = [
  { label: '个人资料', key: 'profile', icon: () => h(NIcon, null, { default: () => h(PersonOutline) }) },
  { label: '退出登录', key: 'logout', icon: () => h(NIcon, null, { default: () => h(LogOutOutline) }) },
]

function active(path: string) {
  return path === '/'
    ? route.path === '/'
    : route.path === path || route.path.startsWith(path + '/')
}

function go(path: string) {
  open.value = false
  router.push(path)
}

function account(key: string) {
  if (key === 'logout') {
    user.logout()
    go('/login')
  } else {
    go(profile.value)
  }
}
</script>
<template>
  <aside class="app-sidebar" :class="{collapsed}" aria-label="主导航">
<button class="sidebar-toggle icon-button" :aria-label="collapsed ? '展开侧栏' : '折叠侧栏'" :aria-expanded="!collapsed" @click="collapsed=!collapsed"><n-icon :component="collapsed ? ChevronForwardOutline : ChevronBackOutline" /></button>
    <router-link class="brand" to="/" aria-label="Para BBS 首页">
<img class="brand-mark" :src="brandLogo" alt="" width="38" height="38"><span class="sidebar-label">Para <em>BBS</em></span></router-link>
    <button class="compose" aria-label="写文章" @click="go('/blog?create=1')">
<n-icon :component="AddOutline" /><span class="sidebar-label">写文章</span></button>
    <nav>
<router-link v-for="item in links" :key="item.path" :to="item.path" :aria-label="item.label" :title="collapsed ? item.label : undefined" :class="{selected:active(item.path)}" :aria-current="active(item.path) ? 'page' : undefined">
<n-icon :component="item.icon" /><span class="sidebar-label">{{ item.label }}</span></router-link>
</nav>
    <div class="sidebar-bottom">
<div class="theme-control">
<span>深色模式</span>
<n-switch :value="theme.isDark" aria-label="切换深浅主题" @update:value="theme.toggleTheme" />
</div>
      <n-dropdown v-if="user.isAuthenticated" trigger="click" :options="accountOptions" @select="account">
<button class="account">
<n-avatar round :size="34" :src="avatar" />{{ user.userInfo?.nickname || '账户' }}</button>
</n-dropdown>
      <button v-else class="account" @click="go('/login')">
<n-icon :component="PersonOutline" />登录</button>
    </div>
  </aside>
  <header class="nav-header">
<button class="mobile-menu icon-button" aria-label="打开导航菜单" @click="open=true">
<n-icon :component="MenuOutline" />
</button>
<router-link class="mobile-brand" to="/"><img :src="brandLogo" alt="" width="28" height="28">Para BBS</router-link>
</header>
  <n-drawer v-model:show="open" :width="280" placement="left">
<n-drawer-content title="Para BBS">
<button class="compose" @click="go('/blog?create=1')">
<n-icon :component="AddOutline" />写文章</button>
<nav class="drawer-nav">
<a v-for="item in links" :key="item.path" :href="router.resolve(item.path).href" :class="{selected:active(item.path)}" @click.prevent="go(item.path)">
<n-icon :component="item.icon" />{{ item.label }}</a>
</nav>
<div class="theme-control">
<span>深色模式</span>
<n-switch :value="theme.isDark" aria-label="切换深浅主题" @update:value="theme.toggleTheme" />
</div>
<button class="account" @click="user.isAuthenticated ? account('logout') : go('/login')">{{ user.isAuthenticated ? '退出登录' : '登录' }}</button>
</n-drawer-content>
</n-drawer>
</template>
<style scoped>
.app-sidebar {
  position: fixed;
  inset: 0 auto 0 0;
  width: var(--sidebar-width);
  box-sizing: border-box;
  z-index: 1000;
  display: flex;
  flex-direction: column;
}

.brand {
  display: flex;
  align-items: center;
  color: var(--text-primary);
  text-decoration: none;
}

.brand-mark {
  object-fit: contain;
  flex-shrink: 0;
}

.mobile-brand img {
  object-fit: contain;
  vertical-align: middle;
  margin-right: 8px;
}

.compose {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 12px;
  width: 100%;
  cursor: pointer;
}

nav {
  display: grid;
}

nav a {
  display: flex;
  align-items: center;
  text-decoration: none;
  color: var(--text-secondary);
}

nav a:hover {
  background: var(--card-hover);
}

nav a.selected {
  color: var(--accent-color);
}

.sidebar-bottom {
  margin-top: auto;
}

.theme-control {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 20px;
  color: var(--text-secondary);
}

.account {
  display: flex;
  align-items: center;
  width: 100%;
  border: 0;
  background: transparent;
  color: var(--text-primary);
  cursor: pointer;
}

.account:hover {
  background: var(--card-hover);
}

.nav-header {
  display: none;
  position: fixed;
  top: 0;
  left: var(--sidebar-width);
  right: 0;
  height: 72px;
  padding: 0 36px;
  box-sizing: border-box;
  align-items: center;
  justify-content: flex-end;
  background: var(--bg-primary);
  z-index: 1000;
}

.icon-button {
  border: 0;
  background: transparent;
  color: var(--text-secondary);
  width: 42px;
  height: 42px;
  display: grid;
  place-items: center;
  border-radius: 50%;
  cursor: pointer;
  font-size: 24px;
}

.icon-button:hover {
  background: var(--bg-secondary);
}

.mobile-menu, .mobile-brand {
  display: none;
}

.drawer-nav {
  margin-bottom: 30px;
}

@media (max-width:900px) {
  .app-sidebar {
    display: none;
  }
  .nav-header {
    display: flex;
    left: 0;
    height: 64px;
    padding: 0 16px;
    gap: 12px;
    justify-content: flex-start;
  }
  .mobile-menu {
    display: grid;
  }
  .mobile-brand {
    display: block;
    color: var(--text-primary);
    text-decoration: none;
    font-size: 20px;
    font-weight: 650;
  }
}

.sidebar-toggle {
  position: absolute;
  top: 50%;
  transform: translateY(-50%);
  margin: 0;
  border: 1px solid var(--line-color);
  background: var(--bg-primary);
  font-size: 15px;
  color: var(--text-tertiary);
  transition: color 180ms, border-color 180ms, background-color 180ms;
}

.sidebar-toggle:hover, .sidebar-toggle:focus-visible {
  color: var(--accent-color);
  border-color: var(--accent-color);
  background: var(--accent-soft);
}

.collapsed .sidebar-label, .collapsed .theme-control>span {
  display: none;
}

.collapsed .brand {
  padding: 0;
  justify-content: center;
}

.collapsed nav a {
  justify-content: center;
}

.collapsed .compose {
  margin-inline: auto;
}

.collapsed .theme-control {
  justify-content: center;
}

.collapsed .account {
  justify-content: center;
  padding-inline: 0;
  font-size: 0;
  gap: 0;
}

.collapsed .account>.n-icon {
  font-size: 24px;
}

/* Publication navigation: fine rules, a small index, and a single active stroke. */

.app-sidebar {
  padding: 30px 20px 20px;
  border-right: 1px solid var(--line-color);
  background: linear-gradient(170deg, var(--bg-primary), color-mix(in srgb, var(--bg-secondary) 65%, var(--bg-primary)));
}

.brand {
  gap: 10px;
  padding: 0 0 24px;
  margin-bottom: 26px;
  border-bottom: 1px solid var(--line-color);
  font-family: var(--font-sans);
  font-size: 27px;
  font-weight: 500;
  letter-spacing: -.04em;
}

.brand em {
  font-weight: 400;
  color: var(--accent-color);
}

.brand-mark {
  width: 30px;
  height: 34px;
}

.compose {
  min-height: 44px;
  margin-bottom: 30px;
  border: 1px solid color-mix(in srgb, var(--accent-color) 28%, var(--line-color));
  border-radius: 8px;
  background: var(--bg-primary);
  color: var(--accent-color);
  font-size: 13px;
  font-weight: 500;
  letter-spacing: .12em;
  transition: background 180ms, border-color 180ms, color 180ms;
}

.compose:hover {
  background: var(--accent-color);
  border-color: var(--accent-color);
  color: var(--on-accent);
}

.compose .n-icon {
  font-size: 20px;
}

nav {
  gap: 6px;
  counter-reset: navigation;
}

nav a {
  position: relative;
  counter-increment: navigation;
  min-height: 46px;
  padding: 0 12px;
  gap: 13px;
  border-radius: 6px;
  font-size: 13px;
  letter-spacing: .055em;
  transition: color 180ms, background 180ms;
}

.app-sidebar nav a::after {
  content: counter(navigation, decimal-leading-zero);
  margin-left: auto;
  font: 10px var(--font-mono);
  letter-spacing: 0;
  color: var(--text-tertiary);
  opacity: .6;
}

nav a.selected {
  background: color-mix(in srgb, var(--accent-soft) 60%, var(--bg-primary));
  font-weight: 550;
}

nav a.selected::before {
  content: '';
  position: absolute;
  left: -20px;
  top: 14px;
  bottom: 14px;
  width: 2px;
  background: var(--accent-color);
}

nav .n-icon {
  font-size: 20px;
}

.sidebar-bottom {
  padding-top: 12px;
  border-top: 1px solid var(--line-color);
}

.theme-control {
  padding: 12px 8px 20px;
  font-size: 11px;
  letter-spacing: .08em;
}

.account {
  padding: 10px 8px;
  border-radius: 6px;
  font-size: 12px;
  gap: 12px;
}

.sidebar-toggle {
  height: 54px;
  width: 22px;
  right: -11px;
  border-radius: 6px;
  box-shadow: 0 3px 10px color-mix(in srgb, var(--text-primary) 3%, transparent);
}

.collapsed {
  padding-inline: 12px;
}

.collapsed .brand {
  padding-bottom: 24px;
}

.collapsed .compose {
  width: 44px;
}

.collapsed nav a {
  padding: 0;
}

.collapsed nav a::after {
  display: none;
}

.collapsed nav a.selected::before {
  left: -12px;
}

.collapsed .theme-control {
  padding-inline: 0;
}

.nav-header .icon-button {
  font-size: 21px;
}
</style>
