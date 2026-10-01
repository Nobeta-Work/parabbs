<script setup lang="ts">
import { ref, watch } from 'vue'

const props = defineProps<{ src?: string | null; alt: string; eager?: boolean }>()
const emit = defineEmits<{ load: []; error: [] }>()
const failed = ref(false)
watch(() => props.src, () => { failed.value = false })
function onError() {
  failed.value = true
  emit('error')
}
</script>

<template>
  <img
    v-if="src && !failed"
    :key="src"
    :src="src"
    :alt="alt"
    :loading="eager ? 'eager' : 'lazy'"
    decoding="async"
    class="content-image"
    @load="emit('load')"
    @error="onError"
  >
</template>

<style scoped>
.content-image {
  display: block;
  width: 100%;
  max-width: 100%;
  height: auto;
  object-fit: contain;
  background: var(--bg-secondary);
}
</style>
