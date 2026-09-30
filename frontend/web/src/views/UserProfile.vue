<script setup lang="ts">
import { ref, watch, computed, onUnmounted, nextTick, h } from 'vue'
import { useRoute } from 'vue-router'
import {
  NUpload, NIcon, useMessage, NSpin, NModal, NForm, NFormItem, NInput, NRadioGroup, NRadio, NAvatar
} from 'naive-ui'
import {
  PencilOutline, CloseOutline, CameraOutline, Person
} from '@vicons/ionicons5'
import { getUserInfo, updateCurrentUserAvatar, updateCurrentUserProfile } from '@/api/user'
import { getPublicBlogPage } from '@/api/blog'
import { resolveAvatarUrl } from '@/utils/avatar'
import { useUserStore } from '@/stores/user'
import type { BlogPublicBriefVO, UserInfoVO, UserSex } from '@/types'
import ArticleCard from '@/components/ArticleCard.vue'
import ProfileIntro from '@/components/ProfileIntro.vue'

const route = useRoute()
const userStore = useUserStore()
const message = useMessage()

const uid = computed(() => String(route.params.uid || ''))
const isCurrentUser = computed(() => String(userStore.userInfo?.id ?? '') === uid.value)

const user = ref<UserInfoVO | null>(null)
const userAvatarUrl = computed(() => resolveAvatarUrl(user.value?.avatar))
const blogList = ref<BlogPublicBriefVO[]>([])
const blogTotal = ref(0)
const blogPage = ref(1)
const hasMoreBlogs = ref(true)
const loadingMoreBlogs = ref(false)
const journeySentinel = ref<HTMLDivElement | null>(null)
let journeyObserver: IntersectionObserver | null = null

const renderDefaultAvatar = () => h(NIcon, null, { default: () => h(Person) })
const loading = ref(false)
const pageLoaded = ref(false)
let portraitFrame = 0
function movePortrait(event: PointerEvent) {
  if (event.pointerType !== 'mouse' || matchMedia('(prefers-reduced-motion: reduce)').matches) return
  const header = event.currentTarget as HTMLElement
  const { clientX, clientY } = event
  cancelAnimationFrame(portraitFrame)
  portraitFrame = requestAnimationFrame(() => {
    const rect = header.getBoundingClientRect()
    const x = (clientX - rect.left) / rect.width, y = (clientY - rect.top) / rect.height
    header.style.setProperty('--portrait-x', `${(x - .5) * 8}px`)
    header.style.setProperty('--portrait-y', `${(y - .5) * 8}px`)
    header.style.setProperty('--light-x', `${x * 100}%`)
    header.style.setProperty('--light-y', `${y * 100}%`)
  })
}
function resetPortrait(event: PointerEvent) {
  cancelAnimationFrame(portraitFrame)
  const header = event.currentTarget as HTMLElement
  header.style.setProperty('--portrait-x', '0px')
  header.style.setProperty('--portrait-y', '0px')
}

// Edit Profile Logic
const showEditModal = ref(false)
const editForm = ref({
  nickname: '', sex: 2 as UserSex, race: ''
})
const saving = ref(false)

// Avatar Crop Logic (Native Implementation)
const showCropModal = ref(false)
const tempAvatarUrl = ref('')
const croppingAvatar = ref(false)
const nativeCropImgRef = ref<HTMLImageElement | null>(null)
const cropContainerRef = ref<HTMLDivElement | null>(null)

// Native Crop State
const scale = ref(1)
const offset = ref({ x: 0, y: 0 })
const isDragging = ref(false)
const activePointerId = ref<number | null>(null)
const lastPointerPos = ref({ x: 0, y: 0 })

const handleAvatarChange = (options: any) => {
  const file = options.file?.file
  if (file) {
    const reader = new FileReader()
    reader.onload = (e) => {
      tempAvatarUrl.value = e.target?.result as string
      // Reset crop state
      scale.value = 1
      offset.value = { x: 0, y: 0 }
      showCropModal.value = true
    }
    reader.readAsDataURL(file)
  }
}

const handleCropPointerDown = (e: PointerEvent) => {
  if (activePointerId.value !== null) return

  activePointerId.value = e.pointerId
  isDragging.value = true
  lastPointerPos.value = { x: e.clientX, y: e.clientY }
  cropContainerRef.value?.setPointerCapture(e.pointerId)
  e.preventDefault()
}

const handleCropPointerMove = (e: PointerEvent) => {
  if (!isDragging.value || activePointerId.value !== e.pointerId) return

  const dx = e.clientX - lastPointerPos.value.x
  const dy = e.clientY - lastPointerPos.value.y
  offset.value.x += dx
  offset.value.y += dy
  lastPointerPos.value = { x: e.clientX, y: e.clientY }
  e.preventDefault()
}

const handleCropPointerUp = (e: PointerEvent) => {
  if (activePointerId.value !== e.pointerId) return

  if (cropContainerRef.value?.hasPointerCapture(e.pointerId)) {
    cropContainerRef.value.releasePointerCapture(e.pointerId)
  }
  activePointerId.value = null
  isDragging.value = false
}

const handleCropWheel = (e: WheelEvent) => {
  const zoomSpeed = 0.001
  const delta = -e.deltaY
  const newScale = Math.max(0.1, Math.min(5, scale.value + delta * zoomSpeed))
  scale.value = newScale
  e.preventDefault()
}

const confirmCrop = async () => {
  if (!nativeCropImgRef.value || !cropContainerRef.value) return
  
  croppingAvatar.value = true
  try {
    const canvas = document.createElement('canvas')
    canvas.width = 400
    canvas.height = 400
    const ctx = canvas.getContext('2d')
    if (!ctx) return

    const img = nativeCropImgRef.value
    const container = cropContainerRef.value
    
    // 背景填黑
    ctx.fillStyle = '#000'
    ctx.fillRect(0, 0, 400, 400)
    
    // 计算比例：Canvas(400) 与 容器物理尺寸 的比例
    const containerSize = container.offsetWidth
    const drawScale = 400 / containerSize
    
    // 计算图片在容器中的实际渲染尺寸
    const renderWidth = img.offsetWidth * scale.value
    const renderHeight = img.offsetHeight * scale.value
    
    // 映射到 Canvas 上的尺寸
    const canvasDrawWidth = renderWidth * drawScale
    const canvasDrawHeight = renderHeight * drawScale
    
    // 映射到 Canvas 上的偏移（以中心为原点）
    const canvasOffsetX = offset.value.x * drawScale
    const canvasOffsetY = offset.value.y * drawScale
    
    // 绘制图片：Canvas 中心点为 (200, 200)
    ctx.drawImage(
      img,
      200 + canvasOffsetX - canvasDrawWidth / 2,
      200 + canvasOffsetY - canvasDrawHeight / 2,
      canvasDrawWidth,
      canvasDrawHeight
    )
    
    const blob = await new Promise<Blob | null>(resolve => canvas.toBlob(resolve, 'image/png'))
    
    if (blob) {
      const file = new File([blob], 'avatar.png', { type: 'image/png' })
      await updateCurrentUserAvatar(file)
      message.success('头像更新成功')
      showCropModal.value = false
      fetchProfile()
    }
  } catch (error) {
    message.error('上传失败')
  } finally {
    croppingAvatar.value = false
  }
}

const openEditModal = () => {
  if (!user.value) return
  editForm.value = {
    nickname: user.value.nickname,
    sex: user.value.sex,
    race: user.value.race
  }
  showEditModal.value = true
}

const handleSaveProfile = async () => {
  if (!editForm.value.nickname) {
    message.warning('Nickname is required')
    return
  }
  saving.value = true
  try {
    const { nickname, sex, race } = editForm.value

    await updateCurrentUserProfile({ nickname, sex, race })
    message.success('Profile Updated')
    showEditModal.value = false
    fetchProfile()

    // Update store if current user
    if (userStore.userInfo && String(userStore.userInfo.id ?? '') === uid.value) {
       userStore.userInfo = { ...userStore.userInfo, nickname, sex, race }
    }
  } catch (error) {
    message.error('Failed to update profile')
  } finally {
    saving.value = false
  }
}

const fetchBlogs = async (reset = false): Promise<void> => {
  if (loadingMoreBlogs.value) return

  loadingMoreBlogs.value = true
  const nextPage = reset ? 1 : blogPage.value + 1
  try {
    const blogs = await getPublicBlogPage({
      pageNum: nextPage,
      pageSize: 6,
      authorId: uid.value,
      sortField: 'createTime',
      sortOrder: 'desc'
    })

    blogPage.value = nextPage
    blogTotal.value = blogs.total
    blogList.value = reset ? blogs.records : [...blogList.value, ...blogs.records]
    hasMoreBlogs.value = nextPage < blogs.pages && blogs.records.length > 0
  } catch (error) {
    message.error('博客加载失败')
  } finally {
    loadingMoreBlogs.value = false
  }
}

const fetchProfile = async () => {
  loading.value = true
  try {
    const profile = await getUserInfo(uid.value)
    user.value = profile
    document.title = `${profile.nickname} - Para BBS`
    await fetchBlogs(true)
    await nextTick()
    journeyObserver?.disconnect()
    if (journeySentinel.value) {
      journeyObserver = new IntersectionObserver((entries) => {
        if (entries[0]?.isIntersecting && hasMoreBlogs.value) {
          void fetchBlogs()
        }
      }, { rootMargin: '360px 0px' })
      journeyObserver.observe(journeySentinel.value)
    }
  } catch (error) {
    message.error('无法加载用户信息')
  } finally {
    loading.value = false
    window.setTimeout(() => { pageLoaded.value = true }, 100)
  }
}


watch(uid, () => { void fetchProfile() }, { immediate: true })

onUnmounted(() => {
  cancelAnimationFrame(portraitFrame)
  journeyObserver?.disconnect()
  journeyObserver = null
})

</script>

<template>
  <div class="profile-page" :class="{ 'loaded': pageLoaded }">
    <n-spin :show="loading">
      <div class="container" v-if="user">

        <div class="profile-masthead"><span>PARA BBS</span><span>{{ blogTotal }} 篇文章</span></div>
        <header class="profile-header" @pointermove="movePortrait" @pointerleave="resetPortrait">
          <div class="cover-orbit" aria-hidden="true"></div>
          <div class="profile-avatar">
<n-upload v-if="isCurrentUser" :show-file-list="false" :custom-request="handleAvatarChange" accept="image/*">
<button class="avatar-button" aria-label="更换头像">
<n-avatar :size="220" :src="userAvatarUrl" :render-icon="renderDefaultAvatar" />
<span class="avatar-edit-badge">
<n-icon :component="CameraOutline" />
</span>
</button>
</n-upload>
<n-avatar v-else :size="220" :src="userAvatarUrl" :render-icon="renderDefaultAvatar" />
</div>
          <div class="profile-info">
<h1><span class="greeting">你好，我是</span><span class="profile-name">{{ user.nickname }}</span></h1>
<p class="uid">UID · {{ user.id }}</p>
<ProfileIntro v-if="user.signature" :text="user.signature" class="signature" />

</div>
          <button v-if="isCurrentUser" class="edit-btn" @click="openEditModal">
<n-icon :component="PencilOutline" />编辑资料</button>
        </header>
        <section class="publications">
<h2><span>文章</span><small>{{ String(blogTotal).padStart(2, '0') }}</small></h2>
<div v-if="blogList.length" class="publication-grid">
<div v-for="(blog, index) in blogList" :key="blog.id" class="publication-entry"><span class="article-number" aria-hidden="true">{{ String(index + 1).padStart(2, '0') }}</span><ArticleCard :blog="blog" /></div>
<div v-if="hasMoreBlogs" ref="journeySentinel" class="journey-sentinel">
<n-spin v-if="loadingMoreBlogs" size="small" />
<span v-else>继续向下加载</span>
</div>
<p v-else class="journey-note">已显示全部文章</p>
</div>
<p v-else class="empty-state">尚未发布文章</p>
</section>

      </div>
    </n-spin>

    <!-- Edit Modal -->
    <n-modal v-model:show="showEditModal" :mask-closable="true">
      <div class="edit-modal-content">
        <div class="modal-header">
          <h3>编辑资料</h3>
          <button class="close-btn" aria-label="关闭" @click="showEditModal = false">
            <n-icon size="24">
<CloseOutline />
</n-icon>
          </button>
        </div>
        
        <n-form :model="editForm" label-placement="top" class="edit-form">
          <n-form-item label="昵称">
            <n-input v-model:value="editForm.nickname" placeholder="输入昵称" />
          </n-form-item>
          
          <n-form-item label="性别">
            <n-radio-group v-model:value="editForm.sex" name="sex">
              <n-radio :value="1">男</n-radio>
              <n-radio :value="2">女</n-radio>
              <n-radio :value="0">保密</n-radio>
            </n-radio-group>
          </n-form-item>
          
          <div class="modal-actions">
            <button class="save-btn" @click="handleSaveProfile" :disabled="saving">
              {{ saving ? '保存中…' : '保存修改' }}
            </button>
          </div>
        </n-form>
      </div>
    </n-modal>

    <!-- Avatar Crop Modal -->
    <n-modal v-model:show="showCropModal" :mask-closable="false">
      <div class="crop-modal-content">
      <div class="modal-header">
        <h3>裁剪头像</h3>
        <button class="close-btn" @click="showCropModal = false">
          <n-icon size="24">
<CloseOutline />
</n-icon>
        </button>
      </div>
      <div class="modal-body">
        <div 
          class="native-cropper-wrapper" 
          ref="cropContainerRef"
          @pointerdown="handleCropPointerDown"
          @pointermove="handleCropPointerMove"
          @pointerup="handleCropPointerUp"
          @pointercancel="handleCropPointerUp"
          @wheel="handleCropWheel"
        >
          <img 
            ref="nativeCropImgRef"
            :src="tempAvatarUrl" 
            class="native-crop-image"
            :style="{
              transform: `translate(${offset.x}px, ${offset.y}px) scale(${scale})`
            }"
          />
          <div class="crop-overlay">
            <div class="crop-viewport">
</div>
          </div>
        </div>
      </div>
      <div class="modal-actions">
        <button class="save-btn" @click="confirmCrop" :disabled="croppingAvatar">
          {{ croppingAvatar ? '上传中…' : '确认上传' }}
        </button>
      </div>
    </div>
    </n-modal>
  </div>
</template>

<style scoped>
.profile-page {
  padding: 24px 48px 64px;
}

.container {
  margin: auto;
}

.profile-header {
  align-items: center;
}

.profile-avatar {
  flex-shrink: 0;
}

.profile-info {
  flex: 1;
}

h1 {
  margin: 0 0 4px;
  overflow-wrap: anywhere;
}

.uid {
  overflow-wrap: anywhere;
}

.avatar-button {
  position: relative;
  background: transparent;
  border: 0;
  cursor: pointer;
}

.avatar-button>.avatar-edit-badge {
  position: absolute;
  right: 0;
  bottom: 0;
  background: var(--accent-color);
  color: var(--on-accent);
  border-radius: 50%;
  width: 28px;
  height: 28px;
  display: grid;
  place-items: center;
}

.edit-btn {
  display: flex;
  align-items: center;
  gap: 8px;
  border: 1px solid var(--line-color);
  color: var(--text-secondary);
  border-radius: 24px;
  cursor: pointer;
  white-space: nowrap;
}

.publications h2 {
  color: var(--text-primary);
  padding: 0;
  margin: 0 0 14px;
}

.publications :deep(.article-card) {
  margin-bottom: 14px;
}

.journey-sentinel, .journey-note, .empty-state {
  padding: 36px;
  text-align: center;
  color: var(--text-tertiary);
  font-size: 13px;
}

.edit-modal-content {
  border-radius: 24px;
  background: var(--modal-bg);
  padding: 40px;
  width: 90%;
  max-width: 500px;
  border: 1px solid var(--line-color);
  box-shadow: none;
}

.crop-modal-content {
  background: var(--modal-bg);
  padding: 24px;
  width: 90vw;
  max-width: 600px;
  height: 70vh;
  /* 降低高度占比，确保在小屏幕也安全 */
  max-height: 600px;
  display: flex;
  flex-direction: column;
  border: 1px solid var(--line-color);
  box-shadow: none;
  border-radius: 24px;
  box-sizing: border-box;
  overflow: hidden;
}

.modal-header {
  flex-shrink: 0;
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-bottom: 16px;
  border-bottom: 1px solid var(--line-color);
}

.modal-body {
  flex: 1;
  min-height: 0;
  position: relative;
  margin: 16px 0;
  background: #111;
  border-radius: 4px;
  overflow: hidden;
  display: flex;
  align-items: center;
  justify-content: center;
}

.native-cropper-wrapper {
  width: 100%;
  max-width: 360px;
  /* 限制最大宽度 */
  max-height: 100%;
  aspect-ratio: 1 / 1;
  /* 严格限制 1:1 */
  position: relative;
  cursor: move;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  box-shadow: 0 0 20px rgba(0,0,0,0.5);
  border: 1px solid rgba(255,255,255,0.1);
  touch-action: none;
  -webkit-user-select: none;
  user-select: none;
}

.native-crop-image {
  max-width: none;
  max-height: none;
  user-select: none;
  pointer-events: none;
  transition: transform 0.05s linear;
}

.crop-overlay {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  pointer-events: none;
  background: rgba(0, 0, 0, 0.2);
}

.crop-viewport {
  width: 100%;
  /* 铺满 1:1 的 wrapper */
  height: 100%;
  border: 1px solid rgba(255, 255, 255, 0.8);
  border-radius: 50%;
  /* 圆形遮罩 */
  box-sizing: border-box;
  position: relative;
}

/* 通过阴影实现视口外的变暗效果 */

.crop-viewport::before {
  content: '';
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  width: 100%;
  height: 100%;
  border-radius: 50%;
  box-shadow: 0 0 0 1000px rgba(0, 0, 0, 0.3);
}

.modal-header h3 {
  font-family: var(--font-body);
  margin: 0;
  font-size: 1.5rem;
  letter-spacing: 0;
}

.close-btn {
  background: none;
  border: none;
  cursor: pointer;
  color: var(--text-primary);
  transition: color 0.3s;
}

.close-btn:hover {
  color: var(--accent-color);
}

.edit-form :deep(.n-form-item-label) {
  font-size: 0.75rem;
  letter-spacing: 0;
  color: var(--text-tertiary);
}

.modal-actions {
  flex-shrink: 0;
  padding-top: 16px;
  display: flex;
  justify-content: flex-end;
  border-top: 1px solid var(--line-color);
}

.save-btn {
  background: var(--text-primary);
  color: var(--bg-primary);
  border: none;
  padding: 12px 30px;
  font-family: var(--font-sans);
  font-weight: 700;
  letter-spacing: 0;
  cursor: pointer;
  transition: all 0.3s;
}

.save-btn:hover {
  background: var(--accent-color);
  transform: translateY(-2px);
  box-shadow: 0 10px 20px rgba(0,0,0,0.15);
}

.save-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

@media (max-width:640px) {
  .profile-page {
    padding: 20px 18px 48px;
  }
  .profile-header {
    flex-wrap: wrap;
    gap: 20px;
  }
  h1 {
    font-size: 24px;
  }
  .edit-btn {
    margin-left: auto;
  }
  .edit-modal-content {
    padding: 24px;
  }
}

.publications h2 span {
  display: inline-block;
}

.signature {
  color: var(--text-secondary);
}

@media (max-width:640px) {
  .profile-header {
    padding: 12px 0;
  }
  .profile-info {
    flex-basis: calc(100% - 100px);
  }
}

/* Author issue: cover composition, editorial rules and numbered contributions. */

.container {
  max-width: 1120px;
}

.profile-masthead {
  display: flex;
  justify-content: space-between;
  gap: 20px;
  padding: 8px 0 16px;
  border-bottom: 1px solid var(--text-primary);
  font-size: 11px;
  letter-spacing: .2em;
  color: var(--text-secondary);
}

.profile-masthead span:first-child {
  color: var(--accent-color);
  font-weight: 650;
}

.profile-header {
  position: relative;
  isolation: isolate;
  overflow: hidden;
  display: grid;
  margin-top: 12px;
  border-bottom: 1px solid color-mix(in srgb, var(--accent-color) 25%, var(--line-color));
}

.cover-orbit {
  position: absolute;
  z-index: -1;
  pointer-events: none;
  border: 1px solid color-mix(in srgb, var(--accent-color) 14%, transparent);
}

.cover-orbit::before, .cover-orbit::after {
  content: '';
  position: absolute;
  border: inherit;
}

h1 {
  font-family: var(--font-sans);
  font-size: clamp(32px, 4vw, 54px);
  line-height: 1.3;
  font-weight: 500;
  letter-spacing: .035em;
  margin-bottom: 12px;
}

.uid {
  color: var(--text-tertiary);
}

.signature {
  font-family: var(--font-sans);
  font-size: 17px;
  line-height: 1.9;
  margin-top: 20px;
  overflow-wrap: anywhere;
}

.edit-btn {
  align-self: end;
  background: var(--bg-primary);
  border-color: var(--line-color);
}

.publications h2 {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  border-bottom: 1px solid var(--text-primary);
  margin-bottom: 0;
  font-family: var(--font-sans);
  font-size: 22px;
}

.publications h2 span {
  padding: 0 0 14px;
  border-bottom: 2px solid var(--accent-color);
}

.publications h2 small {
  font-family: var(--font-sans);
  font-size: 28px;
  font-weight: 400;
  color: var(--accent-color);
}

.publication-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  column-gap: 36px;
}

.publication-entry {
  position: relative;
  min-width: 0;
  border-bottom: 1px solid var(--line-color);
}

.article-number {
  display: block;
  font-family: var(--font-sans);
  line-height: 1;
  color: var(--accent-color);
  opacity: .7;
}

.publications :deep(.article-card) {
  padding: 0;
  margin: 0;
  border: 0;
  border-radius: 0;
  background: transparent;
}

.publications :deep(.article-card:hover) {
  background: transparent;
}

.publications :deep(.article-heading) {
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 12px;
}

.publications :deep(.article-card h2) {
  display: block;
  border: 0;
  font-family: var(--font-sans);
  line-height: 1.5;
  margin: 0;
}

.publications :deep(.summary) {
  line-height: 1.8;
}

.publication-grid > :is(.journey-sentinel, .journey-note) {
  grid-column: 1 / -1;
}

@media (max-width:760px) {
  .edit-btn {
    grid-column: 2;
    justify-self: start;
    margin-left: 0;
  }
}

@media (max-width:480px) {
  .profile-avatar {
    margin-bottom: 4px;
  }
  .profile-info {
    width: 100%;
  }
  h1 {
    font-size: 36px;
  }
}

/* Personal introduction: portrait, greeting and a reserved typewriter line. */

.profile-header {
  grid-template-columns: 220px minmax(0, 1fr);
  grid-template-rows: auto auto;
  height: auto;
  min-height: 360px;
  padding: 44px;
  gap: 20px 44px;
  align-content: center;
  border-radius: 20px;
  background: linear-gradient(120deg, var(--bg-primary), color-mix(in srgb, var(--accent-soft) 55%, var(--bg-primary)));
}

.cover-orbit {
  width: 520px;
  height: 520px;
  top: -250px;
  right: -140px;
  transform: rotate(-20deg);
  border-radius: 36px;
  opacity: .7;
}

.cover-orbit::before, .cover-orbit::after {
  border-radius: 24px;
  inset: 40px;
}

.cover-orbit::after {
  inset: 90px;
}

.profile-info {
  grid-column: 2;
  grid-row: 1;
  align-self: center;
  padding: 0;
  min-width: 0;
}

.profile-info h1 {
  margin: 0 0 12px;
  font-size: clamp(32px, 4vw, 52px);
  line-height: 1.25;
  font-weight: 650;
  letter-spacing: -.025em;
  overflow-wrap: anywhere;
}

.greeting {
  display: block;
  margin-bottom: 10px;
  font-size: 17px;
  font-weight: 400;
  letter-spacing: .04em;
  color: var(--text-secondary);
}

.profile-name {
  display: block;
}

.signature {
  margin: 20px 0 0;
  max-width: none;
}

.uid {
  margin: 10px 0 0;
  font-size: 11px;
  letter-spacing: .06em;
}

.profile-avatar {
  grid-column: 1;
  grid-row: 1 / 3;
  align-self: center;
  position: relative;
  width: 220px;
  height: 220px;
  min-height: 0;
  padding: 6px;
  border-radius: 36px;
  border: 1px solid color-mix(in srgb, var(--accent-color) 25%, var(--line-color));
  background: var(--bg-primary);
  box-shadow: 0 20px 48px -30px color-mix(in srgb, var(--accent-color) 40%, transparent);
  overflow: hidden;
}

.profile-avatar :deep(.n-upload), .profile-avatar :deep(.n-upload-trigger) {
  width: 100%;
  height: 100%;
  display: block;
}

.avatar-button {
  display: block;
  width: 100%;
  height: 100%;
  padding: 0;
  border-radius: 30px;
  overflow: hidden;
}

.profile-avatar :deep(.n-avatar) {
  display: block;
  width: 100% !important;
  height: 100% !important;
  min-height: 0;
  border-radius: 30px !important;
  transform: translate(var(--portrait-x, 0px), var(--portrait-y, 0px)) scale(1.035);
  transition: transform 650ms cubic-bezier(.2,.75,.25,1);
}

.profile-avatar :deep(.n-avatar img) {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.profile-avatar::after {
  content: '';
  position: absolute;
  inset: 6px;
  pointer-events: none;
  border-radius: 30px;
  background: radial-gradient(circle at var(--light-x, 50%) var(--light-y, 30%), rgb(255 255 255 / .2), transparent 65%);
  opacity: .2;
  transition: opacity 500ms;
}

.profile-header:hover .profile-avatar::after {
  opacity: 1;
}

.avatar-button > .avatar-edit-badge {
  right: 12px;
  bottom: 12px;
  z-index: 2;
}

.edit-btn {
  grid-column: 2;
  grid-row: 2;
  justify-self: start;
  margin: 0;
  font-size: 12px;
  padding: 9px 14px;
}

.profile-page.loaded .profile-avatar {
  animation: portrait-reveal 850ms cubic-bezier(.16,1,.3,1) both;
}

.profile-page.loaded .profile-info {
  animation: portrait-reveal 850ms 100ms cubic-bezier(.16,1,.3,1) both;
}

@keyframes portrait-reveal {
  from {
    opacity: 0;
    transform: translateY(14px);
  }
  to {
    opacity: 1;
    transform: none;
  }
}

.publications {
  margin-top: 30px;
}

.publication-grid {
  gap: 18px;
  padding-top: 22px;
}

.publication-entry {
  grid-column: auto;
  display: block;
  padding: 24px;
  border: 1px solid var(--line-color);
  border-radius: 14px;
  background: var(--bg-primary);
  transition: transform 250ms, border-color 250ms, box-shadow 250ms;
}

.publication-entry:hover {
  transform: translateY(-3px);
  border-color: color-mix(in srgb, var(--accent-color) 30%, var(--line-color));
  box-shadow: 0 12px 24px -20px color-mix(in srgb, var(--accent-color) 30%, transparent);
}

.article-number {
  font-size: 13px;
  font-style: normal;
  letter-spacing: .1em;
  margin-bottom: 18px;
}

.publications :deep(.article-card h2) {
  font-size: 21px;
  font-weight: 600;
}

.publications :deep(.article-heading) {
  display: block;
}

.publications :deep(.summary) {
  margin-bottom: 20px;
  font-size: 14px;
}

@media (max-width:760px) {
  .profile-header {
    grid-template-columns: 150px minmax(0, 1fr);
    min-height: 300px;
    padding: 28px;
    gap: 18px 24px;
  }
  .profile-avatar {
    width: 150px;
    height: 150px;
    border-radius: 28px;
  }
  .profile-avatar :deep(.n-avatar), .avatar-button, .profile-avatar::after {
    border-radius: 22px !important;
  }
  .publication-grid {
    grid-template-columns: minmax(0, 1fr);
  }
}

@media (max-width:480px) {
  .profile-header {
    grid-template-columns: minmax(0, 1fr);
    padding: 28px 24px;
    gap: 24px;
  }
  .profile-avatar {
    grid-column: 1;
    grid-row: 1;
    width: 156px;
    height: 156px;
  }
  .profile-info {
    grid-column: 1;
    grid-row: 2;
  }
  .edit-btn {
    grid-column: 1;
    grid-row: 3;
  }
  .profile-info h1 {
    font-size: 36px;
  }
  .signature {
    margin-top: 16px;
  }
  .publication-entry {
    padding: 20px;
  }
}

@media (prefers-reduced-motion:reduce) {
  .profile-page.loaded .profile-avatar, .profile-page.loaded .profile-info {
    animation: none;
  }
  .profile-avatar :deep(.n-avatar) {
    transform: none;
    transition: none;
  }
  .profile-avatar::after, .publication-entry {
    transition: none;
  }
  .publication-entry:hover {
    transform: none;
  }
}
</style>
