<script setup lang="ts">
import { onMounted, onUnmounted, ref, watch } from 'vue'
import Vditor from 'vditor'
import { labelCodeLanguage } from './codeLanguage'
import { observeImageCaptions } from './imageCaptions'
import { installSymbolPairs } from './symbolPairs'
import { observeDiagrams } from './diagrams'
import { pinToolbar } from './toolbarPosition'
import 'vditor/dist/index.css'

const props = defineProps<{
  modelValue: string
  isDark: boolean
  uploadImage: (file: File) => Promise<string>
}>()

const emit = defineEmits<{
  'update:modelValue': [value: string]
}>()

const containerRef = ref<HTMLDivElement | null>(null)
let vditor: Vditor | null = null
let suppressInput = false
let ready = false
let diagrams: ReturnType<typeof observeDiagrams> | undefined
let disposeToolbar: (() => void) | undefined
let disposeCaptions: (() => void) | undefined
let disposePairs: (() => void) | undefined
const toolbarTarget = ref<HTMLElement | null>(null)

const toolbar = [
  'emoji', 'headings', 'bold', 'italic', 'strike', '|',
  'line', 'quote', 'list', 'ordered-list', 'check', 'outdent', 'indent', '|',
  'code', 'inline-code', 'link', 'table', 'upload', '|',
  'undo', 'redo', '|',
  'edit-mode', 'preview', 'fullscreen', 'info',
]

function applyTheme(): void {
  if (!vditor || !ready) return
  vditor.setTheme(
    props.isDark ? 'dark' : 'classic',
    'classic',
    props.isDark ? 'github-dark' : 'github',
  )
  diagrams?.refresh()
}

onMounted(() => {
  if (!containerRef.value) return

  diagrams = observeDiagrams(containerRef.value, () => props.isDark)
  disposeCaptions = observeImageCaptions(containerRef.value)
  disposePairs = installSymbolPairs(containerRef.value)
  vditor = new Vditor(containerRef.value, {
    mode: 'wysiwyg',
    theme: props.isDark ? 'dark' : 'classic',
    tab: '    ',
    placeholder: '开始写作…',
    height: 'auto',
    toolbar,
    toolbarConfig: { pin: false },
    fullscreen: { index: 1100 },
    counter: { enable: true, type: 'markdown' },
    cache: { enable: false },
    preview: {
      markdown: { imageCaption: true },
      hljs: { renderMenu: labelCodeLanguage, lineNumber: true, style: props.isDark ? 'github-dark' : 'github' },
    },
    upload: {
      handler: (files: File[]) => {
        void (async () => {
          for (const file of files) {
            const url = await props.uploadImage(file)
            vditor?.insertValue(`\n![${file.name}](${url})\n`)
          }
        })()
        return null
      },
    },
    input: (value: string) => {
      if (suppressInput) return
      emit('update:modelValue', value)
    },
    after: () => {
      if (!vditor || !containerRef.value) return
      ready = true
      toolbarTarget.value = containerRef.value.querySelector<HTMLElement>('.vditor-toolbar')
      if (toolbarTarget.value) disposeToolbar = pinToolbar(containerRef.value, toolbarTarget.value)
      if (props.modelValue) {
        suppressInput = true
        vditor.setValue(props.modelValue)
        suppressInput = false
      }
      applyTheme()
    },
  })
})

watch(() => props.modelValue, (value) => {
  if (!vditor || !ready) return
  if (vditor.getValue() === value) return
  suppressInput = true
  vditor.setValue(value)
  suppressInput = false
})

watch(() => props.isDark, () => {
  applyTheme()
})

onUnmounted(() => {
  ready = false
  disposeToolbar?.()
  disposeCaptions?.()
  disposePairs?.()
  diagrams?.dispose()
  toolbarTarget.value = null
  vditor?.destroy()
  vditor = null
})
</script>

<template>
  <div ref="containerRef" class="vditor-editor-host"></div>
  <Teleport v-if="toolbarTarget" :to="toolbarTarget"><slot name="toolbar-extra" /></Teleport>
</template>

<style scoped>
.vditor-editor-host :deep(.vditor-toolbar) { position: relative; }
.vditor-editor-host.vditor--fullscreen { overflow: auto !important; }
.vditor-editor-host.vditor--fullscreen :deep(.vditor-toolbar) { position: sticky; top: 0; flex-shrink: 0; }
.vditor-editor-host.vditor--fullscreen :deep(.vditor-content) { min-height: 0; overflow: auto; }
:global(.page-container:has(.vditor--fullscreen)) { z-index: 1001; }
/* Hide block actions, but keep native code-language and other editable controls. */
.vditor-editor-host :deep(.vditor-panel--none > button:is([data-type="up"], [data-type="down"], [data-type="remove"])) { display: none !important; }
.vditor-editor-host :deep(.vditor-panel--none:has(> button[data-type="up"]):has(> button[data-type="down"]):has(> button[data-type="remove"]):not(:has(input, select, textarea))) { display: none !important; }
</style>
