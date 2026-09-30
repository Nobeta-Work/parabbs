<script setup lang="ts">
import ContentImage from '@/components/ContentImage.vue'
import { computed, h, onMounted, onUnmounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import VditorPreview from '@/components/editor/VditorPreview.vue'
import {
  NAvatar,
  NButton,
  NEmpty,
  NIcon,
  NInput,
  NPagination,
  NResult,
  NSpace,
  NSpin,
  NTag,
  useMessage,
} from 'naive-ui'
import {
  ArrowUpOutline,
  ChatbubbleOutline,
  CreateOutline,
  DocumentTextOutline,
  DownloadOutline,
  Heart,
  HeartOutline,
  ListOutline,
  Person,
  PrintOutline,
  ReturnUpBackOutline,
  TrashOutline,
} from '@vicons/ionicons5'
import { getPublicBlog } from '@/api/blog'
import { createComment, deleteComment, getCommentPage } from '@/api/comment'
import { toggleBlogLike, toggleCommentLike } from '@/api/like'
import { useToc } from '@/composables/useToc'
import { useThemeStore } from '@/stores/theme'
import { useUserStore } from '@/stores/user'
import type { BlogPublicDetailVO, CommentVO } from '@/types'
import { DateUtils } from '@/types/date'
import { resolveAvatarUrl } from '@/utils/avatar'
import { storeToRefs } from 'pinia'

const route = useRoute()
const router = useRouter()
const message = useMessage()
const userStore = useUserStore()
const themeStore = useThemeStore()
const { isDark } = storeToRefs(themeStore)

const blog = ref<BlogPublicDetailVO | null>(null)
const loading = ref(false)
const loadError = ref(false)
const liking = ref(false)
const comments = ref<CommentVO[]>([])
const commentsLoading = ref(false)
const commentPage = ref(1)
const commentPages = ref(0)
const commentContent = ref('')
const commentSubmitting = ref(false)
const replyTarget = ref<CommentVO | null>(null)
const changingCommentIds = ref(new Set<string>())
const previewRef = ref<HTMLDivElement | null>(null)
const showBackTop = ref(false)
let scrollTarget: HTMLElement | Window | null = null
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
const content = computed(() => blog.value?.content || '')
const authorAvatarUrl = computed(() => resolveAvatarUrl(blog.value?.author.avatarUrl))
const canEdit = computed(() => {
  const authorId = String(blog.value?.author.id ?? '')
  const currentId = String(userStore.userInfo?.id ?? '')
  return Boolean(authorId && currentId && authorId === currentId) || userStore.hasAnyRole(['ROLE_ADMIN'])
})

const renderDefaultAvatar = () => h(NIcon, null, { default: () => h(Person) })

function handlePreviewReady(): void {
  if (!previewRef.value) return
  extractToc(previewRef.value)
  setupScrollSpy(previewRef.value)
}

async function fetchBlog(): Promise<void> {
  loading.value = true
  loadError.value = false
  try {
    const result = await getPublicBlog(blogId.value)
    blog.value = result
    document.title = `${result.title} | Para BBS`
    await fetchComments(1)
  } catch (error) {
    loadError.value = true
    message.error('博客加载失败')
  } finally {
    loading.value = false
  }
}

async function fetchComments(page = commentPage.value): Promise<void> {
  commentsLoading.value = true
  try {
    const result = await getCommentPage(blogId.value, {
      pageNum: page,
      pageSize: 20,
      sortOrder: 'desc',
    })
    comments.value = result.records
    commentPage.value = result.pageNum
    commentPages.value = result.pages
  } catch (error) {
    message.error('评论加载失败')
  } finally {
    commentsLoading.value = false
  }
}

function requireLogin(): boolean {
  if (userStore.isAuthenticated) {
    return true
  }
  router.push({ path: '/login', query: { redirect: route.fullPath } })
  return false
}

function startReply(comment: CommentVO): void {
  if (!requireLogin()) return
  replyTarget.value = comment
}

function cancelReply(): void {
  replyTarget.value = null
}

async function submitComment(): Promise<void> {
  if (!blog.value || commentSubmitting.value || !requireLogin()) return
  const content = commentContent.value.trim()
  if (!content) {
    message.warning('请输入评论内容')
    return
  }

  commentSubmitting.value = true
  try {
    const replying = Boolean(replyTarget.value)
    await createComment({
      blogId: blog.value.id,
      parentId: replyTarget.value?.id ?? 0,
      content,
    })
    blog.value.commentsCount += 1
    commentContent.value = ''
    replyTarget.value = null
    await fetchComments(replying ? commentPage.value : 1)
    message.success(replying ? '回复成功' : '评论成功')
  } catch (error) {
    message.error('评论发布失败')
  } finally {
    commentSubmitting.value = false
  }
}

function canDeleteComment(comment: CommentVO): boolean {
  return comment.status === 1
    && String(comment.author.id) === String(userStore.userInfo?.id ?? '')
}

async function removeComment(comment: CommentVO): Promise<void> {
  if (!window.confirm('确认删除这条评论？')) return
  try {
    await deleteComment(comment.id)
    if (blog.value) {
      blog.value.commentsCount = Math.max(0, blog.value.commentsCount - 1)
    }
    await fetchComments(commentPage.value)
    message.success('评论已删除')
  } catch (error) {
    message.error('评论删除失败')
  }
}

async function handleCommentLike(comment: CommentVO): Promise<void> {
  if (!requireLogin()) return
  const key = String(comment.id)
  if (changingCommentIds.value.has(key)) return
  changingCommentIds.value = new Set(changingCommentIds.value).add(key)
  try {
    comment.likeCount = await toggleCommentLike(comment.id)
  } catch (error) {
    message.error('评论点赞失败')
  } finally {
    const next = new Set(changingCommentIds.value)
    next.delete(key)
    changingCommentIds.value = next
  }
}

function formatCommentTime(value: string): string {
  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  }).format(new Date(value))
}

async function handleLike(): Promise<void> {
  if (!blog.value || liking.value) {
    return
  }

  if (!userStore.isAuthenticated) {
    router.push({ path: '/login', query: { redirect: route.fullPath } })
    return
  }

  const previousLiked = blog.value.isLiked
  const previousCount = blog.value.likeCount
  blog.value.isLiked = !previousLiked
  blog.value.likeCount = Math.max(0, previousCount + (blog.value.isLiked ? 1 : -1))
  liking.value = true

  try {
    blog.value.likeCount = await toggleBlogLike(blog.value.id)
  } catch (error) {
    blog.value.isLiked = previousLiked
    blog.value.likeCount = previousCount
    message.error('点赞失败')
  } finally {
    liking.value = false
  }
}

function downloadMarkdown(): void {
  if (!blog.value) {
    return
  }

  const blob = new Blob([content.value], { type: 'text/markdown;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = `${blog.value.title || 'para-blog'}.md`
  link.click()
  URL.revokeObjectURL(url)
}

function escapeHtml(value: string): string {
  return value
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')
}

function getPrintableStyles(): string {
  return Array.from(document.querySelectorAll<HTMLLinkElement | HTMLStyleElement>('link[rel="stylesheet"], style'))
    .map((node) => node.outerHTML)
    .join('\n')
}

function getPrintableHtml(): string {
  if (!blog.value) {
    return ''
  }

  const title = escapeHtml(blog.value.title || 'Para Blog')
  const article = document.querySelector<HTMLElement>('.article-card')?.cloneNode(true) as HTMLElement | null

  article?.querySelector('.action-row')?.remove()

  if (article) {
    article.querySelectorAll<HTMLElement>('button, [role="button"]').forEach((element) => {
      element.setAttribute('tabindex', '-1')
    })
  }

  const body = article?.outerHTML || '<article class="article-card empty-print-content">No content yet</article>'
  const htmlClass = document.documentElement.className

  return `<!doctype html>
<html class="${escapeHtml(htmlClass)}">
  <head>
    <meta charset="utf-8">
    <title>${title}</title>
    ${getPrintableStyles()}
    <style>
      @page {
        margin: 18mm 16mm;
      }

      html,
      body {
        width: auto !important;
        height: auto !important;
        min-height: 0 !important;
        overflow: visible !important;
      }

      body {
        margin: 0 !important;
        background: var(--bg-primary) !important;
        color: var(--text-primary) !important;
        print-color-adjust: exact;
        -webkit-print-color-adjust: exact;
      }

      .article-card {
        width: 100% !important;
        max-width: 860px !important;
        margin: 0 auto;
        border: 0 !important;
        box-shadow: none !important;
      }

      .article-header h1 {
        font-size: 40px !important;
      }

      .action-row,
      .detail-sidebar,
      .toc-card,
      .author-card {
        display: none !important;
      }

      .article-preview-container {
        max-width: 860px !important;
      }

      .vditor-reset img,
      .vditor-reset table,
      .vditor-reset pre,
      .vditor-reset blockquote {
        break-inside: avoid;
        page-break-inside: avoid;
      }

      .vditor-reset img {
        max-width: 100% !important;
      }

      .vditor-reset pre {
        white-space: pre-wrap !important;
        overflow: visible !important;
      }

      .empty-print-content {
        padding: 48px 0;
        color: #777;
        text-align: center;
      }
    </style>
  </head>
  <body>
    ${body}
  </body>
</html>`
}

async function waitForPrintAssets(doc: Document): Promise<void> {
  const images = Array.from(doc.images)
  const imageReady = images.map((image) => {
    if (image.complete) {
      return Promise.resolve()
    }

    return new Promise<void>((resolve) => {
      image.onload = () => resolve()
      image.onerror = () => resolve()
    })
  })

  await Promise.race([
    Promise.all(imageReady),
    new Promise((resolve) => window.setTimeout(resolve, 2500)),
  ])
}

async function exportPdf(): Promise<void> {
  if (!blog.value) {
    return
  }

  const iframe = document.createElement('iframe')
  iframe.dataset.pdfPrintFrame = 'true'
  iframe.style.position = 'fixed'
  iframe.style.right = '0'
  iframe.style.bottom = '0'
  iframe.style.width = '0'
  iframe.style.height = '0'
  iframe.style.border = '0'
  iframe.style.visibility = 'hidden'
  document.body.appendChild(iframe)

  const printWindow = iframe.contentWindow
  const printDocument = iframe.contentDocument
  if (!printWindow || !printDocument) {
    iframe.remove()
    message.error('PDF 打印窗口创建失败')
    return
  }

  const cleanup = () => {
    iframe.remove()
  }

  printDocument.open()
  printDocument.write(getPrintableHtml())
  printDocument.close()

  await waitForPrintAssets(printDocument)
  printWindow.addEventListener('afterprint', cleanup, { once: true })
  printWindow.focus()
  printWindow.print()
  window.setTimeout(cleanup, 60000)
}

function goEdit(): void {
  if (blog.value) {
    router.push(`/workspace/blogs/${blog.value.id}`)
  }
}

function goProfile(): void {
  if (blog.value) {
    router.push(`/${blog.value.author.id}`)
  }
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

function scrollToTop(): void {
  const target = scrollTarget ?? resolveScrollTarget()
  target.scrollTo({ top: 0, behavior: 'smooth' })
}

onMounted(() => {
  window.scrollTo(0, 0)
  scrollTarget = resolveScrollTarget()
  updateBackTopVisibility()
  scrollTarget.addEventListener('scroll', updateBackTopVisibility, { passive: true })
  fetchBlog()
})

onUnmounted(() => {
  scrollTarget?.removeEventListener('scroll', updateBackTopVisibility)
  scrollTarget = null
  cleanupToc()
})
</script>

<template>
  <div class="public-detail-page">
    <n-spin :show="loading">
      <n-result
        v-if="loadError"
        status="404"
        title="博客不存在"
        description="这篇博客暂时无法访问。"
      >
        <template #footer>
          <n-button @click="router.push('/blog')">返回博客列表</n-button>
        </template>
      </n-result>

      <template v-else-if="blog">
      <ContentImage :src="blog.coverUrl" :alt="blog.title + '封面'" eager class="article-cover" />
      <div class="detail-shell">
        <aside class="detail-sidebar">
          <section class="author-card editorial-surface">
            <n-avatar
              round
              :size="48"
              :src="authorAvatarUrl"
              :render-icon="renderDefaultAvatar"
              class="author-avatar"
            />
            <h2>{{ blog.author.nickname }}</h2>
            <button class="visit-btn" type="button" @click="goProfile">作者主页</button>
          </section>

          <section class="toc-card">
            <div class="toc-header">
              <n-icon><ListOutline /></n-icon>
              <span>目录</span>
            </div>
            <div v-if="tocItems.length" class="toc-list">
              <button
                v-for="item in visibleTocItems"
                :key="item.id"
                class="toc-item"
                :class="[`toc-level-${item.level}`, { 'toc-active': activeTocId === item.id }]"
                type="button"
                @click="scrollToHeading(previewRef, item.id)"
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
          </section>
        </aside>

        <div class="detail-content">
          <main class="article-card">
            <div class="article-masthead"><span>PARA BBS</span><span>{{ DateUtils.isoToDateOnly(blog.createTime) }}</span></div>
            <header class="article-header editorial-surface">
              <h1>{{ blog.title }}</h1>

            <div class="author-row">
              <n-avatar
                round
                size="small"
                :src="authorAvatarUrl"
                :render-icon="renderDefaultAvatar"
              />
              <span>{{ blog.author.nickname }}</span>
              <span class="meta-dot">/</span>
              <span>{{ DateUtils.isoToDateOnly(blog.createTime) }}</span>
              <template v-if="blog.updateTime && blog.updateTime !== blog.createTime">
                <span class="meta-dot">/</span>
                <span>Updated {{ DateUtils.isoToDateOnly(blog.updateTime) }}</span>
              </template>
            </div>

            <p v-if="blog.summary" class="article-summary">{{ blog.summary }}</p>

            <div v-if="blog.tags?.length" class="tag-row">
              <n-tag v-for="tag in blog.tags" :key="tag.id" size="small" round>
                {{ tag.name }}
              </n-tag>
            </div>

            </header>


            <div v-if="content" ref="previewRef" class="article-preview-container">
              <VditorPreview
                :content="content"
                :is-dark="isDark"
                @ready="handlePreviewReady"
              />
            </div>
            <n-empty v-else class="empty-content" description="No content yet">
              <template #icon>
                <n-icon :component="DocumentTextOutline" />
              </template>
            </n-empty>

            <n-space class="action-row" :size="10" wrap>
            <n-button secondary :loading="liking" @click="handleLike">
              <template #icon>
                <n-icon :component="blog.isLiked ? Heart : HeartOutline" />
              </template>
              {{ blog.likeCount || 0 }}
            </n-button>
            <n-button secondary @click="downloadMarkdown">
              <template #icon><n-icon :component="DownloadOutline" /></template>
              Markdown
            </n-button>
            <n-button secondary @click="exportPdf">
              <template #icon><n-icon :component="PrintOutline" /></template>
              PDF
            </n-button>
            <n-button v-if="canEdit" secondary @click="goEdit">
              <template #icon><n-icon :component="CreateOutline" /></template>
              Edit
            </n-button>
            </n-space>

          </main>

          <section class="comments-section">
            <header class="comments-header">
              <div>
                <span class="comments-eyebrow">Discussion</span>
                <h2>评论 <small>{{ blog.commentsCount || 0 }}</small></h2>
              </div>
            </header>

            <div class="comment-composer">
              <div v-if="replyTarget" class="reply-context">
                <span>回复 {{ replyTarget.author.nickname }}</span>
                <button type="button" @click="cancelReply">取消</button>
              </div>
              <n-input
                v-model:value="commentContent"
                type="textarea"
                :maxlength="2000"
                show-count
                :autosize="{ minRows: 3, maxRows: 8 }"
                :placeholder="replyTarget ? `回复 ${replyTarget.author.nickname}` : '写下你的评论…'"
                @focus="requireLogin"
              />
              <div class="composer-actions">
                <span>{{ userStore.isAuthenticated ? '保持友善，认真交流。' : '登录后参与讨论。' }}</span>
                <n-button type="primary" :loading="commentSubmitting" @click="submitComment">
                  {{ replyTarget ? '发布回复' : '发布评论' }}
                </n-button>
              </div>
            </div>

            <n-spin :show="commentsLoading">
              <div v-if="comments.length" class="comment-list">
                <article v-for="comment in comments" :key="comment.id" class="comment-thread">
                  <div class="comment-row">
                    <n-avatar
                      round
                      :size="38"
                      :src="resolveAvatarUrl(comment.author.avatarUrl)"
                      :render-icon="renderDefaultAvatar"
                    />
                    <div class="comment-body">
                      <div class="comment-meta">
                        <strong>{{ comment.author.nickname }}</strong>
                        <time>{{ formatCommentTime(comment.createTime) }}</time>
                      </div>
                      <p :class="{ 'deleted-comment': comment.status === -1 }">
                        {{ comment.status === -1 ? '该评论已删除' : comment.content }}
                      </p>
                      <div v-if="comment.status === 1" class="comment-actions">
                        <n-button
                          text
                          size="small"
                          :loading="changingCommentIds.has(String(comment.id))"
                          @click="handleCommentLike(comment)"
                        >
                          <template #icon><n-icon :component="HeartOutline" /></template>
                          {{ comment.likeCount || 0 }}
                        </n-button>
                        <n-button text size="small" @click="startReply(comment)">
                          <template #icon><n-icon :component="ReturnUpBackOutline" /></template>
                          回复
                        </n-button>
                        <n-button
                          v-if="canDeleteComment(comment)"
                          text
                          size="small"
                          type="error"
                          @click="removeComment(comment)"
                        >
                          <template #icon><n-icon :component="TrashOutline" /></template>
                          删除
                        </n-button>
                      </div>
                    </div>
                  </div>

                  <div v-if="comment.replies?.length" class="reply-list">
                    <div v-for="reply in comment.replies" :key="reply.id" class="comment-row reply-row">
                      <n-avatar
                        round
                        :size="32"
                        :src="resolveAvatarUrl(reply.author.avatarUrl)"
                        :render-icon="renderDefaultAvatar"
                      />
                      <div class="comment-body">
                        <div class="comment-meta">
                          <strong>{{ reply.author.nickname }}</strong>
                          <span v-if="reply.replyTo">回复 {{ reply.replyTo.nickname }}</span>
                          <time>{{ formatCommentTime(reply.createTime) }}</time>
                        </div>
                        <p :class="{ 'deleted-comment': reply.status === -1 }">
                          {{ reply.status === -1 ? '该回复已删除' : reply.content }}
                        </p>
                        <div v-if="reply.status === 1" class="comment-actions">
                          <n-button
                            text
                            size="small"
                            :loading="changingCommentIds.has(String(reply.id))"
                            @click="handleCommentLike(reply)"
                          >
                            <template #icon><n-icon :component="HeartOutline" /></template>
                            {{ reply.likeCount || 0 }}
                          </n-button>
                          <n-button text size="small" @click="startReply(reply)">
                            <template #icon><n-icon :component="ReturnUpBackOutline" /></template>
                            回复
                          </n-button>
                          <n-button
                            v-if="canDeleteComment(reply)"
                            text
                            size="small"
                            type="error"
                            @click="removeComment(reply)"
                          >
                            <template #icon><n-icon :component="TrashOutline" /></template>
                            删除
                          </n-button>
                        </div>
                      </div>
                    </div>
                  </div>
                </article>
              </div>
              <n-empty v-else class="comments-empty" description="还没有评论，来写下第一条吧。">
                <template #icon><n-icon :component="ChatbubbleOutline" /></template>
              </n-empty>
            </n-spin>

            <n-pagination
              v-if="commentPages > 1"
              v-model:page="commentPage"
              class="comments-pagination"
              :page-count="commentPages"
              @update:page="fetchComments"
            />
          </section>
        </div>
      </div>
      </template>
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
.article-cover {
  height: clamp(220px, 32vw, 460px);
  aspect-ratio: auto;
}
.public-detail-page {
  min-height: 100vh;
  background: var(--bg-primary);
  color: var(--text-primary);
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

.detail-shell {
  margin: 0 auto;
  display: grid;
  align-items: start;
}

.detail-sidebar {
  position: sticky;
  display: flex;
  flex-direction: column;
}

.detail-content {
  min-width: 0;
}

.author-card,
.toc-card,
.article-card,
.comments-section {
  background: var(--bg-primary);
  border: 0;
}

.author-avatar {
  border: 1px solid var(--line-color);
}

.author-card h2 {
  font-family: var(--font-body);
  color: var(--text-primary);
}

.visit-btn {
  font-family: var(--font-sans);
  cursor: pointer;
  text-transform: uppercase;
  transition: all 0.2s ease;
  font-weight: 500;
  letter-spacing: 0;
}

.toc-card {
  overflow: auto;
}

.toc-header {
  display: flex;
  align-items: center;
  gap: 10px;
  font-family: var(--font-body);
  border-bottom: 1px solid var(--line-color);
}

.toc-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.toc-item {
  width: 100%;
  border: 0;
  background: transparent;
  color: var(--text-secondary);
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 8px;
  padding: 4px 0;
  cursor: pointer;
  text-align: left;
  font-family: var(--font-sans);
  transition: all 0.2s ease;
}

.toc-item:hover,
.toc-active {
  color: var(--text-primary);
  transform: translateX(4px);
}

.toc-active {
  font-weight: 700;
}

.toc-toggle {
  color: var(--accent-color);
  flex: 0 0 auto;
}

.article-card {
  min-width: 0;
  padding: 12px 0 48px;
}

.article-header {
  padding-bottom: 34px;
}

.article-header h1 {
  color: var(--text-primary);
}

.author-row,
.tag-row {
  display: flex;
  align-items: center;
  justify-content: center;
  flex-wrap: wrap;
  gap: 10px;
}

.author-row {
  color: var(--text-secondary);
  font-family: var(--font-sans);
}

.meta-dot {
  color: var(--text-tertiary);
}

.article-summary {
  color: var(--text-secondary);
}

.tag-row {
  margin-top: 22px;
}

.action-row {
  justify-content: center;
  max-width: 860px;
  margin: 36px auto 0;
  padding-top: 24px;
  border-top: 1px solid var(--line-color);
}

.comments-section {
  margin-top: 24px;
  padding: 32px 0;
}

.comments-header {
  display: flex;
  align-items: end;
  justify-content: space-between;
  margin-bottom: 20px;
}

.comments-eyebrow {
  font-size: 0.72rem;
  font-weight: 800;
  text-transform: uppercase;
}

.comments-header h2 {
  margin: 5px 0 0;
  font-family: var(--font-body);
  font-size: 1.8rem;
}

.comments-header small {
  color: var(--text-tertiary);
  font-size: 0.8em;
}

.comment-composer {
  margin-bottom: 28px;
  padding: 16px;
  border: 1px solid var(--line-color);
  background: var(--bg-secondary);
  border-radius: 20px;
}

.reply-context,
.composer-actions,
.comment-meta,
.comment-actions {
  display: flex;
  align-items: center;
}

.reply-context {
  justify-content: space-between;
  margin-bottom: 10px;
  color: var(--text-secondary);
  font-size: 0.86rem;
}

.reply-context button {
  border: 0;
  background: transparent;
  color: var(--accent-color);
  cursor: pointer;
}

.composer-actions {
  justify-content: space-between;
  gap: 16px;
  margin-top: 12px;
  color: var(--text-tertiary);
  font-size: 0.82rem;
}

.comment-list,
.reply-list {
  display: flex;
  flex-direction: column;
}

.comment-thread {
  padding: 22px 0;
  border-bottom: 1px solid var(--line-color);
}

.comment-row {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  gap: 13px;
}

.comment-body {
  min-width: 0;
}

.comment-meta {
  flex-wrap: wrap;
  gap: 8px;
  color: var(--text-tertiary);
  font-size: 0.78rem;
}

.comment-meta strong {
  color: var(--text-primary);
  font-size: 0.92rem;
}

.comment-body p {
  margin: 8px 0;
  color: var(--text-secondary);
  line-height: 1.7;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}

.comment-body .deleted-comment {
  color: var(--text-tertiary);
  font-style: italic;
}

.comment-actions {
  gap: 14px;
}

.reply-list {
  gap: 18px;
  margin: 18px 0 0 50px;
  padding: 18px;
  border-left: 2px solid var(--line-color);
  background: var(--bg-secondary);
  border-radius: 16px;
}

.comments-empty {
  padding: 46px 0;
}

.comments-pagination {
  justify-content: center;
  margin-top: 24px;
}

.empty-content {
  padding: 80px 0;
}

@media print {
  .detail-sidebar,
  .action-row,
  .comments-section,
  .back-top-button {
    display: none;
  }
  .public-detail-page {
    padding: 0;
  }
  .detail-shell {
    display: block;
  }
  .article-card {
    border: 0;
    padding: 0;
  }
}

@media (max-width: 1024px) {
  .detail-shell {
    grid-template-columns: 1fr;
    gap: 24px;
  }
  .detail-sidebar {
    position: static;
  }
  .toc-card {
    max-height: none;
  }
  .article-card {
    padding: 32px 22px 44px;
  }
  .comments-section {
    padding: 28px 22px 36px;
  }
}

@media (max-width: 1024px) {
  .detail-sidebar {
    order: 2;
  }
  .article-card, .comments-section {
    padding-inline: 0;
  }
}

.article-masthead {
  display: flex;
  justify-content: space-between;
  gap: 20px;
  padding: 0 0 16px;
  color: var(--text-tertiary);
}

.article-masthead span:first-child {
  color: var(--accent-color);
  font-weight: 650;
}

.article-header {
  text-align: left;
  border-bottom-color: color-mix(in srgb, var(--accent-color) 22%, var(--line-color));
}

.article-header h1 {
  font-family: var(--font-sans);
  overflow-wrap: anywhere;
}

.author-row, .tag-row {
  justify-content: flex-start;
}

.author-row {
  letter-spacing: 0;
}

.article-summary {
  font-family: var(--font-sans);
  font-style: normal;
}

.author-card {
  border-top: 1px solid var(--line-color);
}

.author-card h2, .comments-header h2 {
  font-family: var(--font-sans);
  font-weight: 500;
  overflow-wrap: anywhere;
}

.visit-btn {
  border-color: var(--line-color);
  background: var(--bg-primary);
}

.toc-header {
  font-weight: 500;
  border-bottom-color: var(--text-primary);
}

.comments-section {
  border-top: 1px solid var(--text-primary);
}

.comments-eyebrow {
  color: var(--accent-color);
  letter-spacing: .18em;
}

.article-header {
  border-radius: 0;
  background: none;
}

.article-header::before {
  width: 48px;
  height: 3px;
  top: 0;
  left: 0;
  right: auto;
  border: 0;
  border-radius: 0;
  background: var(--accent-color);
}

.article-header::after {
  display: none;
}

.article-header h1 {
  margin-left: 0;
  max-width: 820px;
}

.article-summary {
  padding-left: 0;
  border-left: 0;
}

.article-header .tag-row {
  margin-top: 28px;
}

.public-detail-page {
  padding: 0 0 64px;
}

.detail-shell {
  width: min(1140px, calc(100% - 64px));
  margin-top: 28px;
  grid-template-columns: minmax(0, 1fr) 180px;
  gap: 40px;
}

.detail-content {
  grid-column: 1;
  grid-row: 1;
}

.detail-sidebar {
  grid-column: 2;
  grid-row: 1;
  gap: 24px;
  top: 28px;
  padding: 8px 0 0 24px;
  border-right: 0;
  border-left: 1px solid var(--line-color);
}

.author-card {
  padding: 8px 0 18px;
  text-align: left;
  border: 0;
  border-bottom: 1px solid var(--line-color);
  border-radius: 0;
  background: none;
}

.author-card::before, .author-card::after {
  display: none;
}

.author-card h2 {
  margin: 12px 0 8px;
  font-size: 16px;
  line-height: 1.5;
  font-weight: 600;
}

.visit-btn {
  width: auto;
  padding: 0 0 3px;
  border: 0;
  border-bottom: 1px solid var(--line-color);
  border-radius: 0;
  font-size: 11px;
  color: var(--text-tertiary);
}

.visit-btn:hover {
  background: transparent;
  color: var(--accent-color);
  border-color: var(--accent-color);
}

.toc-card {
  padding: 0;
  max-height: calc(100dvh - 280px);
}

.toc-header {
  padding-bottom: 12px;
  margin-bottom: 12px;
  font-size: 14px;
}

.toc-item {
  font-size: 13px;
  line-height: 1.65;
  padding-block: 6px;
}

.toc-item:hover, .toc-active {
  transform: none;
  color: var(--accent-color);
}

.toc-level-2 {
  padding-left: 0;
}

.toc-level-3 {
  padding-left: 12px;
}

.toc-level-4 {
  padding-left: 24px;
}

.toc-level-5 {
  padding-left: 36px;
}

.toc-level-6 {
  padding-left: 48px;
}

.toc-item > span:first-child {
  min-width: 0;
  overflow-wrap: anywhere;
}

.toc-card {
  overflow-x: hidden;
  scrollbar-width: thin;
}

.article-card {
  padding-top: 0;
}

.article-masthead {
  padding-bottom: 12px;
  margin-bottom: 0;
  font-size: 11px;
  border-bottom: 1px solid var(--line-color);
  letter-spacing: .08em;
}

.article-header {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 16px 20px;
  padding: 22px 0 24px;
  margin-bottom: 28px;
  border-bottom: 1px solid var(--line-color);
}

.article-header::before {
  display: none;
}

.article-header h1 {
  grid-column: 1 / -1;
  margin: 0;
  font-size: clamp(28px, 2.8vw, 36px);
  line-height: 1.4;
  letter-spacing: -.02em;
  font-weight: 650;
}

.author-row {
  grid-column: 1;
  grid-row: 2;
  gap: 8px;
  font-size: 13px;
}

.article-summary {
  grid-column: 1 / -1;
  grid-row: 3;
  margin: 0;
  max-width: none;
  font-size: 17px;
  line-height: 1.75;
}

.article-header .tag-row {
  grid-column: 2;
  grid-row: 2;
  margin: 0;
  gap: 6px;
  align-content: center;
}

.article-preview-container {
  max-width: none;
  margin: 0;
}

.article-preview-container :deep(.vditor-preview-host.vditor-reset) {
  font-family: var(--font-sans);
  font-size: 17px;
  line-height: 1.85;
  letter-spacing: 0;
  overflow-wrap: anywhere;
}

.article-preview-container :deep(.vditor-preview-host > p) {
  margin-bottom: 1em;
}

.article-preview-container :deep(.vditor-preview-host h1) {
  font-size: 28px;
  margin-block: 1.1em .55em;
}

.article-preview-container :deep(.vditor-preview-host h2) {
  font-size: 23px;
  margin-block: 1.6em .65em;
  padding-bottom: .4em;
  font-weight: 600;
}

.article-preview-container :deep(.vditor-preview-host h3) {
  font-size: 19px;
  margin-block: 1.2em .5em;
}

.article-preview-container :deep(.vditor-preview-host > :first-child) {
  margin-top: 0;
}

.article-preview-container :deep(.vditor-preview-host pre) {
  letter-spacing: 0;
}

.article-preview-container :deep(.vditor-preview-host figure.vditor-image) {
  margin-block: 1.3em;
}

@media (max-width:1100px) {
  .detail-shell {
    grid-template-columns: minmax(0, 1fr);
    max-width: 800px;
  }
  .detail-content {
    grid-column: 1;
    grid-row: 1;
  }
  .detail-sidebar {
    grid-column: 1;
    grid-row: 2;
    position: static;
    border-left: 0;
    border-top: 1px solid var(--line-color);
    padding: 24px 0 0;
  }
  .author-card {
    display: grid;
    grid-template-columns: 48px 1fr;
    gap: 0 14px;
  }
  .author-avatar {
    grid-row: span 2;
  }
  .author-card h2 {
    margin: 0;
  }
  .visit-btn {
    justify-self: start;
  }
  .toc-card {
    max-height: none;
  }
}

@media (max-width:640px) {
  .public-detail-page {
    padding: 0 0 44px;
  }
  .detail-shell { width: calc(100% - 36px); margin-top: 22px; }
  .article-card {
    padding: 0 0 30px;
  }
  .article-header {
    padding: 20px 0;
    margin-bottom: 24px;
    gap: 14px;
  }
  .article-header h1 {
    font-size: 27px;
  }
  .author-row {
    flex-wrap: wrap;
    font-size: 12px;
  }
  .article-header .tag-row {
    grid-column: 1 / -1;
    grid-row: 3;
  }
  .article-summary {
    grid-row: 4;
    font-size: 16px;
  }
  .article-preview-container :deep(.vditor-preview-host.vditor-reset) {
    font-size: 16px;
    line-height: 1.8;
  }
}

@media print {
  .article-header {
    background: none;
    padding: 16px 0;
  }
  .editorial-surface::before, .editorial-surface::after {
    display: none;
  }
}
</style>
