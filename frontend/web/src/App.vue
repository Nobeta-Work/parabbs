<script setup lang="ts">
import { NConfigProvider, NGlobalStyle, NMessageProvider, NDialogProvider, NNotificationProvider, NLoadingBarProvider } from 'naive-ui'
import { useThemeStore } from '@/stores/theme'
import { storeToRefs } from 'pinia'
import { computed } from 'vue'
import type { GlobalThemeOverrides } from 'naive-ui'

const themeStore = useThemeStore()
const { theme, isDark } = storeToRefs(themeStore)
const themeOverrides = computed<GlobalThemeOverrides>(() => ({
  common: {
    fontFamily: 'var(--font-sans)',
    primaryColor: isDark.value ? '#ff9ca8' : '#ce3c4c',
    primaryColorHover: isDark.value ? '#ffbdc5' : '#b72d3d',
    primaryColorPressed: isDark.value ? '#ffbdc5' : '#b72d3d',
    primaryColorSuppl: isDark.value ? '#ff9ca8' : '#ce3c4c',
    bodyColor: isDark.value ? '#19191d' : '#ffffff',
    cardColor: isDark.value ? '#242429' : '#ffffff',
    modalColor: isDark.value ? '#242429' : '#ffffff',
    popoverColor: isDark.value ? '#242429' : '#ffffff',
    textColorBase: isDark.value ? '#efedf1' : '#1f1f1f',
    borderColor: isDark.value ? '#3c3942' : '#e9e8ec',
    borderRadius: '12px',
  },
}))
</script>

<template>
  <n-config-provider :theme="theme" :theme-overrides="themeOverrides">
    <n-global-style />
    <n-loading-bar-provider>
      <n-message-provider>
        <n-dialog-provider>
          <n-notification-provider>
            <router-view />
          </n-notification-provider>
        </n-dialog-provider>
      </n-message-provider>
    </n-loading-bar-provider>
  </n-config-provider>
</template>
