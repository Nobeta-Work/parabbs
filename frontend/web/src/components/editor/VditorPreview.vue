<script setup lang="ts">
import { nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import Vditor from 'vditor'
import { labelCodeLanguage } from './codeLanguage'
import { observeImageCaptions } from './imageCaptions'
import { observeDiagrams } from './diagrams'
import 'vditor/dist/index.css'

const props = defineProps<{
  content: string
  isDark: boolean
}>()

const emit = defineEmits<{
  ready: []
}>()

const containerRef = ref<HTMLDivElement | null>(null)

let diagrams: ReturnType<typeof observeDiagrams> | undefined
let disposeCaptions: (() => void) | undefined

async function render(): Promise<void> {
  if (!containerRef.value) return
  await Vditor.preview(
    containerRef.value,
    props.content,
    {
      mode: props.isDark ? 'dark' : 'light',
      markdown: { imageCaption: true },
      hljs: { renderMenu: labelCodeLanguage, lineNumber: true, style: props.isDark ? 'github-dark' : 'github' },
    },
  )
  emit('ready')
}

watch(() => props.content, () => {
  void nextTick(render)
})

watch(() => props.isDark, () => {
  void nextTick(render)
})

onMounted(() => {
  if (containerRef.value) {
    diagrams = observeDiagrams(containerRef.value, () => props.isDark)
    disposeCaptions = observeImageCaptions(containerRef.value)
  }
  void render()
})
onUnmounted(() => { diagrams?.dispose(); disposeCaptions?.() })
</script>

<template>
  <div ref="containerRef" class="vditor-preview-host vditor-reset"></div>
</template>
