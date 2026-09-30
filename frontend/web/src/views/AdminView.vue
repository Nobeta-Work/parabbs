<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import {
  NAvatar,
  NButton,
  NCheckbox,
  NCheckboxGroup,
  NEmpty,
  NForm,
  NFormItem,
  NIcon,
  NInput,
  NModal,
  NPagination,
  NSelect,
  NSpace,
  NSpin,
  NSwitch,
  NTag,
  useDialog,
  useMessage,
} from 'naive-ui'
import {
  BookOutline,
  KeyOutline,
  PeopleOutline,
  PricetagOutline,
  RefreshOutline,
  ShieldCheckmarkOutline,
  SettingsOutline,
  CreateOutline,
  TrashOutline,
} from '@vicons/ionicons5'
import {
  banAdminUser,
  createAdminPerm,
  createAdminRole,
  createAdminTag,
  deleteAdminBlog,
  getAdminBlogPage,
  getAdminPermPage,
  getAdminRolePage,
  getAdminTagPage,
  getAdminUserPage,
  updateAdminBlogStatus,
  updateAdminPerm,
  updateAdminRole,
  updateAdminTag,
  updateAdminUser,
} from '@/api/admin'
import type {
  AdminBlogVO,
  AdminPermVO,
  AdminRoleVO,
  AdminTagVO,
  AdminUserVO,
  ApiId,
  PageResult,
} from '@/types'
import { DateUtils } from '@/types/date'
import { resolveAvatarUrl } from '@/utils/avatar'

type AdminSection = 'users' | 'blogs' | 'tags' | 'roles' | 'perms'
type ModalMode = 'create' | 'edit'

const message = useMessage()
const dialog = useDialog()

const pageSize = 10
const activeSection = ref<AdminSection>('users')
const userPage = ref(1)
const blogPage = ref(1)
const tagPage = ref(1)
const rolePage = ref(1)
const permPage = ref(1)

const emptyPage = <T>(): PageResult<T> => ({
  total: 0,
  pageNum: 1,
  pageSize,
  pages: 0,
  records: [],
})

const users = ref(emptyPage<AdminUserVO>())
const blogs = ref(emptyPage<AdminBlogVO>())
const tags = ref(emptyPage<AdminTagVO>())
const roles = ref(emptyPage<AdminRoleVO>())
const perms = ref({ ...emptyPage<AdminPermVO>(), pageSize: 100 })

const loading = reactive({
  users: false,
  blogs: false,
  tags: false,
  roles: false,
  perms: false,
})

const userKeyword = ref('')
const userStatus = ref<number | null>(null)
const blogKeyword = ref('')
const blogAuthorId = ref('')
const blogStatus = ref<number | null>(null)
const tagKeyword = ref('')
const roleKeyword = ref('')
const permKeyword = ref('')

const userStatusOptions = [
  { label: '正常', value: 1 },
  { label: '已封禁', value: 0 },
]
const blogStatusOptions = [
  { label: '公开', value: 1 },
  { label: '私有', value: 0 },
]

const sectionItems = [
  { key: 'users' as const, label: '用户', icon: PeopleOutline, count: () => users.value.total },
  { key: 'blogs' as const, label: '博客', icon: BookOutline, count: () => blogs.value.total },
  { key: 'tags' as const, label: '标签', icon: PricetagOutline, count: () => tags.value.total },
  { key: 'roles' as const, label: '角色', icon: ShieldCheckmarkOutline, count: () => roles.value.total },
  { key: 'perms' as const, label: '权限', icon: KeyOutline, count: () => perms.value.total },
]

const roleOptions = computed(() => roles.value.records.map((role) => ({
  label: `${role.roleName} (${role.roleCode})`,
  value: String(role.id),
})))

const userModal = reactive({
  show: false,
  id: '',
  nickname: '',
  status: 1 as 0 | 1,
  roleIds: [] as string[],
})

const tagModal = reactive({
  show: false,
  mode: 'create' as ModalMode,
  id: '',
  name: '',
  description: '',
})

const roleModal = reactive({
  show: false,
  mode: 'create' as ModalMode,
  id: '',
  roleCode: '',
  roleName: '',
  description: '',
  permIds: [] as string[],
})

const permModal = reactive({
  show: false,
  mode: 'create' as ModalMode,
  id: '',
  permCode: '',
  permName: '',
  description: '',
})

function queryValue(value: string): string | undefined {
  const normalized = value.trim()
  return normalized || undefined
}

function dateOnly(value: string | null | undefined): string {
  return value ? DateUtils.isoToDateOnly(value) : '-'
}

async function loadUsers(): Promise<void> {
  loading.users = true
  try {
    users.value = await getAdminUserPage({
      pageNum: userPage.value,
      pageSize,
      keyword: queryValue(userKeyword.value),
      status: userStatus.value === null ? undefined : userStatus.value as 0 | 1,
    })
  } finally {
    loading.users = false
  }
}

async function loadBlogs(): Promise<void> {
  loading.blogs = true
  try {
    blogs.value = await getAdminBlogPage({
      pageNum: blogPage.value,
      pageSize,
      keyword: queryValue(blogKeyword.value),
      authorId: queryValue(blogAuthorId.value),
      isPublished: blogStatus.value === null ? undefined : blogStatus.value as 0 | 1,
    })
  } finally {
    loading.blogs = false
  }
}

async function loadTags(): Promise<void> {
  loading.tags = true
  try {
    tags.value = await getAdminTagPage({
      pageNum: tagPage.value,
      pageSize,
      keyword: queryValue(tagKeyword.value),
    })
  } finally {
    loading.tags = false
  }
}

async function loadRoles(): Promise<void> {
  loading.roles = true
  try {
    roles.value = await getAdminRolePage({
      pageNum: rolePage.value,
      pageSize,
      keyword: queryValue(roleKeyword.value),
    })
  } finally {
    loading.roles = false
  }
}

async function loadPerms(): Promise<void> {
  loading.perms = true
  try {
    perms.value = await getAdminPermPage({
      pageNum: permPage.value,
      pageSize: 100,
      keyword: queryValue(permKeyword.value),
    })
  } finally {
    loading.perms = false
  }
}

async function loadAll(): Promise<void> {
  await Promise.all([loadUsers(), loadBlogs(), loadTags(), loadRoles(), loadPerms()])
}

function searchUsers(): void {
  userPage.value = 1
  loadUsers()
}

function searchBlogs(): void {
  blogPage.value = 1
  loadBlogs()
}

function searchTags(): void {
  tagPage.value = 1
  loadTags()
}

function searchRoles(): void {
  rolePage.value = 1
  loadRoles()
}

function searchPerms(): void {
  permPage.value = 1
  loadPerms()
}

function refreshCurrent(): void {
  if (activeSection.value === 'users') loadUsers()
  if (activeSection.value === 'blogs') loadBlogs()
  if (activeSection.value === 'tags') loadTags()
  if (activeSection.value === 'roles') loadRoles()
  if (activeSection.value === 'perms') loadPerms()
}

function openUserModal(user: AdminUserVO): void {
  userModal.show = true
  userModal.id = String(user.id)
  userModal.nickname = user.nickname
  userModal.status = user.status
  userModal.roleIds = user.roleCodes
    .map((code) => roles.value.records.find((role) => role.roleCode === code)?.id)
    .filter((id): id is ApiId => id !== undefined)
    .map(String)
}

async function saveUser(): Promise<void> {
  await updateAdminUser({
    id: userModal.id,
    status: userModal.status,
    roleIds: userModal.roleIds,
  })
  userModal.show = false
  message.success('用户信息已更新')
  await loadUsers()
}

function confirmBanUser(user: AdminUserVO): void {
  dialog.warning({
    title: '封禁用户',
    content: `确认封禁 ${user.nickname || user.username}？封禁后该用户的登录会立即失效。`,
    positiveText: '封禁',
    negativeText: '取消',
    onPositiveClick: async () => {
      await banAdminUser(user.id)
      message.success('用户已封禁')
      await loadUsers()
    },
  })
}

async function toggleUserStatus(user: AdminUserVO): Promise<void> {
  await updateAdminUser({ id: user.id, status: user.status === 1 ? 0 : 1 })
  message.success(user.status === 1 ? '用户已封禁' : '用户已解禁')
  await loadUsers()
}

function handleUserStatusChange(user: AdminUserVO): void {
  if (user.status === 1) {
    confirmBanUser(user)
    return
  }
  toggleUserStatus(user)
}

async function toggleBlogStatus(blog: AdminBlogVO, value: boolean): Promise<void> {
  await updateAdminBlogStatus({ id: blog.id, isPublished: value ? 1 : 0 })
  message.success(value ? '博客已公开' : '博客已设为私有')
  await loadBlogs()
}

function confirmDeleteBlog(blog: AdminBlogVO): void {
  dialog.warning({
    title: '删除博客',
    content: `确认删除《${blog.title}》？该操作不可撤销。`,
    positiveText: '删除',
    negativeText: '取消',
    onPositiveClick: async () => {
      await deleteAdminBlog(blog.id)
      message.success('博客已删除')
      await loadBlogs()
    },
  })
}

function openTagModal(tag?: AdminTagVO): void {
  tagModal.show = true
  tagModal.mode = tag ? 'edit' : 'create'
  tagModal.id = tag ? String(tag.id) : ''
  tagModal.name = tag?.name ?? ''
  tagModal.description = tag?.description ?? ''
}

async function saveTag(): Promise<void> {
  if (!tagModal.name.trim()) {
    message.warning('请输入标签名')
    return
  }

  if (tagModal.mode === 'create') {
    await createAdminTag({ name: tagModal.name.trim(), description: tagModal.description.trim() })
  } else {
    await updateAdminTag({ id: tagModal.id, name: tagModal.name.trim(), description: tagModal.description.trim() })
  }
  tagModal.show = false
  message.success(tagModal.mode === 'create' ? '标签已创建' : '标签已更新')
  await loadTags()
}

function openRoleModal(role?: AdminRoleVO): void {
  roleModal.show = true
  roleModal.mode = role ? 'edit' : 'create'
  roleModal.id = role ? String(role.id) : ''
  roleModal.roleCode = role?.roleCode ?? ''
  roleModal.roleName = role?.roleName ?? ''
  roleModal.description = role?.description ?? ''
  roleModal.permIds = role?.perms.map((perm) => String(perm.id)) ?? []
}

async function saveRole(): Promise<void> {
  if (!roleModal.roleCode.trim() || !roleModal.roleName.trim()) {
    message.warning('请填写角色编码和角色名')
    return
  }

  const data = {
    roleCode: roleModal.roleCode.trim(),
    roleName: roleModal.roleName.trim(),
    description: roleModal.description.trim(),
    permIds: roleModal.permIds,
  }

  if (roleModal.mode === 'create') {
    await createAdminRole(data)
  } else {
    await updateAdminRole({ id: roleModal.id, ...data })
  }
  roleModal.show = false
  message.success(roleModal.mode === 'create' ? '角色已创建' : '角色已更新')
  await Promise.all([loadRoles(), loadUsers()])
}

function openPermModal(perm?: AdminPermVO): void {
  permModal.show = true
  permModal.mode = perm ? 'edit' : 'create'
  permModal.id = perm ? String(perm.id) : ''
  permModal.permCode = perm?.permCode ?? ''
  permModal.permName = perm?.permName ?? ''
  permModal.description = perm?.description ?? ''
}

async function savePerm(): Promise<void> {
  if (!permModal.permCode.trim() || !permModal.permName.trim()) {
    message.warning('请填写权限编码和权限名')
    return
  }

  const data = {
    permCode: permModal.permCode.trim(),
    permName: permModal.permName.trim(),
    description: permModal.description.trim(),
  }

  if (permModal.mode === 'create') {
    await createAdminPerm(data)
  } else {
    await updateAdminPerm({ id: permModal.id, ...data })
  }
  permModal.show = false
  message.success(permModal.mode === 'create' ? '权限已创建' : '权限已更新')
  await Promise.all([loadPerms(), loadRoles()])
}

watch(userPage, () => loadUsers())
watch(blogPage, () => loadBlogs())
watch(tagPage, () => loadTags())
watch(rolePage, () => loadRoles())
watch(permPage, () => loadPerms())

onMounted(loadAll)
</script>

<template>
  <div class="admin-page">
    <div class="admin-shell">
      <header class="admin-header">
        <div>
          <p class="eyebrow">Para BBS / Control Room</p>
        </div>
        <n-space align="center">
          <n-tag type="success" round>ROLE_ADMIN</n-tag>
          <n-button secondary @click="refreshCurrent">
            <template #icon><n-icon :component="RefreshOutline" /></template>
            刷新
          </n-button>
        </n-space>
      </header>

      <div class="admin-layout">
        <aside class="admin-sidebar">
          <button
            v-for="item in sectionItems"
            :key="item.key"
            type="button"
            class="section-button"
            :class="{ active: activeSection === item.key }"
            @click="activeSection = item.key"
          >
            <n-icon :component="item.icon" />
            <span>{{ item.label }}</span>
            <small>{{ item.count() }}</small>
          </button>
          <div class="sidebar-note">
            <n-icon :component="SettingsOutline" />
            <span>权限变更会在用户下次请求时生效。</span>
          </div>
        </aside>

        <main class="admin-content">
          <section v-if="activeSection === 'users'" class="admin-panel">
            <div class="panel-heading">
              <div>
                <p class="eyebrow">Members</p>
                <h2>用户管理</h2>
              </div>
              <span class="panel-hint">调整状态与角色</span>
            </div>
            <n-space class="filter-row" align="center" wrap>
              <n-input v-model:value="userKeyword" clearable placeholder="搜索用户名或昵称" @keyup.enter="searchUsers" />
              <n-select v-model:value="userStatus" clearable :options="userStatusOptions" placeholder="全部状态" />
              <n-button type="primary" @click="searchUsers">查询</n-button>
            </n-space>
            <n-spin :show="loading.users">
              <div v-if="users.records.length" class="table-wrap">
                <table class="admin-table">
                  <thead><tr><th>用户</th><th>状态</th><th>角色</th><th>加入时间</th><th>操作</th></tr></thead>
                  <tbody>
                    <tr v-for="user in users.records" :key="user.id">
                      <td>
                        <div class="identity-cell">
                          <n-avatar round size="small" :src="resolveAvatarUrl(user.avatarUrl)" />
                          <div><strong>{{ user.nickname || user.username }}</strong><small>@{{ user.username }}</small></div>
                        </div>
                      </td>
                      <td><n-tag :type="user.status === 1 ? 'success' : 'error'" size="small" round>{{ user.status === 1 ? '正常' : '封禁' }}</n-tag></td>
                      <td><div class="tag-list"><n-tag v-for="role in user.roleCodes" :key="role" size="small">{{ role }}</n-tag><span v-if="!user.roleCodes.length" class="muted">未分配</span></div></td>
                      <td>{{ dateOnly(user.createTime) }}</td>
                      <td><n-space><n-button size="small" secondary @click="openUserModal(user)">编辑</n-button><n-button size="small" quaternary :type="user.status === 1 ? 'error' : 'success'" @click="handleUserStatusChange(user)">{{ user.status === 1 ? '封禁' : '解禁' }}</n-button></n-space></td>
                    </tr>
                  </tbody>
                </table>
              </div>
              <n-empty v-else description="暂无用户" />
              <div class="pagination-row"><n-pagination v-model:page="userPage" :page-size="pageSize" :item-count="users.total" /></div>
            </n-spin>
          </section>

          <section v-else-if="activeSection === 'blogs'" class="admin-panel">
            <div class="panel-heading"><div><p class="eyebrow">Content</p><h2>博客管理</h2></div><span class="panel-hint">控制公开状态与删除内容</span></div>
            <n-space class="filter-row" align="center" wrap>
              <n-input v-model:value="blogKeyword" clearable placeholder="搜索博客标题" @keyup.enter="searchBlogs" />
              <n-input v-model:value="blogAuthorId" clearable placeholder="作者 ID" @keyup.enter="searchBlogs" />
              <n-select v-model:value="blogStatus" clearable :options="blogStatusOptions" placeholder="全部状态" />
              <n-button type="primary" @click="searchBlogs">查询</n-button>
            </n-space>
            <n-spin :show="loading.blogs">
              <div v-if="blogs.records.length" class="table-wrap">
                <table class="admin-table blog-admin-table">
                  <thead><tr><th>博客</th><th>作者</th><th>状态</th><th>互动</th><th>更新时间</th><th>操作</th></tr></thead>
                  <tbody>
                    <tr v-for="blog in blogs.records" :key="blog.id">
                      <td><strong>{{ blog.title }}</strong><small class="summary-text">{{ blog.summary || '无摘要' }}</small></td>
                      <td>{{ blog.authorName }}<small class="summary-text">#{{ blog.authorId }}</small></td>
                      <td><n-switch :value="blog.isPublished === 1" @update:value="(value) => toggleBlogStatus(blog, value)"><template #checked>公开</template><template #unchecked>私有</template></n-switch></td>
                      <td>{{ blog.likeCount || 0 }} likes</td>
                      <td>{{ dateOnly(blog.updateTime || blog.createTime) }}</td>
                      <td><n-button size="small" quaternary type="error" @click="confirmDeleteBlog(blog)"><template #icon><n-icon :component="TrashOutline" /></template>删除</n-button></td>
                    </tr>
                  </tbody>
                </table>
              </div>
              <n-empty v-else description="暂无博客" />
              <div class="pagination-row"><n-pagination v-model:page="blogPage" :page-size="pageSize" :item-count="blogs.total" /></div>
            </n-spin>
          </section>

          <section v-else-if="activeSection === 'tags'" class="admin-panel">
            <div class="panel-heading"><div><p class="eyebrow">Taxonomy</p><h2>标签管理</h2></div><n-button type="primary" @click="openTagModal()"><template #icon><n-icon :component="PricetagOutline" /></template>新建标签</n-button></div>
            <n-space class="filter-row" align="center" wrap><n-input v-model:value="tagKeyword" clearable placeholder="搜索标签或描述" @keyup.enter="searchTags" /><n-button type="primary" @click="searchTags">查询</n-button></n-space>
            <n-spin :show="loading.tags">
              <div v-if="tags.records.length" class="table-wrap"><table class="admin-table"><thead><tr><th>名称</th><th>描述</th><th>创建时间</th><th>更新时间</th><th>操作</th></tr></thead><tbody><tr v-for="tag in tags.records" :key="tag.id"><td><n-tag round>{{ tag.name }}</n-tag></td><td>{{ tag.description || '无描述' }}</td><td>{{ dateOnly(tag.createTime) }}</td><td>{{ dateOnly(tag.updateTime) }}</td><td><n-button size="small" secondary @click="openTagModal(tag)"><template #icon><n-icon :component="CreateOutline" /></template>编辑</n-button></td></tr></tbody></table></div>
              <n-empty v-else description="暂无标签" />
              <div class="pagination-row"><n-pagination v-model:page="tagPage" :page-size="pageSize" :item-count="tags.total" /></div>
            </n-spin>
          </section>

          <section v-else-if="activeSection === 'roles'" class="admin-panel">
            <div class="panel-heading"><div><p class="eyebrow">Access Control</p><h2>角色管理</h2></div><n-button type="primary" @click="openRoleModal()"><template #icon><n-icon :component="ShieldCheckmarkOutline" /></template>新建角色</n-button></div>
            <n-space class="filter-row" align="center" wrap><n-input v-model:value="roleKeyword" clearable placeholder="搜索角色编码或名称" @keyup.enter="searchRoles" /><n-button type="primary" @click="searchRoles">查询</n-button></n-space>
            <n-spin :show="loading.roles">
              <div v-if="roles.records.length" class="table-wrap"><table class="admin-table"><thead><tr><th>角色</th><th>描述</th><th>权限</th><th>更新时间</th><th>操作</th></tr></thead><tbody><tr v-for="role in roles.records" :key="role.id"><td><strong>{{ role.roleName }}</strong><small class="summary-text">{{ role.roleCode }}</small></td><td>{{ role.description || '无描述' }}</td><td><div class="tag-list"><n-tag v-for="perm in role.perms" :key="perm.id" size="small" type="info">{{ perm.permCode }}</n-tag><span v-if="!role.perms.length" class="muted">无权限</span></div></td><td>{{ dateOnly(role.updateTime || role.createTime) }}</td><td><n-button size="small" secondary @click="openRoleModal(role)"><template #icon><n-icon :component="CreateOutline" /></template>编辑</n-button></td></tr></tbody></table></div>
              <n-empty v-else description="暂无角色" />
              <div class="pagination-row"><n-pagination v-model:page="rolePage" :page-size="pageSize" :item-count="roles.total" /></div>
            </n-spin>
          </section>

          <section v-else class="admin-panel">
            <div class="panel-heading"><div><p class="eyebrow">Access Control</p><h2>权限管理</h2></div><n-button type="primary" @click="openPermModal()"><template #icon><n-icon :component="KeyOutline" /></template>新建权限</n-button></div>
            <n-space class="filter-row" align="center" wrap><n-input v-model:value="permKeyword" clearable placeholder="搜索权限编码或名称" @keyup.enter="searchPerms" /><n-button type="primary" @click="searchPerms">查询</n-button></n-space>
            <n-spin :show="loading.perms">
              <div v-if="perms.records.length" class="table-wrap"><table class="admin-table"><thead><tr><th>权限</th><th>名称</th><th>描述</th><th>创建时间</th><th>操作</th></tr></thead><tbody><tr v-for="perm in perms.records" :key="perm.id"><td><n-tag type="info" round>{{ perm.permCode }}</n-tag></td><td>{{ perm.permName }}</td><td>{{ perm.description || '无描述' }}</td><td>{{ dateOnly(perm.createTime) }}</td><td><n-button size="small" secondary @click="openPermModal(perm)"><template #icon><n-icon :component="CreateOutline" /></template>编辑</n-button></td></tr></tbody></table></div>
              <n-empty v-else description="暂无权限" />
              <div class="pagination-row"><n-pagination v-model:page="permPage" :page-size="100" :item-count="perms.total" /></div>
            </n-spin>
          </section>
        </main>
      </div>
    </div>

    <n-modal v-model:show="userModal.show"><div class="admin-modal"><p class="eyebrow">Member Access</p><h2>编辑用户</h2><p class="modal-subtitle">{{ userModal.nickname || userModal.id }}</p><n-form label-placement="top"><n-form-item label="状态"><n-select v-model:value="userModal.status" :options="userStatusOptions" /></n-form-item><n-form-item label="角色"><n-select v-model:value="userModal.roleIds" multiple :options="roleOptions" placeholder="选择角色" /></n-form-item></n-form><div class="modal-actions"><n-button @click="userModal.show = false">取消</n-button><n-button type="primary" @click="saveUser">保存</n-button></div></div></n-modal>
    <n-modal v-model:show="tagModal.show"><div class="admin-modal"><p class="eyebrow">Taxonomy</p><h2>{{ tagModal.mode === 'create' ? '新建标签' : '编辑标签' }}</h2><n-form label-placement="top"><n-form-item label="名称"><n-input v-model:value="tagModal.name" maxlength="20" show-count /></n-form-item><n-form-item label="描述"><n-input v-model:value="tagModal.description" type="textarea" maxlength="200" show-count /></n-form-item></n-form><div class="modal-actions"><n-button @click="tagModal.show = false">取消</n-button><n-button type="primary" @click="saveTag">保存</n-button></div></div></n-modal>
    <n-modal v-model:show="roleModal.show"><div class="admin-modal admin-modal-wide"><p class="eyebrow">Access Control</p><h2>{{ roleModal.mode === 'create' ? '新建角色' : '编辑角色' }}</h2><n-form label-placement="top"><n-form-item label="角色编码"><n-input v-model:value="roleModal.roleCode" maxlength="20" /></n-form-item><n-form-item label="角色名"><n-input v-model:value="roleModal.roleName" maxlength="20" /></n-form-item><n-form-item label="描述"><n-input v-model:value="roleModal.description" maxlength="255" /></n-form-item><n-form-item label="权限"><n-checkbox-group v-model:value="roleModal.permIds"><n-space vertical><n-checkbox v-for="perm in perms.records" :key="perm.id" :value="String(perm.id)">{{ perm.permCode }} · {{ perm.permName }}</n-checkbox></n-space></n-checkbox-group></n-form-item></n-form><div class="modal-actions"><n-button @click="roleModal.show = false">取消</n-button><n-button type="primary" @click="saveRole">保存</n-button></div></div></n-modal>
    <n-modal v-model:show="permModal.show"><div class="admin-modal"><p class="eyebrow">Access Control</p><h2>{{ permModal.mode === 'create' ? '新建权限' : '编辑权限' }}</h2><n-form label-placement="top"><n-form-item label="权限编码"><n-input v-model:value="permModal.permCode" maxlength="20" /></n-form-item><n-form-item label="权限名"><n-input v-model:value="permModal.permName" maxlength="20" /></n-form-item><n-form-item label="描述"><n-input v-model:value="permModal.description" maxlength="200" /></n-form-item></n-form><div class="modal-actions"><n-button @click="permModal.show = false">取消</n-button><n-button type="primary" @click="savePerm">保存</n-button></div></div></n-modal>
  </div>
</template>

<style scoped>

.admin-page {
  min-height: 100vh;
  padding: 38px 20px 90px;
  color: var(--text-primary);
}

.admin-shell {
  width: min(1420px, 100%);
  margin: 0 auto;
}

.admin-header,
.panel-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 20px;
}

.admin-header {
  margin-bottom: 28px;
}

.eyebrow {
  margin: 0 0 8px;
  color: var(--accent-highlight);
  font-size: 0.72rem;
  font-weight: 700;
  letter-spacing: 0;
  text-transform: uppercase;
}

h1,
h2 {
  margin: 0;
  line-height: 1.1;
}

h1 {
  font-size: 32px;
}

h2 {
  font-size: 1.65rem;
}

.panel-hint,
.modal-subtitle {
  color: var(--text-secondary);
}

.admin-layout {
  display: grid;
  grid-template-columns: 220px minmax(0, 1fr);
  gap: 22px;
  align-items: start;
}

.admin-sidebar,
.admin-panel,
.admin-modal {
  border: 1px solid var(--line-color);
  background: color-mix(in srgb, var(--bg-primary) 92%, transparent);
  box-shadow: none;
  border-radius: 18px;
}

.admin-sidebar {
  position: sticky;
  top: 92px;
  display: grid;
  gap: 7px;
  padding: 12px;
}

.section-button {
  display: grid;
  grid-template-columns: 22px 1fr auto;
  align-items: center;
  gap: 10px;
  width: 100%;
  padding: 12px 11px;
  border: 0;
  background: transparent;
  color: var(--text-secondary);
  cursor: pointer;
  font: inherit;
  text-align: left;
  transition: background 0.2s ease, color 0.2s ease, transform 0.2s ease;
  border-radius: 18px;
}

.section-button:hover,
.section-button.active {
  background: var(--card-hover);
  color: var(--text-primary);
  transform: translateX(3px);
}

.section-button.active {
  color: var(--accent-color);
}

.section-button small {
  color: var(--text-tertiary);
  font-size: 0.75rem;
}

.sidebar-note {
  display: flex;
  gap: 8px;
  margin-top: 14px;
  padding: 13px 9px 3px;
  border-top: 1px dashed var(--line-color);
  color: var(--text-tertiary);
  font-size: 0.78rem;
  line-height: 1.55;
}

.admin-panel {
  min-width: 0;
  padding: 26px;
}

.panel-heading {
  align-items: center;
  margin-bottom: 22px;
}

.filter-row {
  margin-bottom: 20px;
}

.filter-row :deep(.n-input) {
  min-width: 220px;
}

.filter-row :deep(.n-select) {
  min-width: 140px;
}

.table-wrap {
  overflow-x: auto;
  border: 1px solid var(--line-color);
}

.admin-table {
  width: 100%;
  min-width: 820px;
  border-collapse: collapse;
  text-align: left;
}

.admin-table th,
.admin-table td {
  padding: 15px 14px;
  border-bottom: 1px solid var(--line-color);
  vertical-align: middle;
}

.admin-table th {
  color: var(--text-tertiary);
  font-size: 0.72rem;
  letter-spacing: 0;
  text-transform: uppercase;
}

.admin-table tbody tr:last-child td {
  border-bottom: 0;
}

.admin-table tbody tr:hover {
  background: var(--card-hover);
}

.admin-table td strong,
.admin-table td small {
  display: block;
}

.admin-table td strong {
  color: var(--text-primary);
}

.summary-text,
.identity-cell small {
  margin-top: 4px;
  color: var(--text-tertiary);
  font-size: 0.78rem;
}

.identity-cell {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 190px;
}

.tag-list {
  display: flex;
  flex-wrap: wrap;
  gap: 5px;
  max-width: 280px;
}

.muted {
  color: var(--text-tertiary);
  font-size: 0.84rem;
}

.pagination-row {
  display: flex;
  justify-content: center;
  padding-top: 22px;
}

.admin-modal {
  width: min(92vw, 460px);
  padding: 28px;
  border-radius: 18px;
}

.admin-modal-wide {
  width: min(92vw, 620px);
  border-radius: 18px;
}

.admin-modal h2 {
  margin-bottom: 20px;
  border-radius: 18px;
}

.modal-subtitle {
  margin: -12px 0 20px;
}

.modal-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 22px;
}

@media (max-width: 920px) {
  .admin-layout {
    grid-template-columns: 1fr;
  }

  .admin-sidebar {
    position: static;
    grid-template-columns: repeat(5, minmax(0, 1fr));
  }

  .section-button {
    grid-template-columns: 1fr;
    justify-items: center;
    text-align: center;
    border-radius: 18px;
  }

  .section-button small,
  .sidebar-note {
    display: none;
  }
}

@media (max-width: 620px) {
  .admin-page {
    padding-inline: 12px;
  }

  .admin-header,
  .panel-heading {
    align-items: stretch;
    flex-direction: column;
  }

  .admin-panel {
    padding: 18px 14px;
  }

  .admin-sidebar {
    gap: 2px;
    padding: 6px;
  }

  .section-button {
    padding: 10px 4px;
    font-size: 0.8rem;
    border-radius: 18px;
  }
}

</style>
