<script setup lang="ts">
import { ref, onMounted, onUnmounted, nextTick, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  NInput, NModal,
  NForm, NFormItem, NIcon, NEmpty, NSpin, useMessage, useDialog
} from 'naive-ui'
import { Search, Add, GridOutline, ArrowForwardOutline } from '@vicons/ionicons5'
import { createPrivateBlog, getPublicBlogPage } from '@/api/blog'
import ArticleCard from '@/components/ArticleCard.vue'
import { useInfiniteArticles } from '@/composables/useInfiniteArticles'
import type { BlogPageQuery } from '@/types'

import { useUserStore } from '@/stores/user'

const router = useRouter()
const route = useRoute()
const message = useMessage()
const dialog = useDialog()
const userStore = useUserStore()

const { items: blogList, loading, error, hasMore, loadMore, reset, dispose } = useInfiniteArticles(getPublicBlogPage)
const sentinel = ref<HTMLElement | null>(null)
let observer: IntersectionObserver | undefined
let active = true
async function fillViewport() {
  await nextTick()
  if (!active || loading.value || error.value || !hasMore.value || !sentinel.value) return
  if (sentinel.value.getBoundingClientRect().top < window.innerHeight + 320) {
    await loadMore()
    if (!error.value) void fillViewport()
  }
}
async function retry() { await loadMore(); void fillViewport() }
const showCreateModal = ref(false)

const handleCreateClick = () => {
  if (!userStore.isAuthenticated) {
    dialog.warning({
      title: '需要登录',
      content: '开启创作旅程前，请先登录您的账号。',
      positiveText: '去登录',
      negativeText: '再看看',
      onPositiveClick: () => {
        router.push('/login')
      }
    })
    return
  }
  showCreateModal.value = true
}

const searchForm = ref<BlogPageQuery>({
  pageNum: 1,
  pageSize: 9,
  keyword: typeof route.query.keyword === 'string' ? route.query.keyword : '',
  tagIds: typeof route.query.tagId === 'string' ? [route.query.tagId] : undefined,
  sortField: 'createTime',
  sortOrder: 'desc'
})

const createForm = ref({
  title: ''
})

const sortOptions = [
  { label: '最新发布', value: 'createTime' },
  { label: '最近更新', value: 'updateTime' }
]

const handleSearch = async () => {
  await reset({ ...searchForm.value, keyword: searchForm.value.keyword?.trim() || undefined })
  void fillViewport()
}

const handleCreateBlog = async () => {
  if (!createForm.value.title) {
    message.warning('请输入标题')
    return
  }
  try {
    const blogId = await createPrivateBlog({
      title: createForm.value.title,
      folderId: 0,
      coverUrl: null,
    })
    message.success('创建成功')
    showCreateModal.value = false
    createForm.value.title = ''
    router.push(`/workspace/blogs/${blogId}`)
  } catch (error) {
    message.error('创建失败')
  }
}

watch(() => route.query.create, (value) => { if(value === '1') { handleCreateClick(); const query = {...route.query}; delete query.create; router.replace({path:route.path,query}) } }, {immediate:true})
watch(() => [route.query.keyword, route.query.tagId], () => { searchForm.value.keyword = typeof route.query.keyword === 'string' ? route.query.keyword : ''; searchForm.value.tagIds = typeof route.query.tagId === 'string' ? [route.query.tagId] : undefined; handleSearch() })
onMounted(() => {
  observer = new IntersectionObserver(entries => { if(entries.some(entry => entry.isIntersecting)) void fillViewport() }, { rootMargin: '320px 0px' })
  if (sentinel.value) observer.observe(sentinel.value)
  void handleSearch()
})
onUnmounted(() => { active = false; observer?.disconnect(); dispose() })
</script>

<template>
  <div class="blog-list-page">
    <div class="archive-masthead"><span>PARA BBS</span><span>文章</span></div>
    <!-- Hero Header -->
    <div class="hero-section">
      <div class="hero-content">
<div class="archive-title">
<h1>文章</h1>
</div>
        <div class="search-bar-wrapper">
          <n-input
            v-model:value="searchForm.keyword"
            placeholder="搜索文章、作者或标签"
            class="hero-search-input custom-input"
            size="large"
            @keyup.enter="handleSearch"
          >
            <template #prefix>
              <n-icon :component="Search" class="search-icon" />
            </template>
          </n-input>
          <button class="search-btn" aria-label="搜索" @click="handleSearch">
            <n-icon :component="ArrowForwardOutline" />
          </button>
        </div>
      </div>

      <div class="hero-footer">
        <div class="footer-left">
          <div class="sort-selector">
            <button v-for="option in sortOptions" :key="option.value" class="sort-chip" :class="{ selected: searchForm.sortField === option.value }" :aria-pressed="searchForm.sortField === option.value" @click="searchForm.sortField = option.value; handleSearch()">{{ option.label }}</button>
          </div>
        </div>

        <button v-if="searchForm.tagIds?.length" class="cancel-btn" @click="router.push('/blog')">清除话题筛选</button>

        <div class="footer-right">
          <button class="create-btn" @click="handleCreateClick">
            <n-icon :component="Add" />
            写文章
          </button>
        </div>
      </div>
    </div>

    <!-- Content Section -->
    <div class="content-container">
      <n-spin :show="loading && !blogList.length">
        <div v-if="blogList.length > 0">
          <div class="blog-grid">
<div v-for="(blog, index) in blogList" :key="blog.id" class="archive-entry"><span class="archive-number" aria-hidden="true">{{ String(index + 1).padStart(2, '0') }}</span><ArticleCard :blog="blog" layout="archive" /></div>
</div>


        </div>

        <n-empty v-else-if="!loading && !error" description="没有找到相关文章。" class="empty-state">
           <template #icon>
             <n-icon :component="GridOutline" />
           </template>
        </n-empty>
      </n-spin>
      <div ref="sentinel" class="load-more" role="status"><span v-if="loading">正在加载…</span><button v-else-if="error" @click="retry">加载失败，点击重试</button><button v-else-if="hasMore" @click="retry">加载更多</button><span v-else-if="blogList.length">已显示全部文章</span></div>
    </div>

    <!-- Create Modal -->
    <n-modal v-model:show="showCreateModal">
      <div class="create-modal-content">
        <div class="modal-header">
          <h3>New Article</h3>
        </div>
        <div class="modal-body">
            <n-form :model="createForm" size="large" class="custom-form">
            <n-form-item label="Title" path="title">
                <n-input v-model:value="createForm.title" placeholder="Enter article title" class="custom-input" />
            </n-form-item>
            </n-form>
        </div>
        <div class="modal-actions">
            <button class="cancel-btn" @click="showCreateModal = false">Cancel</button>
            <button class="save-btn" @click="handleCreateBlog">Create</button>
        </div>
      </div>
    </n-modal>
  </div>
</template>

<style scoped>
.blog-list-page {
  max-width: 1040px;
  margin: auto;
  padding: 24px 48px 64px;
}

.hero-content {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(260px, 420px);
  align-items: stretch;
  gap: 22px;
}

.archive-title h1 {
  margin: 0;
}

.search-bar-wrapper {
  display: flex;
  gap: 12px;
  width: 100%;
  position: relative;
}

.hero-search-input {
  border-radius: 4px;
}

.search-btn, .create-btn, .save-btn {
  border: 0;
  border-radius: 24px;
  background: var(--accent-color);
  color: var(--on-accent);
  padding: 10px 20px;
  white-space: nowrap;
  cursor: pointer;
}

.hero-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
}

.sort-selector {
  display: flex;
  gap: 24px;
}

.create-btn {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  background: var(--accent-soft);
  color: var(--accent-color);
}

.blog-grid {
  display: grid;
}

.blog-grid :deep(.article-card + .article-card) {
  border-top: 1px solid transparent;
}

.blog-grid :deep(.article-card:hover) {
  background: var(--bg-secondary);
}

.pagination-container {
  display: flex;
  justify-content: center;
  padding-top: 28px;
}

.empty-state {
  padding: 80px 0;
}

.create-modal-content {
  width: min(480px,92vw);
  background: var(--bg-primary);
  border-radius: 24px;
  padding: 28px;
}

.modal-header h3 {
  font-size: 22px;
  margin: 0 0 24px;
}

.modal-actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}

.cancel-btn {
  border: 0;
  border-radius: 24px;
  background: var(--bg-secondary);
  color: var(--text-secondary);
  padding: 10px 20px;
  cursor: pointer;
}

@media (max-width:640px) {
  .blog-list-page {
    padding: 20px 18px 48px;
  }
  .hero-content {
    flex-direction: column;
    align-items: stretch;
    gap: 22px;
  }
  .search-bar-wrapper {
    width: 100%;
  }
  .hero-footer {
    gap: 12px;
    flex-wrap: wrap;
  }
}

.load-more {
  display: flex;
  justify-content: center;
  padding: 28px;
  color: var(--text-tertiary);
  font-size: 13px;
}

.load-more button {
  padding: 10px 20px;
  border: 0;
  border-radius: 20px;
  color: var(--accent-color);
  background: var(--bg-secondary);
  cursor: pointer;
}

.hero-search-input :deep(.n-input-wrapper) {
  padding-right: 62px;
}

.hero-search-input {
  --n-color: var(--bg-secondary) !important;
  --n-height: 54px !important;
}

.search-btn {
  position: absolute;
  right: 7px;
  top: 7px;
  width: 40px;
  height: 40px;
  padding: 0;
  display: grid;
  place-items: center;
  font-size: 22px;
}

.sort-chip {
  border: 0;
  border-bottom: 2px solid transparent;
  border-radius: 0;
  padding: 8px 0;
  background: transparent;
  color: var(--text-secondary);
  cursor: pointer;
  font-size: 13px;
}

.sort-chip.selected {
  color: var(--accent-color);
  border-bottom-color: var(--accent-color);
}

.archive-masthead {
  display: flex;
  justify-content: space-between;
  padding: 8px 0 16px;
  border-bottom: 1px solid var(--text-primary);
  font-size: 11px;
  letter-spacing: .2em;
  color: var(--text-secondary);
}

.archive-masthead span:first-child {
  color: var(--accent-color);
  font-weight: 650;
}

.hero-section {
  margin-top: 0;
  padding: 28px 0 20px;
  border-bottom: 3px double var(--text-primary);
}

.archive-title h1 {
  font-family: 'Songti SC', SimSun, Georgia, serif;
  font-size: 42px;
  font-weight: 500;
  letter-spacing: .02em;
}

.hero-footer {
  margin: 20px 0 0;
}

.blog-grid {
  margin-top: 28px;
  gap: 0;
}

.archive-entry {
  display: grid;
  grid-template-columns: 60px minmax(0, 1fr);
  gap: 20px;
  padding: 30px 0;
  border-bottom: 1px solid var(--line-color);
}

.archive-number {
  color: var(--accent-color);
  font-family: var(--font-sans);
  font-size: 36px;
  font-style: italic;
  line-height: 1.1;
  opacity: .7;
}

.archive-entry :deep(.article-card), .blog-grid :deep(.article-card:hover) {
  padding: 0;
  border: 0;
  background: transparent;
  border-radius: 0;
}

.archive-entry :deep(h2) {
  font-family: var(--font-sans);
  font-size: 24px;
  font-weight: 500;
}

@media (max-width:640px) {
  .hero-section {
    padding: 24px 0 20px;
  }
  .hero-content { grid-template-columns: minmax(0, 1fr); gap: 18px; }
  .archive-title h1 {
    font-size: 34px;
  }
  .archive-entry {
    grid-template-columns: 36px minmax(0, 1fr);
    gap: 12px;
    padding: 24px 0;
  }
  .archive-number {
    font-size: 28px;
  }
  .archive-entry :deep(h2) {
    font-size: 21px;
  }
}
</style>
