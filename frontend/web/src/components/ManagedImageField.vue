<script setup lang="ts">
import { onBeforeUnmount, ref } from 'vue'
import { NButton, NIcon, useMessage } from 'naive-ui'
import { ImageOutline, TrashOutline } from '@vicons/ionicons5'
import { uploadManagedImage, type ImagePurpose } from '@/api/file'
import ContentImage from './ContentImage.vue'

const props = defineProps<{ purpose: ImagePurpose; disabled?: boolean }>()
const value = defineModel<string | null>({ default: null })
const emit = defineEmits<{ uploading: [value: boolean] }>()
const message = useMessage()
const input = ref<HTMLInputElement | null>(null)
const uploading = ref(false)
let active = true
onBeforeUnmount(() => {
  active = false
  if (uploading.value) emit('uploading', false)
})

async function selectFile(event: Event) {
  const element = event.target as HTMLInputElement
  const file = element.files?.[0]
  element.value = ''
  if (!file || uploading.value || props.disabled) return
  if (!['image/jpeg', 'image/png', 'image/gif', 'image/webp'].includes(file.type)) {
    message.warning('请选择 JPEG、PNG、GIF 或 WebP 图片')
    return
  }
  if (file.size > 15 * 1024 * 1024) {
    message.warning('图片不能超过 15 MiB')
    return
  }
  uploading.value = true
  emit('uploading', true)
  try {
    const result = await uploadManagedImage(file, props.purpose)
    if (active) value.value = result.url
  } catch {
    if (active) message.error('图片上传失败，请重试')
  } finally {
    uploading.value = false
    if (active) emit('uploading', false)
  }
}
</script>

<template>
  <div class="image-field" :aria-busy="uploading">
    <ContentImage :src="value" :alt="purpose === 'COVER' ? '封面预览' : '背景预览'" eager class="image-preview" />
    <input ref="input" type="file" accept="image/jpeg,image/png,image/gif,image/webp" hidden :disabled="disabled || uploading" @change="selectFile">
    <div class="image-actions">
      <n-button :loading="uploading" :disabled="disabled || uploading" size="small" @click="input?.click()">
        <template #icon><n-icon :component="ImageOutline" /></template>
        {{ value ? '替换图片' : '上传图片' }}
      </n-button>
      <n-button v-if="value" :disabled="disabled || uploading" size="small" quaternary @click="value = null">
        <template #icon><n-icon :component="TrashOutline" /></template>
        移除
      </n-button>
    </div>
    <small>JPEG、PNG、GIF、WebP · 最大 15 MiB</small>
  </div>
</template>

<style scoped>
.image-field { width: 100%; min-width: 0; }
.image-preview { border-radius: 12px; margin-bottom: 12px; max-height: 240px; }
.image-actions { display: flex; align-items: center; flex-wrap: wrap; gap: 8px; }
small { display: block; margin-top: 8px; color: var(--text-secondary); font-size: 12px; line-height: 1.5; }
</style>
