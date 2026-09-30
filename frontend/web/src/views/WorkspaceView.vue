<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import {
  NButton,
  NAvatar,
  NCheckbox,
  NEmpty,
  NForm,
  NFormItem,
  NIcon,
  NInput,
  NInputNumber,
  NModal,
  NPagination,
  NSpace,
  NSpin,
  NTag,
  NTree,
  NTreeSelect,
  useDialog,
  useMessage,
} from 'naive-ui'
import {
  Add,
  BookOutline,
  CreateOutline,
  DocumentTextOutline,
  HeartOutline,
  FolderOpenOutline,
  KeyOutline,
  MoveOutline,
  PencilOutline,
  TrashOutline,
} from '@vicons/ionicons5'
import { createPrivateBlog } from '@/api/blog'
import { createAgentToken, deleteAgentToken, getAgentTokenPage } from '@/api/agent'
import {
  createFolder,
  deleteFolder,
  getCurrentUserFolderTree,
  getFolderBlogPage,
  moveBlogsToFolder,
  moveFolder,
  renameFolder,
} from '@/api/folder'
import type { AgentTokenVO, ApiId, BlogPrivateBriefVO, FolderTree } from '@/types'
import { ROOT_FOLDER_ID } from '@/types'
import { DateUtils } from '@/types/date'
import { resolveAvatarUrl } from '@/utils/avatar'

interface TreeNode {
  [key: string]: unknown
  label: string
  key: string
  children?: TreeNode[]
}

type FolderModalMode = 'create' | 'rename'
type WorkspaceModule = 'blogs' | 'agent'

const router = useRouter()
const message = useMessage()
const dialog = useDialog()

const treeLoading = ref(false)
const blogsLoading = ref(false)
const folderTree = ref<FolderTree | null>(null)
const selectedFolderId = ref(String(ROOT_FOLDER_ID))
const selectedBlogIds = ref<string[]>([])
const blogs = ref<BlogPrivateBriefVO[]>([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(10)
const activeModule = ref<WorkspaceModule>('blogs')

const folderModal = reactive({
  show: false,
  mode: 'create' as FolderModalMode,
  targetId: String(ROOT_FOLDER_ID),
  name: '',
})
const moveFolderModal = reactive({
  show: false,
  folderId: '',
  targetParentId: String(ROOT_FOLDER_ID),
})
const moveBlogsModal = reactive({
  show: false,
  targetId: String(ROOT_FOLDER_ID),
})
const createBlogModal = reactive({
  show: false,
  title: '',
})
const agentLoading = ref(false)
const agentTokens = ref<AgentTokenVO[]>([])
const agentTotal = ref(0)
const agentPage = ref(1)
const agentPageSize = 5
const agentModal = reactive({
  show: false,
  name: '',
  expire: 30,
})
const createdAgentToken = ref('')
const showCreatedAgentToken = ref(false)

const rootFolder = computed<FolderTree>(() => {
  if (folderTree.value && String(folderTree.value.id) === String(ROOT_FOLDER_ID)) {
    return {
      ...folderTree.value,
      name: '根目录',
    }
  }

  return {
    id: ROOT_FOLDER_ID,
    name: '根目录',
    level: 0,
    sortOrder: 0,
    children: folderTree.value ? [folderTree.value] : [],
  }
})

const treeOptions = computed<TreeNode[]>(() => [toTreeNode(rootFolder.value)])
const folderSelectOptions = computed<TreeNode[]>(() => treeOptions.value)
const currentFolder = computed(() => findFolder(rootFolder.value, selectedFolderId.value) ?? rootFolder.value)
const childFolders = computed(() => currentFolder.value.children ?? [])
const breadcrumbs = computed(() => findFolderPath(rootFolder.value, selectedFolderId.value) ?? [rootFolder.value])
const checkedAll = computed({
  get: () => blogs.value.length > 0 && selectedBlogIds.value.length === blogs.value.length,
  set: (checked: boolean) => {
    selectedBlogIds.value = checked ? blogs.value.map((blog) => String(blog.id)) : []
  },
})

function toTreeNode(folder: FolderTree): TreeNode {
  return {
    label: getFolderName(folder),
    key: String(folder.id),
    children: folder.children?.map(toTreeNode),
  }
}

function getFolderName(folder: FolderTree): string {
  return String(folder.id) === String(ROOT_FOLDER_ID) ? '根目录' : folder.name
}

function findFolder(folder: FolderTree, id: string): FolderTree | null {
  if (String(folder.id) === id) {
    return folder
  }

  for (const child of folder.children ?? []) {
    const result = findFolder(child, id)
    if (result) {
      return result
    }
  }

  return null
}

function findFolderPath(folder: FolderTree, id: string, path: FolderTree[] = []): FolderTree[] | null {
  const nextPath = [...path, folder]
  if (String(folder.id) === id) {
    return nextPath
  }

  for (const child of folder.children ?? []) {
    const result = findFolderPath(child, id, nextPath)
    if (result) {
      return result
    }
  }

  return null
}

function isDescendant(parentId: string, candidateId: string): boolean {
  const parent = findFolder(rootFolder.value, parentId)
  if (!parent) {
    return false
  }
  return Boolean(parent.children?.some((child) => String(child.id) === candidateId || isDescendant(String(child.id), candidateId)))
}

async function loadTree(): Promise<void> {
  treeLoading.value = true
  try {
    folderTree.value = await getCurrentUserFolderTree()
  } catch (error) {
    message.error('目录树加载失败')
  } finally {
    treeLoading.value = false
  }
}

async function loadBlogs(): Promise<void> {
  blogsLoading.value = true
  selectedBlogIds.value = []
  try {
    const page = await getFolderBlogPage(selectedFolderId.value, {
      pageNum: pageNum.value,
      pageSize: pageSize.value,
      sortField: 'updateTime',
      sortOrder: 'desc',
    })
    blogs.value = page.records
    total.value = page.total
  } catch (error) {
    message.error('博客列表加载失败')
  } finally {
    blogsLoading.value = false
  }
}

async function loadAgentTokens(): Promise<void> {
  agentLoading.value = true
  try {
    const page = await getAgentTokenPage({
      pageNum: agentPage.value,
      pageSize: agentPageSize,
      sortField: 'updateTime',
      sortOrder: 'desc',
    })
    agentTokens.value = page.records
    agentTotal.value = page.total
  } catch (error) {
    message.error('Agent Token 加载失败')
  } finally {
    agentLoading.value = false
  }
}

function selectModule(module: WorkspaceModule): void {
  activeModule.value = module
  if (module === 'agent') {
    loadAgentTokens()
  }
}

function selectFolder(id: ApiId): void {
  selectedFolderId.value = String(id)
}

function openCreateFolder(parentId = selectedFolderId.value): void {
  folderModal.mode = 'create'
  folderModal.targetId = parentId
  folderModal.name = ''
  folderModal.show = true
}

function openRenameFolder(folder: FolderTree): void {
  if (String(folder.id) === String(ROOT_FOLDER_ID)) {
    return
  }
  folderModal.mode = 'rename'
  folderModal.targetId = String(folder.id)
  folderModal.name = folder.name
  folderModal.show = true
}

async function submitFolderModal(): Promise<void> {
  const name = folderModal.name.trim()
  if (!name) {
    message.warning('请输入目录名')
    return
  }

  try {
    if (folderModal.mode === 'create') {
      await createFolder({ parentId: folderModal.targetId, name })
      message.success('目录已创建')
    } else {
      await renameFolder(folderModal.targetId, { name })
      message.success('目录已重命名')
    }
    folderModal.show = false
    await loadTree()
  } catch (error) {
    message.error(folderModal.mode === 'create' ? '创建目录失败' : '重命名失败')
  }
}

function confirmDeleteFolder(folder: FolderTree): void {
  if (String(folder.id) === String(ROOT_FOLDER_ID)) {
    return
  }

  dialog.warning({
    title: '删除目录',
    content: '该目录及其子目录会被删除，目录内博客将由后端移动到根目录。确认继续？',
    positiveText: '删除',
    negativeText: '取消',
    onPositiveClick: async () => {
      try {
        await deleteFolder(folder.id)
        message.success('目录已删除')
        if (String(folder.id) === selectedFolderId.value) {
          selectedFolderId.value = String(ROOT_FOLDER_ID)
        }
        await loadTree()
        await loadBlogs()
      } catch (error) {
        message.error('删除目录失败')
      }
    },
  })
}

function openMoveFolder(folder: FolderTree): void {
  if (String(folder.id) === String(ROOT_FOLDER_ID)) {
    return
  }
  moveFolderModal.folderId = String(folder.id)
  moveFolderModal.targetParentId = String(ROOT_FOLDER_ID)
  moveFolderModal.show = true
}

async function submitMoveFolder(): Promise<void> {
  if (moveFolderModal.folderId === moveFolderModal.targetParentId || isDescendant(moveFolderModal.folderId, moveFolderModal.targetParentId)) {
    message.warning('不能移动到自身或子目录下')
    return
  }

  try {
    await moveFolder(moveFolderModal.folderId, { targetParentId: moveFolderModal.targetParentId })
    message.success('目录已移动')
    moveFolderModal.show = false
    await loadTree()
  } catch (error) {
    message.error('移动目录失败')
  }
}

async function submitCreateBlog(): Promise<void> {
  const title = createBlogModal.title.trim()
  if (!title) {
    message.warning('请输入标题')
    return
  }

  try {
    const id = await createPrivateBlog({
      title,
      folderId: selectedFolderId.value,
      coverUrl: null,
    })
    createBlogModal.show = false
    createBlogModal.title = ''
    router.push(`/workspace/blogs/${id}`)
  } catch (error) {
    message.error('创建博客失败')
  }
}

function openAgentTokenModal(): void {
  agentModal.name = ''
  agentModal.expire = 30
  agentModal.show = true
}

async function submitAgentToken(): Promise<void> {
  const name = agentModal.name.trim()
  if (!name) {
    message.warning('请输入 Token 名称')
    return
  }

  try {
    createdAgentToken.value = await createAgentToken({ name, expire: agentModal.expire })
    agentModal.show = false
    showCreatedAgentToken.value = true
    message.success('Agent Token 已创建')
    await loadAgentTokens()
  } catch (error) {
    message.error('Agent Token 创建失败')
  }
}

async function copyCreatedAgentToken(): Promise<void> {
  if (!createdAgentToken.value) return
  try {
    await navigator.clipboard.writeText(createdAgentToken.value)
    message.success('Token 已复制')
  } catch (error) {
    message.warning('复制失败，请手动复制')
  }
}

function confirmDeleteAgentToken(token: AgentTokenVO): void {
  dialog.warning({
    title: '删除 Agent Token',
    content: `确认删除「${token.name}」？删除后使用该 Token 的请求会立即失效。`,
    positiveText: '删除',
    negativeText: '取消',
    onPositiveClick: async () => {
      try {
        await deleteAgentToken(token.name)
        message.success('Agent Token 已删除')
        if (agentTokens.value.length === 1 && agentPage.value > 1) {
          agentPage.value -= 1
        } else {
          await loadAgentTokens()
        }
      } catch (error) {
        message.error('Agent Token 删除失败')
      }
    },
  })
}

function openMoveBlogs(): void {
  if (selectedBlogIds.value.length === 0) {
    message.warning('请先选择博客')
    return
  }
  moveBlogsModal.targetId = selectedFolderId.value
  moveBlogsModal.show = true
}

async function submitMoveBlogs(): Promise<void> {
  try {
    await moveBlogsToFolder({
      blogIds: selectedBlogIds.value,
      targetId: moveBlogsModal.targetId,
    })
    message.success('博客已移动')
    moveBlogsModal.show = false
    await loadBlogs()
  } catch (error) {
    message.error('移动博客失败')
  }
}

function toggleBlogChecked(id: ApiId, checked: boolean): void {
  const blogId = String(id)
  selectedBlogIds.value = checked
    ? Array.from(new Set([...selectedBlogIds.value, blogId]))
    : selectedBlogIds.value.filter((item) => item !== blogId)
}

watch(selectedFolderId, () => {
  pageNum.value = 1
  loadBlogs()
})

watch(pageNum, () => {
  loadBlogs()
})

watch(agentPage, () => {
  loadAgentTokens()
})

onMounted(async () => {
  await Promise.all([loadTree(), loadBlogs()])
})
</script>

<template>
  <div class="workspace-page">
    <div class="workspace-shell">
      <aside class="folder-pane">
        <div class="pane-header">
          <div>
            <span class="eyebrow">Workspace</span>
            <h1>工作台</h1>
          </div>
        </div>

        <nav class="workspace-module-nav" aria-label="工作台模块">
          <button
            type="button"
            class="workspace-module"
            :class="{ active: activeModule === 'blogs' }"
            @click="selectModule('blogs')"
          >
            <span class="workspace-module-icon"><n-icon :component="BookOutline" /></span>
            <span class="workspace-module-copy">
              <strong>博客</strong>
              <small>目录与文章</small>
            </span>
            <n-icon class="workspace-module-arrow" :component="FolderOpenOutline" />
          </button>
          <button
            type="button"
            class="workspace-module"
            :class="{ active: activeModule === 'agent' }"
            @click="selectModule('agent')"
          >
            <span class="workspace-module-icon"><n-icon :component="KeyOutline" /></span>
            <span class="workspace-module-copy">
              <strong>Agent Token</strong>
              <small>{{ agentTotal ? `${agentTotal} 个凭证` : '访问凭证' }}</small>
            </span>
            <n-icon class="workspace-module-arrow" :component="KeyOutline" />
          </button>
        </nav>

        <div v-if="activeModule === 'blogs'" class="folder-browser">
          <div class="folder-browser-heading">
            <span>目录</span>
            <n-button circle secondary size="small" @click="openCreateFolder()">
              <template #icon><n-icon :component="Add" /></template>
            </n-button>
          </div>
          <n-spin :show="treeLoading">
            <n-tree
              block-line
              block-node
              selectable
              default-expand-all
              :data="treeOptions"
              :selected-keys="[selectedFolderId]"
              @update:selected-keys="(keys) => selectFolder(keys[0] || ROOT_FOLDER_ID)"
            />
          </n-spin>
        </div>
      </aside>

      <main class="content-pane">
        <template v-if="activeModule === 'blogs'">
          <section class="content-header">
          <div class="breadcrumbs">
            <button
              v-for="(folder, index) in breadcrumbs"
              :key="folder.id"
              type="button"
              @click="selectFolder(folder.id)"
            >
              {{ getFolderName(folder) }}
              <span v-if="index < breadcrumbs.length - 1">/</span>
            </button>
          </div>

          <n-space wrap>
            <n-button secondary @click="openCreateFolder()">
              <template #icon><n-icon :component="FolderOpenOutline" /></template>
              新建目录
            </n-button>
            <n-button type="primary" @click="createBlogModal.show = true">
              <template #icon><n-icon :component="CreateOutline" /></template>
              新建博客
            </n-button>
          </n-space>
          </section>

          <section class="library-section">
          <div class="library-head">
            <div class="section-title">
              <span>Contents</span>
              <small>{{ childFolders.length }} folders / {{ total }} blogs</small>
            </div>
            <n-space>
              <n-button secondary :disabled="selectedBlogIds.length === 0" @click="openMoveBlogs">
                <template #icon><n-icon :component="MoveOutline" /></template>
                移动
              </n-button>
            </n-space>
          </div>

          <n-spin :show="blogsLoading">
            <div class="library-content">
              <div v-if="childFolders.length" class="library-zone folder-zone">
                <div class="folder-grid">
                  <article
                    v-for="folder in childFolders"
                    :key="folder.id"
                    class="folder-card"
                    @click="selectFolder(folder.id)"
                  >
                    <div class="folder-main">
                      <n-icon class="folder-icon" :component="FolderOpenOutline" />
                      <div>
                        <h3>{{ getFolderName(folder) }}</h3>
                        <p>{{ folder.children?.length || 0 }} subfolders</p>
                      </div>
                    </div>
                    <div class="folder-actions" @click.stop>
                      <n-button quaternary circle size="small" @click="openCreateFolder(String(folder.id))">
                        <template #icon><n-icon :component="Add" /></template>
                      </n-button>
                      <n-button quaternary circle size="small" @click="openRenameFolder(folder)">
                        <template #icon><n-icon :component="PencilOutline" /></template>
                      </n-button>
                      <n-button quaternary circle size="small" @click="openMoveFolder(folder)">
                        <template #icon><n-icon :component="MoveOutline" /></template>
                      </n-button>
                      <n-button quaternary circle size="small" type="error" @click="confirmDeleteFolder(folder)">
                        <template #icon><n-icon :component="TrashOutline" /></template>
                      </n-button>
                    </div>
                  </article>
                </div>
              </div>

              <div v-if="blogs.length" class="library-zone blog-zone">
                <div class="blog-table">
                  <div class="blog-table-head">
                    <n-checkbox v-model:checked="checkedAll" />
                    <span>Blogs</span>
                  </div>
                  <div
                    v-for="blog in blogs"
                    :key="blog.id"
                    class="blog-row"
                    @click="router.push(`/workspace/blogs/${blog.id}`)"
                  >
                    <n-checkbox
                      :checked="selectedBlogIds.includes(String(blog.id))"
                      @click.stop
                      @update:checked="(checked) => toggleBlogChecked(blog.id, checked)"
                    />
                    <div class="blog-row-content">
                      <div class="blog-main-line">
                        <div class="blog-title-cell">
                          <n-icon :component="DocumentTextOutline" />
                          <h3>{{ blog.title }}</h3>
                        </div>
                        <n-avatar
                          round
                          size="small"
                          :src="resolveAvatarUrl(blog.author.avatarUrl)"
                          class="blog-author-avatar"
                        />
                        <span class="blog-like-count">
                          <n-icon :component="HeartOutline" />
                          {{ blog.likeCount || 0 }}
                        </span>
                      </div>
                      <p class="blog-summary-line">{{ blog.summary || 'No summary' }}</p>
                      <div class="blog-meta-line">
                        <div class="blog-tags">
                          <n-tag
                            v-for="tag in blog.tags"
                            :key="tag.id"
                            size="small"
                            round
                          >
                            {{ tag.name }}
                          </n-tag>
                          <n-tag :type="blog.isPublished === 1 ? 'success' : 'default'" size="small" round>
                            {{ blog.isPublished === 1 ? 'Public' : 'Private' }}
                          </n-tag>
                        </div>
                        <span class="blog-date">{{ DateUtils.isoToDateOnly(blog.updateTime || blog.createTime) }}</span>
                      </div>
                    </div>
                  </div>

                  <div class="pagination-wrap">
                    <n-pagination
                      v-model:page="pageNum"
                      :page-size="pageSize"
                      :item-count="total"
                    />
                  </div>
                </div>
              </div>

              <n-empty
                v-if="!childFolders.length && !blogs.length"
                class="empty-library"
                description="当前目录暂无内容"
              />
            </div>
          </n-spin>
          </section>
        </template>

        <section v-else class="agent-section">
          <div class="agent-heading">
            <div>
              <span class="eyebrow">Authorization</span>
              <h2>Agent Token</h2>
              <p>为 AI Agent 创建访问你个人博客与目录的凭证。</p>
            </div>
            <n-button type="primary" @click="openAgentTokenModal">
              <template #icon><n-icon :component="Add" /></template>
              创建 Token
            </n-button>
          </div>

          <n-spin :show="agentLoading">
            <div v-if="agentTokens.length" class="agent-token-list">
              <div v-for="token in agentTokens" :key="token.name" class="agent-token-row">
                <div class="agent-token-main">
                  <div class="agent-token-mark"><n-icon :component="KeyOutline" /></div>
                  <div>
                    <strong>{{ token.name }}</strong>
                    <code>{{ token.token }}••••••••</code>
                  </div>
                </div>
                <div class="agent-token-meta">
                  <n-tag size="small" round>{{ token.expire === -1 ? '永久' : `${token.expire} 天` }}</n-tag>
                  <n-button size="small" quaternary type="error" @click="confirmDeleteAgentToken(token)">删除</n-button>
                </div>
              </div>
            </div>
            <n-empty v-else description="还没有 Agent Token" />
            <div class="pagination-wrap agent-pagination" v-if="agentTotal > agentPageSize">
              <n-pagination v-model:page="agentPage" :page-size="agentPageSize" :item-count="agentTotal" />
            </div>
          </n-spin>
        </section>
      </main>
    </div>

    <n-modal v-model:show="folderModal.show">
      <div class="workspace-modal">
        <h2>{{ folderModal.mode === 'create' ? '新建目录' : '重命名目录' }}</h2>
        <n-form label-placement="top">
          <n-form-item label="Name">
            <n-input v-model:value="folderModal.name" @keyup.enter="submitFolderModal" />
          </n-form-item>
        </n-form>
        <div class="modal-actions">
          <n-button @click="folderModal.show = false">取消</n-button>
          <n-button type="primary" @click="submitFolderModal">确认</n-button>
        </div>
      </div>
    </n-modal>

    <n-modal v-model:show="moveFolderModal.show">
      <div class="workspace-modal">
        <h2>移动目录</h2>
        <n-form label-placement="top">
          <n-form-item label="Target">
            <n-tree-select
              v-model:value="moveFolderModal.targetParentId"
              :options="folderSelectOptions"
              :to="false"
              default-expand-all
            />
          </n-form-item>
        </n-form>
        <div class="modal-actions">
          <n-button @click="moveFolderModal.show = false">取消</n-button>
          <n-button type="primary" @click="submitMoveFolder">移动</n-button>
        </div>
      </div>
    </n-modal>

    <n-modal v-model:show="moveBlogsModal.show">
      <div class="workspace-modal">
        <h2>移动博客</h2>
        <n-form label-placement="top">
          <n-form-item label="Target">
            <n-tree-select
              v-model:value="moveBlogsModal.targetId"
              :options="folderSelectOptions"
              :to="false"
              default-expand-all
            />
          </n-form-item>
        </n-form>
        <div class="modal-actions">
          <n-button @click="moveBlogsModal.show = false">取消</n-button>
          <n-button type="primary" @click="submitMoveBlogs">移动</n-button>
        </div>
      </div>
    </n-modal>

    <n-modal v-model:show="createBlogModal.show">
      <div class="workspace-modal">
        <h2>新建博客</h2>
        <n-form label-placement="top">
          <n-form-item label="Title">
            <n-input v-model:value="createBlogModal.title" :maxlength="20" show-count @keyup.enter="submitCreateBlog" />
          </n-form-item>
        </n-form>
        <div class="modal-actions">
          <n-button @click="createBlogModal.show = false">取消</n-button>
          <n-button type="primary" @click="submitCreateBlog">创建</n-button>
        </div>
      </div>
    </n-modal>

    <n-modal v-model:show="agentModal.show">
      <div class="workspace-modal agent-modal">
        <h2>创建 Agent Token</h2>
        <p class="modal-help">Token 创建后不可修改，如需调整期限请删除后重新创建。</p>
        <n-form label-placement="top">
          <n-form-item label="名称">
            <n-input v-model:value="agentModal.name" maxlength="20" show-count placeholder="例如：我的写作助手" @keyup.enter="submitAgentToken" />
          </n-form-item>
          <n-form-item label="有效期">
            <n-input-number v-model:value="agentModal.expire" :min="-1" :max="3650" :precision="0" :show-button="false" style="width: 100%">
              <template #suffix>天，-1 为永久</template>
            </n-input-number>
          </n-form-item>
        </n-form>
        <div class="modal-actions">
          <n-button @click="agentModal.show = false">取消</n-button>
          <n-button type="primary" @click="submitAgentToken">创建</n-button>
        </div>
      </div>
    </n-modal>

    <n-modal v-model:show="showCreatedAgentToken">
      <div class="workspace-modal agent-modal">
        <h2>Token 已创建</h2>
        <p class="modal-help">完整 Token 只在创建时显示一次，请立即复制并妥善保存。</p>
        <div class="created-token-box"><code>{{ createdAgentToken }}</code></div>
        <div class="modal-actions">
          <n-button @click="showCreatedAgentToken = false">关闭</n-button>
          <n-button type="primary" @click="copyCreatedAgentToken">复制 Token</n-button>
        </div>
      </div>
    </n-modal>
  </div>
</template>

<style scoped>


.workspace-page {
  min-height: 100vh;
  background: var(--bg-primary);
  color: var(--text-primary);
  padding: 24px 28px 64px;
}

.workspace-shell {
  width: min(1440px, 100%);
  margin: 0 auto;
  display: grid;
  align-items: start;
  grid-template-columns: 240px minmax(0, 1fr);
  gap: 24px;
}

.folder-pane,
.content-pane section,
.workspace-modal {
  background: var(--bg-primary);
  border: 1px solid var(--line-color);
}

.folder-pane {
  position: sticky;
  top: 92px;
  padding: 24px;
  max-height: calc(100vh - 120px);
  overflow: auto;
  border-radius: 20px;
  background: var(--bg-secondary);
  border: 0;
}

.pane-header,
.content-header,
.library-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
}

.eyebrow {
  color: var(--accent-color);
  font-family: var(--font-sans);
  font-size: 0.72rem;
  font-weight: 700;
  letter-spacing: 0;
  text-transform: uppercase;
}

.pane-header h1 {
  margin: 4px 0 0;
  font-family: var(--font-body);
  color: var(--text-primary);
  font-size: 26px;
  font-weight: 600;
}

.workspace-module-nav {
  display: grid;
  gap: 8px;
  margin: 28px 0 24px;
}

.workspace-module {
  display: grid;
  grid-template-columns: 34px minmax(0, 1fr) auto;
  align-items: center;
  gap: 10px;
  width: 100%;
  padding: 11px 12px;
  border: 1px solid transparent;
  background: transparent;
  color: var(--text-secondary);
  text-align: left;
  cursor: pointer;
  transition: border-color 0.2s ease, background 0.2s ease, color 0.2s ease;
  border-radius: 18px;
}

.workspace-module:hover,
.workspace-module.active {
  border-color: var(--line-color);
  background: var(--card-hover);
  color: var(--text-primary);
}

.workspace-module.active {
  border-color: var(--accent-color);
}

.workspace-module-icon {
  display: grid;
  width: 32px;
  height: 32px;
  place-items: center;
  border: 1px solid currentColor;
  color: var(--accent-color);
}

.workspace-module-copy {
  display: grid;
  gap: 2px;
  min-width: 0;
}

.workspace-module-copy strong {
  overflow: hidden;
  color: inherit;
  font-size: 0.9rem;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.workspace-module-copy small {
  overflow: hidden;
  color: var(--text-tertiary);
  font-size: 0.74rem;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.workspace-module-arrow {
  color: var(--text-tertiary);
  font-size: 0.95rem;
}

.folder-browser {
  padding-top: 18px;
  border-top: 1px solid var(--line-color);
}

.folder-browser-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
  color: var(--text-tertiary);
  font-size: 0.72rem;
  font-weight: 700;
  letter-spacing: 0;
  text-transform: uppercase;
}

.content-pane {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 0;
}

.content-pane section {
  padding: 24px;
  border-radius: 20px;
}

.content-header {
  margin-bottom: 22px;
}

.library-section {
  overflow: hidden;
  padding: 0 !important;
}

.agent-section {
  min-height: 100%;
}

.agent-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 18px;
  margin-bottom: 20px;
}

.agent-heading h2 {
  margin: 0;
  color: var(--text-primary);
  font-family: var(--font-body);
  font-size: 1.5rem;
}

.agent-heading p:not(.eyebrow) {
  margin: 8px 0 0;
  color: var(--text-secondary);
  font-size: 0.9rem;
}

.agent-token-list {
  display: grid;
  gap: 10px;
}

.agent-token-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
  padding: 14px 16px;
  border: 1px solid var(--line-color);
  background: var(--card-hover);
}

.agent-token-main,
.agent-token-meta {
  display: flex;
  align-items: center;
  gap: 12px;
}

.agent-token-mark {
  display: grid;
  width: 34px;
  height: 34px;
  place-items: center;
  border: 1px solid var(--accent-color);
  color: var(--accent-color);
}

.agent-token-main strong,
.agent-token-main code {
  display: block;
}

.agent-token-main code {
  margin-top: 4px;
  color: var(--text-tertiary);
  font-size: 0.78rem;
}

.agent-pagination {
  padding: 18px 0 0;
}

.modal-help {
  margin: -10px 0 22px;
  color: var(--text-secondary);
  font-size: 0.88rem;
  line-height: 1.6;
}

.created-token-box {
  overflow-x: auto;
  padding: 14px;
  border: 1px dashed var(--accent-color);
  background: var(--card-hover);
  color: var(--text-primary);
}

.created-token-box code {
  white-space: nowrap;
}

.breadcrumbs {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.breadcrumbs button {
  border: 0;
  background: transparent;
  color: var(--text-secondary);
  cursor: pointer;
  font-family: var(--font-sans);
  font-weight: 700;
}

.breadcrumbs button:hover {
  color: var(--accent-color);
}

.section-title {
  display: flex;
  align-items: baseline;
  gap: 10px;
  margin-bottom: 0;
  font-family: var(--font-body);
  font-size: 1.45rem;
  color: var(--text-primary);
}

.section-title small {
  color: var(--text-tertiary);
  font-style: italic;
}

.library-head {
  padding: 24px;
  border-bottom: 1px solid var(--line-color);
}

.library-content {
  min-height: 220px;
}

.library-zone {
  padding: 24px;
}

.folder-zone {
  background: var(--card-hover);
}

.blog-zone {
  background: var(--bg-primary);
}

.folder-zone + .blog-zone {
  border-top: 1px solid var(--line-color);
}

.folder-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(250px, 1fr));
  gap: 16px;
}

.folder-card {
  border: 1px solid var(--line-color);
  background: var(--bg-primary);
  padding: 18px;
  cursor: pointer;
  transition: all 0.25s ease;
  border-radius: 16px;
}

.folder-card:hover {
  border-color: var(--accent-color);
  transform: translateY(-4px);
  box-shadow: none;
}

.folder-main {
  display: flex;
  align-items: center;
  gap: 14px;
}

.folder-icon {
  color: var(--accent-color);
  font-size: 1.8rem;
}

.folder-card h3 {
  margin: 0;
  color: var(--text-primary);
  font-family: var(--font-body);
  font-size: 1.2rem;
}

.folder-card p {
  margin: 4px 0 0;
  color: var(--text-secondary);
  font-size: 0.86rem;
}

.folder-actions {
  display: flex;
  justify-content: flex-end;
  gap: 4px;
  margin-top: 16px;
}

.blog-table {
  background: transparent;
}

.blog-table-head,
.blog-row {
  display: grid;
  grid-template-columns: 34px minmax(0, 1fr);
  gap: 12px;
  align-items: stretch;
  padding: 12px 18px;
}

.blog-table-head {
  color: var(--text-tertiary);
  border-bottom: 1px solid var(--line-color);
  font-size: 0.72rem;
  font-weight: 700;
  letter-spacing: 0;
  text-transform: uppercase;
}

.blog-row {
  cursor: pointer;
  border-bottom: 1px solid var(--line-color);
  transition: background 0.2s ease;
}

.blog-row:last-child {
  border-bottom: 0;
}

.blog-row:hover {
  background: var(--card-hover);
}

.blog-row-content {
  display: grid;
  gap: 6px;
  min-width: 0;
}

.blog-main-line {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto auto;
  align-items: center;
  gap: 12px;
  min-width: 0;
}

.blog-title-cell {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.blog-title-cell > .n-icon {
  flex: 0 0 auto;
  color: var(--accent-color);
}

.blog-title-cell h3 {
  margin: 0;
  overflow: hidden;
  color: var(--text-primary);
  font-family: var(--font-body);
  font-size: 1.05rem;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.blog-author-avatar {
  flex: 0 0 auto;
}

.blog-like-count {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  color: var(--text-secondary);
  font-size: 0.84rem;
  white-space: nowrap;
}

.blog-like-count .n-icon {
  color: var(--accent-color);
}

.blog-summary-line {
  overflow: hidden;
  margin: 0;
  color: var(--text-secondary);
  font-size: 0.86rem;
  line-height: 1.35;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.blog-meta-line {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  min-width: 0;
}

.blog-tags {
  display: flex;
  align-items: center;
  flex-wrap: nowrap;
  gap: 6px;
  min-width: 0;
  overflow: hidden;
}

.blog-tags :deep(.n-tag) {
  flex: 0 0 auto;
}

.blog-date {
  flex: 0 0 auto;
  color: var(--text-tertiary);
  font-size: 0.78rem;
  white-space: nowrap;
}

.pagination-wrap {
  display: flex;
  justify-content: center;
  padding: 20px;
  border-top: 1px solid var(--line-color);
}

.empty-library {
  padding: 72px 24px;
}

.workspace-modal {
  width: min(92vw, 460px);
  padding: 26px;
  box-shadow: none;
  border-radius: 24px;
}

.workspace-modal h2 {
  margin: 0 0 22px;
  font-family: var(--font-body);
  font-size: 1.55rem;
  color: var(--text-primary);
}

.modal-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

@media (max-width: 1080px) {
  .workspace-shell {
    grid-template-columns: 1fr;
  }

  .folder-pane {
    position: static;
    max-height: none;
  }

  .blog-table-head {
    display: none;
  }

  .blog-row {
    grid-template-columns: 34px minmax(0, 1fr);
  }
}

@media (max-width: 680px) {
  .content-header,
  .library-head {
    align-items: stretch;
    flex-direction: column;
  }

  .agent-heading {
    align-items: stretch;
    flex-direction: column;
  }

  .blog-main-line {
    grid-template-columns: minmax(0, 1fr) auto auto;
    gap: 8px;
  }

}

</style>
