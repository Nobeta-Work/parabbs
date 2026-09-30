import type { ApiId, PageQuery, PageResult } from './api'

export interface AdminLoginDTO {
  username: string
  password: string
}

export interface AdminUserVO {
  id: ApiId
  username: string
  nickname: string
  avatarUrl: string | null
  status: 0 | 1
  createTime: string
  roleCodes: string[]
}

export interface AdminUserPageQuery extends PageQuery {
  keyword?: string
  status?: 0 | 1
}

export interface AdminUserEditDTO {
  id: ApiId
  status?: 0 | 1
  roleIds?: ApiId[]
}

export interface AdminBlogVO {
  id: ApiId
  title: string
  summary: string | null
  coverUrl: string | null
  authorId: ApiId
  authorName: string
  isPublished: 0 | 1
  likeCount: number
  createTime: string
  updateTime: string
}

export interface AdminBlogPageQuery extends PageQuery {
  keyword?: string
  authorId?: ApiId
  isPublished?: 0 | 1
}

export interface AdminBlogStatusDTO {
  id: ApiId
  isPublished: 0 | 1
}

export interface AdminTagVO {
  id: ApiId
  name: string
  description: string | null
  createTime: string
  updateTime: string
}

export interface AdminTagPageQuery extends PageQuery {
  keyword?: string
}

export interface AdminTagSaveDTO {
  name: string
  description?: string
}

export interface AdminTagEditDTO extends AdminTagSaveDTO {
  id: ApiId
}

export interface AdminPermVO {
  id: ApiId
  permCode: string
  permName: string
  description: string | null
  createTime: string
}

export interface AdminPermPageQuery extends PageQuery {
  keyword?: string
}

export interface AdminPermSaveDTO {
  permCode: string
  permName: string
  description?: string
}

export interface AdminPermEditDTO extends AdminPermSaveDTO {
  id: ApiId
}

export interface AdminRoleVO {
  id: ApiId
  roleCode: string
  roleName: string
  description: string | null
  createTime: string
  updateTime: string
  perms: AdminPermVO[]
}

export interface AdminRolePageQuery extends PageQuery {
  keyword?: string
}

export interface AdminRoleSaveDTO {
  roleCode: string
  roleName: string
  description?: string
  permIds?: ApiId[]
}

export interface AdminRoleEditDTO extends AdminRoleSaveDTO {
  id: ApiId
}

export type AdminPageResult<T> = PageResult<T>
