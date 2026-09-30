import type { ApiId } from './api'

export type UserRole = 'ROLE_USER' | 'ROLE_ADMIN' | (string & {})
export type UserSex = 0 | 1 | 2

export interface UserInfo {
    id: number | null
    username: string | null
    nickname: string | null
    avatarUrl: string | null
    backgroundImageUrl?: string | null
    token: string | null
    refreshToken?: string | null
    tokenExpireIn?: string | null
    sex?: number | null
    race?: string | null
    signature?: string | null
    roles?: UserRole[]
    createTime?: string | null
}

export interface UserState {
    token: string | null
    refreshToken: string | null
    tokenExpireIn: string | null
    userInfo: UserInfo | null
}

export interface LoginDTO {
    username: string
    password: string
}

export interface RegisterDTO {
    username: string
    password: string
    nickname: string
    sex: UserSex
    race: string
}

export interface TokenVO {
    accessToken: string
    refreshToken: string
    expireIn: string
}

export interface UserBriefVO {
    id: ApiId
    nickname: string
    avatarUrl: string | null
}

export interface UserInfoVO {
    id: ApiId
    nickname: string
    avatarUrl: string | null
    backgroundImageUrl: string | null
    sex: UserSex
    race: string
    signature: string | null
    createTime: string
}

export interface UserProfileVO extends UserInfoVO {
    username: string
    roles: UserRole[]
}

export interface UserProfileDTO {
    nickname: string
    sex: UserSex
    race: string
    signature: string
    backgroundImageUrl: string | null
}

export interface PasswordEditDTO {
    oldPassword: string
    newPassword: string
}

export interface AvatarVO {
    avatarUrl: string
}
