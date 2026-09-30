<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import VditorEditor from '@/components/editor/VditorEditor.vue'
import {
  NButton,
  NEmpty,
  NForm,
  NFormItem,
  NIcon,
  NInput,
  NSelect,
  NSpin,
  NSwitch,
  NTag,
  NTreeSelect,
  useDialog,
  useMessage,
} from 'naive-ui'
import {
  ArrowBackOutline,
  ArrowUpOutline,
  CheckmarkCircleOutline,
  CloseCircleOutline,
  EllipsisHorizontalOutline,
  FolderOpenOutline,
  PricetagOutline,
  SaveOutline,
  TrashOutline,
} from '@vicons/ionicons5'
import { deletePrivateBlog, getPrivateBlog, updatePrivateBlog } from '@/api/blog'
import { getCurrentUserFolderTree } from '@/api/folder'
import { createTag, getTagPage } from '@/api/tag'
import { uploadImage } from '@/api/file'
import { useToc } from '@/composables/useToc'
import { useThemeStore } from '@/stores/theme'
import type { ApiId, BlogPrivateDetailVO, FolderTree, PublishStatus, TagBriefVO } from '@/types'
import { ROOT_FOLDER_ID } from '@/types'
import { storeToRefs } from 'pinia'

interface SelectOption {
  [key: string]: unknown
  label: string
  value: string
}

interface FolderOption {
  [key: string]: unknown
  label: string
  key: string
  children?: FolderOption[]
}

const route = useRoute()
const router = useRouter()
const message = useMessage()
const dialog = useDialog()
const themeStore = useThemeStore()
const { isDark } = storeToRefs(themeStore)
const {
  activeTocId,
  cleanupToc,
  expandedTocIds,
  extractToc,
  scrollToHeading,
  setupScrollSpy,
  tocItems,
  toggleTocExpand,
  visibleTocItems,
} = useToc()

const blogId = computed(() => String(route.params.id || ''))
const loading = ref(false)
const saving = ref(false)
const dirty = ref(false)
const autosaveEnabled = ref(false)
const autosavePending = ref(false)
const loadError = ref(false)
const tagOptions = ref<SelectOption[]>([])
const folderTree = ref<FolderTree | null>(null)
const showBackTop = ref(false)
const mobileToolbarExpanded = ref(false)
let autosaveTimer: number | undefined
let tocTimer: number | undefined
let contentInitialized = false
let scrollTarget: HTMLElement | Window | null = null
const editorRef = ref<HTMLElement | null>(null)

const AUTOSAVE_DELAY = 60_000

const form = reactive({
  title: '',
  summary: '',
  content: '',
  folderId: String(ROOT_FOLDER_ID),
  isPublished: 0 as PublishStatus,
  tagIds: [] as string[],
})

const saveStateText = computed(() => {
  if (saving.value) {
    return 'Saving'
  }
  return dirty.value ? 'Unsaved' : 'Saved'
})
const saveStateType = computed(() => (dirty.value ? 'warning' : 'success'))
const autosaveStateText = computed(() => {
  if (!autosaveEnabled.value) {
    return 'Off'
  }

  return autosavePending.value ? 'Queued' : 'On'
})
const folderOptions = computed<FolderOption[]>(() => [
  {
    label: '根目录',
    key: String(ROOT_FOLDER_ID),
    children: folderTree.value?.children?.map(toFolderOption) ?? [],
  },
])

function toFolderOption(folder: FolderTree): FolderOption {
  return {
    label: String(folder.id) === String(ROOT_FOLDER_ID) ? '根目录' : folder.name,
    key: String(folder.id),
    children: folder.children?.map(toFolderOption),
  }
}

function setFormFromBlog(blog: BlogPrivateDetailVO): void {
  contentInitialized = false
  form.title = blog.title
  form.summary = blog.summary || ''
  form.content = blog.content || ''
  form.folderId = String(blog.folderId ?? ROOT_FOLDER_ID)
  form.isPublished = blog.isPublished
  form.tagIds = blog.tags.map((tag) => String(tag.id))
  tagOptions.value = mergeTagOptions(tagOptions.value, blog.tags.map((tag) => ({ label: tag.name, value: String(tag.id) })))
  nextTick(() => {
    dirty.value = false
    contentInitialized = true
  })
}

function mergeTagOptions(current: SelectOption[], incoming: SelectOption[]): SelectOption[] {
  const map = new Map(current.map((option) => [option.value, option]))
  incoming.forEach((option) => map.set(option.value, option))
  return Array.from(map.values())
}

function createTagOption(label: string): SelectOption {
  const name = label.trim().slice(0, 20)
  return { label: name, value: name }
}

async function loadPage(): Promise<void> {
  loading.value = true
  loadError.value = false
  try {
    const [blog, tree] = await Promise.all([
      getPrivateBlog(blogId.value),
      getCurrentUserFolderTree(),
    ])
    folderTree.value = tree
    setFormFromBlog(blog)
    document.title = `${blog.title} | 编辑博客`
    await nextTick()
    scheduleTocSync()
    await handleTagSearch('')
  } catch (error) {
    loadError.value = true
    message.error('编辑内容加载失败')
  } finally {
    loading.value = false
  }
}

function clearAutosaveTimer(): void {
  window.clearTimeout(autosaveTimer)
  autosaveTimer = undefined
  autosavePending.value = false
}

function scheduleAutosave(): void {
  clearAutosaveTimer()

  if (!autosaveEnabled.value || !dirty.value || loading.value) {
    return
  }

  autosavePending.value = true
  autosaveTimer = window.setTimeout(() => {
    autosaveTimer = undefined
    autosavePending.value = false
    void runAutosave()
  }, AUTOSAVE_DELAY)
}

function getSaveSnapshot(content = form.content): string {
  return JSON.stringify({
    title: form.title.trim(),
    summary: form.summary,
    content,
    folderId: form.folderId,
    isPublished: form.isPublished,
    tagIds: [...form.tagIds],
  })
}

async function runAutosave(): Promise<void> {
  if (!autosaveEnabled.value || !dirty.value) {
    return
  }

  if (saving.value) {
    scheduleAutosave()
    return
  }

  if (!form.title.trim()) {
    return
  }

  const saved = await handleSave({ silent: true })
  if (!saved && autosaveEnabled.value && dirty.value) {
    scheduleAutosave()
  }
}

async function handleEditorUpload(file: File): Promise<string> {
  try {
    const result = await uploadImage(file)
    return typeof result === 'string' ? result : result.data
  } catch (error) {
    message.error('图片上传失败')
    throw error
  }
}

function scheduleTocSync(): void {
  window.clearTimeout(tocTimer)
  tocTimer = window.setTimeout(() => {
    if (!editorRef.value) {
      return
    }

    extractToc(editorRef.value)
    setupScrollSpy(editorRef.value)
  }, 180)
}

function resolveScrollTarget(): HTMLElement | Window {
  return document.querySelector<HTMLElement>('.main-layout .n-layout-scroll-container')
    ?? document.querySelector<HTMLElement>('.n-layout-scroll-container')
    ?? window
}

function getScrollTop(target = resolveScrollTarget()): number {
  return target instanceof Window ? window.scrollY : target.scrollTop
}

function updateBackTopVisibility(): void {
  showBackTop.value = getScrollTop() > 360
}

function updateScrollEffects(): void {
  updateBackTopVisibility()

  if (scrollTarget && !(scrollTarget instanceof Window)) {
    window.dispatchEvent(new Event('scroll'))
  }
}

function scrollToTop(): void {
  const target = scrollTarget ?? resolveScrollTarget()
  target.scrollTo({ top: 0, behavior: 'smooth' })
}

function toggleMobileToolbar(): void {
  mobileToolbarExpanded.value = !mobileToolbarExpanded.value
}

async function handleTagSearch(keyword: string): Promise<void> {
  try {
    const result = await getTagPage({
      pageNum: 1,
      pageSize: 8,
      keyword: keyword || undefined,
    })
    tagOptions.value = mergeTagOptions(
      tagOptions.value,
      result.records.map((tag) => ({ label: tag.name, value: String(tag.id) })),
    )
  } catch (error) {
    console.error('Failed to search tags', error)
  }
}

async function resolveTagIds(): Promise<ApiId[]> {
  const optionByValue = new Map(tagOptions.value.map((option) => [option.value, option]))
  const resolved: ApiId[] = []

  for (const value of form.tagIds) {
    const option = optionByValue.get(value)
    if (option) {
      resolved.push(value)
      continue
    }

    const name = value.trim()
    if (!name) {
      continue
    }

    const tag: TagBriefVO = await createTag({ name })
    tagOptions.value = mergeTagOptions(tagOptions.value, [{ label: tag.name, value: String(tag.id) }])
    resolved.push(tag.id)
  }

  return resolved
}

async function handleSave(options: { silent?: boolean } = {}): Promise<boolean> {
  if (!form.title.trim()) {
    if (!options.silent) {
      message.warning('请输入标题')
    }
    return false
  }

  if (saving.value) {
    return false
  }

  saving.value = true
  const content = form.content
  const snapshot = getSaveSnapshot(content)
  try {
    const tagIds = await resolveTagIds()
    await updatePrivateBlog(blogId.value, {
      title: form.title.trim(),
      summary: form.summary,
      content,
      folderId: form.folderId,
      isPublished: form.isPublished,
      tagIds,
    })
    dirty.value = getSaveSnapshot() !== snapshot
    if (!options.silent) {
      message.success('已保存')
    }
    if (!dirty.value) {
      clearAutosaveTimer()
    } else {
      scheduleAutosave()
    }
    return true
  } catch (error) {
    if (!options.silent) {
      message.error('保存失败')
    }
    return false
  } finally {
    saving.value = false
  }
}

function handleDelete(): void {
  dialog.warning({
    title: '删除博客',
    content: '删除后无法从前端恢复，确认继续？',
    positiveText: '删除',
    negativeText: '取消',
    onPositiveClick: async () => {
      try {
        await deletePrivateBlog(blogId.value)
        message.success('已删除')
        router.push('/workspace')
      } catch (error) {
        message.error('删除失败')
      }
    },
  })
}

watch(form, () => {
  if (contentInitialized) {
    dirty.value = true
    scheduleAutosave()
  }
}, { deep: true })

watch(() => form.content, () => {
  if (contentInitialized) {
    scheduleTocSync()
  }
})

watch(autosaveEnabled, () => {
  scheduleAutosave()
})

onMounted(() => {
  loadPage()
  nextTick(() => {
    scrollTarget = resolveScrollTarget()
    updateScrollEffects()
    scrollTarget.addEventListener('scroll', updateScrollEffects, { passive: true })
    window.addEventListener('resize', updateScrollEffects)
  })
})

onUnmounted(() => {
  clearAutosaveTimer()
  window.clearTimeout(tocTimer)
  cleanupToc()
  scrollTarget?.removeEventListener('scroll', updateScrollEffects)
  window.removeEventListener('resize', updateScrollEffects)
  scrollTarget = null
})
</script>

<template>
  <div class="private-detail-page">
    <n-spin :show="loading">
      <div v-if="loadError" class="load-error">
        <n-empty description="无法打开这篇博客" />
        <n-button @click="router.push('/workspace')">返回工作台</n-button>
      </div>

      <div v-else class="editor-shell">
        <header class="editor-header">
          <button class="text-link" type="button" @click="router.push('/workspace')">
            <n-icon :component="ArrowBackOutline" />
            Workspace
          </button>

          <div class="state-group">
            <n-tag :type="saveStateType" round>
              <template #icon>
                <n-icon :component="dirty ? CloseCircleOutline : CheckmarkCircleOutline" />
              </template>
              {{ saveStateText }}
            </n-tag>
            <div class="autosave-control">
              <span>Autosave</span>
              <n-switch v-model:value="autosaveEnabled" size="small" :disabled="saving" />
              <span class="autosave-state">{{ autosaveStateText }}</span>
            </div>
            <n-button type="primary" :loading="saving" @click="() => handleSave()">
              <template #icon><n-icon :component="SaveOutline" /></template>
              Save
            </n-button>
            <n-button secondary type="error" @click="handleDelete">
              <template #icon><n-icon :component="TrashOutline" /></template>
            </n-button>
          </div>
        </header>

        <div class="workbench">
          <aside class="metadata-column">
            <section class="meta-panel">
              <n-form label-placement="top" class="meta-form">
                <n-form-item label="Title">
                  <n-input v-model:value="form.title" size="large" :maxlength="20" show-count class="custom-input" />
                </n-form-item>
                <n-form-item label="Summary">
                  <n-input
                    v-model:value="form.summary"
                    type="textarea"
                    :maxlength="200"
                    show-count
                    :autosize="{ minRows: 2, maxRows: 4 }"
                    class="custom-input"
                  />
                </n-form-item>
                <n-form-item label="Status">
                  <div class="switch-row">
                    <span>{{ form.isPublished === 1 ? 'Public' : 'Private' }}</span>
                    <n-switch v-model:value="form.isPublished" :checked-value="1" :unchecked-value="0" />
                  </div>
                </n-form-item>
                <n-form-item label="Folder">
                  <n-tree-select
                    v-model:value="form.folderId"
                    :options="folderOptions"
                    default-expand-all
                    class="custom-select"
                  >
                    <template #arrow>
                      <n-icon :component="FolderOpenOutline" />
                    </template>
                  </n-tree-select>
                </n-form-item>
                <n-form-item label="Tags">
                  <n-select
                    v-model:value="form.tagIds"
                    :options="tagOptions"
                    multiple
                    filterable
                    tag
                    clearable
                    class="custom-select"
                    :on-create="createTagOption"
                    @search="handleTagSearch"
                  >
                    <template #arrow>
                      <n-icon :component="PricetagOutline" />
                    </template>
                  </n-select>
                </n-form-item>
              </n-form>
            </section>

            <aside class="toc-panel">
              <div class="toc-title">Contents</div>
              <div v-if="tocItems.length" class="toc-list">
                <button
                  v-for="item in visibleTocItems"
                  :key="item.id"
                  type="button"
                  class="toc-item"
                  :class="[`toc-level-${item.level}`, { 'toc-active': activeTocId === item.id }]"
                  @click="scrollToHeading(editorRef, item.id)"
                >
                  <span>{{ item.text }}</span>
                  <span
                    v-if="item.hasChildren"
                    class="toc-toggle"
                    @click="toggleTocExpand(item.id, $event)"
                  >
                    {{ expandedTocIds.has(item.id) ? '-' : '+' }}
                  </span>
                </button>
              </div>
              <n-empty v-else size="small" description="No contents" />
            </aside>
          </aside>

          <main class="editor-card">
            <div
              ref="editorRef"
              class="editor-container"
              :class="{ 'mobile-toolbar-expanded': mobileToolbarExpanded }"
            >
              <VditorEditor
                v-model="form.content"
                :is-dark="isDark"
                :upload-image="handleEditorUpload"
              >
                <template #toolbar-extra>
              <button
                class="mobile-toolbar-toggle"
                type="button"
                :aria-expanded="mobileToolbarExpanded"
                aria-label="切换更多编辑工具"
                @click="toggleMobileToolbar"
              >
                <n-icon :component="EllipsisHorizontalOutline" />
                <span>{{ mobileToolbarExpanded ? '收起' : '更多' }}</span>
              </button>
                </template>
              </VditorEditor>
            </div>

          </main>
        </div>
      </div>
    </n-spin>

    <button
      v-show="showBackTop"
      class="back-top-button"
      type="button"
      title="回到顶部"
      aria-label="回到顶部"
      @click="scrollToTop"
    >
      <n-icon :component="ArrowUpOutline" />
    </button>

  </div>
</template>

<style scoped>


.private-detail-page {
  min-height: 100vh;
  background: var(--bg-primary);
  color: var(--text-primary);
  padding: 24px 28px 72px;
}

.editor-shell {
  width: min(1440px, 100%);
  margin: 0 auto;
}

.editor-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
  margin-bottom: 18px;
}

.text-link {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  border: 0;
  background: transparent;
  color: var(--text-secondary);
  cursor: pointer;
  font-family: inherit;
  font-weight: 700;
  text-transform: none;
  letter-spacing: 0.02em;
}

.text-link:hover {
  color: var(--accent-color);
}

.state-group {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
}

.autosave-control {
  min-height: 34px;
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 0 12px;
  border: 1px solid var(--line-color);
  color: var(--text-secondary);
  font-family: inherit;
  font-size: 0.75rem;
  text-transform: uppercase;
  white-space: nowrap;
  border-radius: 24px;
  letter-spacing: 0;
  font-weight: 400;
}

.autosave-state {
  color: var(--text-tertiary);
}

.meta-panel,
.editor-card {
  border: 1px solid var(--line-color);
  background: var(--bg-primary);
}

.meta-panel {
  padding: 20px;
  background: var(--bg-secondary);
  border: 0;
  border-radius: 20px;
}

.meta-form {
  display: grid;
  grid-template-columns: 1fr;
  gap: 14px;
}

.meta-form :deep(.n-form-item-label) {
  font-family: inherit;
  color: var(--text-tertiary);
  text-transform: uppercase;
  letter-spacing: 0;
  font-size: 13px;
  font-weight: 500;
}

.custom-input,
.custom-select {
  background: transparent;
}

.switch-row {
  min-height: 34px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  color: var(--text-primary);
  font-family: inherit;
}

.workbench {
  display: grid;
  align-items: start;
  grid-template-columns: 260px minmax(0, 1fr);
  gap: 24px;
}

.metadata-column {
  position: sticky;
  top: 92px;
  max-height: calc(100vh - 120px);
  overflow: auto;
  display: flex;
  flex-direction: column;
  gap: 18px;
  font-family: inherit;
}

.editor-card {
  position: relative;
  padding: 0;
  min-width: 0;
  overflow: visible;
  border: 0;
  background: transparent;
  box-shadow: none;
}

.editor-container {
  min-height: 720px;
  position: relative;
  border: 1px solid var(--line-color);
  border-radius: 18px;
}

.mobile-toolbar-toggle {
  display: none;
  border-radius: 18px;
}

.back-top-button {
  position: fixed;
  right: 28px;
  bottom: 28px;
  z-index: 120;
  width: 46px;
  height: 46px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: 1px solid var(--line-color);
  background: var(--modal-bg);
  color: var(--text-primary);
  cursor: pointer;
  box-shadow: none;
  transition: transform 0.2s ease, border-color 0.2s ease, color 0.2s ease;
  border-radius: 50%;
}

.back-top-button:hover {
  transform: translateY(-3px);
  border-color: var(--accent-color);
  color: var(--accent-color);
}

.load-error {
  min-height: 420px;
  display: flex;
  flex-direction: column;
  gap: 18px;
  align-items: center;
  justify-content: center;
}

@media (max-width: 1200px) {
  .meta-form,
  .workbench {
    grid-template-columns: 1fr;
  }

  .metadata-column {
    position: static;
    max-height: none;
  }
}

@media (max-width: 720px) {
  .editor-header,
  .state-group {
    align-items: stretch;
    flex-direction: column;
  }

  .autosave-control {
    justify-content: space-between;
  }

  .meta-panel {
    padding: 16px;
  }
}

.meta-panel,
.toc-panel {
  border-color: var(--line-color);
  border-radius: 2px;
  background: var(--bg-primary);
  box-shadow: none;
}

.toc-panel {
  padding: 18px;
  background: var(--bg-secondary);
  border: 0;
  border-radius: 20px;
}

.toc-title {
  padding-bottom: 14px;
  margin-bottom: 14px;
  border-bottom: 1px solid var(--line-color);
  color: var(--text-primary);
  font-size: 15px;
  font-weight: 600;
}

.toc-list {
  max-height: 320px;
  overflow: auto;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.toc-item {
  width: 100%;
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 8px;
  padding: 4px 0;
  border: 0;
  background: transparent;
  color: var(--text-secondary);
  cursor: pointer;
  line-height: 1.5;
  text-align: left;
  transition: color 0.18s ease, transform 0.18s ease;
}

.toc-item:hover,
.toc-item.toc-active {
  color: var(--accent-color);
  transform: translateX(4px);
}

.toc-item.toc-active {
  font-weight: 700;
}

.toc-toggle {
  flex: 0 0 auto;
  color: var(--accent-color);
}

.toc-level-2 { padding-left: 14px; }
.toc-level-3 { padding-left: 28px; }
.toc-level-4 { padding-left: 42px; }
.toc-level-5 { padding-left: 56px; }
.toc-level-6 { padding-left: 70px; }

.editor-card :deep(.vditor) {
  overflow: visible !important;
}

.editor-card :deep(.vditor-toolbar) {
  z-index: 20 !important;
  box-sizing: border-box;
  border-left: 0;
  box-shadow: none;
}

.editor-card :deep(.vditor-content),
.editor-card :deep(.vditor-wysiwyg),
.editor-card :deep(.vditor-ir),
.editor-card :deep(.vditor-sv) {
  min-height: 720px;
  overflow: visible;
}

@media (max-width: 720px) {
  .private-detail-page {
    padding-inline: 12px;
  }

  .toc-panel {
    padding: 16px;
  }

  .editor-card :deep(.vditor-toolbar) {
    display: flex;
    flex-wrap: wrap;
    align-content: flex-start;
    overflow: visible;
    padding-right: 64px;
  }

  .editor-card :deep(.vditor-toolbar > *) {
    float: none;
  }

  .editor-card :deep(.vditor-toolbar__item) {
    padding: 0 3px;
  }

  .editor-card :deep(.vditor-toolbar > :nth-child(n + 9):not(.mobile-toolbar-toggle)) {
    display: none;
  }

  .mobile-toolbar-expanded :deep(.vditor-toolbar) {
    max-height: none;
    overflow: visible;
  }

  .mobile-toolbar-expanded :deep(.vditor-toolbar > :nth-child(n + 9):not(.mobile-toolbar-toggle)) {
    display: block;
  }

  .mobile-toolbar-toggle {
    position: absolute;
    right: 8px;
    top: 4px;
    z-index: 21;
    display: inline-flex;
    align-items: center;
    gap: 3px;
    min-height: 30px;
    padding: 0 8px;
    border: 1px solid var(--line-color);
    border-radius: 2px;
    background: var(--bg-secondary);
    color: var(--text-secondary);
    cursor: pointer;
    font: inherit;
    font-size: 0.72rem;
    white-space: nowrap;
  }

  .mobile-toolbar-toggle:active {
    transform: translateY(1px);
  }
}
/* Fullscreen owns its scroll area; page-level overflow rules must not override it. */
.editor-card :deep(.vditor--fullscreen) { overflow: auto !important; }
.editor-card :deep(.vditor--fullscreen .vditor-content) { min-height: 0; overflow: auto; }
@media (min-width: 721px) and (max-width: 1200px) { .meta-form { grid-template-columns: 1fr 1fr; } }

</style>
