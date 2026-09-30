<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { NIcon } from 'naive-ui'
import { SearchOutline, ArrowForwardOutline } from '@vicons/ionicons5'
const router = useRouter()
const keyword = ref('')
function search() {
  const value = keyword.value.trim()
  router.push({ path: '/blog', query: value ? { keyword: value } : {} })
}
</script>

<template>
  <main class="search-home editorial-surface">
    <svg class="home-geometry" viewBox="0 0 1440 900" preserveAspectRatio="xMidYMid slice" aria-hidden="true">
      <g class="geometry-grid"><path d="M120 0V900 M1320 0V900 M0 120H1440 M0 780H1440"/><path d="M120 120H420V420H120Z M1020 480H1320V780H1020Z"/></g>
      <g class="geometry-lines"><path d="M-100 600L500 0 M940 900L1540 300 M120 780L420 480 M1020 420L1320 120"/><circle cx="1320" cy="120" r="210"/><circle cx="120" cy="780" r="150"/><path d="M1130 610L1240 720L1130 830L1020 720Z"/></g>
      <g class="geometry-fill"><path d="M120 120H220V220Z"/><path d="M1320 780H1200V660Z"/></g>
      <g class="geometry-points"><circle cx="120" cy="120" r="3"/><circle cx="1320" cy="780" r="3"/><circle cx="1020" cy="480" r="3"/></g>
    </svg>
    <section class="search-entry" aria-label="搜索文章">
      <p class="edition"><span>EST. 2025</span><span class="edition-divider" aria-hidden="true">/</span><span>VERSION 0.5.1</span></p>
      <h1 aria-label="Para BBS"><span>Para</span><em>BBS</em></h1>
      <p class="tagline">此岸彼岸·在所有的岸上</p>
      <div class="hero-rule" aria-hidden="true"></div>
      <form class="home-search" role="search" @submit.prevent="search">
        <n-icon :component="SearchOutline" aria-hidden="true" />
        <input v-model="keyword" type="search" aria-label="搜索文章、作者或标签" placeholder="搜索文章、作者或标签" autocomplete="off">
        <button type="submit" aria-label="搜索"><span>探索</span><n-icon :component="ArrowForwardOutline" /></button>
      </form>
    </section>
  </main>
</template>

<style scoped>
.search-home {
  min-height: calc(100dvh - 24px);
  display: grid;
  justify-items: center;
  align-items: start;
  padding: clamp(48px, 9vh, 96px) 40px 80px;
}

.search-entry {
  width: min(960px, 100%);
  text-align: center;
}

.search-home {
  background: linear-gradient(150deg, var(--bg-primary) 18%, color-mix(in srgb, var(--accent-soft) 35%, var(--bg-primary)) 62%, var(--bg-primary) 95%);
}

.search-home::before, .search-home::after {
  display: none;
}

.home-geometry {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  z-index: -1;
  pointer-events: none;
}

.geometry-grid {
  fill: none;
  stroke: var(--text-tertiary);
  stroke-opacity: .12;
  stroke-width: .7;
}

.geometry-lines {
  fill: none;
  stroke: var(--accent-color);
  stroke-opacity: .2;
  stroke-width: 1;
}

.geometry-fill {
  fill: var(--accent-color);
  fill-opacity: .055;
}

.geometry-points {
  fill: var(--accent-color);
  fill-opacity: .45;
}

.search-entry {
  position: relative;
}

h1 {
  display: flex;
  justify-content: center;
  align-items: baseline;
  gap: .18em;
  white-space: nowrap;
  margin: 0;
  font-family: 'Bodoni MT', Didot, 'Times New Roman', serif;
  font-size: clamp(88px, 12vw, 184px);
  line-height: 1.08;
  font-weight: 400;
  letter-spacing: -.045em;
}

h1 span {
  color: var(--text-primary);
}

h1 em {
  color: var(--accent-color);
  font-weight: 400;
  letter-spacing: -.055em;
  padding-right: .08em;
}

.home-search {
  display: flex;
  align-items: center;
  gap: 18px;
  width: min(760px, 100%);
  margin: auto;
  padding: 12px 14px 12px 28px;
  border: 1px solid var(--line-color);
  border-radius: 48px;
  background: var(--bg-primary);
  box-shadow: 0 12px 40px -16px color-mix(in srgb, var(--text-primary) 12%, transparent);
}

.home-search>.n-icon {
  font-size: 24px;
  color: var(--text-tertiary);
  flex-shrink: 0;
  transition: color 220ms;
}

input {
  flex: 1;
  min-width: 0;
  padding: 8px 0;
  border: 0;
  outline: 0;
  color: var(--text-primary);
  background: transparent;
  font-size: 16px;
}

input::placeholder {
  color: var(--text-tertiary);
}

.home-search button {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 14px;
  min-width: 128px;
  height: 48px;
  padding: 0 24px;
  border: 0;
  border-radius: 30px;
  color: var(--on-accent);
  background: var(--accent-color);
  cursor: pointer;
  font-size: 14px;
  letter-spacing: .1em;
  flex-shrink: 0;
  transition: background-color 200ms, transform 200ms;
}

.home-search button .n-icon {
  font-size: 20px;
  transition: transform 220ms;
}

.home-search button:hover .n-icon {
  transform: translateX(3px);
}

.home-search button:hover {
  background: var(--accent-highlight);
}

.home-search button:active {
  transform: scale(.96);
}

.edition {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 24px;
  margin: 0 0 36px;
  font-size: 12px;
  letter-spacing: .3em;
  color: var(--text-tertiary);
}

.edition-divider {
  color: var(--accent-color);
}

.tagline {
  margin: 26px 0 0;
  font-family: var(--font-sans);
  font-size: clamp(18px, 1.6vw, 22px);
  font-style: normal;
  letter-spacing: .08em;
  line-height: 1.6;
  color: var(--text-secondary);
}

.hero-rule {
  width: 72px;
  height: 1px;
  background: var(--line-color);
  margin: 26px auto clamp(44px, 8vh, 88px);
}

.edition, .tagline, .hero-rule {
  animation: enter 850ms 100ms cubic-bezier(.16,1,.3,1) both;
}

h1 {
  animation: enter 850ms cubic-bezier(.16,1,.3,1) both;
}

.home-search {
  animation: enter 800ms 100ms cubic-bezier(.16,1,.3,1) both;
  transition: border-color 220ms, box-shadow 300ms, transform 300ms;
}

.home-search:focus-within {
  border-color: var(--accent-color);
  box-shadow: 0 0 0 4px color-mix(in srgb, var(--accent-color) 6%, transparent), 0 18px 44px -22px color-mix(in srgb, var(--accent-color) 25%, transparent);
  transform: translateY(-2px);
}

.home-search:focus-within>.n-icon {
  color: var(--accent-color);
}

@keyframes enter {
  from {
    opacity: 0;
    translate: 0 16px;
  }
  to {
    opacity: 1;
    translate: 0 0;
  }
}

@media (max-width:640px) {
  .search-home {
    min-height: calc(100dvh - 64px);
    padding: 48px 20px 80px;
  }
  h1 {
    font-size: clamp(56px, 16vw, 100px);
    margin-bottom: 0;
  }
  .home-search {
    gap: 10px;
    padding: 9px 10px 9px 18px;
  }
  .home-search>.n-icon {
    font-size: 20px;
  }
  input {
    font-size: 14px;
  }
  .home-search button {
    min-width: 44px;
    width: 44px;
    height: 44px;
    padding: 0;
  }
  .home-search button span {
    display: none;
  }
  .edition {
    gap: 14px;
    font-size: 10px;
    letter-spacing: .2em;
    margin-bottom: 28px;
  }
  .tagline {
    font-size: 19px;
    margin-top: 24px;
  }
  .hero-rule {
    margin: 22px auto 32px;
    width: 56px;
  }
}

@media (prefers-reduced-motion: reduce) {
  h1, .home-search, .edition, .tagline, .hero-rule {
    animation: none;
  }
  .home-search, .home-search button, .home-search .n-icon {
    transition: none;
    transform: none !important;
  }
}
</style>
