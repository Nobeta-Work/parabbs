import type { ApiId, PageQuery } from './api'
import type { TagBriefVO } from './tag'
import type { UserBriefVO } from './user'

export type PublishStatus = 0 | 1

export interface BlogBaseVO {
    id: ApiId
    title: string
    summary: string | null
    coverUrl: string | null
    isPublished: PublishStatus
    likeCount: number
    commentsCount: number
    createTime: string
    updateTime: string
    author: UserBriefVO
    tags: TagBriefVO[]
}

export interface BlogPublicBriefVO extends BlogBaseVO {}

export interface BlogPublicDetailVO extends BlogPublicBriefVO {
    content: string | null
    isLiked: boolean
}

export interface BlogPrivateBriefVO extends BlogBaseVO {
    folderId: ApiId
}

export interface BlogPrivateDetailVO extends BlogPrivateBriefVO {
    content: string | null
}

export interface BlogPageQuery extends PageQuery {
    keyword?: string
    authorId?: ApiId
    tagIds?: ApiId[]
}

export interface BlogSaveDTO {
    title: string
    coverUrl: string | null
    folderId?: ApiId
}

export interface BlogEditDTO {
    coverUrl: string | null
    folderId: ApiId
    isPublished: PublishStatus
    title: string
    summary?: string
    content?: string
    tagIds?: ApiId[]
}
