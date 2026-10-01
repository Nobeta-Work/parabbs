<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'

const props = defineProps<{ text: string }>()
const visible = ref('')
const reduced = ref(false)
type GraphemeSegmenter = new (locale: undefined, options: { granularity: 'grapheme' }) => { segment(text: string): Iterable<{ segment: string }> }
const Segmenter = (Intl as typeof Intl & { Segmenter?: GraphemeSegmenter }).Segmenter
const characters = computed(() => Segmenter ? Array.from(new Segmenter(undefined, { granularity: 'grapheme' }).segment(props.text), part => part.segment) : Array.from(props.text))
let index = 0
let timer: ReturnType<typeof setTimeout> | undefined
let media: MediaQueryList | undefined
function stop() { clearTimeout(timer); timer = undefined }
function step() {
  stop()
  if (reduced.value || document.hidden || !props.text) return
  if (index < characters.value.length) {
    visible.value += characters.value[index++]!
    timer = setTimeout(step, 110)
  } else {
    timer = setTimeout(() => { index = 0; visible.value = ''; timer = setTimeout(step, 300) }, 2800)
  }
}
function restart() {
  stop(); index = 0; visible.value = reduced.value ? props.text : ''
  if (!reduced.value) step()
}
function motionChanged() { reduced.value = media?.matches ?? false; restart() }
function visibilityChanged() { if (document.hidden) stop(); else step() }
watch(() => props.text, restart)
onMounted(() => {
  media = matchMedia('(prefers-reduced-motion: reduce)')
  motionChanged()
  media.addEventListener('change', motionChanged)
  document.addEventListener('visibilitychange', visibilityChanged)
})
onUnmounted(() => { stop(); media?.removeEventListener('change', motionChanged); document.removeEventListener('visibilitychange', visibilityChanged) })
</script>

<template>
  <div class="profile-intro">
    <p class="intro-line">
      <span class="screen-reader-text">{{ text }}</span>
      <span class="intro-reserve" aria-hidden="true">{{ text }}</span>
      <span class="intro-typed" aria-hidden="true">{{ visible }}<span v-if="!reduced" class="typing-caret"></span></span>
    </p>
  </div>
</template>

<style scoped>
.profile-intro { display: flex; align-items: flex-start; gap: 12px; width: fit-content; max-width: 48ch; }
.intro-line { display: grid; flex: 1; min-width: 0; margin: 0; font-size: clamp(18px, 2vw, 23px); line-height: 1.65; color: var(--text-secondary); overflow-wrap: anywhere; }
.intro-reserve, .intro-typed { grid-area: 1 / 1; }
.intro-reserve { visibility: hidden; }
.typing-caret { display: inline-block; width: 2px; height: 1em; margin-left: 4px; vertical-align: -.12em; background: var(--accent-color); animation: caret-blink 1s step-end infinite; }
.screen-reader-text { position: absolute; width: 1px; height: 1px; padding: 0; overflow: hidden; clip-path: inset(50%); white-space: nowrap; }
@keyframes caret-blink { 50% { opacity: 0; } }
@media(prefers-reduced-motion:reduce) { .typing-caret { animation: none; } }
</style>
