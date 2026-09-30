<script setup lang="ts">
import { ref, watch, nextTick } from 'vue'
import { useRoute } from 'vue-router'
import TopNav from '@/components/TopNav.vue'
const route = useRoute()
const collapsed = ref(false)
try { collapsed.value = localStorage.getItem('sidebar-collapsed') === 'true' } catch { /* Storage can be unavailable. */ }
watch(collapsed, value => { try { localStorage.setItem('sidebar-collapsed', String(value)) } catch { /* Keep the in-memory preference. */ } })
watch(() => route.path, async () => { await nextTick(); window.scrollTo({ top: 0, left: 0 }) })
</script>
<template>
<div class="main-layout" :style="{ '--sidebar-width': collapsed ? '80px' : '240px' }">
<TopNav v-model:collapsed="collapsed" />
<div class="page-container">
<router-view />
</div>
</div>
</template>
<style scoped>
.main-layout {
  min-height: 100dvh;
  background: var(--bg-primary);
}

.page-container {
  position: relative;
  z-index: 1;
  margin-left: var(--sidebar-width);
  min-width: 0;
  padding-top: 24px;
}

@media (max-width: 900px) {
  .page-container {
    margin-left: 0;
    padding-top: 64px;
  }
}
</style>
