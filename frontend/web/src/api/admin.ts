import type {
  AdminBlogPageQuery,
  AdminBlogStatusDTO,
  AdminBlogVO,
  AdminPermEditDTO,
  AdminPermPageQuery,
  AdminPermSaveDTO,
  AdminPermVO,
  AdminRoleEditDTO,
  AdminRolePageQuery,
  AdminRoleSaveDTO,
  AdminRoleVO,
  AdminTagEditDTO,
  AdminTagPageQuery,
  AdminTagSaveDTO,
  AdminTagVO,
  AdminUserEditDTO,
  AdminUserPageQuery,
  AdminUserVO,
  PageResult,
} from '@/types'
import { normalizePageResult } from '@/utils/page'
import request from '@/utils/request'

export async function getAdminUserPage(query: AdminUserPageQuery): Promise<PageResult<AdminUserVO>> {
  const page = await request<PageResult<AdminUserVO>>({
    url: '/admin/user/page',
    method: 'get',
    params: query,
  })
  return normalizePageResult(page)
}

export function updateAdminUser(data: AdminUserEditDTO): Promise<void> {
  return request<void>({
    url: '/admin/user',
    method: 'put',
    data,
  })
}

export function banAdminUser(userId: string | number): Promise<void> {
  return request<void>({
    url: '/admin/user',
    method: 'delete',
    params: { userId },
  })
}

export async function getAdminBlogPage(query: AdminBlogPageQuery): Promise<PageResult<AdminBlogVO>> {
  const page = await request<PageResult<AdminBlogVO>>({
    url: '/admin/blog/page',
    method: 'get',
    params: query,
  })
  return normalizePageResult(page)
}

export function updateAdminBlogStatus(data: AdminBlogStatusDTO): Promise<void> {
  return request<void>({
    url: '/admin/blog',
    method: 'put',
    data,
  })
}

export function deleteAdminBlog(blogId: string | number): Promise<void> {
  return request<void>({
    url: '/admin/blog',
    method: 'delete',
    params: { blogId },
  })
}

export async function getAdminTagPage(query: AdminTagPageQuery): Promise<PageResult<AdminTagVO>> {
  const page = await request<PageResult<AdminTagVO>>({
    url: '/admin/tag/page',
    method: 'get',
    params: query,
  })
  return normalizePageResult(page)
}

export function createAdminTag(data: AdminTagSaveDTO): Promise<void> {
  return request<void>({
    url: '/admin/tag',
    method: 'post',
    data,
  })
}

export function updateAdminTag(data: AdminTagEditDTO): Promise<void> {
  return request<void>({
    url: '/admin/tag',
    method: 'put',
    data,
  })
}

export async function getAdminRolePage(query: AdminRolePageQuery): Promise<PageResult<AdminRoleVO>> {
  const page = await request<PageResult<AdminRoleVO>>({
    url: '/admin/role/page',
    method: 'get',
    params: query,
  })
  return normalizePageResult(page)
}

export function createAdminRole(data: AdminRoleSaveDTO): Promise<void> {
  return request<void>({
    url: '/admin/role',
    method: 'post',
    data,
  })
}

export function updateAdminRole(data: AdminRoleEditDTO): Promise<void> {
  return request<void>({
    url: '/admin/role',
    method: 'put',
    data,
  })
}

export async function getAdminPermPage(query: AdminPermPageQuery): Promise<PageResult<AdminPermVO>> {
  const page = await request<PageResult<AdminPermVO>>({
    url: '/admin/perm/page',
    method: 'get',
    params: query,
  })
  return normalizePageResult(page)
}

export function createAdminPerm(data: AdminPermSaveDTO): Promise<void> {
  return request<void>({
    url: '/admin/perm',
    method: 'post',
    data,
  })
}

export function updateAdminPerm(data: AdminPermEditDTO): Promise<void> {
  return request<void>({
    url: '/admin/perm',
    method: 'put',
    data,
  })
}
