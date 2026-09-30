import { defineStore } from 'pinia'
import { ref } from 'vue'
import { darkTheme, type GlobalTheme } from 'naive-ui'

export const useThemeStore = defineStore('theme', () => {
  const savedTheme = localStorage.getItem('theme')
  const isDark = ref(savedTheme === 'dark')
  const theme = ref<GlobalTheme | null>(isDark.value ? darkTheme : null)

  function applyTheme(nextIsDark: boolean) {
    document.documentElement.classList.toggle('dark', nextIsDark)
    document.documentElement.dataset.theme = nextIsDark ? 'dark' : 'light'
    document.documentElement.style.colorScheme = nextIsDark ? 'dark' : 'light'
    document
      .querySelector('meta[name="theme-color"]')
      ?.setAttribute('content', nextIsDark ? '#19191d' : '#ffffff')
    localStorage.setItem('theme', nextIsDark ? 'dark' : 'light')
  }

  applyTheme(isDark.value)

  function toggleTheme() {
    isDark.value = !isDark.value
    theme.value = isDark.value ? darkTheme : null
    applyTheme(isDark.value)
  }

  return { isDark, theme, toggleTheme }
})
