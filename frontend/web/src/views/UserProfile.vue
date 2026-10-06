<script setup lang="ts">
import { ref, watch, computed, onUnmounted, nextTick, h } from 'vue'
import { useRoute } from 'vue-router'
import {
  NUpload, NIcon, useMessage, NSpin, NModal, NForm, NFormItem, NInput, NRadioGroup, NRadio, NAvatar, NPagination
} from 'naive-ui'
import {
  PencilOutline, CloseOutline, Person
} from '@vicons/ionicons5'
import { getUserInfo, updateCurrentUserAvatar, updateCurrentUserProfile, openPasswordSettings } from '@/api/user'
import { getPublicBlogPage } from '@/api/blog'
import { resolveAvatarUrl } from '@/utils/avatar'
import { DateUtils } from '@/types/date'
import { useUserStore } from '@/stores/user'
import type { BlogPublicBriefVO, UserInfoVO, UserSex } from '@/types'
import ArticleCard from '@/components/ArticleCard.vue'
import ProfileIntro from '@/components/ProfileIntro.vue'
import ContentImage from '@/components/ContentImage.vue'
import ManagedImageField from '@/components/ManagedImageField.vue'

const route = useRoute()
const userStore = useUserStore()
const message = useMessage()

async function managePassword() {
  try { await openPasswordSettings() }
  catch { message.error('无法打开账号密码设置，请重试') }
}

const uid = computed(() => String(route.params.uid || ''))
const isCurrentUser = computed(() => String(userStore.userInfo?.id ?? '') === uid.value)

const user = ref<UserInfoVO | null>(null)
const backgroundLoaded = ref(false)
const backgroundUploading = ref(false)
watch(() => user.value?.backgroundImageUrl, () => { backgroundLoaded.value = false })
const userAvatarUrl = computed(() => resolveAvatarUrl(user.value?.avatarUrl))
const blogList = ref<BlogPublicBriefVO[]>([])
const publicationsElement = ref<HTMLElement | null>(null)
const summaryObserver = new ResizeObserver(entries => {
  for (const entry of entries) {
    const element = entry.target as HTMLElement
    const lineHeight = parseFloat(getComputedStyle(element).lineHeight)
    if (lineHeight > 0) element.style.setProperty('--profile-summary-lines', String(Math.max(1, Math.floor(entry.contentRect.height / lineHeight))))
  }
})
watch([blogList, publicationsElement], async () => {
  summaryObserver.disconnect()
  await nextTick()
  publicationsElement.value?.querySelectorAll('.summary').forEach(element => summaryObserver.observe(element))
}, { flush: 'post' })
const blogTotal = ref(0)
const blogPage = ref(1)
const blogPages = ref(1)
const loadingBlogs = ref(false)
const blogError = ref(false)
const requestedBlogPage = ref(1)
const compactViewport = matchMedia('(max-width: 900px), (max-height: 540px)')
const blogPageSize = ref(compactViewport.matches ? 1 : 3)
function resizePage() {
  blogPageSize.value = compactViewport.matches ? 1 : 3
  if (user.value) void fetchBlogs(1)
}
compactViewport.addEventListener('change', resizePage)
let blogRequest = 0
let profileRequest = 0

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
  nickname: '', sex: 2 as UserSex, race: '', signature: '', backgroundImageUrl: null as string | null,
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
      const { avatarUrl } = await updateCurrentUserAvatar(file)
      if (userStore.userInfo) userStore.userInfo.avatarUrl = avatarUrl
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
    race: user.value.race,
    signature: user.value.signature ?? '',
    backgroundImageUrl: user.value.backgroundImageUrl ?? null,
  }
  showEditModal.value = true
}

const handleSaveProfile = async () => {
  if (saving.value || backgroundUploading.value || !isCurrentUser.value) return
  if (!editForm.value.nickname) {
    message.warning('Nickname is required')
    return
  }
  saving.value = true
  try {
    const { nickname, sex, race, signature, backgroundImageUrl } = editForm.value

    await updateCurrentUserProfile({ nickname, sex, race, signature, backgroundImageUrl })
    message.success('Profile Updated')
    showEditModal.value = false
    fetchProfile()

    // Update store if current user
    if (userStore.userInfo && String(userStore.userInfo.id ?? '') === uid.value) {
       userStore.userInfo = { ...userStore.userInfo, nickname, sex, race, signature, backgroundImageUrl }
    }
  } catch (error) {
    message.error('Failed to update profile')
  } finally {
    saving.value = false
  }
}

const fetchBlogs = async (page = 1): Promise<void> => {
  const request = ++blogRequest
  const authorId = uid.value
  loadingBlogs.value = true
  blogError.value = false
  requestedBlogPage.value = page
  try {
    const blogs = await getPublicBlogPage({
      pageNum: page,
      pageSize: blogPageSize.value,
      authorId,
      sortField: 'createTime',
      sortOrder: 'desc'
    })

    if (request !== blogRequest || authorId !== uid.value) return
    blogPage.value = page
    blogTotal.value = blogs.total
    blogList.value = blogs.records
    blogPages.value = Math.max(1, blogs.pages)
  } catch (error) {
    if (request !== blogRequest || authorId !== uid.value) return
    blogError.value = true
    message.error('博客加载失败，请重试')
  } finally {
    if (request === blogRequest) loadingBlogs.value = false
  }
}

const fetchProfile = async () => {
  const request = ++profileRequest
  const authorId = uid.value
  ++blogRequest
  loadingBlogs.value = false
  loading.value = true
  pageLoaded.value = false
  user.value = null
  blogList.value = []
  blogPage.value = 1
  blogTotal.value = 0
  blogPages.value = 1
  try {
    const profile = await getUserInfo(authorId)
    if (request !== profileRequest || authorId !== uid.value) return
    user.value = profile
    document.title = `${profile.nickname} - Para BBS`
    await fetchBlogs(1)
  } catch (error) {
    if (request === profileRequest) message.error('无法加载用户信息')
  } finally {
    if (request === profileRequest) {
      loading.value = false
      pageLoaded.value = true
    }
  }
}


watch(uid, () => { void fetchProfile() }, { immediate: true })

onUnmounted(() => {
  cancelAnimationFrame(portraitFrame)
  summaryObserver.disconnect()
  compactViewport.removeEventListener('change', resizePage)
  ++blogRequest
  ++profileRequest
})

</script>

<template>
  <div class="profile-page" :class="{ loaded: pageLoaded, 'has-background': backgroundLoaded }">
    <ContentImage :src="user?.backgroundImageUrl" alt="" eager class="profile-background" @load="backgroundLoaded = true" @error="backgroundLoaded = false" />
    <button v-if="isCurrentUser && user" class="edit-btn" @click="openEditModal">
      <n-icon :component="PencilOutline" />编辑资料
    </button>
    <n-spin :show="loading">
      <div class="container" v-if="user">

        <div class="profile-masthead"><span>PARA BBS</span><span>UID {{ user.id }}</span></div>
        <header class="profile-hero" @pointermove="movePortrait" @pointerleave="resetPortrait">
          <div class="profile-info">
            <h1>{{ user.nickname }}</h1>
            <ProfileIntro v-if="user.signature" :text="user.signature" class="signature" :title="user.signature" />
            <p v-else class="signature-empty">暂无简介</p>
            <dl class="profile-details" aria-label="个人资料">
              <div><dt>性别</dt><dd>{{ user.sex === 1 ? '男' : user.sex === 2 ? '女' : '保密' }}</dd></div>
              <div><dt>种族</dt><dd :title="user.race || undefined">{{ user.race || '未填写' }}</dd></div>
              <div><dt>加入</dt><dd><time :datetime="user.createTime">{{ user.createTime ? DateUtils.isoToDateOnly(user.createTime) : '未提供' }}</time></dd></div>
            </dl>
          </div>
          <div class="profile-avatar">
            <n-upload v-if="isCurrentUser" :show-file-list="false" :custom-request="handleAvatarChange" accept="image/*">
              <button class="avatar-button" aria-label="更换头像">
                <n-avatar :size="220" :src="userAvatarUrl" :render-icon="renderDefaultAvatar" />
                <span class="avatar-edit-badge">CHANGE</span>
              </button>
            </n-upload>
            <n-avatar v-else :size="220" :src="userAvatarUrl" :render-icon="renderDefaultAvatar" />
          </div>
        </header>
        <section ref="publicationsElement" class="publications" aria-label="个人文章">
          <h2><span>文章<small>{{ blogTotal }} 篇</small></span><span class="page-indicator">{{ blogPage }} / {{ blogPages }}</span></h2>
          <n-spin :show="loadingBlogs" class="publication-loading">
            <div v-if="blogList.length" class="publication-grid">
              <div v-for="(blog, index) in blogList" :key="blog.id" class="publication-entry">
                <span class="article-number" aria-hidden="true">{{ String((blogPage - 1) * blogPageSize + index + 1).padStart(2, '0') }}</span>
                <ArticleCard :blog="blog" />
                <router-link class="article-open" :to="'/blog/' + blog.id" :aria-label="'阅读：' + blog.title">阅读文章<span aria-hidden="true">↗</span></router-link>
              </div>
            </div>
            <p v-else-if="!blogError" class="empty-state">尚未发布文章</p>
          </n-spin>
          <div v-if="blogError" class="page-error"><span>文章加载失败</span><button @click="fetchBlogs(requestedBlogPage)">重试</button></div>
          <nav v-if="blogTotal > 0" class="pagination" aria-label="文章分页">
            <n-pagination :page="blogPage" :page-count="blogPages" :page-slot="5" :disabled="loadingBlogs" @update:page="fetchBlogs" />
          </nav>
        </section>
      </div>
    </n-spin>

    <!-- Edit Modal -->
    <n-modal v-model:show="showEditModal" :mask-closable="!saving && !backgroundUploading" :close-on-esc="!saving && !backgroundUploading">
      <div class="edit-modal-content">
        <div class="modal-header">
          <h3>编辑资料</h3>
          <button type="button" class="text-btn" @click="managePassword">账号密码设置</button>
          <button class="close-btn" aria-label="关闭" :disabled="saving || backgroundUploading" @click="showEditModal = false">
            <n-icon size="24">
<CloseOutline />
</n-icon>
          </button>
        </div>
        
        <n-form :model="editForm" label-placement="top" class="edit-form">
          <n-form-item label="昵称">
            <n-input v-model:value="editForm.nickname" placeholder="输入昵称" :maxlength="10" :disabled="saving" />
          </n-form-item>
          
          <n-form-item label="性别">
            <n-radio-group v-model:value="editForm.sex" name="sex" :disabled="saving">
              <n-radio :value="1">男</n-radio>
              <n-radio :value="2">女</n-radio>
              <n-radio :value="0">保密</n-radio>
            </n-radio-group>
          </n-form-item>
          <n-form-item label="种族">
            <n-input v-model:value="editForm.race" :maxlength="10" :disabled="saving" />
          </n-form-item>
          <n-form-item label="简介">
            <n-input v-model:value="editForm.signature" type="textarea" :maxlength="255" show-count :autosize="{ minRows: 2, maxRows: 4 }" :disabled="saving" placeholder="写一句关于自己的话" />
          </n-form-item>
          <n-form-item label="主页背景">
            <ManagedImageField :key="uid" v-model="editForm.backgroundImageUrl" purpose="BACKGROUND" :disabled="saving" @uploading="backgroundUploading = $event" />
          </n-form-item>
          
          <div class="modal-actions">
            <button class="save-btn" @click="handleSaveProfile" :disabled="saving || backgroundUploading">
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
  --profile-surface: var(--bg-primary);
  --profile-serif: 'Baskerville', 'Georgia', 'Times New Roman', 'Noto Serif SC', 'Source Han Serif SC', 'Songti SC', 'STSong', 'SimSun', serif;
  position: relative; isolation: isolate; height: 100dvh; min-height: 0;
  padding: 28px 48px 24px; box-sizing: border-box; overflow: hidden; color: var(--text-primary);
  background: radial-gradient(ellipse at 90% 0%, var(--accent-soft), transparent 48%), var(--bg-primary);
}
.profile-page::before { content: ''; position: absolute; z-index: -1; width: 500px; height: 500px; right: -240px; top: -220px; border: 1px solid color-mix(in srgb, var(--accent-color) 16%, transparent); border-radius: 50%; pointer-events: none; }
.profile-background { position: absolute; inset: 0; width: 100%; height: 100%; z-index: -2; object-fit: cover; background: transparent; }
.has-background::after { content: ''; position: absolute; inset: 0; z-index: -1; background: linear-gradient(90deg, rgb(12 17 26 / .86), rgb(12 17 26 / .48)); pointer-events: none; }
.profile-page > :deep(.n-spin-container), .profile-page > :deep(.n-spin-container > .n-spin-content) { height: 100%; min-height: 0; }
.container { display: grid; grid-template-rows: 32px minmax(150px, .85fr) minmax(240px, 1fr); gap: 24px; max-width: 1280px; height: 100%; min-height: 0; margin: auto; }
.profile-masthead { display: flex; align-items: flex-start; justify-content: space-between; padding-right: 120px; border-bottom: 1px solid var(--line-color); color: var(--text-tertiary); font-size: 10px; letter-spacing: .16em; }
.profile-masthead span:first-child { color: var(--accent-color); }
.edit-btn { position: absolute; top: 20px; right: 48px; z-index: 2; display: flex; align-items: center; gap: 8px; padding: 7px 12px; border: 1px solid var(--line-color); border-radius: 20px; background: var(--profile-surface); color: var(--text-secondary); cursor: pointer; font-size: 12px; }
.profile-hero { display: grid; grid-template-columns: minmax(0, 1fr) auto; align-items: center; gap: 48px; min-height: 0; }
.profile-info { min-width: 0; }
h1 { margin: 0; font-family: var(--profile-serif); font-size: clamp(38px, 5vw, 76px); font-weight: 400; line-height: 1.1; letter-spacing: -.025em; overflow-wrap: anywhere; }
.signature { margin-top: 20px; max-width: 54ch; }
.signature :deep(.intro-line) { font-family: var(--profile-serif); font-size: 18px; line-height: 1.75; display: grid; }
.signature :deep(.intro-reserve), .signature :deep(.intro-typed) { display: -webkit-box; -webkit-line-clamp: 3; -webkit-box-orient: vertical; overflow: hidden; }
.signature-empty { margin: 20px 0 0; color: var(--text-tertiary); font-family: var(--profile-serif); font-size: 16px; }
.profile-details { display: flex; flex-wrap: wrap; gap: 12px 24px; margin: 22px 0 0; }
.profile-details > div { display: flex; align-items: baseline; gap: 10px; min-width: 0; }
.profile-details dt { color: var(--text-tertiary); font-size: 11px; flex-shrink: 0; }
.profile-details dd { margin: 0; color: var(--text-secondary); font-size: 13px; max-width: 16ch; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.profile-avatar { width: clamp(130px, 28dvh, 240px); aspect-ratio: 1; padding: 6px; border: 1px solid var(--line-color); border-radius: 24px; background: var(--profile-surface); box-shadow: 0 24px 48px -34px rgb(0 0 0 / .3); transform: translate(var(--portrait-x, 0px), var(--portrait-y, 0px)); transition: transform 450ms; }
.profile-avatar :deep(.n-upload), .profile-avatar :deep(.n-upload-trigger) { display: block; width: 100%; height: 100%; }
.profile-avatar :deep(.n-avatar) { display: block; width: 100% !important; height: 100% !important; border-radius: 18px !important; }
.profile-avatar :deep(.n-avatar img) { object-fit: contain; }
.avatar-button { position: relative; display: block; width: 100%; height: 100%; padding: 0; border: 0; border-radius: 18px; background: transparent; cursor: pointer; }
.avatar-edit-badge { position: absolute; inset: auto 10px 10px; padding: 9px; border-radius: 10px; background: rgb(12 17 26 / .8); color: white; font-size: 10px; letter-spacing: .2em; opacity: 0; transform: translateY(4px); transition: opacity 200ms, transform 200ms; pointer-events: none; }
.avatar-button:hover .avatar-edit-badge, .avatar-button:focus-visible .avatar-edit-badge { opacity: 1; transform: none; }
.publications { display: flex; flex-direction: column; min-width: 0; min-height: 0; gap: 16px; }
.publications > h2 { display: flex; justify-content: space-between; align-items: center; margin: 0; padding: 0 0 12px; border-bottom: 1px solid var(--line-color); font-family: var(--profile-serif); font-size: 21px; line-height: 1; font-weight: 500; flex-shrink: 0; }
.publications > h2 small { margin-left: 12px; font-size: 11px; font-weight: 400; color: var(--text-tertiary); }
.page-indicator { font-size: 11px; font-weight: 400; color: var(--text-tertiary); }
.publication-loading { flex: 1; min-height: 0; }
.publication-loading :deep(.n-spin-content) { height: 100%; min-height: 0; }
.publication-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 24px; height: 100%; min-height: 0; }
.publication-entry { display: flex; flex-direction: column; min-width: 0; min-height: 0; padding-right: 24px; border-right: 1px solid var(--line-color); }
.publication-entry:last-child { padding-right: 0; border-right: 0; }
.article-number { display: block; margin-bottom: 10px; color: var(--accent-color); font-family: var(--profile-serif); font-style: italic; font-size: 12px; letter-spacing: .08em; flex-shrink: 0; }
.publications :deep(.article-card) { display: flex; flex: 1; flex-direction: column; min-height: 0; padding: 0; border: 0; border-radius: 0; background: transparent; }
.publications :deep(.cover-link) { flex: 0 0 35%; min-height: 0; max-height: 35%; margin-bottom: 12px; border-radius: 8px; }
.publications :deep(.cover-link:not(:has(.article-cover))) { display: none; }
.publications :deep(.article-cover) { width: 100%; height: 100%; margin: 0; object-fit: contain; background: var(--bg-secondary); }
.publications :deep(.article-card h2) { margin-bottom: 6px; font-family: var(--profile-serif); font-size: 18px; font-weight: 600; line-height: 1.45; display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; }
.publications :deep(.article-body) { display: flex; flex-direction: column; flex: 1; min-height: 0; }
.publications :deep(.article-heading), .publications :deep(footer) { flex-shrink: 0; }
.publications :deep(.summary) { flex: 1; min-height: 0; margin-bottom: 8px; font-size: 12px; line-height: 1.65; overflow-wrap: anywhere; -webkit-line-clamp: var(--profile-summary-lines, unset); }
.publications :deep(.author) { display: none; }
.publications :deep(footer) { justify-content: flex-start; gap: 8px; }
.publications :deep(.tags) { flex-shrink: 1; }
.publications :deep(.tag) { font-size: 10px; padding: 2px 7px; }
.publications :deep(.metrics) { font-size: 10px; gap: 10px; }
.publications :deep(.metrics .n-icon) { font-size: 14px; }
.publications :deep(.meta) { font-size: 10px; }
.article-open { display: flex; justify-content: space-between; align-items: center; margin-top: 12px; padding-top: 10px; color: var(--text-secondary); font-size: 11px; text-decoration: none; flex-shrink: 0; }
.article-open span { font-size: 18px; transition: transform 200ms; }
.article-open:hover, .article-open:focus-visible { color: var(--accent-color); }
.article-open:hover span { transform: translate(2px, -2px); }
.pagination { display: flex; justify-content: flex-end; flex-shrink: 0; min-height: 28px; }
.empty-state { margin: 0; display: grid; place-items: center; height: 100%; color: var(--text-tertiary); font-size: 14px; }
.page-error { display: flex; align-items: center; gap: 12px; font-size: 12px; }
.page-error button { border: 1px solid var(--line-color); border-radius: 8px; padding: 4px 10px; background: var(--profile-surface); color: var(--text-primary); cursor: pointer; }
.has-background { --profile-surface: #202631; --text-primary: #fafafa; --text-secondary: #e0e2e8; --text-tertiary: #c3c8d2; --line-color: rgb(255 255 255 / .24); --accent-color: #ffb5be; }
.has-background .publications :deep(.article-cover) { background: rgb(255 255 255 / .06); }
.has-background .pagination :deep(.n-pagination-item) { color: white; }
.loaded .profile-info { animation: profile-reveal 650ms ease both; }
@keyframes profile-reveal { from { opacity: 0; transform: translateY(8px); } to { opacity: 1; transform: none; } }
@media (max-width:1100px) { .profile-page { padding-inline: 32px; } .edit-btn { right: 32px; } .publication-grid { gap: 16px; } .publication-entry { padding-right: 16px; } }
@media (max-width:900px) {
  .profile-page { height: calc(100dvh - 64px); padding: 24px; }
  .container { grid-template-rows: 24px minmax(140px, .8fr) minmax(220px, 1fr); gap: 16px; }
  .edit-btn { top: 16px; right: 24px; }
  .profile-hero { gap: 20px; }
  .profile-avatar { width: clamp(92px, 18dvh, 150px); }
  .signature { margin-top: 12px; }
  .signature :deep(.intro-line) { font-size: 14px; line-height: 1.6; }
  .publication-grid { grid-template-columns: minmax(0, 1fr); }
  .publication-entry { padding: 0; border: 0; }
  .publications :deep(.article-card) { display: grid; grid-template-columns: minmax(0, 1fr) minmax(0, 1.5fr); gap: 18px; align-items: center; }
  .publications :deep(.cover-link) { max-height: none; height: 100%; margin: 0; }
  .publications :deep(.article-body) { grid-column: 2; min-width: 0; }
  .publications :deep(.article-card:not(:has(.article-cover))) { grid-template-columns: minmax(0, 1fr); }
  .publications :deep(.article-card:not(:has(.article-cover)) .article-body) { grid-column: 1; }
}
@media (max-height:620px) { .container { grid-template-rows: 24px minmax(110px, .65fr) minmax(180px, 1fr); gap: 12px; } .profile-avatar { width: 100px; } h1 { font-size: 36px; } .signature { margin-top: 10px; } .signature :deep(.intro-line) { font-size: 13px; } .publications { gap: 10px; }  }
@media (max-height:540px) { .profile-page { padding-block: 16px; } .container { grid-template-rows: 20px minmax(96px, .8fr) minmax(130px, 1fr); gap: 8px; } .profile-avatar { width: 76px; padding: 4px; } .signature :deep(.intro-reserve), .signature :deep(.intro-typed) { -webkit-line-clamp: 1; } .publication-grid { grid-template-columns: minmax(0, 1fr); } .publications :deep(.article-card) { display: grid; grid-template-columns: minmax(0, 1fr) minmax(0, 2fr); gap: 18px; align-items: center; } .publications :deep(.cover-link) { height: 100%; max-height: none; margin: 0; } .publications :deep(.article-body) { grid-column: 2; } .publications :deep(.article-card:not(:has(.article-cover))) { grid-template-columns: minmax(0, 1fr); } .publications :deep(.article-card:not(:has(.article-cover)) .article-body) { grid-column: 1; } .article-number { display: none; } .article-open { margin-top: 6px; padding-top: 0; } }
@media (max-width:640px) { .edit-modal-content { padding: 24px; } }
@media (max-width:900px) { .profile-details { gap: 8px 16px; margin-top: 14px; } .profile-details > div { gap: 6px; } .profile-details dd { font-size: 12px; } }
@media (max-height:620px) { .profile-details { gap: 6px 16px; margin-top: 10px; } .signature-empty { margin-top: 10px; font-size: 13px; } }
@media (max-height:540px) { .profile-details { margin-top: 6px; } .profile-details dt, .profile-details dd { font-size: 10px; } h1 { font-size: 32px; } .signature { margin-top: 6px; } }
@media (max-height:540px) { .publications :deep(.tags) { display: none; } .publications :deep(.article-card h2) { -webkit-line-clamp: 1; font-size: 15px; } .publications > h2 { padding-bottom: 6px; font-size: 15px; } .publications { gap: 6px; } }
@media (prefers-reduced-motion:reduce) { .loaded .profile-info { animation: none; } .profile-avatar, .avatar-edit-badge, .article-open span { transform: none; transition: none; } }
.edit-modal-content {
  box-sizing: border-box;
  max-height: calc(100dvh - 48px);
  overflow-y: auto;
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
</style>
