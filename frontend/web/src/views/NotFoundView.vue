<script setup lang="ts">
import { useRouter } from 'vue-router'
import { NResult, NButton } from 'naive-ui'
import { useThemeStore } from '@/stores/theme'
import { storeToRefs } from 'pinia'

const router = useRouter()
const themeStore = useThemeStore()
const { isDark } = storeToRefs(themeStore)
</script>

<template>
  <div class="not-found-page" :class="{ 'not-found-page--dark': isDark }">
    <div class="geometric-bg">
      <div class="shape shape-1"></div>
      <div class="shape shape-2"></div>
    </div>
    
    <div class="content glass">
      <n-result
        status="404"
        title="404 资源不存在"
        description="生活总归带点荒谬"
      >
        <template #footer>
          <n-button type="primary" size="large" @click="router.push('/')">
            找点乐子
          </n-button>
        </template>
      </n-result>
    </div>
  </div>
</template>

<style scoped>

.not-found-page {
  height: 80vh;
  display: flex;
  justify-content: center;
  align-items: center;
  position: relative;
  overflow: hidden;
}

.content {
  padding: 40px;
  border-radius: 2px;
  background: rgba(255, 255, 255, 0.5);
  z-index: 10;
}

.not-found-page--dark .content {
  background: rgba(20, 20, 25, 0.65);
}

.geometric-bg {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  z-index: 0;
}

.shape {
  position: absolute;
  filter: blur(80px);
  animation: float 20s infinite ease-in-out;
}

.shape-1 {
  top: 20%;
  left: 20%;
  width: 300px;
  height: 300px;
  background: rgba(189, 52, 254, 0.3);
  border-radius: 50%;
}

.shape-2 {
  bottom: 20%;
  right: 20%;
  width: 250px;
  height: 250px;
  background: rgba(65, 209, 255, 0.3);
  border-radius: 50%;
  animation-delay: -5s;
}

@keyframes float {
  0%, 100% { transform: translate(0, 0); }
  50% { transform: translate(30px, -30px); }
}

</style>
